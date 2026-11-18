package online.jaringansaya.notifier

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/** Cek kesehatan izin & kondisi perangkat. */
object Health {

    fun notifAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun postNotif(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun exactAlarm(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun battery(ctx: Context): Pair<Int, Boolean> {
        val bm = ctx.getSystemService(BatteryManager::class.java) ?: return Pair(-1, false)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return Pair(pct, bm.isCharging)
    }
}
