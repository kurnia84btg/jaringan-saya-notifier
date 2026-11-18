package online.jaringansaya.notifier

import android.content.Context
import android.os.Build
import java.util.UUID

/** Pengaturan & status ringan, disimpan di SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun str(key: String, def: String = ""): String = sp.getString(key, def) ?: def
    private fun putStr(key: String, v: String) = sp.edit().putString(key, v.trim()).apply()
    private fun long(key: String): Long = sp.getLong(key, 0L)
    private fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()

    // ---- Pengaturan ----
    var notifUrl: String
        get() = str("notif_url")
        set(v) = putStr("notif_url", v)

    /** Kosong = pakai notifUrl. */
    var heartbeatUrl: String
        get() = str("hb_url")
        set(v) = putStr("hb_url", v)

    var token: String
        get() = str("token")
        set(v) = putStr("token", v)

    /** Secret HMAC (opsional). */
    var secret: String
        get() = str("secret")
        set(v) = putStr("secret", v)

    var deviceName: String
        get() = str("device_name", Build.MODEL ?: "android")
        set(v) = putStr("device_name", v)

    /** Paket aplikasi yang dipantau, pisahkan dengan koma. DANA = id.dana */
    var packages: String
        get() = str("packages", "id.dana")
        set(v) = putStr("packages", v)

    var heartbeatMinutes: Int
        get() = sp.getInt("hb_min", 10)
        set(v) = sp.edit().putInt("hb_min", v.coerceIn(1, 60)).apply()

    val deviceId: String
        get() {
            val existing = sp.getString("device_id", null)
            if (existing != null) return existing
            val id = UUID.randomUUID().toString()
            sp.edit().putString("device_id", id).apply()
            return id
        }

    // ---- Status ----
    var lastHeartbeatAt: Long
        get() = long("last_hb_at")
        set(v) = putLong("last_hb_at", v)

    var lastHeartbeatOk: Boolean
        get() = sp.getBoolean("last_hb_ok", false)
        set(v) = sp.edit().putBoolean("last_hb_ok", v).apply()

    var nextHeartbeatAt: Long
        get() = long("next_hb_at")
        set(v) = putLong("next_hb_at", v)

    var lastSentAt: Long
        get() = long("last_sent_at")
        set(v) = putLong("last_sent_at", v)

    var lastCaptureAt: Long
        get() = long("last_capture_at")
        set(v) = putLong("last_capture_at", v)

    var listenerConnectedAt: Long
        get() = long("listener_at")
        set(v) = putLong("listener_at", v)

    val lastError: String get() = str("last_error")
    val lastErrorAt: Long get() = long("last_error_at")

    fun setError(msg: String) {
        sp.edit()
            .putString("last_error", msg.take(300))
            .putLong("last_error_at", System.currentTimeMillis())
            .apply()
    }

    fun packageList(): Set<String> =
        packages.split(",", ";", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun effectiveHeartbeatUrl(): String = heartbeatUrl.ifBlank { notifUrl }
}
