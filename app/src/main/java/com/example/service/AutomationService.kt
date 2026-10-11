package com.example.service

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

/**
 * Foreground Service: keeps long automation campaigns alive when the screen is
 * off or the app is backgrounded (Doze/background kills otherwise stall tasks
 * silently). Shows live campaign status in the notification shade.
 */
class AutomationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Smart Automation running"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "Preparing campaign…"
                startForeground(NOTIF_ID, buildNotification(title, text))
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Smart Automation running"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                notifyUpdate(title, text)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Automation",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "Status of the running automation campaign" }
                )
            }
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        ensureChannel()
        val launch = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notifyUpdate(title: String, text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIF_ID, buildNotification(title, text))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "automation_status"
        private const val NOTIF_ID = 1001
        private const val ACTION_START = "com.example.action.AUTO_START"
        private const val ACTION_UPDATE = "com.example.action.AUTO_UPDATE"
        private const val ACTION_STOP = "com.example.action.AUTO_STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        fun start(ctx: Context, title: String, text: String) {
            try {
                val i = Intent(ctx.applicationContext, AutomationService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.applicationContext.startForegroundService(i)
                } else {
                    ctx.applicationContext.startService(i)
                }
            } catch (_: Exception) {
            }
        }

        fun update(ctx: Context, title: String, text: String) {
            try {
                ctx.applicationContext.startService(
                    Intent(ctx.applicationContext, AutomationService::class.java)
                        .setAction(ACTION_UPDATE)
                        .putExtra(EXTRA_TITLE, title)
                        .putExtra(EXTRA_TEXT, text)
                )
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.applicationContext.startService(
                    Intent(ctx.applicationContext, AutomationService::class.java)
                        .setAction(ACTION_STOP)
                )
            } catch (_: Exception) {
            }
        }
    }
}
