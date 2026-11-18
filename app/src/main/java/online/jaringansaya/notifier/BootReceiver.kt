package online.jaringansaya.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Hidupkan kembali semua setelah HP restart atau aplikasi di-update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        KeepAliveService.start(app)
        HeartbeatReceiver.schedule(app, 10_000)
        FlushWorker.schedulePeriodic(app)
        FlushWorker.enqueue(app)
    }
}
