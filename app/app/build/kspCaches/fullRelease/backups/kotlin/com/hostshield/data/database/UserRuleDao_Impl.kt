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
import com.hostshield.`data`.model.RuleType
import com.hostshield.`data`.model.UserRule
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
public class UserRuleDao_Impl(
  __db: RoomDatabase,
) : UserRuleDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfUserRule: EntityInsertionAdapter<UserRule>

  private val __converters: Converters = Converters()

  private val __deletionAdapterOfUserRule: EntityDeletionOrUpdateAdapter<UserRule>

  private val __updateAdapterOfUserRule: EntityDeletionOrUpdateAdapter<UserRule>

  private val __preparedStmtOfDeleteById: SharedSQLiteStatement

  private val __preparedStmtOfSetEnabled: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfUserRule = object : EntityInsertionAdapter<UserRule>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `user_rules` (`id`,`hostname`,`type`,`redirect_ip`,`comment`,`enabled`,`is_wildcard`,`is_regex`,`created_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: UserRule) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.hostname)
        val _tmp: String = __converters.fromRuleType(entity.type)
        statement.bindString(3, _tmp)
        statement.bindString(4, entity.redirectIp)
        statement.bindString(5, entity.comment)
        val _tmp_1: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.isWildcard) 1 else 0
        statement.bindLong(7, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isRegex) 1 else 0
        statement.bindLong(8, _tmp_3.toLong())
        statement.bindLong(9, entity.createdAt)
      }
    }
    this.__deletionAdapterOfUserRule = object : EntityDeletionOrUpdateAdapter<UserRule>(__db) {
      protected override fun createQuery(): String = "DELETE FROM `user_rules` WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: UserRule) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfUserRule = object : EntityDeletionOrUpdateAdapter<UserRule>(__db) {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `user_rules` SET `id` = ?,`hostname` = ?,`type` = ?,`redirect_ip` = ?,`comment` = ?,`enabled` = ?,`is_wildcard` = ?,`is_regex` = ?,`created_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: UserRule) {
        statement.bindLong(1, entity.id)
        statement.bindString(2, entity.hostname)
        val _tmp: String = __converters.fromRuleType(entity.type)
        statement.bindString(3, _tmp)
        statement.bindString(4, entity.redirectIp)
        statement.bindString(5, entity.comment)
        val _tmp_1: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.isWildcard) 1 else 0
        statement.bindLong(7, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isRegex) 1 else 0
        statement.bindLong(8, _tmp_3.toLong())
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.id)
      }
    }
    this.__preparedStmtOfDeleteById = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM user_rules WHERE id = ?"
        return _query
      }
    }
    this.__preparedStmtOfSetEnabled = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "UPDATE user_rules SET enabled = ? WHERE id = ?"
        return _query
      }
    }
  }

  public override suspend fun insert(rule: UserRule): Long = CoroutinesRoom.execute(__db, true,
      object : Callable<Long> {
    public override fun call(): Long {
      __db.beginTransaction()
      try {
        val _result: Long = __insertionAdapterOfUserRule.insertAndReturnId(rule)
        __db.setTransactionSuccessful()
        return _result
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun insertAll(rules: List<UserRule>): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfUserRule.insert(rules)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun delete(rule: UserRule): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __deletionAdapterOfUserRule.handle(rule)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun update(rule: UserRule): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __updateAdapterOfUserRule.handle(rule)
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

  public override fun getAllRules(): Flow<List<UserRule>> {
    val _sql: String = "SELECT * FROM user_rules ORDER BY type, hostname"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("user_rules"), object :
        Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp: String
            _tmp = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_2 != 0
            val _tmpIsRegex: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_3 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override suspend fun getAllRulesList(): List<UserRule> {
    val _sql: String = "SELECT * FROM user_rules ORDER BY type, hostname"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp: String
            _tmp = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_2 != 0
            val _tmpIsRegex: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_3 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override suspend fun getEnabledByType(type: RuleType): List<UserRule> {
    val _sql: String = "SELECT * FROM user_rules WHERE type = ? AND enabled = 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    val _tmp: String = __converters.fromRuleType(type)
    _statement.bindString(_argIndex, _tmp)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp_1)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_2 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_3 != 0
            val _tmpIsRegex: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_4 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override fun getByType(type: RuleType): Flow<List<UserRule>> {
    val _sql: String = "SELECT * FROM user_rules WHERE type = ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    val _tmp: String = __converters.fromRuleType(type)
    _statement.bindString(_argIndex, _tmp)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("user_rules"), object :
        Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp_1: String
            _tmp_1 = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp_1)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_2 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_3 != 0
            val _tmpIsRegex: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_4 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override suspend fun getEnabledWildcards(): List<UserRule> {
    val _sql: String = "SELECT * FROM user_rules WHERE is_wildcard = 1 AND enabled = 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp: String
            _tmp = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_2 != 0
            val _tmpIsRegex: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_3 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override suspend fun getEnabledRegexRules(): List<UserRule> {
    val _sql: String = "SELECT * FROM user_rules WHERE is_regex = 1 AND enabled = 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp: String
            _tmp = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_2 != 0
            val _tmpIsRegex: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_3 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override fun search(query: String): Flow<List<UserRule>> {
    val _sql: String = "SELECT * FROM user_rules WHERE hostname LIKE '%' || ? || '%'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, query)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("user_rules"), object :
        Callable<List<UserRule>> {
      public override fun call(): List<UserRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfHostname: Int = getColumnIndexOrThrow(_cursor, "hostname")
          val _cursorIndexOfType: Int = getColumnIndexOrThrow(_cursor, "type")
          val _cursorIndexOfRedirectIp: Int = getColumnIndexOrThrow(_cursor, "redirect_ip")
          val _cursorIndexOfComment: Int = getColumnIndexOrThrow(_cursor, "comment")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfIsWildcard: Int = getColumnIndexOrThrow(_cursor, "is_wildcard")
          val _cursorIndexOfIsRegex: Int = getColumnIndexOrThrow(_cursor, "is_regex")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "created_at")
          val _result: MutableList<UserRule> = ArrayList<UserRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: UserRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpHostname: String
            _tmpHostname = _cursor.getString(_cursorIndexOfHostname)
            val _tmpType: RuleType
            val _tmp: String
            _tmp = _cursor.getString(_cursorIndexOfType)
            _tmpType = __converters.toRuleType(_tmp)
            val _tmpRedirectIp: String
            _tmpRedirectIp = _cursor.getString(_cursorIndexOfRedirectIp)
            val _tmpComment: String
            _tmpComment = _cursor.getString(_cursorIndexOfComment)
            val _tmpEnabled: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_1 != 0
            val _tmpIsWildcard: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsWildcard)
            _tmpIsWildcard = _tmp_2 != 0
            val _tmpIsRegex: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRegex)
            _tmpIsRegex = _tmp_3 != 0
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item =
                UserRule(_tmpId,_tmpHostname,_tmpType,_tmpRedirectIp,_tmpComment,_tmpEnabled,_tmpIsWildcard,_tmpIsRegex,_tmpCreatedAt)
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

  public override fun countByType(type: RuleType): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM user_rules WHERE type = ?"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    val _tmp: String = __converters.fromRuleType(type)
    _statement.bindString(_argIndex, _tmp)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("user_rules"), object : Callable<Int> {
      public override fun call(): Int {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Int
          if (_cursor.moveToFirst()) {
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(0)
            _result = _tmp_1
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

  public override suspend fun exists(hostname: String): Boolean {
    val _sql: String = "SELECT EXISTS(SELECT 1 FROM user_rules WHERE hostname = ?)"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, hostname)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<Boolean> {
      public override fun call(): Boolean {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _result: Boolean
          if (_cursor.moveToFirst()) {
            val _tmp: Int
            _tmp = _cursor.getInt(0)
            _result = _tmp != 0
          } else {
            _result = false
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
