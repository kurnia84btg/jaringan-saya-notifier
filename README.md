# Jaringan Saya Notifier

Aplikasi Android pengganti MacroDroid untuk JARINGAN SAYA:
membaca notifikasi DANA → mengirim ke server, plus heartbeat tiap 10 menit.

Penyusun: KURNIA S.AB

## Kenapa lebih andal dari MacroDroid

| Masalah di MacroDroid | Solusi di aplikasi ini |
|---|---|
| Heartbeat 10 menit tapi jalan 2 jam sekali (Doze) | `AlarmManager.setExactAndAllowWhileIdle`, tetap berjalan saat HP tidur |
| Proses dimatikan penghemat baterai | Foreground service (notifikasi menetap) + mulai otomatis saat boot |
| POST gagal = pembayaran hilang | Disimpan dulu di **antrean SQLite**, lalu dikirim ulang otomatis (WorkManager + backoff) |
| Notifikasi masuk saat listener putus | Saat tersambung lagi, notifikasi yang masih tampil dipindai ulang |
| Notifikasi terkirim dobel | `uid` (SHA-256) unik di HP & server |
| Tidak tahu HP mati | Heartbeat berisi baterai, antrean, status izin → server bisa kirim peringatan WA |

## Build APK tanpa Android Studio (cukup dari HP)

1. Buat repo **PRIVATE** di GitHub, lalu upload seluruh isi folder ini (termasuk `.github`, `gradlew`, `gradle/`, `app/jsnotifier.keystore`).
2. Buka tab **Actions → Build APK → Run workflow** (atau otomatis berjalan setiap push ke `main`).
3. Setelah selesai (±3–5 menit), buka run tersebut → **Artifacts → jaringan-saya-notifier** → unduh zip → di dalamnya ada `app-release.apk`.

Dengan Android Studio: buka folder ini → Build → Build APK(s).

> Keystore `app/jsnotifier.keystore` sengaja disertakan agar setiap build punya tanda tangan yang sama
> (update tinggal install menimpa, data tidak hilang). Karena itu repo **wajib private**.

## Pemasangan di HP

1. Install APK (izinkan "instal dari sumber tidak dikenal").
2. **Android 13+**: Pengaturan → Aplikasi → Jaringan Saya Notifier → menu **⋮ → Izinkan setelan terbatas**.
   Tanpa ini, akses notifikasi tidak bisa diaktifkan untuk APK di luar Play Store.
3. Buka aplikasi, tekan tombol 1–5 sampai semua status **✓**.
4. Kunci aplikasi di Recent (ikon gembok). Set juga **DANA → Baterai → Tanpa batasan**.
5. Isi URL, token, lalu **Simpan** → **Kirim notifikasi TES** → pastikan masuk ke server.
6. **Matikan macro MacroDroid** (JARINGAN SAYA & HEARTBEAT) agar tidak dobel.

Tips per merek (Xiaomi, Oppo/realme, Vivo, Samsung): https://dontkillmyapp.com

## Kontrak API (server)

Semua request: `POST`, `Content-Type: application/json`

Header:
- `Authorization: Bearer <token>` dan `X-Api-Token: <token>` (cadangan)
- `X-Device-Id: <uuid>`
- Jika secret diisi: `X-Timestamp: <unix detik>`, `X-Signature: hex(HMAC_SHA256(secret, "<ts>.<body>"))`

### event = `payment_notification` (juga `test`)
```json
{
  "event": "payment_notification",
  "device_id": "uuid", "device_name": "HP Kasir", "app_version": "1.0.0",
  "uid": "sha256 unik",
  "package": "id.dana",
  "title": "...", "text": "...", "big_text": "...", "sub_text": "...",
  "amount": 10000,
  "posted_at": 1763495000000, "captured_at": 1763495001000
}
```
`amount` hasil parsing "Rp..." di HP (bisa `null`). Server sebaiknya tetap memparsing ulang dari teks.

### event = `heartbeat`
```json
{
  "event": "heartbeat", "device_id": "uuid", "device_name": "...",
  "battery": 80, "charging": true, "pending": 0,
  "notif_access": true, "listener_connected": true,
  "battery_unrestricted": true, "exact_alarm": true,
  "last_capture_at": 0, "last_sent_at": 0, "android_sdk": 34, "model": "...", "ts": 0
}
```

### Balasan
- `2xx` → dianggap terkirim. `409` → dianggap duplikat (juga sukses).
- Selain itu → disimpan dan dikirim ulang nanti. **Duplikat harus dibalas 200/409**, jangan 4xx lain.

Contoh penerima PHP: `server-contoh/notifier.php`.

## Struktur kode

```
app/src/main/java/online/jaringansaya/notifier/
  PayListenerService.kt  pembaca notifikasi DANA → antrean
  Store.kt               antrean SQLite (dedupe uid)
  Sender.kt              HTTP POST, flush antrean, heartbeat, HMAC
  KeepAliveService.kt    foreground service (anti dimatikan)
  HeartbeatReceiver.kt   alarm heartbeat (tahan Doze)
  FlushWorker.kt         kirim ulang + penjaga 15 menit
  BootReceiver.kt        hidup lagi setelah restart/update
  MainActivity.kt        layar pengaturan, status, uji
  Payloads.kt / Health.kt / Prefs.kt / Notifications.kt
```
