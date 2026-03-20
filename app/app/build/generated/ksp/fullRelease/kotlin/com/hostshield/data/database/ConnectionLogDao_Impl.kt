package com.hostshield.`data`.database

import android.database.Cursor
import androidx.room.CoroutinesRoom
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.SharedSQLiteStatement
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import com.hostshield.`data`.model.ConnectionLogEntry
import java.lang.Class
import java.util.ArrayList
import java.util.concurrent.Callable
import javax.`annotation`.processing.Generated
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
public class ConnectionLogDao_Impl(
  __db: RoomDatabase,
) : ConnectionLogDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfConnectionLogEntry: EntityInsertionAdapter<ConnectionLogEntry>

  private val __preparedStmtOfDeleteOlderThan: SharedSQLiteStatement

  private val __preparedStmtOfDeleteAll: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfConnectionLogEntry = object :
        EntityInsertionAdapter<ConnectionLogEntry>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `connection_log` (`id`,`uid`,`package_name`,`app_label`,`destination`,`port`,`protocol`,`action`,`interface_name`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: ConnectionLogEntry) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.uid.toLong())
        statement.bindString(3, entity.packageName)
        statement.bindString(4, entity.appLabel)
        statement.bindString(5, entity.destination)
        statement.bindLong(6, entity.port.toLong())
        statement.bindString(7, entity.protocol)
        statement.bindString(8, entity.action)
        statement.bindString(9, entity.interfaceName)
        statement.bindLong(10, entity.timestamp)
      }
    }
    this.__preparedStmtOfDeleteOlderThan = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM connection_log WHERE timestamp < ?"
        return _query
      }
    }
    this.__preparedStmtOfDeleteAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM connection_log"
        return _query
      }
    }
  }

  public override suspend fun insert(entry: ConnectionLogEntry): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfConnectionLogEntry.insert(entry)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
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

  public override fun getRecentLogs(limit: Int): Flow<List<ConnectionLogEntry>> {
    val _sql: String = "SELECT * FROM connection_log ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("connection_log"), object :
        Callable<List<ConnectionLogEntry>> {
      public override fun call(): List<ConnectionLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfDestination: Int = getColumnIndexOrThrow(_cursor, "destination")
          val _cursorIndexOfPort: Int = getColumnIndexOrThrow(_cursor, "port")
          val _cursorIndexOfProtocol: Int = getColumnIndexOrThrow(_cursor, "protocol")
          val _cursorIndexOfAction: Int = getColumnIndexOrThrow(_cursor, "action")
          val _cursorIndexOfInterfaceName: Int = getColumnIndexOrThrow(_cursor, "interface_name")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _result: MutableList<ConnectionLogEntry> =
              ArrayList<ConnectionLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: ConnectionLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpDestination: String
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination)
            val _tmpPort: Int
            _tmpPort = _cursor.getInt(_cursorIndexOfPort)
            val _tmpProtocol: String
            _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol)
            val _tmpAction: String
            _tmpAction = _cursor.getString(_cursorIndexOfAction)
            val _tmpInterfaceName: String
            _tmpInterfaceName = _cursor.getString(_cursorIndexOfInterfaceName)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            _item =
                ConnectionLogEntry(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpDestination,_tmpPort,_tmpProtocol,_tmpAction,_tmpInterfaceName,_tmpTimestamp)
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

  public override fun getBlockedLogs(limit: Int): Flow<List<ConnectionLogEntry>> {
    val _sql: String =
        "SELECT * FROM connection_log WHERE action = 'REJECT' ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("connection_log"), object :
        Callable<List<ConnectionLogEntry>> {
      public override fun call(): List<ConnectionLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfDestination: Int = getColumnIndexOrThrow(_cursor, "destination")
          val _cursorIndexOfPort: Int = getColumnIndexOrThrow(_cursor, "port")
          val _cursorIndexOfProtocol: Int = getColumnIndexOrThrow(_cursor, "protocol")
          val _cursorIndexOfAction: Int = getColumnIndexOrThrow(_cursor, "action")
          val _cursorIndexOfInterfaceName: Int = getColumnIndexOrThrow(_cursor, "interface_name")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _result: MutableList<ConnectionLogEntry> =
              ArrayList<ConnectionLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: ConnectionLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpDestination: String
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination)
            val _tmpPort: Int
            _tmpPort = _cursor.getInt(_cursorIndexOfPort)
            val _tmpProtocol: String
            _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol)
            val _tmpAction: String
            _tmpAction = _cursor.getString(_cursorIndexOfAction)
            val _tmpInterfaceName: String
            _tmpInterfaceName = _cursor.getString(_cursorIndexOfInterfaceName)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            _item =
                ConnectionLogEntry(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpDestination,_tmpPort,_tmpProtocol,_tmpAction,_tmpInterfaceName,_tmpTimestamp)
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

  public override fun getLogsForApp(uid: Int, limit: Int): Flow<List<ConnectionLogEntry>> {
    val _sql: String = "SELECT * FROM connection_log WHERE uid = ? ORDER BY timestamp DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, uid.toLong())
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("connection_log"), object :
        Callable<List<ConnectionLogEntry>> {
      public override fun call(): List<ConnectionLogEntry> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfDestination: Int = getColumnIndexOrThrow(_cursor, "destination")
          val _cursorIndexOfPort: Int = getColumnIndexOrThrow(_cursor, "port")
          val _cursorIndexOfProtocol: Int = getColumnIndexOrThrow(_cursor, "protocol")
          val _cursorIndexOfAction: Int = getColumnIndexOrThrow(_cursor, "action")
          val _cursorIndexOfInterfaceName: Int = getColumnIndexOrThrow(_cursor, "interface_name")
          val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_cursor, "timestamp")
          val _result: MutableList<ConnectionLogEntry> =
              ArrayList<ConnectionLogEntry>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: ConnectionLogEntry
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpDestination: String
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination)
            val _tmpPort: Int
            _tmpPort = _cursor.getInt(_cursorIndexOfPort)
            val _tmpProtocol: String
            _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol)
            val _tmpAction: String
            _tmpAction = _cursor.getString(_cursorIndexOfAction)
            val _tmpInterfaceName: String
            _tmpInterfaceName = _cursor.getString(_cursorIndexOfInterfaceName)
            val _tmpTimestamp: Long
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp)
            _item =
                ConnectionLogEntry(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpDestination,_tmpPort,_tmpProtocol,_tmpAction,_tmpInterfaceName,_tmpTimestamp)
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

  public override fun getTopBlockedApps(since: Long, limit: Int): Flow<List<FirewallTopApp>> {
    val _sql: String = """
        |
        |        SELECT uid, package_name, app_label, COUNT(*) as cnt
        |        FROM connection_log WHERE action = 'REJECT' AND timestamp > ?
        |        GROUP BY uid ORDER BY cnt DESC LIMIT ?
        |    
        """.trimMargin()
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, since)
    _argIndex = 2
    _statement.bindLong(_argIndex, limit.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("connection_log"), object :
        Callable<List<FirewallTopApp>> {
      public override fun call(): List<FirewallTopApp> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfUid: Int = 0
          val _cursorIndexOfPackageName: Int = 1
          val _cursorIndexOfAppLabel: Int = 2
          val _cursorIndexOfCnt: Int = 3
          val _result: MutableList<FirewallTopApp> = ArrayList<FirewallTopApp>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: FirewallTopApp
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpCnt: Int
            _tmpCnt = _cursor.getInt(_cursorIndexOfCnt)
            _item = FirewallTopApp(_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpCnt)
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

  public override fun getTotalBlockedCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM connection_log WHERE action = 'REJECT'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("connection_log"), object : Callable<Int>
        {
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

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
