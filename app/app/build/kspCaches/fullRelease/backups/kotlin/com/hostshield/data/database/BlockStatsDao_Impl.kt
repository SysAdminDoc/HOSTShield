package com.hostshield.`data`.database

import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.CoroutinesRoom
import androidx.room.CoroutinesRoom.Companion.execute
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.util.createCancellationSignal
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import com.hostshield.`data`.model.BlockStats
import java.lang.Class
import java.util.ArrayList
import java.util.concurrent.Callable
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
public class BlockStatsDao_Impl(
  __db: RoomDatabase,
) : BlockStatsDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfBlockStats: EntityInsertionAdapter<BlockStats>
  init {
    this.__db = __db
    this.__insertionAdapterOfBlockStats = object : EntityInsertionAdapter<BlockStats>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `block_stats` (`date`,`blocked_count`,`allowed_count`,`total_queries`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: BlockStats) {
        statement.bindString(1, entity.date)
        statement.bindLong(2, entity.blockedCount.toLong())
        statement.bindLong(3, entity.allowedCount.toLong())
        statement.bindLong(4, entity.totalQueries.toLong())
      }
    }
  }

  public override suspend fun upsert(stats: BlockStats): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfBlockStats.insert(stats)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override fun getRecentStats(days: Int): Flow<List<BlockStats>> {
    val _sql: String = "SELECT * FROM block_stats ORDER BY date DESC LIMIT ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, days.toLong())
    return CoroutinesRoom.createFlow(__db, false, arrayOf("block_stats"), object :
        Callable<List<BlockStats>> {
      public override fun call(): List<BlockStats> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfDate: Int = getColumnIndexOrThrow(_cursor, "date")
          val _cursorIndexOfBlockedCount: Int = getColumnIndexOrThrow(_cursor, "blocked_count")
          val _cursorIndexOfAllowedCount: Int = getColumnIndexOrThrow(_cursor, "allowed_count")
          val _cursorIndexOfTotalQueries: Int = getColumnIndexOrThrow(_cursor, "total_queries")
          val _result: MutableList<BlockStats> = ArrayList<BlockStats>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: BlockStats
            val _tmpDate: String
            _tmpDate = _cursor.getString(_cursorIndexOfDate)
            val _tmpBlockedCount: Int
            _tmpBlockedCount = _cursor.getInt(_cursorIndexOfBlockedCount)
            val _tmpAllowedCount: Int
            _tmpAllowedCount = _cursor.getInt(_cursorIndexOfAllowedCount)
            val _tmpTotalQueries: Int
            _tmpTotalQueries = _cursor.getInt(_cursorIndexOfTotalQueries)
            _item = BlockStats(_tmpDate,_tmpBlockedCount,_tmpAllowedCount,_tmpTotalQueries)
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

  public override suspend fun getStatsByDate(date: String): BlockStats? {
    val _sql: String = "SELECT * FROM block_stats WHERE date = ? LIMIT 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, date)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<BlockStats?> {
      public override fun call(): BlockStats? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfDate: Int = getColumnIndexOrThrow(_cursor, "date")
          val _cursorIndexOfBlockedCount: Int = getColumnIndexOrThrow(_cursor, "blocked_count")
          val _cursorIndexOfAllowedCount: Int = getColumnIndexOrThrow(_cursor, "allowed_count")
          val _cursorIndexOfTotalQueries: Int = getColumnIndexOrThrow(_cursor, "total_queries")
          val _result: BlockStats?
          if (_cursor.moveToFirst()) {
            val _tmpDate: String
            _tmpDate = _cursor.getString(_cursorIndexOfDate)
            val _tmpBlockedCount: Int
            _tmpBlockedCount = _cursor.getInt(_cursorIndexOfBlockedCount)
            val _tmpAllowedCount: Int
            _tmpAllowedCount = _cursor.getInt(_cursorIndexOfAllowedCount)
            val _tmpTotalQueries: Int
            _tmpTotalQueries = _cursor.getInt(_cursorIndexOfTotalQueries)
            _result = BlockStats(_tmpDate,_tmpBlockedCount,_tmpAllowedCount,_tmpTotalQueries)
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

  public override fun getTotalBlocked(): Flow<Int?> {
    val _sql: String = "SELECT SUM(blocked_count) FROM block_stats"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("block_stats"), object : Callable<Int?> {
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

  public override fun getTotalQueries(): Flow<Int?> {
    val _sql: String = "SELECT SUM(total_queries) FROM block_stats"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("block_stats"), object : Callable<Int?> {
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

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
