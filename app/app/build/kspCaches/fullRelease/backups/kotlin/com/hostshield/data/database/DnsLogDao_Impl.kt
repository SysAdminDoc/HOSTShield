package com.hostshield.`data`.database

import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.CoroutinesRoom
import androidx.room.CoroutinesRoom.Companion.execute
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.SharedSQLiteStatement
import androidx.room.util.createCancellationSignal
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import com.hostshield.`data`.model.DnsLogEntry
import java.lang.Class
import java.util.ArrayList
import java.util.concurrent.Callable
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
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
public class DnsLogDao_Impl(
  __db: RoomDatabase,
) : DnsLogDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfDnsLogEntry: EntityInsertionAdapter<DnsLogEntry>

  private val __insertionAdapterOfDnsLogEntry_1: EntityInsertionAdapter<DnsLogEntry>

  private val __preparedStmtOfUpdateAppInfo: SharedSQLiteStatement

  private val __preparedStmtOfDeleteOlderThan: SharedSQLiteStatement

  private val __preparedStmtOfDeleteOldestBatch: SharedSQLiteStatement

  private val __preparedStmtOfDeleteAll: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfDnsLogEntry = object : EntityInsertionAdapter<DnsLogEntry>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `dns_logs` (`id`,`hostname`,`blocked`,`app_package`,`app_label`,`timestamp`,`source_ip`,`query_type`,`response_time_ms`,`upstream_server`,`cname_chain`,`resolved_ips`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: DnsLogEntry) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.hostname)
        val _tmp: Int = if (entity.blocked) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindString(4, entity.appPackage)
        statement.bindString(5, entity.appLabel)
        statement.bindLong(6, entity.timestamp)
        statement.bindString(7, entity.sourceIp)
        statement.bindString(8, entity.queryType)
        statement.bindLong(9, entity.responseTimeMs.toLong())
        statement.bindString(10, entity.upstreamServer)
        statement.bindString(11, entity.cnameChain)
        statement.bindString(12, entity.resolvedIps)
      }
    }
    this.__insertionAdapterOfDnsLogEntry_1 = object : EntityInsertionAdapter<DnsLogEntry>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `dns_logs` (`id`,`hostname`,`blocked`,`app_package`,`app_label`,`timestamp`,`source_ip`,`query_type`,`response_time_ms`,`upstream_server`,`cname_chain`,`resolved_ips`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: DnsLogEntry) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.hostname)
        val _tmp: Int = if (entity.blocked) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindString(4, entity.appPackage)
        statement.bindString(5, entity.appLabel)
        statement.bindLong(6, entity.timestamp)
        statement.bindString(7, entity.sourceIp)
        statement.bindString(8, entity.queryType)
        statement.bindLong(9, entity.responseTimeMs.toLong())
        statement.bindString(10, entity.upstreamServer)
        statement.bindString(11, entity.cnameChain)
        statement.bindString(12, entity.resolvedIps)
      }
    }
    this.__preparedStmtOfUpdateAppInfo = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE dns_logs SET app_package = ?, app_label = ? WHERE id = ? AND app_package = ''"
        return _query
      }
    }
    this.__preparedStmtOfDeleteOlderThan = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM dns_logs WHERE timestamp < ?"
        return _query
      }
    }
    this.__preparedStmtOfDeleteOldestBatch = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "DELETE FROM dns_logs WHERE id IN (SELECT id FROM dns_logs WHERE timestamp < ? LIMIT ?)"
        return _query
      }
    }
    this.__preparedStmtOfDeleteAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM dns_logs"
        return _query
      }
    }
  }

  public override suspend fun insert(entry: DnsLogEntry): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfDnsLogEntry.insert(entry)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun insertAndGetId(entry: DnsLogEntry): Long =
      CoroutinesRoom.execute(__db, true, object : Callable<Long> {
    public override fun call(): Long {
      __db.beginTransaction()
      try {
        val _result: Long = __insertionAdapterOfDnsLogEntry.insertAndReturnId(entry)
        __db.setTransactionSuccessful()
        return _result
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun insertAll(entries: List<DnsLogEntry>): Unit =
      CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfDnsLogEntry_1.insert(entries)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun updateAppInfo(
    id: Long,
    pkg: String,
    label: String,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfUpdateAppInfo.acquire()
      var _argIndex: Int = 1
      _stmt.bindString(_argIndex, pkg)
      _argIndex = 2
      _stmt.bindString(_argIndex, label)
      _argIndex = 3
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
        __preparedStmtOfUpdateAppInfo.release(_stmt)
      }
    }
  })

  public override suspend fun deleteOlderThan(before: Long): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeleteOlderThan.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, before)
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeleteOlderThan.release(_stmt)
      }
    }
  })

  public override suspend fun deleteOldestBatch(before: Long, batchSize: Int): Int =
      CoroutinesRoom.execute(__db, true, object : Callable<Int> {
    public override fun call(): Int {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeleteOldestBatch.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, before)
      _argIndex = 2
      _stmt.bindLong(_argIndex, batchSize.toLong())
      try {
        __db.beginTransaction()
        try {
          val _result: Int = _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
          return _result
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeleteOldestBatch.release(_stmt)
      }
    }
  })

  public override suspend fun deleteAll(): Unit = CoroutinesRoom.execute(__db, true, object :
      Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeleteAll.acquire()
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeleteAll.release(_stmt)
      }
    }
  })

  public override fun getRecentLogs(limit: Int): Flow<List<DnsLogEntry>> {
    val _sql: String = "SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<DnsLogEntry>> {
      public override fun call(): List<DnsLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfBlocked: Int = getColumnIndexOrThrow(_cursor, "blocked")
          val _cursorIndexOfAppPackage: Int = getColumnIndexOrThrow(_cursor, "app_package")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _cursorIndexOfSourceIp: Int = getColumnIndexOrThrow(_cursor, "source_ip")
          val _cursorIndexOfQueryType: Int = getColumnIndexOrThrow(_cursor, "query_type")
          val _cursorIndexOfResponseTimeMs: Int = getColumnIndexOrThrow(_cursor, "response_time_ms")
          val _cursorIndexOfUpstreamServer: Int = getColumnIndexOrThrow(_cursor, "upstream_server")
          val _cursorIndexOfCnameChain: Int = getColumnIndexOrThrow(_cursor, "cname_chain")
          val _cursorIndexOfResolvedIps: Int = getColumnIndexOrThrow(_cursor, "resolved_ips")
          val _result: MutableList<DnsLogEntry> = ArrayList<DnsLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: DnsLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            val _tmpSourceIp: String
            _tmpSourceIp = _cursor.getString(_cursorIndexOfSourceIp)
            val _tmpQueryType: String
            _tmpQueryType = _cursor.getString(_cursorIndexOfQueryType)
            val _tmpResponseTimeMs: Int
            _tmpResponseTimeMs = _cursor.getInt(_cursorIndexOfResponseTimeMs)
            val _tmpUpstreamServer: String
            _tmpUpstreamServer = _cursor.getString(_cursorIndexOfUpstreamServer)
            val _tmpCnameChain: String
            _tmpCnameChain = _cursor.getString(_cursorIndexOfCnameChain)
            val _tmpResolvedIps: String
            _tmpResolvedIps = _cursor.getString(_cursorIndexOfResolvedIps)
            _item =
                DnsLogEntry(_tmpId,_tmpHostname,_tmpBlocked,_tmpAppPackage,_tmpAppLabel,_tmpTimestamp,_tmpSourceIp,_tmpQueryType,_tmpResponseTimeMs,_tmpUpstreamServer,_tmpCnameChain,_tmpResolvedIps)
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

  public override fun getBlockedLogs(limit: Int): Flow<List<DnsLogEntry>> {
    val _sql: String = "SELECT * FROM dns_logs WHERE blocked = 1 ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<DnsLogEntry>> {
      public override fun call(): List<DnsLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfBlocked: Int = getColumnIndexOrThrow(_cursor, "blocked")
          val _cursorIndexOfAppPackage: Int = getColumnIndexOrThrow(_cursor, "app_package")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _cursorIndexOfSourceIp: Int = getColumnIndexOrThrow(_cursor, "source_ip")
          val _cursorIndexOfQueryType: Int = getColumnIndexOrThrow(_cursor, "query_type")
          val _cursorIndexOfResponseTimeMs: Int = getColumnIndexOrThrow(_cursor, "response_time_ms")
          val _cursorIndexOfUpstreamServer: Int = getColumnIndexOrThrow(_cursor, "upstream_server")
          val _cursorIndexOfCnameChain: Int = getColumnIndexOrThrow(_cursor, "cname_chain")
          val _cursorIndexOfResolvedIps: Int = getColumnIndexOrThrow(_cursor, "resolved_ips")
          val _result: MutableList<DnsLogEntry> = ArrayList<DnsLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: DnsLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            val _tmpSourceIp: String
            _tmpSourceIp = _cursor.getString(_cursorIndexOfSourceIp)
            val _tmpQueryType: String
            _tmpQueryType = _cursor.getString(_cursorIndexOfQueryType)
            val _tmpResponseTimeMs: Int
            _tmpResponseTimeMs = _cursor.getInt(_cursorIndexOfResponseTimeMs)
            val _tmpUpstreamServer: String
            _tmpUpstreamServer = _cursor.getString(_cursorIndexOfUpstreamServer)
            val _tmpCnameChain: String
            _tmpCnameChain = _cursor.getString(_cursorIndexOfCnameChain)
            val _tmpResolvedIps: String
            _tmpResolvedIps = _cursor.getString(_cursorIndexOfResolvedIps)
            _item =
                DnsLogEntry(_tmpId,_tmpHostname,_tmpBlocked,_tmpAppPackage,_tmpAppLabel,_tmpTimestamp,_tmpSourceIp,_tmpQueryType,_tmpResponseTimeMs,_tmpUpstreamServer,_tmpCnameChain,_tmpResolvedIps)
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

  public override fun searchLogs(query: String, limit: Int): Flow<List<DnsLogEntry>> {
    val _sql: String =
        "SELECT * FROM dns_logs WHERE hostname LIKE '%' || ? || '%' ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, query)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<DnsLogEntry>> {
      public override fun call(): List<DnsLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfBlocked: Int = getColumnIndexOrThrow(_cursor, "blocked")
          val _cursorIndexOfAppPackage: Int = getColumnIndexOrThrow(_cursor, "app_package")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _cursorIndexOfSourceIp: Int = getColumnIndexOrThrow(_cursor, "source_ip")
          val _cursorIndexOfQueryType: Int = getColumnIndexOrThrow(_cursor, "query_type")
          val _cursorIndexOfResponseTimeMs: Int = getColumnIndexOrThrow(_cursor, "response_time_ms")
          val _cursorIndexOfUpstreamServer: Int = getColumnIndexOrThrow(_cursor, "upstream_server")
          val _cursorIndexOfCnameChain: Int = getColumnIndexOrThrow(_cursor, "cname_chain")
          val _cursorIndexOfResolvedIps: Int = getColumnIndexOrThrow(_cursor, "resolved_ips")
          val _result: MutableList<DnsLogEntry> = ArrayList<DnsLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: DnsLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            val _tmpSourceIp: String
            _tmpSourceIp = _cursor.getString(_cursorIndexOfSourceIp)
            val _tmpQueryType: String
            _tmpQueryType = _cursor.getString(_cursorIndexOfQueryType)
            val _tmpResponseTimeMs: Int
            _tmpResponseTimeMs = _cursor.getInt(_cursorIndexOfResponseTimeMs)
            val _tmpUpstreamServer: String
            _tmpUpstreamServer = _cursor.getString(_cursorIndexOfUpstreamServer)
            val _tmpCnameChain: String
            _tmpCnameChain = _cursor.getString(_cursorIndexOfCnameChain)
            val _tmpResolvedIps: String
            _tmpResolvedIps = _cursor.getString(_cursorIndexOfResolvedIps)
            _item =
                DnsLogEntry(_tmpId,_tmpHostname,_tmpBlocked,_tmpAppPackage,_tmpAppLabel,_tmpTimestamp,_tmpSourceIp,_tmpQueryType,_tmpResponseTimeMs,_tmpUpstreamServer,_tmpCnameChain,_tmpResolvedIps)
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

  public override fun getTopBlocked(limit: Int): Flow<List<TopHostname>> {
    val _sql: String =
        "SELECT hostname, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 GROUP BY hostname ORDER BY cnt DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<TopHostname>> {
      public override fun call(): List<TopHostname> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHostname: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<TopHostname> = ArrayList<TopHostname>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopHostname
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopHostname(_tmpHostname,_tmpCnt)
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

  public override fun getTopBlockedApps(limit: Int): Flow<List<TopApp>> {
    val _sql: String =
        "SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 AND app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<TopApp>> {
      public override fun call(): List<TopApp> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfAppPackage: Int = 0
          val _cursorIndexOfAppLabel: Int = 1
          val _cursorIndexOfCnt: Int = 2
          val _result: MutableList<TopApp> = ArrayList<TopApp>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopApp
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopApp(_tmpAppPackage,_tmpAppLabel,_tmpCnt)
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

  public override fun getTopQueryApps(limit: Int): Flow<List<TopApp>> {
    val _sql: String =
        "SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<TopApp>> {
      public override fun call(): List<TopApp> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfAppPackage: Int = 0
          val _cursorIndexOfAppLabel: Int = 1
          val _cursorIndexOfCnt: Int = 2
          val _result: MutableList<TopApp> = ArrayList<TopApp>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopApp
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopApp(_tmpAppPackage,_tmpAppLabel,_tmpCnt)
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

  public override fun getBlockedCountSince(since: Long): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM dns_logs WHERE blocked = 1 AND timestamp > ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object : Callable<Int> {
      public override fun call(): Int {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Int
          if (_cursor.moveToFirst()) {
            val _tmp: Int
            _tmp = _cursor.getInt(0)
            _result = _tmp
          } else {
            _result = 0
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

  public override fun getTotalCountSince(since: Long): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM dns_logs WHERE timestamp > ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object : Callable<Int> {
      public override fun call(): Int {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Int
          if (_cursor.moveToFirst()) {
            val _tmp: Int
            _tmp = _cursor.getInt(0)
            _result = _tmp
          } else {
            _result = 0
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

  public override fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>> {
    val _sql: String = """
        |
        |        SELECT CAST((timestamp / 3600000) % 24 AS INTEGER) as hour, COUNT(*) as cnt
        |        FROM dns_logs WHERE blocked = 1 AND timestamp > ?
        |        GROUP BY hour ORDER BY hour
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<HourlyStat>> {
      public override fun call(): List<HourlyStat> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHour: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<HourlyStat> = ArrayList<HourlyStat>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HourlyStat
            val _tmpHour: Int
            _tmpHour = _cursor.getInt(_cursorIndexOfHour)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = HourlyStat(_tmpHour,_tmpCnt)
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

  public override fun getHourlyTotal(since: Long): Flow<List<HourlyStat>> {
    val _sql: String = """
        |
        |        SELECT CAST((timestamp / 3600000) % 24 AS INTEGER) as hour, COUNT(*) as cnt
        |        FROM dns_logs WHERE timestamp > ?
        |        GROUP BY hour ORDER BY hour
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<HourlyStat>> {
      public override fun call(): List<HourlyStat> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHour: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<HourlyStat> = ArrayList<HourlyStat>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HourlyStat
            val _tmpHour: Int
            _tmpHour = _cursor.getInt(_cursorIndexOfHour)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = HourlyStat(_tmpHour,_tmpCnt)
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

  public override suspend fun getTotalLogCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM dns_logs"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<Int> {
      public override fun call(): Int {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Int
          if (_cursor.moveToFirst()) {
            val _tmp: Int
            _tmp = _cursor.getInt(0)
            _result = _tmp
          } else {
            _result = 0
          }
          return _result
        } finally {
          _cursor.close()
          _statement.release()
        }
      }
    })
  }

  public override fun getLogsForApp(pkg: String, limit: Int): Flow<List<DnsLogEntry>> {
    val _sql: String =
        "SELECT * FROM dns_logs WHERE app_package = ? ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, pkg)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<DnsLogEntry>> {
      public override fun call(): List<DnsLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfBlocked: Int = getColumnIndexOrThrow(_cursor, "blocked")
          val _cursorIndexOfAppPackage: Int = getColumnIndexOrThrow(_cursor, "app_package")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _cursorIndexOfSourceIp: Int = getColumnIndexOrThrow(_cursor, "source_ip")
          val _cursorIndexOfQueryType: Int = getColumnIndexOrThrow(_cursor, "query_type")
          val _cursorIndexOfResponseTimeMs: Int = getColumnIndexOrThrow(_cursor, "response_time_ms")
          val _cursorIndexOfUpstreamServer: Int = getColumnIndexOrThrow(_cursor, "upstream_server")
          val _cursorIndexOfCnameChain: Int = getColumnIndexOrThrow(_cursor, "cname_chain")
          val _cursorIndexOfResolvedIps: Int = getColumnIndexOrThrow(_cursor, "resolved_ips")
          val _result: MutableList<DnsLogEntry> = ArrayList<DnsLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: DnsLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            val _tmpSourceIp: String
            _tmpSourceIp = _cursor.getString(_cursorIndexOfSourceIp)
            val _tmpQueryType: String
            _tmpQueryType = _cursor.getString(_cursorIndexOfQueryType)
            val _tmpResponseTimeMs: Int
            _tmpResponseTimeMs = _cursor.getInt(_cursorIndexOfResponseTimeMs)
            val _tmpUpstreamServer: String
            _tmpUpstreamServer = _cursor.getString(_cursorIndexOfUpstreamServer)
            val _tmpCnameChain: String
            _tmpCnameChain = _cursor.getString(_cursorIndexOfCnameChain)
            val _tmpResolvedIps: String
            _tmpResolvedIps = _cursor.getString(_cursorIndexOfResolvedIps)
            _item =
                DnsLogEntry(_tmpId,_tmpHostname,_tmpBlocked,_tmpAppPackage,_tmpAppLabel,_tmpTimestamp,_tmpSourceIp,_tmpQueryType,_tmpResponseTimeMs,_tmpUpstreamServer,_tmpCnameChain,_tmpResolvedIps)
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

  public override suspend fun getById(id: Long): DnsLogEntry? {
    val _sql: String = "SELECT * FROM dns_logs WHERE id = ? LIMIT 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, id)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<DnsLogEntry?> {
      public override fun call(): DnsLogEntry? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfBlocked: Int = getColumnIndexOrThrow(_cursor, "blocked")
          val _cursorIndexOfAppPackage: Int = getColumnIndexOrThrow(_cursor, "app_package")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _cursorIndexOfSourceIp: Int = getColumnIndexOrThrow(_cursor, "source_ip")
          val _cursorIndexOfQueryType: Int = getColumnIndexOrThrow(_cursor, "query_type")
          val _cursorIndexOfResponseTimeMs: Int = getColumnIndexOrThrow(_cursor, "response_time_ms")
          val _cursorIndexOfUpstreamServer: Int = getColumnIndexOrThrow(_cursor, "upstream_server")
          val _cursorIndexOfCnameChain: Int = getColumnIndexOrThrow(_cursor, "cname_chain")
          val _cursorIndexOfResolvedIps: Int = getColumnIndexOrThrow(_cursor, "resolved_ips")
          val _result: DnsLogEntry?
          if (_cursor.moveToFirst()) {
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            val _tmpSourceIp: String
            _tmpSourceIp = _cursor.getString(_cursorIndexOfSourceIp)
            val _tmpQueryType: String
            _tmpQueryType = _cursor.getString(_cursorIndexOfQueryType)
            val _tmpResponseTimeMs: Int
            _tmpResponseTimeMs = _cursor.getInt(_cursorIndexOfResponseTimeMs)
            val _tmpUpstreamServer: String
            _tmpUpstreamServer = _cursor.getString(_cursorIndexOfUpstreamServer)
            val _tmpCnameChain: String
            _tmpCnameChain = _cursor.getString(_cursorIndexOfCnameChain)
            val _tmpResolvedIps: String
            _tmpResolvedIps = _cursor.getString(_cursorIndexOfResolvedIps)
            _result =
                DnsLogEntry(_tmpId,_tmpHostname,_tmpBlocked,_tmpAppPackage,_tmpAppLabel,_tmpTimestamp,_tmpSourceIp,_tmpQueryType,_tmpResponseTimeMs,_tmpUpstreamServer,_tmpCnameChain,_tmpResolvedIps)
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

  public override fun getDailyBreakdown(since: Long): Flow<List<DailyBreakdown>> {
    val _sql: String = """
        |
        |        SELECT date(timestamp / 1000, 'unixepoch', 'localtime') as day,
        |            COUNT(*) as total,
        |            SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked
        |        FROM dns_logs WHERE timestamp > ?
        |        GROUP BY day ORDER BY day ASC
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<DailyBreakdown>> {
      public override fun call(): List<DailyBreakdown> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfDay: Int = 0
          val _cursorIndexOfTotal: Int = 1
          val _cursorIndexOfBlocked: Int = 2
          val _result: MutableList<DailyBreakdown> = ArrayList<DailyBreakdown>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: DailyBreakdown
            val _tmpDay: String
            _tmpDay = _cursor.getString(_cursorIndexOfDay)
            val _tmpTotal: Int
            _tmpTotal = _cursor.getInt(_cursorIndexOfTotal)
            val _tmpBlocked: Int
            _tmpBlocked = _cursor.getInt(_cursorIndexOfBlocked)
            _item = DailyBreakdown(_tmpDay,_tmpTotal,_tmpBlocked)
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

  public override fun getDomainsForApp(pkg: String, limit: Int): Flow<List<AppDomainStat>> {
    val _sql: String = """
        |
        |        SELECT hostname, MAX(blocked) as blocked, COUNT(*) as cnt
        |        FROM dns_logs WHERE app_package = ?
        |        GROUP BY hostname ORDER BY cnt DESC LIMIT ?
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, pkg)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<AppDomainStat>> {
      public override fun call(): List<AppDomainStat> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHostname: Int = 0
          val _cursorIndexOfBlocked: Int = 1
          val _cursorIndexOfCnt: Int = 2
          val _result: MutableList<AppDomainStat> = ArrayList<AppDomainStat>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: AppDomainStat
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpBlocked: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfBlocked)
            _tmpBlocked = _tmp != 0
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = AppDomainStat(_tmpHostname,_tmpBlocked,_tmpCnt)
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

  public override fun getAllAppsWithCounts(): Flow<List<AppQueryStat>> {
    val _sql: String = """
        |
        |        SELECT app_package, app_label,
        |            COUNT(*) as total_queries,
        |            SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_queries
        |        FROM dns_logs WHERE app_package != ''
        |        GROUP BY app_package ORDER BY total_queries DESC
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<AppQueryStat>> {
      public override fun call(): List<AppQueryStat> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfAppPackage: Int = 0
          val _cursorIndexOfAppLabel: Int = 1
          val _cursorIndexOfTotalQueries: Int = 2
          val _cursorIndexOfBlockedQueries: Int = 3
          val _result: MutableList<AppQueryStat> = ArrayList<AppQueryStat>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: AppQueryStat
            val _tmpAppPackage: String
            _tmpAppPackage = _cursor.getString(_cursorIndexOfAppPackage)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpTotalQueries: Int
            _tmpTotalQueries = _cursor.getInt(_cursorIndexOfTotalQueries)
            val _tmpBlockedQueries: Int
            _tmpBlockedQueries = _cursor.getInt(_cursorIndexOfBlockedQueries)
            _item = AppQueryStat(_tmpAppPackage,_tmpAppLabel,_tmpTotalQueries,_tmpBlockedQueries)
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

  public override suspend fun getRecentBlockedDomains(recentStart: Long, limit: Int):
      List<TopHostname> {
    val _sql: String = """
        |
        |        SELECT hostname, COUNT(*) as cnt FROM dns_logs
        |        WHERE blocked = 1 AND timestamp > ?
        |        GROUP BY hostname ORDER BY cnt DESC LIMIT ?
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, recentStart)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<TopHostname>> {
      public override fun call(): List<TopHostname> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHostname: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<TopHostname> = ArrayList<TopHostname>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopHostname
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopHostname(_tmpHostname,_tmpCnt)
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

  public override suspend fun getOlderBlockedDomains(
    olderStart: Long,
    olderEnd: Long,
    limit: Int,
  ): List<TopHostname> {
    val _sql: String = """
        |
        |        SELECT hostname, COUNT(*) as cnt FROM dns_logs
        |        WHERE blocked = 1 AND timestamp BETWEEN ? AND ?
        |        GROUP BY hostname ORDER BY cnt DESC LIMIT ?
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 3)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, olderStart)
    _argIndex = 2
    _statement.bindLong(_argIndex, olderEnd)
    _argIndex = 3
    _statement.bindLong(_argIndex, limit.toLong())
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<TopHostname>> {
      public override fun call(): List<TopHostname> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHostname: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<TopHostname> = ArrayList<TopHostname>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopHostname
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopHostname(_tmpHostname,_tmpCnt)
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

  public override fun getHourlyLatency(since: Long): Flow<List<HourlyLatency>> {
    val _sql: String = """
        |
        |        SELECT CAST((timestamp / 3600000) % 24 AS INTEGER) as hour,
        |            AVG(response_time_ms) as avgMs,
        |            MAX(response_time_ms) as maxMs,
        |            COUNT(*) as cnt
        |        FROM dns_logs WHERE timestamp > ? AND response_time_ms > 0 AND blocked = 0
        |        GROUP BY hour ORDER BY hour
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<HourlyLatency>> {
      public override fun call(): List<HourlyLatency> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHour: Int = 0
          val _cursorIndexOfAvgMs: Int = 1
          val _cursorIndexOfMaxMs: Int = 2
          val _cursorIndexOfCnt: Int = 3
          val _result: MutableList<HourlyLatency> = ArrayList<HourlyLatency>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: HourlyLatency
            val _tmpHour: Int
            _tmpHour = _cursor.getInt(_cursorIndexOfHour)
            val _tmpAvgMs: Float
            _tmpAvgMs = _cursor.getFloat(_cursorIndexOfAvgMs)
            val _tmpMaxMs: Int
            _tmpMaxMs = _cursor.getInt(_cursorIndexOfMaxMs)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = HourlyLatency(_tmpHour,_tmpAvgMs,_tmpMaxMs,_tmpCnt)
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

  public override fun getMostQueriedDomains(since: Long, limit: Int): Flow<List<TopHostname>> {
    val _sql: String = """
        |
        |        SELECT hostname, COUNT(*) as cnt
        |        FROM dns_logs WHERE timestamp > ?
        |        GROUP BY hostname ORDER BY cnt DESC LIMIT ?
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("dns_logs"), object :
        Callable<List<TopHostname>> {
      public override fun call(): List<TopHostname> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfHostname: Int = 0
          val _cursorIndexOfCnt: Int = 1
          val _result: MutableList<TopHostname> = ArrayList<TopHostname>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: TopHostname
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = TopHostname(_tmpHostname,_tmpCnt)
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

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
