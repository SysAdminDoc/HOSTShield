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
import com.hostshield.`data`.model.BlockingProfile
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
public class ProfileDao_Impl(
  __db: RoomDatabase,
) : ProfileDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfBlockingProfile: EntityInsertionAdapter<BlockingProfile>

  private val __deletionAdapterOfBlockingProfile: EntityDeletionOrUpdateAdapter<BlockingProfile>

  private val __updateAdapterOfBlockingProfile: EntityDeletionOrUpdateAdapter<BlockingProfile>

  private val __preparedStmtOfDeactivateAll: SharedSQLiteStatement

  private val __preparedStmtOfActivate: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfBlockingProfile = object :
        EntityInsertionAdapter<BlockingProfile>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `profiles` (`id`,`name`,`is_active`,`source_ids`,`schedule_start`,`schedule_end`,`days_of_week`,`wifi_ssids`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: BlockingProfile) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.name)
        val _tmp: Int = if (entity.isActive) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindString(4, entity.sourceIds)
        statement.bindString(5, entity.scheduleStart)
        statement.bindString(6, entity.scheduleEnd)
        statement.bindString(7, entity.daysOfWeek)
        statement.bindString(8, entity.wifiSsids)
      }
    }
    this.__deletionAdapterOfBlockingProfile = object :
        EntityDeletionOrUpdateAdapter<BlockingProfile>(__db) {
      protected override fun createQuery(): String = "DELETE FROM `profiles` WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: BlockingProfile) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfBlockingProfile = object :
        EntityDeletionOrUpdateAdapter<BlockingProfile>(__db) {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `profiles` SET `id` = ?,`name` = ?,`is_active` = ?,`source_ids` = ?,`schedule_start` = ?,`schedule_end` = ?,`days_of_week` = ?,`wifi_ssids` = ? WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: BlockingProfile) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.name)
        val _tmp: Int = if (entity.isActive) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindString(4, entity.sourceIds)
        statement.bindString(5, entity.scheduleStart)
        statement.bindString(6, entity.scheduleEnd)
        statement.bindString(7, entity.daysOfWeek)
        statement.bindString(8, entity.wifiSsids)
        statement.bindLong(9, entity.id)
      }
    }
    this.__preparedStmtOfDeactivateAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "UPDATE profiles SET is_active = 0"
        return _query
      }
    }
    this.__preparedStmtOfActivate = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "UPDATE profiles SET is_active = 1 WHERE id = ?"
        return _query
      }
    }
  }

  public override suspend fun insert(profile: BlockingProfile): Long = CoroutinesRoom.execute(__db,
      true, object : Callable<Long> {
    public override fun call(): Long {
      __db.beginTransaction()
      try {
        val _result: Long = __insertionAdapterOfBlockingProfile.insertAndReturnId(profile)
        __db.setTransactionSuccessful()
        return _result
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun delete(profile: BlockingProfile): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __deletionAdapterOfBlockingProfile.handle(profile)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun update(profile: BlockingProfile): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __updateAdapterOfBlockingProfile.handle(profile)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun deactivateAll(): Unit = CoroutinesRoom.execute(__db, true, object :
      Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeactivateAll.acquire()
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeactivateAll.release(_stmt)
      }
    }
  })

  public override suspend fun activate(id: Long): Unit = CoroutinesRoom.execute(__db, true, object :
      Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfActivate.acquire()
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
        __preparedStmtOfActivate.release(_stmt)
      }
    }
  })

  public override fun getAllProfiles(): Flow<List<BlockingProfile>> {
    val _sql: String = "SELECT * FROM profiles ORDER BY name"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("profiles"), object :
        Callable<List<BlockingProfile>> {
      public override fun call(): List<BlockingProfile> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfName: Int = getColumnIndexOrThrow(_cursor, "name")
          val _cursorIndexOfIsActive: Int = getColumnIndexOrThrow(_cursor, "is_active")
          val _cursorIndexOfSourceIds: Int = getColumnIndexOrThrow(_cursor, "source_ids")
          val _cursorIndexOfScheduleStart: Int = getColumnIndexOrThrow(_cursor, "schedule_start")
          val _cursorIndexOfScheduleEnd: Int = getColumnIndexOrThrow(_cursor, "schedule_end")
          val _cursorIndexOfDaysOfWeek: Int = getColumnIndexOrThrow(_cursor, "days_of_week")
          val _cursorIndexOfWifiSsids: Int = getColumnIndexOrThrow(_cursor, "wifi_ssids")
          val _result: MutableList<BlockingProfile> = ArrayList<BlockingProfile>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: BlockingProfile
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpName: String
            _tmpName = _cursor.getString(_cursorIndexOfName)
            val _tmpIsActive: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfIsActive)
            _tmpIsActive = _tmp != 0
            val _tmpSourceIds: String
            _tmpSourceIds = _cursor.getString(_cursorIndexOfSourceIds)
            val _tmpScheduleStart: String
            _tmpScheduleStart = _cursor.getString(_cursorIndexOfScheduleStart)
            val _tmpScheduleEnd: String
            _tmpScheduleEnd = _cursor.getString(_cursorIndexOfScheduleEnd)
            val _tmpDaysOfWeek: String
            _tmpDaysOfWeek = _cursor.getString(_cursorIndexOfDaysOfWeek)
            val _tmpWifiSsids: String
            _tmpWifiSsids = _cursor.getString(_cursorIndexOfWifiSsids)
            _item =
                BlockingProfile(_tmpId,_tmpName,_tmpIsActive,_tmpSourceIds,_tmpScheduleStart,_tmpScheduleEnd,_tmpDaysOfWeek,_tmpWifiSsids)
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

  public override suspend fun getAllProfilesList(): List<BlockingProfile> {
    val _sql: String = "SELECT * FROM profiles ORDER BY name"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<BlockingProfile>> {
      public override fun call(): List<BlockingProfile> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfName: Int = getColumnIndexOrThrow(_cursor, "name")
          val _cursorIndexOfIsActive: Int = getColumnIndexOrThrow(_cursor, "is_active")
          val _cursorIndexOfSourceIds: Int = getColumnIndexOrThrow(_cursor, "source_ids")
          val _cursorIndexOfScheduleStart: Int = getColumnIndexOrThrow(_cursor, "schedule_start")
          val _cursorIndexOfScheduleEnd: Int = getColumnIndexOrThrow(_cursor, "schedule_end")
          val _cursorIndexOfDaysOfWeek: Int = getColumnIndexOrThrow(_cursor, "days_of_week")
          val _cursorIndexOfWifiSsids: Int = getColumnIndexOrThrow(_cursor, "wifi_ssids")
          val _result: MutableList<BlockingProfile> = ArrayList<BlockingProfile>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: BlockingProfile
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpName: String
            _tmpName = _cursor.getString(_cursorIndexOfName)
            val _tmpIsActive: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfIsActive)
            _tmpIsActive = _tmp != 0
            val _tmpSourceIds: String
            _tmpSourceIds = _cursor.getString(_cursorIndexOfSourceIds)
            val _tmpScheduleStart: String
            _tmpScheduleStart = _cursor.getString(_cursorIndexOfScheduleStart)
            val _tmpScheduleEnd: String
            _tmpScheduleEnd = _cursor.getString(_cursorIndexOfScheduleEnd)
            val _tmpDaysOfWeek: String
            _tmpDaysOfWeek = _cursor.getString(_cursorIndexOfDaysOfWeek)
            val _tmpWifiSsids: String
            _tmpWifiSsids = _cursor.getString(_cursorIndexOfWifiSsids)
            _item =
                BlockingProfile(_tmpId,_tmpName,_tmpIsActive,_tmpSourceIds,_tmpScheduleStart,_tmpScheduleEnd,_tmpDaysOfWeek,_tmpWifiSsids)
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

  public override suspend fun getActiveProfile(): BlockingProfile? {
    val _sql: String = "SELECT * FROM profiles WHERE is_active = 1 LIMIT 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<BlockingProfile?> {
      public override fun call(): BlockingProfile? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfName: Int = getColumnIndexOrThrow(_cursor, "name")
          val _cursorIndexOfIsActive: Int = getColumnIndexOrThrow(_cursor, "is_active")
          val _cursorIndexOfSourceIds: Int = getColumnIndexOrThrow(_cursor, "source_ids")
          val _cursorIndexOfScheduleStart: Int = getColumnIndexOrThrow(_cursor, "schedule_start")
          val _cursorIndexOfScheduleEnd: Int = getColumnIndexOrThrow(_cursor, "schedule_end")
          val _cursorIndexOfDaysOfWeek: Int = getColumnIndexOrThrow(_cursor, "days_of_week")
          val _cursorIndexOfWifiSsids: Int = getColumnIndexOrThrow(_cursor, "wifi_ssids")
          val _result: BlockingProfile?
          if (_cursor.moveToFirst()) {
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpName: String
            _tmpName = _cursor.getString(_cursorIndexOfName)
            val _tmpIsActive: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfIsActive)
            _tmpIsActive = _tmp != 0
            val _tmpSourceIds: String
            _tmpSourceIds = _cursor.getString(_cursorIndexOfSourceIds)
            val _tmpScheduleStart: String
            _tmpScheduleStart = _cursor.getString(_cursorIndexOfScheduleStart)
            val _tmpScheduleEnd: String
            _tmpScheduleEnd = _cursor.getString(_cursorIndexOfScheduleEnd)
            val _tmpDaysOfWeek: String
            _tmpDaysOfWeek = _cursor.getString(_cursorIndexOfDaysOfWeek)
            val _tmpWifiSsids: String
            _tmpWifiSsids = _cursor.getString(_cursorIndexOfWifiSsids)
            _result =
                BlockingProfile(_tmpId,_tmpName,_tmpIsActive,_tmpSourceIds,_tmpScheduleStart,_tmpScheduleEnd,_tmpDaysOfWeek,_tmpWifiSsids)
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

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
