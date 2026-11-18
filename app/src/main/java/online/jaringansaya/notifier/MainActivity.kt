package online.jaringansaya.notifier

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var p: Prefs
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var log: TextView
    private val fields = HashMap<String, EditText>()
    private val bg = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)

        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 2)
        }

        header("Status")
        status = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.2f)
        }
        root.addView(status)

        header("Izin & ketahanan (semua harus ✓)")
        hint(
            "Android 13+: jika tombol Akses Notifikasi abu-abu / 'pengaturan dibatasi', buka " +
                "Info Aplikasi → menu ⋮ → 'Izinkan setelan terbatas', lalu ulangi."
        )
        button("1. Akses notifikasi") {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        button("2. Izinkan notifikasi aplikasi ini") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                openAppInfo()
            }
        }
        button("3. Baterai: tanpa batasan") { requestBattery() }
        button("4. Alarm & pengingat tepat waktu") {
            if (Build.VERSION.SDK_INT >= 31) {
                safeStart(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                toast("Tidak perlu di Android ini")
            }
        }
        button("5. Info aplikasi (Autostart, data latar, setelan terbatas)") { openAppInfo() }
        hint(
            "Lakukan juga: kunci aplikasi ini di Recent (ikon gembok), dan set baterai DANA " +
                "ke 'tanpa batasan' agar notifikasinya tidak telat."
        )

        header("Pengaturan server")
        field("notif_url", "URL penerima notifikasi (https://...)", p.notifUrl, uri = true)
        field("hb_url", "URL heartbeat (kosong = sama dengan di atas)", p.heartbeatUrl, uri = true)
        field("token", "Token (Authorization: Bearer)", p.token)
        field("secret", "Secret HMAC (opsional)", p.secret)
        field("device_name", "Nama perangkat", p.deviceName)
        field("packages", "Paket dipantau (pisah koma). DANA = id.dana", p.packages)
        field("hb_min", "Interval heartbeat (menit, 1–60)", p.heartbeatMinutes.toString(), number = true)
        button("Simpan") { save() }

        header("Uji")
        button("Kirim notifikasi TES") { sendTest() }
        button("Kirim heartbeat sekarang") { sendHeartbeat() }
        button("Kirim ulang antrean sekarang") { flushNow() }

        header("20 antrean terakhir")
        log = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(log)

        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        KeepAliveService.start(this)
        HeartbeatReceiver.ensureScheduled(this)
        refresh()
    }

    // ---------- Aksi ----------

    private fun save() {
        val url = text("notif_url")
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            toast("URL harus diawali https://")
            return
        }
        p.notifUrl = url
        p.heartbeatUrl = text("hb_url")
        p.token = text("token")
        p.secret = text("secret")
        p.deviceName = text("device_name")
        p.packages = text("packages").ifBlank { "id.dana" }
        p.heartbeatMinutes = text("hb_min").toIntOrNull() ?: 10
        fields["hb_min"]?.setText(p.heartbeatMinutes.toString())
        HeartbeatReceiver.schedule(this, 5_000)
        toast("Tersimpan")
        refresh()
    }

    private fun sendTest() {
        val uid = "test-" + UUID.randomUUID().toString()
        val json = Payloads.notification(
            p, uid, "test", "TES Jaringan Saya Notifier",
            "Ini notifikasi tes Rp1.000", "", "", System.currentTimeMillis()
        ).put("event", "test")
        Store.get(this).enqueue(uid, json.toString())
        flushNow()
    }

    private fun flushNow() {
        toast("Mengirim…")
        bg.execute {
            val ok = Sender.flush(this)
            if (!ok) FlushWorker.enqueue(this)
            runOnUiThread {
                toast(if (ok) "Terkirim ✓" else "Gagal: ${p.lastError}")
                refresh()
            }
        }
    }

    private fun sendHeartbeat() {
        toast("Mengirim heartbeat…")
        bg.execute {
            val r = Sender.heartbeat(this)
            runOnUiThread {
                toast(if (r.ok) "Heartbeat OK (${r.code})" else "Gagal (${r.code}) ${r.message}")
                refresh()
            }
        }
    }

    private fun requestBattery() {
        if (!Health.batteryUnrestricted(this)) {
            safeStart(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAppInfo() {
        safeStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun refresh() {
        fun ok(b: Boolean) = if (b) "✓" else "✗"
        val store = Store.get(this)
        val sb = StringBuilder()
        sb.append(ok(Health.notifAccess(this))).append(" Akses notifikasi\n")
        sb.append(ok(Health.postNotif(this))).append(" Izin tampil notifikasi\n")
        sb.append(ok(Health.batteryUnrestricted(this))).append(" Baterai tanpa batasan\n")
        sb.append(ok(Health.exactAlarm(this))).append(" Alarm tepat waktu\n")
        sb.append(ok(KeepAliveService.running)).append(" Layanan latar berjalan\n")
        sb.append(ok(PayListenerService.connected)).append(" Pembaca notifikasi terhubung\n\n")
        sb.append("Antrean belum terkirim : ").append(store.pendingCount()).append('\n')
        sb.append("Notifikasi terakhir    : ").append(Notifications.time(p.lastCaptureAt)).append('\n')
        sb.append("Terkirim terakhir      : ").append(Notifications.time(p.lastSentAt)).append('\n')
        sb.append("Heartbeat terakhir     : ").append(Notifications.time(p.lastHeartbeatAt))
            .append(if (p.lastHeartbeatAt > 0) (if (p.lastHeartbeatOk) " ✓" else " ✗") else "").append('\n')
        sb.append("Heartbeat berikutnya   : ").append(Notifications.time(p.nextHeartbeatAt)).append('\n')
        if (p.lastError.isNotEmpty()) {
            sb.append("Error terakhir (").append(Notifications.time(p.lastErrorAt)).append("):\n")
                .append(p.lastError).append('\n')
        }
        sb.append("\nID perangkat: ").append(p.deviceId)
        status.text = sb.toString()

        val lines = StringBuilder()
        for (item in store.recent(20)) {
            val j = try { JSONObject(item.payload) } catch (_: Exception) { JSONObject() }
            val mark = if (item.sentAt != null) "✓" else "… (${item.attempts}x)"
            val amount = j.optLong("amount", -1L)
            val amt = if (amount >= 0) "Rp$amount" else "-"
            lines.append("[").append(mark).append(" ").append(Notifications.time(item.createdAt)).append("] ")
                .append(amt).append(" | ").append(j.optString("title")).append('\n')
        }
        log.text = if (lines.isEmpty()) "(kosong)" else lines.toString()
    }

    // ---------- Helper UI ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun header(t: String) {
        root.addView(TextView(this).apply {
            text = t
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        })
    }

    private fun hint(t: String) {
        root.addView(TextView(this).apply {
            text = t
            textSize = 12f
            alpha = 0.75f
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun button(label: String, onClick: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    private fun field(key: String, label: String, value: String, uri: Boolean = false, number: Boolean = false) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        })
        val et = EditText(this).apply {
            setText(value)
            setSingleLine(true)
            inputType = when {
                number -> InputType.TYPE_CLASS_NUMBER
                uri -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            if (Build.VERSION.SDK_INT >= 26) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        fields[key] = et
        root.addView(et)
    }

    private fun text(key: String): String = fields[key]?.text?.toString()?.trim() ?: ""

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (_: Exception) {
            openAppInfoFallback()
        }
    }

    private fun openAppInfoFallback() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            toast("Buka Pengaturan secara manual")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
