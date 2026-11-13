package com.example.engine.process

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
import com.example.MainActivity
import com.example.R

class SandboxMonitorService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 0) ?: 0
        val proxyPort = intent?.getIntExtra(EXTRA_RELAY_PORT, 0) ?: 0
        startForeground(NOTIFICATION_ID, buildNotification(activeCount, proxyPort))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(activeCount: Int, relayPort: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val subtitle = if (relayPort > 0) {
            "$activeCount isolated slots active · Relay 127.0.0.1:$relayPort"
        } else {
            "$activeCount isolated sandbox slots monitored"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CloneCore Sandbox Engine Active")
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sandbox Engine Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status for active multi-process clone slots and proxy relay."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "clonecore_sandbox_monitor"
        const val NOTIFICATION_ID = 4091
        const val ACTION_START = "com.example.action.START_MONITOR"
        const val ACTION_STOP = "com.example.action.STOP_MONITOR"
        const val EXTRA_ACTIVE_COUNT = "active_count"
        const val EXTRA_RELAY_PORT = "relay_port"
    }
}
