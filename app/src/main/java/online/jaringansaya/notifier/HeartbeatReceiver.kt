package online.jaringansaya.notifier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Heartbeat berbasis AlarmManager (allowWhileIdle) — tetap jalan saat HP Doze,
 * tidak seperti interval biasa yang bisa tertunda berjam-jam.
 */
class HeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val p = Prefs(app)

        // Jadwalkan berikutnya dulu, supaya rantai alarm tidak putus walau kirim gagal.
        schedule(app, p.heartbeatMinutes * 60_000L)
        KeepAliveService.start(app)

        val pending = goAsync()
        Thread {
            try {
                Sender.heartbeat(app)
                if (Store.get(app).pendingCount() > 0) FlushWorker.enqueue(app)
            } catch (e: Exception) {
                p.setError("Heartbeat error: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {

        private fun pi(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 1001,
                Intent(ctx, HeartbeatReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun schedule(ctx: Context, delayMs: Long) {
            val app = ctx.applicationContext
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            val at = System.currentTimeMillis() + delayMs
            val pi = pi(app)
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            Prefs(app).nextHeartbeatAt = at
        }

        /** Jadwalkan jika belum ada jadwal ke depan (mis. setelah proses dimatikan). */
        fun ensureScheduled(ctx: Context) {
            val p = Prefs(ctx)
            val lateBy = System.currentTimeMillis() - p.nextHeartbeatAt
            if (p.nextHeartbeatAt == 0L || lateBy > 2 * 60_000L) {
                schedule(ctx, 3_000)
            }
        }
    }
}
