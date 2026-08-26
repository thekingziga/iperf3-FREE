package dev.ziga.iperf3

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Shared number formatting for the UI, the notification and the history list. */
object Format {

    private val bitUnits = listOf("bit/s", "kbit/s", "Mbit/s", "Gbit/s", "Tbit/s")
    private val byteUnits = listOf("B", "KB", "MB", "GB", "TB")

    /** Splits a bit rate into value and unit so the UI can size them differently. */
    fun bitrateParts(bitsPerSecond: Double): Pair<String, String> {
        var value = if (bitsPerSecond.isFinite()) abs(bitsPerSecond) else 0.0
        var unit = 0
        while (value >= 1000.0 && unit < bitUnits.lastIndex) {
            value /= 1000.0
            unit++
        }
        val decimals = when {
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 2
        }
        return String.format(Locale.US, "%.${decimals}f", value) to bitUnits[unit]
    }

    fun bitrate(bitsPerSecond: Double): String =
        bitrateParts(bitsPerSecond).let { (value, unit) -> "$value $unit" }

    fun bytes(count: Long): String {
        var value = count.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < byteUnits.lastIndex) {
            value /= 1024.0
            unit++
        }
        val decimals = if (unit == 0) 0 else 2
        return String.format(Locale.US, "%.${decimals}f %s", value, byteUnits[unit])
    }

    fun seconds(value: Double): String = when {
        value >= 3600 -> String.format(Locale.US, "%d:%02d:%02d", (value / 3600).toInt(), (value % 3600 / 60).toInt(), (value % 60).toInt())
        value >= 60 -> String.format(Locale.US, "%d:%02d", (value / 60).toInt(), (value % 60).toInt())
        else -> String.format(Locale.US, "%.0f s", value)
    }

    fun percent(value: Double): String = String.format(Locale.US, "%.2f %%", value)

    fun milliseconds(value: Double): String = String.format(Locale.US, "%.3f ms", value)

    fun timestamp(epochMillis: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
