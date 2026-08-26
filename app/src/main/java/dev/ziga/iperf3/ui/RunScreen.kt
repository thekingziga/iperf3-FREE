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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ziga.iperf3.Direction
import dev.ziga.iperf3.DirectionSummary
import dev.ziga.iperf3.EngineState
import dev.ziga.iperf3.Format
import dev.ziga.iperf3.Protocol
import dev.ziga.iperf3.Role
import dev.ziga.iperf3.RunPhase
import kotlinx.coroutines.delay

@Composable
fun RunScreen(
    state: EngineState,
    onStop: () -> Unit,
    onDone: () -> Unit,
    onRunAgain: () -> Unit,
) {
    val config = state.config ?: return
    // Keeps the elapsed clock moving between interval reports.
    val now by produceState(System.currentTimeMillis(), state.phase) {
        while (state.phase.isActive) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = state.latest?.time
        ?: if (state.phase.isActive) (now - state.startedAt) / 1000.0 else 0.0

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LiveCard(state, elapsed) }
            if (state.samples.size > 1) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ThroughputChart(state.samples, config.direction)
                        }
                    }
                }
            }
            state.summary?.let { summary ->
                item { SummaryCard(summary, state) }
            }
            state.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Test failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item { LogCard(state) }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.phase.isActive) {
                    Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Text(if (config.role == Role.SERVER) "  Stop server" else "  Stop test")
                    }
                } else {
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Back to setup") }
                    Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("  Run again")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(state: EngineState, elapsed: Double) {
    val config = state.config ?: return
    val latest = state.latest
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    config.role == Role.SERVER && state.connections == 0 -> "Waiting for a client"
                    config.role == Role.SERVER -> "Serving test #${state.connections}"
                    state.phase == RunPhase.STARTING -> "Connecting to ${config.host}"
                    state.phase == RunPhase.DONE -> "Finished"
                    state.phase == RunPhase.CANCELLED -> "Stopped"
                    state.phase == RunPhase.FAILED -> "Failed"
                    else -> config.describe()
                },
                style = MaterialTheme.typography.titleMedium,
            )

            // While running the headline is the current interval; once the run is
            // over it becomes the result for the whole test.
            val summary = state.summary.takeIf { state.phase.isTerminal }
            if (config.direction == Direction.BIDIR) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigSpeed("Upload", summary?.sent?.bitsPerSecond ?: latest?.bitsPerSecond ?: 0.0)
                    BigSpeed(
                        "Download",
                        summary?.let { it.reverseReceived ?: it.reverseSent }?.bitsPerSecond
                            ?: latest?.reverseBitsPerSecond ?: 0.0,
                    )
                }
            } else {
                BigSpeed(
                    label = if (config.role == Role.SERVER) "Throughput" else when (config.direction) {
                        Direction.DOWNLOAD -> "Download"
                        Direction.UPLOAD -> "Upload"
                        Direction.BIDIR -> "Throughput"
                    },
                    bitsPerSecond = summary?.headline(config.direction)?.bitsPerSecond
                        ?: latest?.bitsPerSecond ?: 0.0,
                )
            }

            val progress = state.progress
            if (state.phase.isActive) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Text(
                text = buildString {
                    append(Format.seconds(elapsed))
                    config.totalSeconds?.let { append(" of ${Format.seconds(it.toDouble())}") }
                    append(" · ")
                    append(Format.bytes(state.samples.sumOf { it.bytes }))
                    append(" transferred")
                    state.networkLabel?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            latest?.let { sample ->
                val extras = buildList {
                    sample.retransmits?.let { add("retransmits $it") }
                    sample.jitterMs?.let { add("jitter ${Format.milliseconds(it)}") }
                    sample.lostPercent?.let { add("loss ${Format.percent(it)}") }
                }
                if (extras.isNotEmpty()) {
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigSpeed(label: String, bitsPerSecond: Double) {
    val (value, unit) = Format.bitrateParts(bitsPerSecond)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displayMedium)
            Text(" $unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(summary: dev.ziga.iperf3.RunSummary, state: EngineState) {
    val config = state.config ?: return
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Result", style = MaterialTheme.typography.titleMedium)
            summary.sent?.let { DirectionRow(if (config.direction == Direction.BIDIR) "Sent (up)" else "Sent", it, config.protocol) }
            summary.received?.let { DirectionRow(if (config.direction == Direction.BIDIR) "Received (up leg)" else "Received", it, config.protocol) }
            summary.reverseSent?.let { DirectionRow("Sent (down leg)", it, config.protocol) }
            summary.reverseReceived?.let { DirectionRow("Received (down)", it, config.protocol) }
            val cpu = listOfNotNull(
                summary.cpuLocal?.let { "phone ${Format.percent(it)}" },
                summary.cpuRemote?.let { "server ${Format.percent(it)}" },
            )
            if (cpu.isNotEmpty()) {
                Text("CPU: ${cpu.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionRow(label: String, summary: DirectionSummary, protocol: Protocol) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(Format.bitrate(summary.bitsPerSecond), style = MaterialTheme.typography.titleMedium)
        }
        val details = buildList {
            add("${Format.bytes(summary.bytes)} in ${Format.seconds(summary.seconds)}")
            summary.retransmits?.let { add("$it retransmits") }
            if (protocol == Protocol.UDP) {
                summary.jitterMs?.let { add("jitter ${Format.milliseconds(it)}") }
                summary.lostPercent?.let { add("loss ${Format.percent(it)}") }
                summary.packets?.let { add("$it packets") }
            }
        }
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogCard(state: EngineState) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("iperf3 log", style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "Hide" else "Show ${state.log.size} lines", style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                state.log.takeLast(120).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
