package com.lelloman.androidoscopy.session

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.lelloman.androidoscopy.Androidoscopy
import kotlinx.coroutines.*

class DiagnosticService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        startForeground(ID, notification(this))
        scope.launch {
            Androidoscopy.sessionState.collect { state ->
                if (!state.active) stopSelf()
                else getSystemService(NotificationManager::class.java).notify(ID, notification(this@DiagnosticService))
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { Androidoscopy.stopSession(); stopSelf() }
        if (!Androidoscopy.isSessionActive) stopSelf()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        if (Androidoscopy.isSessionActive) Androidoscopy.stopSession()
        super.onDestroy()
    }

    companion object {
        private const val ID = 0xADC0
        private const val CHANNEL = "androidoscopy.session"
        private const val STOP = "androidoscopy.STOP"
        fun dismiss(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        }
        fun showPairing(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, notification(context)) }
        }
        private fun notification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Diagnostic session", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val intent = Intent().setClassName(context.packageName, "com.lelloman.androidoscopy.ui.SessionActivity")
            val target = if (context.packageManager.resolveActivity(intent, 0) != null) intent
                else context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
            val open = PendingIntent.getActivity(context, ID, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(context, ID, Intent(context, DiagnosticService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val state = Androidoscopy.sessionState.value
            val text = if (state.pairing != null) "PC requests access. Tap to compare numbers."
                else state.remainingMs?.let { "Expires after inactivity · ${(it + 59_999) / 60_000} min remaining" }
                    ?: "Debug diagnostics available"
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
            return builder.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Androidoscopy diagnostics")
                .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        }
    }
}
