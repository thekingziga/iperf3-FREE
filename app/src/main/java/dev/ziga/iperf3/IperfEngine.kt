package dev.ziga.iperf3

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Runs the bundled iperf3 executable and turns its `--json-stream` output into
 * [EngineState].
 *
 * A process-wide singleton on purpose: exactly one test may run at a time, and
 * both the foreground service and the UI observe the same run.
 */
object IperfEngine {

    private const val MAX_LOG_LINES = 300
    private const val MAX_STORED_SAMPLES = 600

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var process: Process? = null
    @Volatile private var stopRequested = false
    private var job: Job? = null

    /**
     * The iperf3 binary is shipped as a jniLib so the installer extracts it into
     * nativeLibraryDir, the only app-owned directory Android allows exec from.
     */
    fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libiperf3.so")

    fun start(context: Context, config: IperfConfig): Boolean {
        if (_state.value.phase.isActive) return false
        val app = context.applicationContext
        stopRequested = false
        _state.value = EngineState(
            phase = RunPhase.STARTING,
            config = config,
            startedAt = System.currentTimeMillis(),
            networkLabel = NetworkLabel.current(app),
        )
        job = scope.launch { execute(app, config) }
        return true
    }

    /** Asks iperf3 to shut down; falls back to a kill if it ignores SIGTERM. */
    fun stop() {
        val running = process ?: return
        stopRequested = true
        running.destroy()
        scope.launch {
            runInterruptible { running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) }
                .let { exited -> if (!exited) running.destroyForcibly() }
        }
    }

    /** Clears a finished run so the UI goes back to the setup form. */
    fun reset() {
        if (_state.value.phase.isActive) return
        _state.value = EngineState()
    }

    private suspend fun execute(app: Context, config: IperfConfig) {
        val binary = binary(app)
        if (!binary.exists()) {
            finish(app, exitCode = -1, startupError = "iperf3 binary missing for ${Build.SUPPORTED_ABIS.firstOrNull()}")
            return
        }
        val args = config.toArgs()
        appendLog("$ iperf3 ${args.joinToString(" ")}")

        val process = try {
            ProcessBuilder(listOf(binary.absolutePath) + args)
                .directory(app.filesDir)
                .start()
        } catch (e: IOException) {
            finish(app, exitCode = -1, startupError = e.message ?: "Could not start iperf3")
            return
        }
        this.process = process
        _state.update { it.copy(phase = RunPhase.RUNNING) }

        val stderrJob = scope.launch {
            runCatching { process.errorStream.bufferedReader().forEachLine(::appendLog) }
        }
        runCatching {
            process.inputStream.bufferedReader().forEachLine(::handleLine)
        }.onFailure { failure ->
            if (!stopRequested) appendLog("output stream ended: ${failure.message}")
        }
        val exitCode = runCatching { runInterruptible { process.waitFor() } }.getOrDefault(-1)
        stderrJob.join()
        this.process = null
        finish(app, exitCode)
    }

    private suspend fun finish(app: Context, exitCode: Int, startupError: String? = null) {
        val current = _state.value
        val phase = when {
            startupError != null -> RunPhase.FAILED
            stopRequested -> RunPhase.CANCELLED
            current.error != null || exitCode != 0 -> RunPhase.FAILED
            else -> RunPhase.DONE
        }
        val error = when (phase) {
            RunPhase.FAILED -> startupError
                ?: current.error
                ?: current.log.lastOrNull { it.isNotBlank() && !it.startsWith("$ ") }
                ?: "iperf3 exited with code $exitCode"
            else -> null
        }
        val finished = current.copy(
            phase = phase,
            finishedAt = System.currentTimeMillis(),
            error = error,
        )
        _state.value = finished
        val config = finished.config
        if (config != null && config.isClient) {
            History.append(app, finished.toRecord(config))
        }
    }

    // ---- json-stream parsing -------------------------------------------------

    private fun handleLine(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty()) return
        val event = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()
        if (event == null) {
            appendLog(line)
            return
        }
        val data = event["data"]
        when (event.string("event")) {
            "start" -> onStart(data as? JsonObject)
            "interval" -> onInterval(data as? JsonObject)
            "end" -> onEnd(data as? JsonObject)
            "error" -> _state.update {
                it.copy(error = (data as? JsonPrimitive)?.content ?: "iperf3 reported an error")
            }
            "server_output_text" -> (data as? JsonPrimitive)?.content
                ?.lines()?.filter { it.isNotBlank() }?.forEach(::appendLog)
        }
    }

    private fun onStart(data: JsonObject?) {
        (data?.get("connecting_to") as? JsonObject)?.let {
            appendLog("connecting to ${it.string("host")} port ${it.number("port")?.toInt()}")
        }
        _state.update { state ->
            // In server mode every accepted client starts a fresh test.
            if (state.config?.role == Role.SERVER) {
                state.copy(samples = emptyList(), summary = null, connections = state.connections + 1)
            } else {
                state
            }
        }
    }

    private fun onInterval(data: JsonObject?) {
        val sum = data?.get("sum") as? JsonObject ?: return
        val reverse = data["sum_bidir_reverse"] as? JsonObject
        val sample = Sample(
            time = sum.number("end") ?: return,
            bitsPerSecond = sum.number("bits_per_second") ?: 0.0,
            bytes = sum.number("bytes")?.toLong() ?: 0L,
            reverseBitsPerSecond = reverse?.number("bits_per_second"),
            retransmits = sum.number("retransmits")?.toInt(),
            jitterMs = sum.number("jitter_ms"),
            lostPercent = sum.number("lost_percent"),
            omitted = sum.boolean("omitted") ?: false,
        )
        _state.update { it.copy(samples = it.samples + sample) }
    }

    private fun onEnd(data: JsonObject?) {
        if (data == null) return
        val cpu = data["cpu_utilization_percent"] as? JsonObject
        val summary = RunSummary(
            sent = data.direction("sum_sent") ?: data.direction("sum"),
            received = data.direction("sum_received"),
            reverseSent = data.direction("sum_sent_bidir_reverse"),
            reverseReceived = data.direction("sum_received_bidir_reverse"),
            cpuLocal = cpu?.number("host_total"),
            cpuRemote = cpu?.number("remote_total"),
        )
        // A failed run still emits `"end":{}`; there is no result to show for it.
        if (summary.headline(Direction.UPLOAD) == null && summary.reverseSent == null &&
            summary.reverseReceived == null
        ) {
            return
        }
        _state.update { it.copy(summary = summary) }
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        _state.update { it.copy(log = (it.log + line).takeLast(MAX_LOG_LINES)) }
    }

    private fun EngineState.toRecord(config: IperfConfig) = RunRecord(
        id = UUID.randomUUID().toString(),
        startedAt = startedAt,
        finishedAt = finishedAt,
        config = config,
        outcome = phase,
        summary = summary,
        error = error,
        samples = samples.downsampled(MAX_STORED_SAMPLES),
        networkLabel = networkLabel,
    )

    private fun List<Sample>.downsampled(max: Int): List<Sample> {
        if (size <= max) return this
        val step = size.toDouble() / max
        return List(max) { index -> this[(index * step).toInt().coerceAtMost(size - 1)] }
    }

    private fun JsonObject.direction(key: String): DirectionSummary? {
        val obj = this[key] as? JsonObject ?: return null
        return DirectionSummary(
            bytes = obj.number("bytes")?.toLong() ?: 0L,
            bitsPerSecond = obj.number("bits_per_second") ?: 0.0,
            seconds = obj.number("seconds") ?: 0.0,
            retransmits = obj.number("retransmits")?.toInt(),
            jitterMs = obj.number("jitter_ms"),
            lostPackets = obj.number("lost_packets")?.toLong(),
            packets = obj.number("packets")?.toLong(),
            lostPercent = obj.number("lost_percent"),
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun JsonObject.number(key: String): Double? = string(key)?.toDoubleOrNull()
    private fun JsonObject.boolean(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()
}
