package online.jaringansaya.notifier

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Antrean lokal (SQLite). Setiap notifikasi disimpan DULU, baru dikirim.
 * Jadi kalau internet putus / server down, data tidak hilang dan dikirim ulang.
 */
class Store private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "queue.db", null, 1) {

    data class Item(
        val id: Long,
        val uid: String,
        val payload: String,
        val createdAt: Long,
        val attempts: Int,
        val sentAt: Long?
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uid TEXT NOT NULL UNIQUE, " +
                "payload TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "attempts INTEGER NOT NULL DEFAULT 0, " +
                "sent_at INTEGER)"
        )
        db.execSQL("CREATE INDEX idx_pending ON queue(sent_at, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** @return true jika baru (bukan duplikat). */
    fun enqueue(uid: String, payload: String): Boolean {
        val cv = ContentValues().apply {
            put("uid", uid)
            put("payload", payload)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "queue", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun pending(limit: Int): List<Item> = query("sent_at IS NULL", "id ASC", limit)

    fun recent(limit: Int): List<Item> = query(null, "id DESC", limit)

    private fun query(where: String?, order: String, limit: Int): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.query(
            "queue",
            arrayOf("id", "uid", "payload", "created_at", "attempts", "sent_at"),
            where, null, null, null, order, limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Item(
                        id = c.getLong(0),
                        uid = c.getString(1),
                        payload = c.getString(2),
                        createdAt = c.getLong(3),
                        attempts = c.getInt(4),
                        sentAt = if (c.isNull(5)) null else c.getLong(5)
                    )
                )
            }
        }
        return out
    }

    fun markSent(id: Long) {
        writableDatabase.execSQL(
            "UPDATE queue SET sent_at = ?, attempts = attempts + 1 WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), id)
        )
    }

    fun markFailed(id: Long) {
        writableDatabase.execSQL(
            "UPDATE queue SET attempts = attempts + 1 WHERE id = ?",
            arrayOf<Any>(id)
        )
    }

    fun pendingCount(): Long =
        DatabaseUtils.queryNumEntries(readableDatabase, "queue", "sent_at IS NULL")

    /** Hapus yang sudah terkirim lebih dari 7 hari. */
    fun cleanup() {
        val limit = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        writableDatabase.delete(
            "queue", "sent_at IS NOT NULL AND sent_at < ?", arrayOf(limit.toString())
        )
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(ctx: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(ctx.applicationContext).also { instance = it }
            }
    }
}
