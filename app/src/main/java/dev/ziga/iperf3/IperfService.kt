package dev.ziga.iperf3

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Keeps a test alive while the app is in the background and mirrors the engine
 * state into the notification shade.
 *
 * The test itself lives in [IperfEngine]; this service owns only the foreground
 * lifetime, the wake lock and the notifications.
 */
class IperfService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var collector: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> IperfEngine.stop()
            else -> if (!IperfEngine.state.value.phase.isActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val config = intent.getStringExtra(EXTRA_CONFIG)
            ?.let { runCatching { json.decodeFromString<IperfConfig>(it) }.getOrNull() }
        if (config == null) {
            stopSelf()
            return
        }
        // Must happen within a few seconds of startForegroundService().
        val pending = EngineState(phase = RunPhase.STARTING, config = config, startedAt = System.currentTimeMillis())
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RUN,
            Notifications.running(this, pending),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (IperfEngine.start(this, config)) acquireWakeLock(config)
        observeEngine()
    }

    private fun observeEngine() {
        if (collector?.isActive == true) return
        collector = lifecycleScope.launch {
            var lastUpdate = 0L
            IperfEngine.state.collect { state ->
                when {
                    state.phase == RunPhase.IDLE -> Unit
                    state.phase.isTerminal -> finishWith(state)
                    else -> {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= NOTIFICATION_INTERVAL_MS) {
                            lastUpdate = now
                            notify(Notifications.ID_RUN, Notifications.running(this@IperfService, state))
                        }
                    }
                }
            }
        }
    }

    private fun finishWith(state: EngineState) {
        collector?.cancel()
        collector = null
        if (state.phase != RunPhase.CANCELLED) {
            notify(Notifications.ID_RESULT, Notifications.result(this, state))
        }
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun acquireWakeLock(config: IperfConfig) {
        val timeout = config.totalSeconds
            ?.let { (it + 60) * 1000L }
            ?: TimeUnit.HOURS.toMillis(6)
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iperf3:test")
            ?.apply {
                setReferenceCounted(false)
                acquire(timeout)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.ziga.iperf3.action.START"
        const val ACTION_STOP = "dev.ziga.iperf3.action.STOP"
        private const val EXTRA_CONFIG = "config"
        private const val NOTIFICATION_INTERVAL_MS = 400L

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun start(context: Context, config: IperfConfig) {
            val intent = Intent(context, IperfService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONFIG, json.encodeToString(config))
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IperfService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
