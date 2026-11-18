package online.jaringansaya.notifier

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pengirim HTTP. Semua fungsi di sini WAJIB dipanggil dari thread latar. */
object Sender {

    private val lock = Any()

    data class Resp(val ok: Boolean, val code: Int, val message: String)

    fun post(ctx: Context, url: String, body: String): Resp {
        val p = Prefs(ctx)
        if (url.isBlank()) return Resp(false, 0, "URL belum diisi")
        if (!url.startsWith("https://")) return Resp(false, 0, "URL harus https://")

        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.useCaches = false
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "JaringanSayaNotifier/" + BuildConfig.VERSION_NAME)
            c.setRequestProperty("X-Device-Id", p.deviceId)
            if (p.token.isNotBlank()) {
                c.setRequestProperty("Authorization", "Bearer " + p.token)
                // Cadangan: sebagian hosting Apache membuang header Authorization.
                c.setRequestProperty("X-Api-Token", p.token)
            }
            if (p.secret.isNotBlank()) {
                val ts = (System.currentTimeMillis() / 1000).toString()
                c.setRequestProperty("X-Timestamp", ts)
                c.setRequestProperty("X-Signature", hmac(p.secret, "$ts.$body"))
            }

            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }?.take(300) ?: ""
            // 409 = server bilang duplikat → anggap sukses (sudah tercatat).
            Resp(code in 200..299 || code == 409, code, text)
        } catch (e: Exception) {
            Resp(false, -1, e.javaClass.simpleName + ": " + (e.message ?: ""))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Kirim semua antrean yang belum terkirim, urut dari yang terlama.
     * @return true jika antrean kosong (semua sukses).
     */
    fun flush(ctx: Context): Boolean = synchronized(lock) {
        val store = Store.get(ctx)
        val p = Prefs(ctx)
        var allOk = true

        outer@ while (true) {
            val batch = store.pending(20)
            if (batch.isEmpty()) break
            for (item in batch) {
                val r = post(ctx, p.notifUrl, item.payload)
                if (r.ok) {
                    store.markSent(item.id)
                    p.lastSentAt = System.currentTimeMillis()
                } else {
                    store.markFailed(item.id)
                    p.setError("Kirim gagal (${r.code}) ${r.message}")
                    allOk = false
                    break@outer
                }
            }
        }
        store.cleanup()
        Notifications.refresh(ctx)
        allOk
    }

    fun heartbeat(ctx: Context): Resp {
        val p = Prefs(ctx)
        val body = Payloads.heartbeat(ctx, p).toString()
        val r = post(ctx, p.effectiveHeartbeatUrl(), body)
        p.lastHeartbeatAt = System.currentTimeMillis()
        p.lastHeartbeatOk = r.ok
        if (!r.ok) p.setError("Heartbeat gagal (${r.code}) ${r.message}")
        Notifications.refresh(ctx)
        return r
    }

    private fun hmac(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Payloads.hex(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }
}
