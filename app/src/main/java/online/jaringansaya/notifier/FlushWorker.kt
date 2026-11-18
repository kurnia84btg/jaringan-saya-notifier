package online.jaringansaya.notifier

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Kirim ulang antrean saat internet tersedia (dengan backoff),
 * plus "penjaga" tiap 15 menit yang menghidupkan lagi layanan & alarm.
 */
class FlushWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val app = applicationContext
        if (tags.contains(TAG_PERIODIC)) {
            KeepAliveService.start(app)
            HeartbeatReceiver.ensureScheduled(app)
        }
        return if (Sender.flush(app)) Result.success() else Result.retry()
    }

    companion object {
        const val TAG_PERIODIC = "periodic"

        private val netConstraint: Constraints
            get() = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(netConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("flush", ExistingWorkPolicy.KEEP, req)
        }

        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<FlushWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG_PERIODIC)
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
