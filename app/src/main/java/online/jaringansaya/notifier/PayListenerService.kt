package online.jaringansaya.notifier

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

/**
 * Membaca notifikasi dari aplikasi yang dipantau (default DANA: id.dana),
 * menyimpannya ke antrean, lalu langsung mengirim ke server.
 */
class PayListenerService : NotificationListenerService() {

    private val exec = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Prefs(this).listenerConnectedAt = System.currentTimeMillis()
        KeepAliveService.start(this)
        HeartbeatReceiver.ensureScheduled(this)

        // Tangkap notifikasi yang masih tampil (mungkin masuk saat listener terputus).
        exec.execute {
            try {
                activeNotifications?.forEach { capture(it) }
            } catch (_: Exception) {
            }
            sendNow()
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
        try {
            NotificationListenerService.requestRebind(
                ComponentName(this, PayListenerService::class.java)
            )
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        exec.execute {
            if (capture(s)) sendNow()
        }
    }

    /** @return true jika ada item baru masuk antrean. */
    private fun capture(sbn: StatusBarNotification): Boolean {
        val p = Prefs(this)
        if (sbn.packageName !in p.packageList()) return false

        val n = sbn.notification ?: return false
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return false

        val ex = n.extras ?: return false
        fun cs(key: String): String = ex.getCharSequence(key)?.toString()?.trim() ?: ""

        val title = cs(Notification.EXTRA_TITLE)
        val text = cs(Notification.EXTRA_TEXT)
        val big = cs(Notification.EXTRA_BIG_TEXT)
        val sub = cs(Notification.EXTRA_SUB_TEXT)
        if (title.isEmpty() && text.isEmpty() && big.isEmpty()) return false

        val uid = Payloads.sha256("${sbn.packageName}|${sbn.postTime}|$title|$text|$big")
        val json = Payloads.notification(p, uid, sbn.packageName, title, text, big, sub, sbn.postTime)

        val added = Store.get(this).enqueue(uid, json.toString())
        if (added) p.lastCaptureAt = System.currentTimeMillis()
        return added
    }

    private fun sendNow() {
        if (!Sender.flush(this)) FlushWorker.enqueue(this)
    }

    override fun onDestroy() {
        connected = false
        exec.shutdown()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var connected = false
    }
}
