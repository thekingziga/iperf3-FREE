package dev.ziga.iperf3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

/** Builds the ongoing-progress and result notifications. */
object Notifications {

    const val CHANNEL_RUN = "iperf_run"
    const val CHANNEL_RESULT = "iperf_result"
    const val ID_RUN = 1
    const val ID_RESULT = 2

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "Running test", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live throughput while a test is running"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies you when a test finishes"
            }
        )
    }

    fun running(context: Context, state: EngineState): Notification {
        val config = state.config
        val progress = state.progress
        val builder = NotificationCompat.Builder(context, CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(title(state))
            .setContentText(line(state))
            .setSubText(config?.describe())
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop", stopIntent(context))
        if (progress != null) {
            builder.setProgress(1000, (progress * 1000).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun result(context: Context, state: EngineState): Notification {
        val title = when (state.phase) {
            RunPhase.DONE -> "Test complete"
            RunPhase.CANCELLED -> "Test stopped"
            else -> "Test failed"
        }
        val detail = when (state.phase) {
            RunPhase.FAILED -> state.error ?: "iperf3 reported an error"
            else -> resultLines(state)
        }
        return NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(title)
            .setContentText(detail.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSubText(state.config?.describe())
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
    }

    private fun title(state: EngineState): String {
        val config = state.config ?: return "iperf3"
        if (config.role == Role.SERVER) {
            return if (state.connections > 0) "Serving test #${state.connections}" else "Waiting for clients"
        }
        val latest = state.latest ?: return "Connecting…"
        val forward = Format.bitrate(latest.bitsPerSecond)
        return when (config.direction) {
            Direction.UPLOAD -> "↑ $forward"
            Direction.DOWNLOAD -> "↓ $forward"
            Direction.BIDIR -> "↑ $forward   ↓ ${Format.bitrate(latest.reverseBitsPerSecond ?: 0.0)}"
        }
    }

    private fun line(state: EngineState): String {
        val config = state.config ?: return ""
        val elapsed = Format.seconds(state.elapsedSeconds)
        return when {
            config.role == Role.SERVER -> "Listening on port ${config.port} · $elapsed"
            config.totalSeconds != null -> "$elapsed of ${Format.seconds(config.totalSeconds!!.toDouble())}" +
                (state.latest?.let { " · ${Format.bytes(state.samples.sumOf { s -> s.bytes })} transferred" } ?: "")
            config.targetBytes() != null -> "${Format.bytes(state.samples.sumOf { it.bytes })} of ${Format.bytes(config.targetBytes()!!)}"
            else -> elapsed
        }
    }

    private fun resultLines(state: EngineState): String {
        val summary = state.summary ?: return "No results were reported"
        val config = state.config
        return buildList {
            summary.sent?.let { add("Sent: ${Format.bitrate(it.bitsPerSecond)} · ${Format.bytes(it.bytes)} in ${Format.seconds(it.seconds)}") }
            summary.received?.let { add("Received: ${Format.bitrate(it.bitsPerSecond)} · ${Format.bytes(it.bytes)}") }
            summary.reverseSent?.let { add("Reverse sent: ${Format.bitrate(it.bitsPerSecond)}") }
            summary.reverseReceived?.let { add("Reverse received: ${Format.bitrate(it.bitsPerSecond)}") }
            val loss = summary.received?.lostPercent ?: summary.sent?.lostPercent
            if (config?.protocol == Protocol.UDP && loss != null) {
                add("Loss: ${Format.percent(loss)}" + (summary.received?.jitterMs?.let { " · jitter ${Format.milliseconds(it)}" } ?: ""))
            }
            summary.sent?.retransmits?.let { add("Retransmits: $it") }
        }.joinToString("\n").ifEmpty { "No results were reported" }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = Intent(context, IperfService::class.java).setAction(IperfService.ACTION_STOP)
        return PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
