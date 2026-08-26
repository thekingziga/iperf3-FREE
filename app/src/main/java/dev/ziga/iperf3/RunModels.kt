package dev.ziga.iperf3

import kotlinx.serialization.Serializable

/** One `interval` event from iperf3, i.e. one report line of a running test. */
@Serializable
data class Sample(
    /** Seconds since the test started (end of the interval). */
    val time: Double,
    val bitsPerSecond: Double,
    val bytes: Long,
    /** Reverse direction of a `--bidir` test; null otherwise. */
    val reverseBitsPerSecond: Double? = null,
    val retransmits: Int? = null,
    val jitterMs: Double? = null,
    val lostPercent: Double? = null,
    val omitted: Boolean = false,
)

/** Totals for one direction of the test, as reported in the `end` event. */
@Serializable
data class DirectionSummary(
    val bytes: Long,
    val bitsPerSecond: Double,
    val seconds: Double,
    val retransmits: Int? = null,
    val jitterMs: Double? = null,
    val lostPackets: Long? = null,
    val packets: Long? = null,
    val lostPercent: Double? = null,
)

@Serializable
data class RunSummary(
    val sent: DirectionSummary? = null,
    val received: DirectionSummary? = null,
    /** `--bidir` counterparts, present only for bidirectional tests. */
    val reverseSent: DirectionSummary? = null,
    val reverseReceived: DirectionSummary? = null,
    val cpuLocal: Double? = null,
    val cpuRemote: Double? = null,
) {
    /**
     * The number a user means by "the result": what the phone measured in the
     * direction it asked for.
     */
    fun headline(direction: Direction): DirectionSummary? = when (direction) {
        Direction.UPLOAD -> sent ?: received
        Direction.DOWNLOAD -> received ?: sent
        Direction.BIDIR -> sent ?: received
    }
}

enum class RunPhase {
    IDLE, STARTING, RUNNING, DONE, FAILED, CANCELLED;

    val isActive get() = this == STARTING || this == RUNNING
    val isTerminal get() = this == DONE || this == FAILED || this == CANCELLED
}

/** Everything the UI and the notification need to render the current run. */
data class EngineState(
    val phase: RunPhase = RunPhase.IDLE,
    val config: IperfConfig? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val samples: List<Sample> = emptyList(),
    val summary: RunSummary? = null,
    val error: String? = null,
    val log: List<String> = emptyList(),
    val networkLabel: String? = null,
    /** Server mode: how many client tests have been served so far. */
    val connections: Int = 0,
) {
    val latest: Sample? get() = samples.lastOrNull()

    /** 0f..1f, or null when the run has no known end (server mode, block-limited runs). */
    val progress: Float?
        get() {
            val config = config ?: return null
            val latest = latest ?: return if (phase.isActive) 0f else null
            config.totalSeconds?.let { total ->
                return (latest.time / total).coerceIn(0.0, 1.0).toFloat()
            }
            config.targetBytes()?.let { target ->
                val done = samples.sumOf { it.bytes }
                return (done.toDouble() / target).coerceIn(0.0, 1.0).toFloat()
            }
            return null
        }

    val elapsedSeconds: Double
        get() = latest?.time ?: if (startedAt > 0 && phase.isActive) {
            (System.currentTimeMillis() - startedAt) / 1000.0
        } else 0.0
}

/** A finished run, as stored in the history file. */
@Serializable
data class RunRecord(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    val config: IperfConfig,
    val outcome: RunPhase,
    val summary: RunSummary? = null,
    val error: String? = null,
    val samples: List<Sample> = emptyList(),
    val networkLabel: String? = null,
)

/** A named, reusable configuration. */
@Serializable
data class Profile(val name: String, val config: IperfConfig)
