<?php
/**
 * CONTOH penerima di server JARINGAN SAYA (PHP + PDO MySQL).
 * Sesuaikan dengan struktur aplikasi Anda. Inti yang WAJIB:
 *   1. Cek token (dan signature jika memakai secret).
 *   2. Dedupe berdasarkan `uid` (UNIQUE) → kirim ulang tidak membuat dobel.
 *   3. Balas HTTP 200 (juga untuk duplikat). Selain 2xx/409, aplikasi akan kirim ulang.
 *
 * Tabel:
 * CREATE TABLE notifier_inbox (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   uid VARCHAR(80) NOT NULL UNIQUE,
 *   device_id VARCHAR(64) NOT NULL,
 *   package VARCHAR(100), title VARCHAR(255), body TEXT,
 *   amount BIGINT NULL, posted_at BIGINT, raw JSON,
 *   processed TINYINT NOT NULL DEFAULT 0,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 * CREATE TABLE notifier_devices (
 *   device_id VARCHAR(64) PRIMARY KEY,
 *   device_name VARCHAR(100), last_heartbeat DATETIME, raw JSON
 * );
 */

const NOTIFIER_TOKEN  = 'GANTI_DENGAN_TOKEN_PANJANG_ACAK';
const NOTIFIER_SECRET = ''; // isi jika memakai secret HMAC di aplikasi

header('Content-Type: application/json');
$body = file_get_contents('php://input');

// 1. Autentikasi
// (Hosting Apache kadang membuang header Authorization → pakai X-Api-Token sebagai cadangan)
$auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
$tok  = str_starts_with($auth, 'Bearer ') ? substr($auth, 7) : ($_SERVER['HTTP_X_API_TOKEN'] ?? '');
if (!hash_equals(NOTIFIER_TOKEN, $tok)) {
    http_response_code(401);
    exit('{"ok":false,"error":"unauthorized"}');
}
if (NOTIFIER_SECRET !== '') {
    $ts  = $_SERVER['HTTP_X_TIMESTAMP'] ?? '';
    $sig = $_SERVER['HTTP_X_SIGNATURE'] ?? '';
    $calc = hash_hmac('sha256', $ts . '.' . $body, NOTIFIER_SECRET);
    if (!ctype_digit($ts) || abs(time() - (int)$ts) > 86400 * 7 || !hash_equals($calc, $sig)) {
        // Toleransi 7 hari: item antrean bisa terkirim terlambat saat HP offline.
        http_response_code(401);
        exit('{"ok":false,"error":"bad signature"}');
    }
}

$d = json_decode($body, true);
if (!is_array($d)) { http_response_code(400); exit('{"ok":false}'); }

$pdo = new PDO('mysql:host=localhost;dbname=NAMA_DB;charset=utf8mb4', 'USER', 'PASS', [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
]);

switch ($d['event'] ?? '') {
    case 'heartbeat':
        $st = $pdo->prepare('INSERT INTO notifier_devices (device_id, device_name, last_heartbeat, raw)
            VALUES (?, ?, NOW(), ?) ON DUPLICATE KEY UPDATE device_name = VALUES(device_name),
            last_heartbeat = NOW(), raw = VALUES(raw)');
        $st->execute([$d['device_id'], $d['device_name'] ?? '', $body]);
        // Ide: cron tiap 5 menit → jika last_heartbeat > 20 menit, kirim WA peringatan ke admin.
        break;

    case 'payment_notification':
    case 'test':
        $st = $pdo->prepare('INSERT IGNORE INTO notifier_inbox
            (uid, device_id, package, title, body, amount, posted_at, raw, processed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)');
        $text = ($d['big_text'] ?? '') !== '' ? $d['big_text'] : ($d['text'] ?? '');
        $st->execute([
            $d['uid'], $d['device_id'], $d['package'] ?? '', $d['title'] ?? '', $text,
            $d['amount'], $d['posted_at'] ?? 0, $body, $d['event'] === 'test' ? 1 : 0,
        ]);
        // Proses pencocokan pembayaran (mis. nominal unik → aktifkan voucher/paket)
        // sebaiknya dijalankan terpisah dari tabel notifier_inbox WHERE processed = 0.
        break;
}

echo '{"ok":true}';
