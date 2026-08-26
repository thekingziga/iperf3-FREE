package dev.ziga.iperf3

import kotlinx.serialization.Serializable

enum class Role { CLIENT, SERVER }
enum class Protocol { TCP, UDP }

/** Direction as seen from the phone: upload is the iperf3 default, download is `-R`. */
enum class Direction { UPLOAD, DOWNLOAD, BIDIR }

enum class IpVersion { AUTO, IPV4, IPV6 }

/** What ends the test: a duration (`-t`), a byte count (`-n`) or a block count (`-k`). */
enum class TestLimit { TIME, BYTES, BLOCKS }

@Serializable
data class IperfConfig(
    val role: Role = Role.CLIENT,
    val host: String = "",
    val port: Int = 5201,
    val protocol: Protocol = Protocol.TCP,
    val direction: Direction = Direction.UPLOAD,
    val limit: TestLimit = TestLimit.TIME,
    val durationSeconds: Int = 10,
    val bytes: String = "100M",
    val blocks: Int = 1000,
    val intervalSeconds: Double = 1.0,
    val parallelStreams: Int = 1,
    /** `-b`, e.g. "10M". Blank means unlimited for TCP and iperf3's 1 Mbit/s default for UDP. */
    val targetBitrate: String = "",
    val omitSeconds: Int = 0,
    /** `-l` read/write buffer size. */
    val blockSize: String = "",
    /** `-w` socket buffer / window size. */
    val windowSize: String = "",
    /** `-M` TCP maximum segment size. */
    val mss: String = "",
    val noDelay: Boolean = false,
    val zeroCopy: Boolean = false,
    /** `-C` congestion control algorithm, e.g. "cubic" or "bbr". */
    val congestion: String = "",
    /** `--dscp`, e.g. "46" or "AF11". */
    val dscp: String = "",
    /** `-B` local address to bind the test to. */
    val bindAddress: String = "",
    val ipVersion: IpVersion = IpVersion.AUTO,
    val connectTimeoutMs: Int = 0,
    val getServerOutput: Boolean = false,
    /** Server only: `-1`, handle a single client then exit. */
    val serverOneOff: Boolean = false,
) {
    val isClient get() = role == Role.CLIENT

    /** Total wall-clock length of a time-limited run, including the omitted warm-up. */
    val totalSeconds: Int?
        get() = if (isClient && limit == TestLimit.TIME) durationSeconds + omitSeconds else null

    /** Human-readable one-liner used for notifications and history rows. */
    fun describe(): String = when (role) {
        Role.SERVER -> "Server on port $port"
        Role.CLIENT -> {
            val arrow = when (direction) {
                Direction.UPLOAD -> "↑"
                Direction.DOWNLOAD -> "↓"
                Direction.BIDIR -> "↕"
            }
            "$arrow $host:$port · ${protocol.name}" +
                if (parallelStreams > 1) " · $parallelStreams streams" else ""
        }
    }

    /** Returns a validation error, or null when the configuration can be run. */
    fun validate(): String? {
        if (port !in 1..65535) return "Port must be between 1 and 65535"
        if (intervalSeconds < 0.1 || intervalSeconds > 60) return "Report interval must be 0.1–60 seconds"
        if (role == Role.SERVER) return null
        if (host.isBlank()) return "Enter the iperf3 server address"
        if (host.any { it.isWhitespace() }) return "Server address cannot contain spaces"
        when (limit) {
            TestLimit.TIME -> if (durationSeconds !in 1..86400) return "Duration must be 1–86400 seconds"
            TestLimit.BYTES -> if (parseSize(bytes) == null) return "Bytes must look like 100M or 2G"
            TestLimit.BLOCKS -> if (blocks < 1) return "Block count must be at least 1"
        }
        if (parallelStreams !in 1..128) return "Parallel streams must be 1–128"
        if (omitSeconds < 0 || omitSeconds > 60) return "Omit must be 0–60 seconds"
        if (targetBitrate.isNotBlank() && parseSize(targetBitrate) == null) return "Bitrate must look like 10M"
        if (blockSize.isNotBlank() && parseSize(blockSize) == null) return "Block size must look like 128K"
        if (windowSize.isNotBlank() && parseSize(windowSize) == null) return "Window size must look like 512K"
        if (mss.isNotBlank() && mss.toIntOrNull() == null) return "MSS must be a number"
        return null
    }

    /** Builds the iperf3 command line, always in line-delimited JSON mode. */
    fun toArgs(): List<String> = buildList {
        if (role == Role.SERVER) {
            add("-s")
            add("-p"); add(port.toString())
            if (bindAddress.isNotBlank()) { add("-B"); add(bindAddress.trim()) }
            if (serverOneOff) add("-1")
        } else {
            add("-c"); add(host.trim())
            add("-p"); add(port.toString())
            if (protocol == Protocol.UDP) add("-u")
            when (limit) {
                TestLimit.TIME -> { add("-t"); add(durationSeconds.toString()) }
                TestLimit.BYTES -> { add("-n"); add(bytes.trim()) }
                TestLimit.BLOCKS -> { add("-k"); add(blocks.toString()) }
            }
            if (parallelStreams > 1) { add("-P"); add(parallelStreams.toString()) }
            if (targetBitrate.isNotBlank()) { add("-b"); add(targetBitrate.trim()) }
            when (direction) {
                Direction.UPLOAD -> Unit
                Direction.DOWNLOAD -> add("-R")
                Direction.BIDIR -> add("--bidir")
            }
            if (omitSeconds > 0) { add("-O"); add(omitSeconds.toString()) }
            if (blockSize.isNotBlank()) { add("-l"); add(blockSize.trim()) }
            if (windowSize.isNotBlank()) { add("-w"); add(windowSize.trim()) }
            if (mss.isNotBlank() && protocol == Protocol.TCP) { add("-M"); add(mss.trim()) }
            if (noDelay && protocol == Protocol.TCP) add("-N")
            if (zeroCopy) add("-Z")
            if (congestion.isNotBlank() && protocol == Protocol.TCP) { add("-C"); add(congestion.trim()) }
            if (dscp.isNotBlank()) { add("--dscp"); add(dscp.trim()) }
            if (bindAddress.isNotBlank()) { add("-B"); add(bindAddress.trim()) }
            if (connectTimeoutMs > 0) { add("--connect-timeout"); add(connectTimeoutMs.toString()) }
            if (getServerOutput) add("--get-server-output")
        }
        add("-i"); add(formatInterval(intervalSeconds))
        when (ipVersion) {
            IpVersion.IPV4 -> add("-4")
            IpVersion.IPV6 -> add("-6")
            IpVersion.AUTO -> Unit
        }
        add("--forceflush")
        add("--json-stream")
    }

    /** Byte target of the run, when it has one, for progress reporting. */
    fun targetBytes(): Long? = if (isClient && limit == TestLimit.BYTES) parseSize(bytes) else null

    private fun formatInterval(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    companion object {
        /** Parses iperf3-style sizes ("100", "128K", "1.5M", "2G"); 1K = 1024. */
        fun parseSize(raw: String): Long? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val multiplier = when (text.last().uppercaseChar()) {
                'K' -> 1024L
                'M' -> 1024L * 1024
                'G' -> 1024L * 1024 * 1024
                'T' -> 1024L * 1024 * 1024 * 1024
                else -> 1L
            }
            val number = if (multiplier == 1L) text else text.dropLast(1)
            val value = number.toDoubleOrNull() ?: return null
            if (value <= 0) return null
            return (value * multiplier).toLong()
        }
    }
}
