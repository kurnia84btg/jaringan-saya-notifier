package online.jaringansaya.notifier

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        FlushWorker.schedulePeriodic(this)
    }
}
