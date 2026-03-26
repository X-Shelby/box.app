package com.box.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.box.app.MainActivity
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.ServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BoxForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loopJob: Job? = null

    @Volatile
    private var foregroundStarted: Boolean = false

    @Volatile
    private var desiredStatus: ServiceStatus = ServiceStatus.Checking

    @Volatile
    private var desiredStatusSetAtMs: Long = 0L

    @Volatile
    private var lastTitle: String = ""

    @Volatile
    private var lastText: String = ""

    @Volatile
    private var lastSeenRunningAtMs: Long = 0L

    @Volatile
    private var cachedRunningPid: String? = null

    @Volatile
    private var cachedRunningCore: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    private fun ensureForegroundStarted() {
        if (foregroundStarted) return
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.service_status_checking), "")
            )
            foregroundStarted = true
        } catch (t: Throwable) {
            foregroundStarted = false
            stopForegroundCompat()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isNotificationEnabled(this)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 12+ requires startForeground() very quickly after startForegroundService().
        // Always enter foreground immediately; any heavy work happens after this.
        ensureForegroundStarted()

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                desiredStatus = ServiceStatus.Stopping
                desiredStatusSetAtMs = System.currentTimeMillis()
                cachedRunningPid = null
                cachedRunningCore = null
                scope.launch {
                    runCatching { BoxApi.stopService() }
                }
            }
            ACTION_RESTART_SERVICE -> {
                desiredStatus = ServiceStatus.Restarting
                desiredStatusSetAtMs = System.currentTimeMillis()
                cachedRunningPid = null
                cachedRunningCore = null
                scope.launch {
                    runCatching { BoxApi.restartService() }
                }
            }
            ACTION_UPDATE_STATUS -> {
                val statusName = intent.getStringExtra(EXTRA_STATUS).orEmpty()
                desiredStatus = statusFromName(statusName)
                desiredStatusSetAtMs = System.currentTimeMillis()
            }
        }

        // Push one immediate update (box.app style) so UI doesn't have to wait for the next 15s tick.
        scope.launch {
            runCatching { updateNotificationOnce() }
        }

        if (loopJob?.isActive != true) {
            loopJob = scope.launch {
                while (true) {
                    runCatching {
                        if (!isNotificationEnabled(this@BoxForegroundService)) {
                            stopForegroundCompat()
                            stopSelf()
                            return@launch
                        }

                        val displayStatus = updateNotificationOnce()
                        if (displayStatus is ServiceStatus.Stopped) {
                            stopForegroundCompat()
                            stopSelf()
                            return@launch
                        }
                    }
                    delay(REFRESH_MS)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopServicePi = PendingIntent.getService(
            this,
            1,
            Intent(this, BoxForegroundService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartServicePi = PendingIntent.getService(
            this,
            2,
            Intent(this, BoxForegroundService::class.java).setAction(ACTION_RESTART_SERVICE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_box)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .addAction(0, getString(R.string.service_action_stop), stopServicePi)
            .addAction(0, getString(R.string.service_action_restart), restartServicePi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun computeDisplayStatus(isRunning: Boolean, nowMs: Long): ServiceStatus {
        if (isRunning) {
            val transient = desiredStatus is ServiceStatus.Starting ||
                desiredStatus is ServiceStatus.Stopping ||
                desiredStatus is ServiceStatus.Restarting

            return if (transient && (nowMs - desiredStatusSetAtMs) in 0..TRANSITION_MS) {
                desiredStatus
            } else {
                ServiceStatus.Running
            }
        }

        val inGrace = (nowMs - lastSeenRunningAtMs) in 0..GRACE_MS
        if (inGrace && (desiredStatus is ServiceStatus.Starting || desiredStatus is ServiceStatus.Restarting || desiredStatus is ServiceStatus.Stopping)) {
            return desiredStatus
        }

        return ServiceStatus.Stopped
    }

    private suspend fun updateNotificationOnce(): ServiceStatus {
        val info = readServiceInfo()
        val nowMs = System.currentTimeMillis()
        if (info.isRunning) {
            lastSeenRunningAtMs = nowMs
        }

        val displayStatus = computeDisplayStatus(isRunning = info.isRunning, nowMs = nowMs)
        if (displayStatus is ServiceStatus.Stopped && !info.isRunning) {
            return ServiceStatus.Stopped
        }

        val title = when (displayStatus) {
            ServiceStatus.Starting -> getString(R.string.service_status_starting)
            ServiceStatus.Stopping -> getString(R.string.service_status_stopping)
            ServiceStatus.Restarting -> getString(R.string.service_status_restarting)
            ServiceStatus.Running -> getString(R.string.service_status_running)
            ServiceStatus.Stopped -> getString(R.string.service_status_stopped)
            ServiceStatus.Checking -> getString(R.string.service_status_checking)
        }

        val details = buildString {
            append("PID ")
            append(info.pid)
            if (info.core.isNotBlank()) {
                append(" · ")
                append(info.core)
            }
            if (info.rssMb != null) {
                append(" · ")
                append(info.rssMb)
                append("MB")
            }
        }

        if (title != lastTitle || details != lastText) {
            lastTitle = title
            lastText = details
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(title, details))
        }

        return displayStatus
    }

    private data class ServiceInfo(
        val isRunning: Boolean,
        val pid: String,
        val core: String,
        val rssMb: Long?
    )

    private suspend fun readServiceInfo(): ServiceInfo {
        val cachedPid = cachedRunningPid
        val cachedCore = cachedRunningCore
        if (!cachedPid.isNullOrBlank() && cachedPid.all(Char::isDigit)) {
            val sep = "___BOX_FG_SEP___"
            val cmd = """
                if [ -d '/proc/$cachedPid' ]; then echo '1'; else echo '0'; fi
                echo '$sep'
                grep '^VmRSS:' /proc/$cachedPid/status 2>/dev/null | head -n 1
            """.trimIndent()
            val res = ShellExecutor.execute(cmd)
            val parts = res.stdout.split(sep)
            val alive = parts.getOrNull(0)?.trim() == "1"
            if (alive) {
                val rssLine = parts.getOrNull(1)?.trim().orEmpty()
                val rssKb = rssLine.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
                val rssMb = rssKb?.let { it / 1024 }
                return ServiceInfo(true, cachedPid, cachedCore.orEmpty(), rssMb)
            }

            cachedRunningPid = null
            cachedRunningCore = null
        }

        val pidRes = runCatching { BoxApi.getPid() }.getOrNull()
        val pid = pidRes?.stdout?.trim().orEmpty()
        val isRunning = pid.isNotBlank() && pid.all(Char::isDigit)
        if (!isRunning) {
            cachedRunningPid = null
            cachedRunningCore = null
            return ServiceInfo(false, "-", "", null)
        }

        val sep = "___BOX_FG_SEP___"
        val cmd = """
            grep -m1 '^bin_name=' /data/adb/box/settings.ini 2>/dev/null | head -n 1
            echo '$sep'
            grep '^VmRSS:' /proc/$pid/status 2>/dev/null | head -n 1
        """.trimIndent()

        val res = ShellExecutor.execute(cmd)
        val parts = res.stdout.split(sep)
        val coreLine = parts.getOrNull(0)?.trim().orEmpty()
        val rssLine = parts.getOrNull(1)?.trim().orEmpty()

        val core = coreLine
            .substringAfter("=", "")
            .trim()
            .trim('"')

        val rssKb = rssLine.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        val rssMb = rssKb?.let { it / 1024 }

        cachedRunningPid = pid
        cachedRunningCore = core
        return ServiceInfo(true, pid, core, rssMb)
    }

    private fun statusFromName(name: String): ServiceStatus {
        return when (name) {
            "Starting" -> ServiceStatus.Starting
            "Stopping" -> ServiceStatus.Stopping
            "Restarting" -> ServiceStatus.Restarting
            "Running" -> ServiceStatus.Running
            "Stopped" -> ServiceStatus.Stopped
            else -> ServiceStatus.Checking
        }
    }

    companion object {
        private const val CHANNEL_ID = "box_service"
        private const val NOTIFICATION_ID = 1001

        private const val REFRESH_MS = 15_000L
        private const val GRACE_MS = 10_000L
        private const val TRANSITION_MS = 6_000L

        private const val ACTION_STOP = "com.box.app.service.action.STOP"
        private const val ACTION_UPDATE_STATUS = "com.box.app.service.action.UPDATE_STATUS"
        private const val ACTION_STOP_SERVICE = "com.box.app.service.action.STOP_SERVICE"
        private const val ACTION_RESTART_SERVICE = "com.box.app.service.action.RESTART_SERVICE"
        private const val EXTRA_STATUS = "status"

        private fun isNotificationEnabled(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean("enable_notifications", false)
        }

        fun ensureRunning(context: Context) {
            if (!isNotificationEnabled(context)) return
            val i = Intent(context, BoxForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, BoxForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun updateStatus(context: Context, status: ServiceStatus) {
            if (!isNotificationEnabled(context)) {
                stop(context)
                return
            }
            val i = Intent(context, BoxForegroundService::class.java)
                .setAction(ACTION_UPDATE_STATUS)
                .putExtra(EXTRA_STATUS, status.javaClass.simpleName)
            context.startService(i)
        }
    }
}
