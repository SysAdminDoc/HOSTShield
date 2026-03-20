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
import com.hostshield.`data`.model.FirewallRule
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
public class FirewallRuleDao_Impl(
  __db: RoomDatabase,
) : FirewallRuleDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfFirewallRule: EntityInsertionAdapter<FirewallRule>

  private val __insertionAdapterOfFirewallRule_1: EntityInsertionAdapter<FirewallRule>

  private val __deletionAdapterOfFirewallRule: EntityDeletionOrUpdateAdapter<FirewallRule>

  private val __updateAdapterOfFirewallRule: EntityDeletionOrUpdateAdapter<FirewallRule>

  private val __preparedStmtOfSetWifi: SharedSQLiteStatement

  private val __preparedStmtOfSetMobile: SharedSQLiteStatement

  private val __preparedStmtOfSetVpn: SharedSQLiteStatement

  private val __preparedStmtOfBlockAll: SharedSQLiteStatement

  private val __preparedStmtOfAllowAll: SharedSQLiteStatement

  private val __preparedStmtOfResetAll: SharedSQLiteStatement

  private val __preparedStmtOfDeleteByUid: SharedSQLiteStatement
  init {
    this.__db = __db
    this.__insertionAdapterOfFirewallRule = object : EntityInsertionAdapter<FirewallRule>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `firewall_rules` (`id`,`uid`,`package_name`,`app_label`,`wifi_allowed`,`mobile_allowed`,`vpn_allowed`,`is_system`,`enabled`,`updated_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: FirewallRule) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.uid.toLong())
        statement.bindString(3, entity.packageName)
        statement.bindString(4, entity.appLabel)
        val _tmp: Int = if (entity.wifiAllowed) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: Int = if (entity.mobileAllowed) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.vpnAllowed) 1 else 0
        statement.bindLong(7, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isSystem) 1 else 0
        statement.bindLong(8, _tmp_3.toLong())
        val _tmp_4: Int = if (entity.enabled) 1 else 0
        statement.bindLong(9, _tmp_4.toLong())
        statement.bindLong(10, entity.updatedAt)
      }
    }
    this.__insertionAdapterOfFirewallRule_1 = object : EntityInsertionAdapter<FirewallRule>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR IGNORE INTO `firewall_rules` (`id`,`uid`,`package_name`,`app_label`,`wifi_allowed`,`mobile_allowed`,`vpn_allowed`,`is_system`,`enabled`,`updated_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: FirewallRule) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.uid.toLong())
        statement.bindString(3, entity.packageName)
        statement.bindString(4, entity.appLabel)
        val _tmp: Int = if (entity.wifiAllowed) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: Int = if (entity.mobileAllowed) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.vpnAllowed) 1 else 0
        statement.bindLong(7, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isSystem) 1 else 0
        statement.bindLong(8, _tmp_3.toLong())
        val _tmp_4: Int = if (entity.enabled) 1 else 0
        statement.bindLong(9, _tmp_4.toLong())
        statement.bindLong(10, entity.updatedAt)
      }
    }
    this.__deletionAdapterOfFirewallRule = object :
        EntityDeletionOrUpdateAdapter<FirewallRule>(__db) {
      protected override fun createQuery(): String = "DELETE FROM `firewall_rules` WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: FirewallRule) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfFirewallRule = object : EntityDeletionOrUpdateAdapter<FirewallRule>(__db)
        {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `firewall_rules` SET `id` = ?,`uid` = ?,`package_name` = ?,`app_label` = ?,`wifi_allowed` = ?,`mobile_allowed` = ?,`vpn_allowed` = ?,`is_system` = ?,`enabled` = ?,`updated_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: FirewallRule) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.uid.toLong())
        statement.bindString(3, entity.packageName)
        statement.bindString(4, entity.appLabel)
        val _tmp: Int = if (entity.wifiAllowed) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: Int = if (entity.mobileAllowed) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.vpnAllowed) 1 else 0
        statement.bindLong(7, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isSystem) 1 else 0
        statement.bindLong(8, _tmp_3.toLong())
        val _tmp_4: Int = if (entity.enabled) 1 else 0
        statement.bindLong(9, _tmp_4.toLong())
        statement.bindLong(10, entity.updatedAt)
        statement.bindLong(11, entity.id)
      }
    }
    this.__preparedStmtOfSetWifi = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET wifi_allowed = ?, updated_at = ? WHERE uid = ?"
        return _query
      }
    }
    this.__preparedStmtOfSetMobile = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET mobile_allowed = ?, updated_at = ? WHERE uid = ?"
        return _query
      }
    }
    this.__preparedStmtOfSetVpn = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET vpn_allowed = ?, updated_at = ? WHERE uid = ?"
        return _query
      }
    }
    this.__preparedStmtOfBlockAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET wifi_allowed = 0, mobile_allowed = 0, vpn_allowed = 0, updated_at = ? WHERE uid = ?"
        return _query
      }
    }
    this.__preparedStmtOfAllowAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET wifi_allowed = 1, mobile_allowed = 1, vpn_allowed = 1, updated_at = ? WHERE uid = ?"
        return _query
      }
    }
    this.__preparedStmtOfResetAll = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String =
            "UPDATE firewall_rules SET wifi_allowed = 1, mobile_allowed = 1, vpn_allowed = 1"
        return _query
      }
    }
    this.__preparedStmtOfDeleteByUid = object : SharedSQLiteStatement(__db) {
      public override fun createQuery(): String {
        val _query: String = "DELETE FROM firewall_rules WHERE uid = ?"
        return _query
      }
    }
  }

  public override suspend fun insert(rule: FirewallRule): Long = CoroutinesRoom.execute(__db, true,
      object : Callable<Long> {
    public override fun call(): Long {
      __db.beginTransaction()
      try {
        val _result: Long = __insertionAdapterOfFirewallRule.insertAndReturnId(rule)
        __db.setTransactionSuccessful()
        return _result
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun insertAll(rules: List<FirewallRule>): Unit =
      CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfFirewallRule_1.insert(rules)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun delete(rule: FirewallRule): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __deletionAdapterOfFirewallRule.handle(rule)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun update(rule: FirewallRule): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __updateAdapterOfFirewallRule.handle(rule)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun setWifi(
    uid: Int,
    allowed: Boolean,
    ts: Long,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfSetWifi.acquire()
      var _argIndex: Int = 1
      val _tmp: Int = if (allowed) 1 else 0
      _stmt.bindLong(_argIndex, _tmp.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, ts)
      _argIndex = 3
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfSetWifi.release(_stmt)
      }
    }
  })

  public override suspend fun setMobile(
    uid: Int,
    allowed: Boolean,
    ts: Long,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfSetMobile.acquire()
      var _argIndex: Int = 1
      val _tmp: Int = if (allowed) 1 else 0
      _stmt.bindLong(_argIndex, _tmp.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, ts)
      _argIndex = 3
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfSetMobile.release(_stmt)
      }
    }
  })

  public override suspend fun setVpn(
    uid: Int,
    allowed: Boolean,
    ts: Long,
  ): Unit = CoroutinesRoom.execute(__db, true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfSetVpn.acquire()
      var _argIndex: Int = 1
      val _tmp: Int = if (allowed) 1 else 0
      _stmt.bindLong(_argIndex, _tmp.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, ts)
      _argIndex = 3
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfSetVpn.release(_stmt)
      }
    }
  })

  public override suspend fun blockAll(uid: Int, ts: Long): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfBlockAll.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, ts)
      _argIndex = 2
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfBlockAll.release(_stmt)
      }
    }
  })

  public override suspend fun allowAll(uid: Int, ts: Long): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfAllowAll.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, ts)
      _argIndex = 2
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfAllowAll.release(_stmt)
      }
    }
  })

  public override suspend fun resetAll(): Unit = CoroutinesRoom.execute(__db, true, object :
      Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfResetAll.acquire()
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfResetAll.release(_stmt)
      }
    }
  })

  public override suspend fun deleteByUid(uid: Int): Unit = CoroutinesRoom.execute(__db, true,
      object : Callable<Unit> {
    public override fun call() {
      val _stmt: SupportSQLiteStatement = __preparedStmtOfDeleteByUid.acquire()
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, uid.toLong())
      try {
        __db.beginTransaction()
        try {
          _stmt.executeUpdateDelete()
          __db.setTransactionSuccessful()
        } finally {
          __db.endTransaction()
        }
      } finally {
        __preparedStmtOfDeleteByUid.release(_stmt)
      }
    }
  })

  public override fun getAllRules(): Flow<List<FirewallRule>> {
    val _sql: String = "SELECT * FROM firewall_rules ORDER BY app_label COLLATE NOCASE"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("firewall_rules"), object :
        Callable<List<FirewallRule>> {
      public override fun call(): List<FirewallRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfWifiAllowed: Int = getColumnIndexOrThrow(_cursor, "wifi_allowed")
          val _cursorIndexOfMobileAllowed: Int = getColumnIndexOrThrow(_cursor, "mobile_allowed")
          val _cursorIndexOfVpnAllowed: Int = getColumnIndexOrThrow(_cursor, "vpn_allowed")
          val _cursorIndexOfIsSystem: Int = getColumnIndexOrThrow(_cursor, "is_system")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_cursor, "updated_at")
          val _result: MutableList<FirewallRule> = ArrayList<FirewallRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: FirewallRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpWifiAllowed: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfWifiAllowed)
            _tmpWifiAllowed = _tmp != 0
            val _tmpMobileAllowed: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfMobileAllowed)
            _tmpMobileAllowed = _tmp_1 != 0
            val _tmpVpnAllowed: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfVpnAllowed)
            _tmpVpnAllowed = _tmp_2 != 0
            val _tmpIsSystem: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSystem)
            _tmpIsSystem = _tmp_3 != 0
            val _tmpEnabled: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_4 != 0
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt)
            _item =
                FirewallRule(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpWifiAllowed,_tmpMobileAllowed,_tmpVpnAllowed,_tmpIsSystem,_tmpEnabled,_tmpUpdatedAt)
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

  public override suspend fun getAllRulesList(): List<FirewallRule> {
    val _sql: String = "SELECT * FROM firewall_rules ORDER BY app_label COLLATE NOCASE"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<List<FirewallRule>> {
      public override fun call(): List<FirewallRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfWifiAllowed: Int = getColumnIndexOrThrow(_cursor, "wifi_allowed")
          val _cursorIndexOfMobileAllowed: Int = getColumnIndexOrThrow(_cursor, "mobile_allowed")
          val _cursorIndexOfVpnAllowed: Int = getColumnIndexOrThrow(_cursor, "vpn_allowed")
          val _cursorIndexOfIsSystem: Int = getColumnIndexOrThrow(_cursor, "is_system")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_cursor, "updated_at")
          val _result: MutableList<FirewallRule> = ArrayList<FirewallRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: FirewallRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpWifiAllowed: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfWifiAllowed)
            _tmpWifiAllowed = _tmp != 0
            val _tmpMobileAllowed: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfMobileAllowed)
            _tmpMobileAllowed = _tmp_1 != 0
            val _tmpVpnAllowed: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfVpnAllowed)
            _tmpVpnAllowed = _tmp_2 != 0
            val _tmpIsSystem: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSystem)
            _tmpIsSystem = _tmp_3 != 0
            val _tmpEnabled: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_4 != 0
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt)
            _item =
                FirewallRule(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpWifiAllowed,_tmpMobileAllowed,_tmpVpnAllowed,_tmpIsSystem,_tmpEnabled,_tmpUpdatedAt)
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

  public override suspend fun getByUid(uid: Int): FirewallRule? {
    val _sql: String = "SELECT * FROM firewall_rules WHERE uid = ? LIMIT 1"
    val _statement: RoomSQLiteQuery = acquire(_sql, 1)
    var _argIndex: Int = 1
    _statement.bindLong(_argIndex, uid.toLong())
    val _cancellationSignal: CancellationSignal? = createCancellationSignal()
    return execute(__db, false, _cancellationSignal, object : Callable<FirewallRule?> {
      public override fun call(): FirewallRule? {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfWifiAllowed: Int = getColumnIndexOrThrow(_cursor, "wifi_allowed")
          val _cursorIndexOfMobileAllowed: Int = getColumnIndexOrThrow(_cursor, "mobile_allowed")
          val _cursorIndexOfVpnAllowed: Int = getColumnIndexOrThrow(_cursor, "vpn_allowed")
          val _cursorIndexOfIsSystem: Int = getColumnIndexOrThrow(_cursor, "is_system")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_cursor, "updated_at")
          val _result: FirewallRule?
          if (_cursor.moveToFirst()) {
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpWifiAllowed: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfWifiAllowed)
            _tmpWifiAllowed = _tmp != 0
            val _tmpMobileAllowed: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfMobileAllowed)
            _tmpMobileAllowed = _tmp_1 != 0
            val _tmpVpnAllowed: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfVpnAllowed)
            _tmpVpnAllowed = _tmp_2 != 0
            val _tmpIsSystem: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSystem)
            _tmpIsSystem = _tmp_3 != 0
            val _tmpEnabled: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_4 != 0
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt)
            _result =
                FirewallRule(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpWifiAllowed,_tmpMobileAllowed,_tmpVpnAllowed,_tmpIsSystem,_tmpEnabled,_tmpUpdatedAt)
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

  public override fun getBlockedRules(): Flow<List<FirewallRule>> {
    val _sql: String =
        "SELECT * FROM firewall_rules WHERE wifi_allowed = 0 OR mobile_allowed = 0 OR vpn_allowed = 0"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("firewall_rules"), object :
        Callable<List<FirewallRule>> {
      public override fun call(): List<FirewallRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfWifiAllowed: Int = getColumnIndexOrThrow(_cursor, "wifi_allowed")
          val _cursorIndexOfMobileAllowed: Int = getColumnIndexOrThrow(_cursor, "mobile_allowed")
          val _cursorIndexOfVpnAllowed: Int = getColumnIndexOrThrow(_cursor, "vpn_allowed")
          val _cursorIndexOfIsSystem: Int = getColumnIndexOrThrow(_cursor, "is_system")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_cursor, "updated_at")
          val _result: MutableList<FirewallRule> = ArrayList<FirewallRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: FirewallRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpWifiAllowed: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfWifiAllowed)
            _tmpWifiAllowed = _tmp != 0
            val _tmpMobileAllowed: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfMobileAllowed)
            _tmpMobileAllowed = _tmp_1 != 0
            val _tmpVpnAllowed: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfVpnAllowed)
            _tmpVpnAllowed = _tmp_2 != 0
            val _tmpIsSystem: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSystem)
            _tmpIsSystem = _tmp_3 != 0
            val _tmpEnabled: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_4 != 0
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt)
            _item =
                FirewallRule(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpWifiAllowed,_tmpMobileAllowed,_tmpVpnAllowed,_tmpIsSystem,_tmpEnabled,_tmpUpdatedAt)
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

  public override fun getBlockedCount(): Flow<Int> {
    val _sql: String =
        "SELECT COUNT(*) FROM firewall_rules WHERE wifi_allowed = 0 OR mobile_allowed = 0 OR vpn_allowed = 0"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("firewall_rules"), object : Callable<Int>
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

  public override fun search(query: String): Flow<List<FirewallRule>> {
    val _sql: String =
        "SELECT * FROM firewall_rules WHERE app_label LIKE '%' || ? || '%' OR package_name LIKE '%' || ? || '%'"
    val _statement: RoomSQLiteQuery = acquire(_sql, 2)
    var _argIndex: Int = 1
    _statement.bindString(_argIndex, query)
    _argIndex = 2
    _statement.bindString(_argIndex, query)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("firewall_rules"), object :
        Callable<List<FirewallRule>> {
      public override fun call(): List<FirewallRule> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfUid: Int = getColumnIndexOrThrow(_cursor, "uid")
          val _cursorIndexOfPackageName: Int = getColumnIndexOrThrow(_cursor, "package_name")
          val _cursorIndexOfAppLabel: Int = getColumnIndexOrThrow(_cursor, "app_label")
          val _cursorIndexOfWifiAllowed: Int = getColumnIndexOrThrow(_cursor, "wifi_allowed")
          val _cursorIndexOfMobileAllowed: Int = getColumnIndexOrThrow(_cursor, "mobile_allowed")
          val _cursorIndexOfVpnAllowed: Int = getColumnIndexOrThrow(_cursor, "vpn_allowed")
          val _cursorIndexOfIsSystem: Int = getColumnIndexOrThrow(_cursor, "is_system")
          val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_cursor, "enabled")
          val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_cursor, "updated_at")
          val _result: MutableList<FirewallRule> = ArrayList<FirewallRule>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: FirewallRule
            val _tmpId: Long
            _tmpId = _cursor.getLong(_cursorIndexOfId)
            val _tmpUid: Int
            _tmpUid = _cursor.getInt(_cursorIndexOfUid)
            val _tmpPackageName: String
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName)
            val _tmpAppLabel: String
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel)
            val _tmpWifiAllowed: Boolean
            val _tmp: Int
            _tmp = _cursor.getInt(_cursorIndexOfWifiAllowed)
            _tmpWifiAllowed = _tmp != 0
            val _tmpMobileAllowed: Boolean
            val _tmp_1: Int
            _tmp_1 = _cursor.getInt(_cursorIndexOfMobileAllowed)
            _tmpMobileAllowed = _tmp_1 != 0
            val _tmpVpnAllowed: Boolean
            val _tmp_2: Int
            _tmp_2 = _cursor.getInt(_cursorIndexOfVpnAllowed)
            _tmpVpnAllowed = _tmp_2 != 0
            val _tmpIsSystem: Boolean
            val _tmp_3: Int
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSystem)
            _tmpIsSystem = _tmp_3 != 0
            val _tmpEnabled: Boolean
            val _tmp_4: Int
            _tmp_4 = _cursor.getInt(_cursorIndexOfEnabled)
            _tmpEnabled = _tmp_4 != 0
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt)
            _item =
                FirewallRule(_tmpId,_tmpUid,_tmpPackageName,_tmpAppLabel,_tmpWifiAllowed,_tmpMobileAllowed,_tmpVpnAllowed,_tmpIsSystem,_tmpEnabled,_tmpUpdatedAt)
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
