package com.hostshield.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v5.0: Persistent DNS cache (L2) backed by SQLite.
 *
 * Survives app restarts and device reboots. The in-memory DnsCache (L1) is
 * populated from this disk cache on startup, providing instant DNS resolution
 * without waiting for upstream queries.
 *
 * Design choices:
 * - Separate SQLite database (not Room) to keep it independent from the main
 *   HostShield database. The DNS cache is ephemeral data that can be deleted
 *   without affecting user data.
 * - WAL journal mode for concurrent reads from the VPN thread while writes
 *   happen on the IO dispatcher.
 * - Batch writes: entries are written in transactions for efficiency.
 * - Size-capped: oldest entries evicted when cache exceeds maxEntries.
 * - Raw DNS response bytes stored as Base64 to avoid SQLite blob issues.
 *
 * Lifecycle:
 * - loadAll() called on VPN start to warm the L1 cache
 * - persistBatch() called periodically (every 60s) from DnsVpnService log flusher
 * - clear() called when user clears DNS cache from Settings
 */
@Singleton
class DnsDiskCache @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "DnsDiskCache"
        private const val DB_NAME = "dns_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE = "dns_cache"
        private const val MAX_ENTRIES = 10_000
        private const val EVICT_BATCH = 2000 // evict oldest 2K when full
    }

    private val db: SQLiteDatabase

    init {
        val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE $TABLE (
                        domain TEXT NOT NULL,
                        qtype INTEGER NOT NULL,
                        response TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        original_ttl_ms INTEGER NOT NULL,
                        inserted_at INTEGER NOT NULL,
                        PRIMARY KEY (domain, qtype)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX idx_expires ON $TABLE (expires_at)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                onCreate(db)
            }
        }
        db = helper.writableDatabase
        db.enableWriteAheadLogging()
    }

    /**
     * Load all non-expired entries from disk. Called on VPN startup to warm L1.
     *
     * @return List of (domain, qtype, responseBytes, expiresAt, originalTtlMs) tuples
     */
    suspend fun loadAll(): List<DiskCacheEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<DiskCacheEntry>()
        val now = System.currentTimeMillis()
        try {
            db.rawQuery(
                "SELECT domain, qtype, response, expires_at, original_ttl_ms FROM $TABLE WHERE expires_at > ?",
                arrayOf(now.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val response = Base64.decode(cursor.getString(2), Base64.NO_WRAP)
                        entries.add(DiskCacheEntry(
                            domain = cursor.getString(0),
                            qtype = cursor.getInt(1),
                            response = response,
                            expiresAt = cursor.getLong(3),
                            originalTtlMs = cursor.getLong(4)
                        ))
                    } catch (_: Exception) { /* skip corrupt entries */ }
                }
            }
            Log.i(TAG, "Loaded ${entries.size} entries from disk cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load disk cache: ${e.message}")
        }
        entries
    }

    /**
     * Persist a batch of cache entries to disk. Called periodically from
     * the VPN service's log flusher coroutine.
     *
     * Uses INSERT OR REPLACE to update existing entries.
     */
    suspend fun persistBatch(entries: List<DiskCacheEntry>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        try {
            db.beginTransaction()
            try {
                val stmt = db.compileStatement(
                    "INSERT OR REPLACE INTO $TABLE (domain, qtype, response, expires_at, original_ttl_ms, inserted_at) VALUES (?, ?, ?, ?, ?, ?)"
                )
                for (entry in entries) {
                    stmt.clearBindings()
                    stmt.bindString(1, entry.domain)
                    stmt.bindLong(2, entry.qtype.toLong())
                    stmt.bindString(3, Base64.encodeToString(entry.response, Base64.NO_WRAP))
                    stmt.bindLong(4, entry.expiresAt)
                    stmt.bindLong(5, entry.originalTtlMs)
                    stmt.bindLong(6, System.currentTimeMillis())
                    stmt.executeInsert()
                }
                db.setTransactionSuccessful()
                Log.d(TAG, "Persisted ${entries.size} entries to disk")
            } finally {
                db.endTransaction()
            }

            // Evict if over size limit
            evictIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist batch: ${e.message}")
        }
    }

    /** Clear all entries from disk cache. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            db.execSQL("DELETE FROM $TABLE")
            Log.i(TAG, "Disk cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear disk cache: ${e.message}")
        }
    }

    /** Remove expired entries. Called lazily during eviction. */
    suspend fun purgeExpired() = withContext(Dispatchers.IO) {
        try {
            val deleted = db.delete(TABLE, "expires_at < ?",
                arrayOf(System.currentTimeMillis().toString()))
            if (deleted > 0) Log.d(TAG, "Purged $deleted expired entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge expired: ${e.message}")
        }
    }

    /** Get current disk cache entry count. */
    fun getSize(): Int {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (_: Exception) { 0 }
    }

    private fun evictIfNeeded() {
        try {
            val count = getSize()
            if (count <= MAX_ENTRIES) return

            // First: purge expired
            db.delete(TABLE, "expires_at < ?",
                arrayOf(System.currentTimeMillis().toString()))

            // If still over limit, evict oldest by inserted_at
            val afterPurge = getSize()
            if (afterPurge > MAX_ENTRIES) {
                db.execSQL(
                    "DELETE FROM $TABLE WHERE rowid IN (SELECT rowid FROM $TABLE ORDER BY inserted_at ASC LIMIT ?)",
                    arrayOf(EVICT_BATCH)
                )
                Log.d(TAG, "Evicted $EVICT_BATCH oldest entries (was $afterPurge)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eviction failed: ${e.message}")
        }
    }

    data class DiskCacheEntry(
        val domain: String,
        val qtype: Int,
        val response: ByteArray,
        val expiresAt: Long,
        val originalTtlMs: Long
    )
}
