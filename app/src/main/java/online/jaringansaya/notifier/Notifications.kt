package online.jaringansaya.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifications {
    const val CHANNEL = "status"
    const val ID = 1

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL, "Status layanan", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    fun time(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ms))

    fun build(ctx: Context): Notification {
        val p = Prefs(ctx)
        val pending = Store.get(ctx).pendingCount()
        val hb = when {
            p.lastHeartbeatAt == 0L -> "belum"
            p.lastHeartbeatOk -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.lastHeartbeatAt)) + " ✓"
            else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.lastHeartbeatAt)) + " ✗"
        }
        val warn = if (Health.notifAccess(ctx)) "" else " · AKSES NOTIFIKASI MATI!"

        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Jaringan Saya Notifier aktif")
            .setContentText("Antrean $pending · Heartbeat $hb$warn")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun refresh(ctx: Context) {
        if (!KeepAliveService.running) return
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(ID, build(ctx))
        } catch (_: Exception) {
        }
    }
}
