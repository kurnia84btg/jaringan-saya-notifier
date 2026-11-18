package online.jaringansaya.notifier

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.security.MessageDigest

object Payloads {

    // Cocok untuk: "Rp10.000", "Rp 10.000,00", "Rp1,500,000", "Rp. 25000"
    private val amountRe =
        Regex("""Rp\.?\s?([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)""", RegexOption.IGNORE_CASE)

    fun parseAmount(s: String): Long? {
        val m = amountRe.find(s) ?: return null
        return m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()
    }

    fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return hex(d)
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0f])
        }
        return sb.toString()
    }

    private fun base(p: Prefs, event: String): JSONObject = JSONObject().apply {
        put("event", event)
        put("device_id", p.deviceId)
        put("device_name", p.deviceName)
        put("app_version", BuildConfig.VERSION_NAME)
    }

    fun notification(
        p: Prefs,
        uid: String,
        pkg: String,
        title: String,
        text: String,
        bigText: String,
        subText: String,
        postedAt: Long
    ): JSONObject = base(p, "payment_notification").apply {
        put("uid", uid)
        put("package", pkg)
        put("title", title)
        put("text", text)
        put("big_text", bigText)
        put("sub_text", subText)
        put("amount", parseAmount("$title $bigText $text") ?: JSONObject.NULL)
        put("posted_at", postedAt)
        put("captured_at", System.currentTimeMillis())
    }

    fun heartbeat(ctx: Context, p: Prefs): JSONObject = base(p, "heartbeat").apply {
        val (pct, charging) = Health.battery(ctx)
        put("battery", pct)
        put("charging", charging)
        put("pending", Store.get(ctx).pendingCount())
        put("notif_access", Health.notifAccess(ctx))
        put("listener_connected", PayListenerService.connected)
        put("battery_unrestricted", Health.batteryUnrestricted(ctx))
        put("exact_alarm", Health.exactAlarm(ctx))
        put("last_capture_at", p.lastCaptureAt)
        put("last_sent_at", p.lastSentAt)
        put("android_sdk", Build.VERSION.SDK_INT)
        put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("ts", System.currentTimeMillis())
    }
}
