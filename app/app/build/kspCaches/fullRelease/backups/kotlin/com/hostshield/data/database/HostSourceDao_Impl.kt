package com.hostshield.`data`.database

import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.CoroutinesRoom
import androidx.room.CoroutinesRoom.Companion.execute
import androidx.room.EntityDeletionOrUpdateAdapter
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.SharedSQLiteStatement
import androidx.room.util.createCancellationSignal
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import com.hostshield.`data`.model.HostSource
import com.hostshield.`data`.model.SourceCategory
import com.hostshield.`data`.model.SourceHealth
import java.lang.Class
import java.util.ArrayList
import java.util.concurrent.Callable
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
public class HostSourceDao_Impl(
  __db: RoomDatabase,
) : HostSourceDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfHostSource: EntityInsertionAdapter<HostSource>

  private val __converters: Converters = Converters()

  private val __deletionAdapterOfHostSource: EntityDeletionOrUpdateAdapter<HostSource>

  private val __updateAdapterOfHostSource: EntityDeletionOrUpdateAdapter<HostSource>

  private val __preparedStmtOfDeleteById: SharedSQLiteStatement

  private val __preparedStmtOfSetEnabled: SharedSQLiteStatement

  private val __preparedStmtOfUpdateSourceMeta: SharedSQLiteStatement

  private val __preparedStmtOfUpdateHealth: SharedSQLiteStatement

  private val __preparedStmtOfUpdateChangelog: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfHostSource = object : EntityInsertionAdapter<HostSource>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `host_sources` (`id`,`url`,`label`,`description`,`enabled`,`category`,`entry_count`,`last_updated`,`last_modified_online`,`etag`,`is_builtin`,`size_bytes`,`health`,`last_error`,`consecutive_failures`,`prev_entry_count`,`domains_added`,`domains_removed`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: HostSource) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.url)
        statement.bindString(3, entity.label)
        statement.bindString(4, entity.description)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: String = __converters.fromSourceCategory(entity.category)
        statement.bindString(6, _tmp_1)
        statement.bindLong(7, entity.entryCount.toLong())
        statement.bindLong(8, entity.lastUpdated)
        statement.bindString(9, entity.lastModifiedOnline)
        statement.bindString(10, entity.etag)
        val _tmp_2: Int = if (entity.isBuiltin) 1 else 0
        statement.bindLong(11, _tmp_2.toLong())
        statement.bindLong(12, entity.sizeBytes)
        val _tmp_3: String = __converters.fromSourceHealth(entity.health)
        statement.bindString(13, _tmp_3)
        statement.bindString(14, entity.lastError)
        statement.bindLong(15, entity.consecutiveFailures.toLong())
        statement.bindLong(16, entity.prevEntryCount.toLong())
        statement.bindLong(17, entity.domainsAdded.toLong())
        statement.bindLong(18, entity.domainsRemoved.toLong())
      }
    }
    this.__deletionAdapterOfHostSource = object : EntityDeletionOrUpdateAdapter<HostSource>(__db) {
      protected override fun createQuery(): String = "DELETE FROM `host_sources` WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: HostSource) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfHostSource = object : EntityDeletionOrUpdateAdapter<HostSource>(__db) {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `host_sources` SET `id` = ?,`url` = ?,`label` = ?,`description` = ?,`enabled` = ?,`category` = ?,`entry_count` = ?,`last_updated` = ?,`last_modified_online` = ?,`etag` = ?,`is_builtin` = ?,`size_bytes` = ?,`health` = ?,`last_error` = ?,`consecutive_failures` = ?,`prev_entry_count` = ?,`domains_added` = ?,`domains_removed` = ? WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: HostSource) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.url)
        statement.bindString(3, entity.label)
        statement.bindString(4, entity.description)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: String = __converters.fromSourceCategory(entity.category)
        statement.bindString(6, _tmp_1)
        statement.bindLong(7, entity.entryCount.toLong())
        statement.bindLong(8, entity.lastUpdated)
        statement.bindString(9, entity.lastModifiedOnline)
        statement.bindString(10, entity.etag)
        val _tmp_2: Int = if (entity.isBuiltin) 1 else 0
        statement.bindLong(11, _tmp_2.toLong())
        statement.bindLong(12, entity.sizeBytes)
        val _tmp_3: String = __converters.fromSourceHealth(entity.health)
        statement.bindString(13, _tmp_3)
        statement.bindString(14, entity.lastError)
        statement.bindLong(15, entity.consecutiveFailures.toLong())
        statement.bindLong(16, entity.prevEntryCount.toLong())
        statement.bindLong(17, entity.domainsAdded.toLong())
        statement.bindLong(18, entity.domainsRemoved.toLong())
        statement.bindLong(19, entity.id)
      }
    }
    this.__preparedStmtOfDeleteById = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM host_sources WHERE id = ?"
        return _query
      }
    }
    this.__preparedStmtOfSetEnabled = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "UPDATE host_sources SET enabled = ? WHERE id = ?"
        return _query
      }
    }
    this.__preparedStmtOfUpdateSourceMeta = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE host_sources SET entry_count = ?, last_updated = ?, etag = ?, size_bytes = ? WHERE id = ?"
        return _query
      }
    }
    this.__preparedStmtOfUpdateHealth = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE host_sources SET health = ?, last_error = ?, consecutive_failures = ? WHERE id = ?"
        return _query
      }
    }
    this.__preparedStmtOfUpdateChangelog = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE host_sources SET prev_entry_count = ?, domains_added = ?, domains_removed = ? WHERE id = ?"
        return _query
      }
    }
  }

  public override suspend fun insert(source: HostSource): Long = CoroutinesRoom.execute(__db, true,
      object : Callable<Long> {
    public override fun call(): Long {
      __db.beginTransaction()
      try {
        val _result: Long = __insertionAdapterOfHostSource.insertAndReturnId(source)
        __db.setTransactionSuccessful()
        return _result
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun insertAll(sources: List<HostSource>): Unit =
      CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfHostSource.insert(sources)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun delete(source: HostSource): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __deletionAdapterOfHostSource.handle(source)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun update(source: HostSource): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __updateAdapterOfHostSource.handle(source)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun deleteById(id: Long): Unit = CoroutinesRoom.execute(__db, true, object
      : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeleteById.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, id)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeleteById.release(_stmt)
      }
    }
  })

  public override suspend fun setEnabled(id: Long, enabled: Boolean): Unit =
      CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfSetEnabled.acquire()
      var _argIndex: Int = 1
      val _tmp: Int = if (enabled) 1 else 0
      _stmt.bindLong(_argIndex, _tmp.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, id)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfSetEnabled.release(_stmt)
      }
    }
  })

  public override suspend fun updateSourceMeta(
    id: Long,
    count: Int,
    timestamp: Long,
    etag: String,
    size: Long,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfUpdateSourceMeta.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, count.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, timestamp)
      _argIndex = 3
      _stmt.bindString(_argIndex, etag)
      _argIndex = 4
      _stmt.bindLong(_argIndex, size)
      _argIndex = 5
      _stmt.bindLong(_argIndex, id)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfUpdateSourceMeta.release(_stmt)
      }
    }
  })

  public override suspend fun updateHealth(
    id: Long,
    health: SourceHealth,
    error: String,
    failures: Int,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfUpdateHealth.acquire()
      var _argIndex: Int = 1
      val _tmp: String = __converters.fromSourceHealth(health)
      _stmt.bindString(_argIndex, _tmp)
      _argIndex = 2
      _stmt.bindString(_argIndex, error)
      _argIndex = 3
      _stmt.bindLong(_argIndex, failures.toLong())
      _argIndex = 4
      _stmt.bindLong(_argIndex, id)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfUpdateHealth.release(_stmt)
      }
    }
  })

  public override suspend fun updateChangelog(
    id: Long,
    prevCount: Int,
    added: Int,
    removed: Int,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfUpdateChangelog.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, prevCount.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, added.toLong())
      _argIndex = 3
      _stmt.bindLong(_argIndex, removed.toLong())
      _argIndex = 4
      _stmt.bindLong(_argIndex, id)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfUpdateChangelog.release(_stmt)
      }
    }
  })

  public override fun getAllSources(): Flow<List<HostSource>> {
    val _sql: String = "SELECT * FROM host_sources ORDER BY category, label"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("host_sources"), object :
        Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public override suspend fun getEnabledSources(): List<HostSource> {
    val _sql: String = "SELECT * FROM host_sources WHERE enabled = 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public override suspend fun getAllSourcesList(): List<HostSource> {
    val _sql: String = "SELECT * FROM host_sources"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public override suspend fun getById(id: Long): HostSource? {
    val _sql: String = "SELECT * FROM host_sources WHERE id = ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, id)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<HostSource?> {
      public override fun call(): HostSource? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: HostSource?
          if (_cursor.moveToFirst()) {
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _result =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
          } else {
            _result = null
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public override fun getByCategory(category: SourceCategory): Flow<List<HostSource>> {
    val _sql: String = "SELECT * FROM host_sources WHERE category = ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    val _tmp: String = __converters.fromSourceCategory(category)
    _statement.bindString(_argIndex, _tmp)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("host_sources"), object :
        Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpCategory: SourceCategory
            val _tmp_2: String
            _tmp_2 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_2)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_3 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_4: String
            _tmp_4 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_4)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public override fun getTotalEnabledEntries(): Flow<Int?> {
    val _sql: String = "SELECT SUM(entry_count) FROM host_sources WHERE enabled = 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("host_sources"), object : Callable<Int?> {
      public override fun call(): Int? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Int?
          if (_cursor.moveToFirst()) {
            val _tmp: Int?
            if (_cursor.isNull(0)) {
              _tmp = null
            } else {
              _tmp = _cursor.getInt(0)
            }
            _result = _tmp
          } else {
            _result = null
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public override fun getUnhealthySources(): Flow<List<HostSource>> {
    val _sql: String = "SELECT * FROM host_sources WHERE health = 'ERROR' OR health = 'DEAD'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("host_sources"), object :
        Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public override suspend fun getEnabledAllowlistSources(): List<HostSource> {
    val _sql: String = "SELECT * FROM host_sources WHERE enabled = 1 AND category = 'ALLOWLIST'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public override suspend fun getEnabledBlockSources(): List<HostSource> {
    val _sql: String = "SELECT * FROM host_sources WHERE enabled = 1 AND category != 'ALLOWLIST'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<HostSource>> {
      public override fun call(): List<HostSource> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUrl: Int = getColumnIndexOrThrow(_cursor, "url")
          val _cursorIndexOfLabel: Int = getColumnIndexOrThrow(_cursor, "label")
          val _cursorIndexOfDescription: Int = getColumnIndexOrThrow(_cursor, "description")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfCategory: Int = getColumnIndexOrThrow(_cursor, "category")
          val _cursorIndexOfEntryCount: Int = getColumnIndexOrThrow(_cursor, "entry_count")
          val _cursorIndexOfLastUpdated: Int = getColumnIndexOrThrow(_cursor, "last_updated")
          val _cursorIndexOfLastModifiedOnline: Int = getColumnIndexOrThrow(_cursor,
              "last_modified_online")
          val _cursorIndexOfEtag: Int = getColumnIndexOrThrow(_cursor, "etag")
          val _cursorIndexOfIsBuiltin: Int = getColumnIndexOrThrow(_cursor, "is_builtin")
          val _cursorIndexOfSizeBytes: Int = getColumnIndexOrThrow(_cursor, "size_bytes")
          val _cursorIndexOfHealth: Int = getColumnIndexOrThrow(_cursor, "health")
          val _cursorIndexOfLastError: Int = getColumnIndexOrThrow(_cursor, "last_error")
          val _cursorIndexOfConsecutiveFailures: Int = getColumnIndexOrThrow(_cursor,
              "consecutive_failures")
          val _cursorIndexOfPrevEntryCount: Int = getColumnIndexOrThrow(_cursor, "prev_entry_count")
          val _cursorIndexOfDomainsAdded: Int = getColumnIndexOrThrow(_cursor, "domains_added")
          val _cursorIndexOfDomainsRemoved: Int = getColumnIndexOrThrow(_cursor, "domains_removed")
          val _result: MutableList<HostSource> = ArrayList<HostSource>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HostSource
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUrl: String
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl)
            val _tmpLabel: String
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel)
            val _tmpDescription: String
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription)
            val _tmpEnabled: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp != 0
            val _tmpCategory: SourceCategory
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfCategory)
            _tmpCategory = __converters.toSourceCategory(_tmp_1)
            val _tmpEntryCount: Int
            _tmpEntryCount = _cursor.getInt(_cursorIndexOfEntryCount)
            val _tmpLastUpdated: Long
            _tmpLastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated)
            val _tmpLastModifiedOnline: String
            _tmpLastModifiedOnline = _cursor.getString(_cursorIndexOfLastModifiedOnline)
            val _tmpEtag: String
            _tmpEtag = _cursor.getString(_cursorIndexOfEtag)
            val _tmpIsBuiltin: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsBuiltin)
            _tmpIsBuiltin = _tmp_2 != 0
            val _tmpSizeBytes: Long
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes)
            val _tmpHealth: SourceHealth
            val _tmp_3: String
            _tmp_3 = _cursor.getString(_cursorIndexOfHealth)
            _tmpHealth = __converters.toSourceHealth(_tmp_3)
            val _tmpLastError: String
            _tmpLastError = _cursor.getString(_cursorIndexOfLastError)
            val _tmpConsecutiveFailures: Int
            _tmpConsecutiveFailures = _cursor.getInt(_cursorIndexOfConsecutiveFailures)
            val _tmpPrevEntryCount: Int
            _tmpPrevEntryCount = _cursor.getInt(_cursorIndexOfPrevEntryCount)
            val _tmpDomainsAdded: Int
            _tmpDomainsAdded = _cursor.getInt(_cursorIndexOfDomainsAdded)
            val _tmpDomainsRemoved: Int
            _tmpDomainsRemoved = _cursor.getInt(_cursorIndexOfDomainsRemoved)
            _item =
                HostSource(_tmpId,_tmpUrl,_tmpLabel,_tmpDescription,_tmpEnabled,_tmpCategory,_tmpEntryCount,_tmpLastUpdated,_tmpLastModifiedOnline,_tmpEtag,_tmpIsBuiltin,_tmpSizeBytes,_tmpHealth,_tmpLastError,_tmpConsecutiveFailures,_tmpPrevEntryCount,_tmpDomainsAdded,_tmpDomainsRemoved)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
