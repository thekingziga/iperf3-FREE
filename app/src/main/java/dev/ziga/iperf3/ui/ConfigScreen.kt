package dev.ziga.iperf3.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ziga.iperf3.Direction
import dev.ziga.iperf3.IpVersion
import dev.ziga.iperf3.IperfConfig
import dev.ziga.iperf3.Profile
import dev.ziga.iperf3.Protocol
import dev.ziga.iperf3.Role
import dev.ziga.iperf3.TestLimit

private val DURATION_PRESETS = listOf(10 to "10 s", 30 to "30 s", 60 to "1 min", 300 to "5 min", 1800 to "30 min")

@Composable
fun ConfigScreen(
    config: IperfConfig,
    profiles: List<Profile>,
    onChange: ((IperfConfig) -> IperfConfig) -> Unit,
    onApplyProfile: (IperfConfig) -> Unit,
    onSaveProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onStart: () -> Unit,
) {
    val error = config.validate()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Mode") {
                    ChoiceRow(
                        label = null,
                        options = listOf(Role.CLIENT to "Client", Role.SERVER to "Server"),
                        selected = config.role,
                        onSelect = { role -> onChange { it.copy(role = role) } },
                    )
                    Text(
                        if (config.role == Role.CLIENT) {
                            "Runs a test against an iperf3 server."
                        } else {
                            "Listens for incoming iperf3 tests on this device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (config.role == Role.CLIENT) {
                item { ConnectionCard(config, onChange) }
                item { DurationCard(config, onChange) }
                item { LoadCard(config, onChange) }
            } else {
                item { ServerCard(config, onChange) }
            }

            item { AdvancedCard(config, onChange) }
            item { ProfilesCard(config, profiles, onApplyProfile, onSaveProfile, onDeleteProfile) }
        }

        Surface(tonalElevation = 3.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onStart,
                    enabled = error == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        if (config.role == Role.SERVER) "  Start server" else "  Start test",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(config: IperfConfig, onChange: (((IperfConfig) -> IperfConfig)) -> Unit) {
    SectionCard("Server") {
        LabeledField(
            label = "Address",
            value = config.host,
            onChange = { host -> onChange { it.copy(host = host) } },
            placeholder = "192.168.1.10 or iperf.example.net",
            keyboardType = KeyboardType.Uri,
        )
        IntField(
            label = "Port",
            value = config.port,
            range = 1..65535,
            onChange = { port -> onChange { it.copy(port = port) } },
        )
        ChoiceRow(
            label = "Direction",
            options = listOf(
                Direction.DOWNLOAD to "Download (-R)",
                Direction.UPLOAD to "Upload",
                Direction.BIDIR to "Both (--bidir)",
            ),
            selected = config.direction,
            onSelect = { direction -> onChange { it.copy(direction = direction) } },
        )
        ChoiceRow(
            label = "Protocol",
            options = listOf(Protocol.TCP to "TCP", Protocol.UDP to "UDP"),
            selected = config.protocol,
            onSelect = { protocol -> onChange { it.copy(protocol = protocol) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationCard(config: IperfConfig, onChange: (((IperfConfig) -> IperfConfig)) -> Unit) {
    SectionCard("How long") {
        ChoiceRow(
            label = "Stop after",
            options = listOf(
                TestLimit.TIME to "Time",
                TestLimit.BYTES to "Amount of data",
                TestLimit.BLOCKS to "Blocks",
            ),
            selected = config.limit,
            onSelect = { limit -> onChange { it.copy(limit = limit) } },
        )
        when (config.limit) {
            TestLimit.TIME -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_PRESETS.forEach { (seconds, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = config.durationSeconds == seconds,
                            onClick = { onChange { it.copy(durationSeconds = seconds) } },
                            label = { Text(label) },
                        )
                    }
                }
                IntField(
                    label = "Duration (seconds)",
                    value = config.durationSeconds,
                    range = 1..86_400,
                    onChange = { seconds -> onChange { it.copy(durationSeconds = seconds) } },
                    supporting = "Up to 24 hours. The test keeps running in the background.",
                )
            }
            TestLimit.BYTES -> LabeledField(
                label = "Amount of data",
                value = config.bytes,
                onChange = { bytes -> onChange { it.copy(bytes = bytes) } },
                placeholder = "100M",
                supporting = "Suffixes K, M, G (1 K = 1024).",
            )
            TestLimit.BLOCKS -> IntField(
                label = "Blocks",
                value = config.blocks,
                onChange = { blocks -> onChange { it.copy(blocks = blocks) } },
                supporting = "Number of buffers to send (-k).",
            )
        }
        IntField(
            label = "Warm-up to ignore (seconds)",
            value = config.omitSeconds,
            range = 0..60,
            onChange = { omit -> onChange { it.copy(omitSeconds = omit) } },
            supporting = "-O, skips TCP slow start in the results.",
        )
    }
}

@Composable
private fun LoadCard(config: IperfConfig, onChange: (((IperfConfig) -> IperfConfig)) -> Unit) {
    SectionCard("Load") {
        IntField(
            label = "Parallel streams",
            value = config.parallelStreams,
            range = 1..128,
            onChange = { streams -> onChange { it.copy(parallelStreams = streams) } },
            supporting = "-P, use several to saturate fast links.",
        )
        LabeledField(
            label = "Target bitrate",
            value = config.targetBitrate,
            onChange = { rate -> onChange { it.copy(targetBitrate = rate) } },
            placeholder = if (config.protocol == Protocol.UDP) "1M (iperf3 default)" else "unlimited",
            supporting = "-b, e.g. 10M or 1.5G. Leave empty for the iperf3 default.",
        )
        LabeledField(
            label = "Report interval (seconds)",
            value = config.intervalSeconds.toString(),
            onChange = { text ->
                text.toDoubleOrNull()?.let { value -> onChange { it.copy(intervalSeconds = value) } }
            },
            keyboardType = KeyboardType.Decimal,
            supporting = "-i, how often speed is sampled and the notification updates.",
        )
    }
}

@Composable
private fun ServerCard(config: IperfConfig, onChange: (((IperfConfig) -> IperfConfig)) -> Unit) {
    SectionCard("Server") {
        IntField(
            label = "Listen port",
            value = config.port,
            range = 1..65535,
            onChange = { port -> onChange { it.copy(port = port) } },
        )
        LabeledField(
            label = "Bind address (optional)",
            value = config.bindAddress,
            onChange = { address -> onChange { it.copy(bindAddress = address) } },
            placeholder = "0.0.0.0",
        )
        SwitchRow(
            label = "Single test then stop",
            description = "-1, exits after the first client disconnects.",
            checked = config.serverOneOff,
            onChange = { oneOff -> onChange { it.copy(serverOneOff = oneOff) } },
        )
    }
}

@Composable
private fun AdvancedCard(config: IperfConfig, onChange: (((IperfConfig) -> IperfConfig)) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard("Advanced") {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "Hide options" else "Show socket and QoS options",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        if (expanded) {
            ChoiceRow(
                label = "IP version",
                options = listOf(IpVersion.AUTO to "Auto", IpVersion.IPV4 to "IPv4", IpVersion.IPV6 to "IPv6"),
                selected = config.ipVersion,
                onSelect = { version -> onChange { it.copy(ipVersion = version) } },
            )
            if (config.role == Role.CLIENT) {
                LabeledField(
                    label = "Buffer length",
                    value = config.blockSize,
                    onChange = { size -> onChange { it.copy(blockSize = size) } },
                    placeholder = "128K",
                    supporting = "-l, bytes read/written per call.",
                )
                LabeledField(
                    label = "Window / socket buffer",
                    value = config.windowSize,
                    onChange = { size -> onChange { it.copy(windowSize = size) } },
                    placeholder = "512K",
                    supporting = "-w",
                )
                if (config.protocol == Protocol.TCP) {
                    LabeledField(
                        label = "MSS",
                        value = config.mss,
                        onChange = { mss -> onChange { it.copy(mss = mss) } },
                        placeholder = "1400",
                        keyboardType = KeyboardType.Number,
                        supporting = "-M",
                    )
                    LabeledField(
                        label = "Congestion control",
                        value = config.congestion,
                        onChange = { algorithm -> onChange { it.copy(congestion = algorithm) } },
                        placeholder = "cubic, bbr, reno…",
                        supporting = "-C, must be available on this kernel.",
                    )
                    SwitchRow(
                        label = "Disable Nagle (-N)",
                        checked = config.noDelay,
                        onChange = { value -> onChange { it.copy(noDelay = value) } },
                    )
                }
                LabeledField(
                    label = "DSCP / ToS",
                    value = config.dscp,
                    onChange = { dscp -> onChange { it.copy(dscp = dscp) } },
                    placeholder = "46 or AF11",
                    supporting = "--dscp",
                )
                LabeledField(
                    label = "Bind to local address",
                    value = config.bindAddress,
                    onChange = { address -> onChange { it.copy(bindAddress = address) } },
                    placeholder = "e.g. the Wi-Fi address",
                    supporting = "-B",
                )
                IntField(
                    label = "Connect timeout (ms)",
                    value = config.connectTimeoutMs,
                    range = 0..600_000,
                    onChange = { timeout -> onChange { it.copy(connectTimeoutMs = timeout) } },
                    supporting = "--connect-timeout, 0 uses the system default.",
                )
                SwitchRow(
                    label = "Zero copy (-Z)",
                    description = "Send with sendfile() where supported.",
                    checked = config.zeroCopy,
                    onChange = { value -> onChange { it.copy(zeroCopy = value) } },
                )
                SwitchRow(
                    label = "Fetch server output",
                    description = "--get-server-output, adds the server's log to the run log.",
                    checked = config.getServerOutput,
                    onChange = { value -> onChange { it.copy(getServerOutput = value) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilesCard(
    config: IperfConfig,
    profiles: List<Profile>,
    onApplyProfile: (IperfConfig) -> Unit,
    onSaveProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    SectionCard("Saved setups") {
        if (profiles.isEmpty()) {
            Text(
                "Save this configuration to run it again with one tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    InputChip(
                        selected = profile.config == config,
                        onClick = { onApplyProfile(profile.config) },
                        label = { Text(profile.name) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Delete ${profile.name}",
                                modifier = Modifier.clickable { onDeleteProfile(profile.name) },
                            )
                        },
                    )
                }
            }
        }
        TextButton(onClick = { name = ""; showDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Save current setup")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Save setup") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveProfile(name); showDialog = false },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}
