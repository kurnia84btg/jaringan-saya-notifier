package online.jaringansaya.notifier

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Layanan latar depan (notifikasi menetap). Inilah yang membuat proses
 * tidak mudah dimatikan sistem / penghemat baterai.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.build(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID, n)
        }
        HeartbeatReceiver.ensureScheduled(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Kalau aplikasi di-swipe dari Recent, pastikan alarm tetap ada.
        HeartbeatReceiver.schedule(this, 5_000)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, KeepAliveService::class.java)
                )
            } catch (e: Exception) {
                // Android 12+ bisa menolak start dari latar jika baterai masih dibatasi.
                Prefs(ctx).setError("Gagal start layanan: ${e.javaClass.simpleName}")
            }
        }
    }
}
