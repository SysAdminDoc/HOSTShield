package com.hostshield.`data`.database

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.room.RoomOpenHelper
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.lang.Class
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import javax.`annotation`.processing.Generated
import kotlin.Any
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.Set

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
public class HostShieldDatabase_Impl : HostShieldDatabase() {
  private val _hostSourceDao: Lazy<HostSourceDao> = lazy {
    HostSourceDao_Impl(this)
  }


  private val _userRuleDao: Lazy<UserRuleDao> = lazy {
    UserRuleDao_Impl(this)
  }


  private val _dnsLogDao: Lazy<DnsLogDao> = lazy {
    DnsLogDao_Impl(this)
  }


  private val _blockStatsDao: Lazy<BlockStatsDao> = lazy {
    BlockStatsDao_Impl(this)
  }


  private val _profileDao: Lazy<ProfileDao> = lazy {
    ProfileDao_Impl(this)
  }


  private val _firewallRuleDao: Lazy<FirewallRuleDao> = lazy {
    FirewallRuleDao_Impl(this)
  }


  private val _connectionLogDao: Lazy<ConnectionLogDao> = lazy {
    ConnectionLogDao_Impl(this)
  }


  protected override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
    val _openCallback: SupportSQLiteOpenHelper.Callback = RoomOpenHelper(config, object :
        RoomOpenHelper.Delegate(7) {
      public override fun createAllTables(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `host_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `label` TEXT NOT NULL, `description` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `category` TEXT NOT NULL, `entry_count` INTEGER NOT NULL, `last_updated` INTEGER NOT NULL, `last_modified_online` TEXT NOT NULL, `etag` TEXT NOT NULL, `is_builtin` INTEGER NOT NULL, `size_bytes` INTEGER NOT NULL, `health` TEXT NOT NULL, `last_error` TEXT NOT NULL, `consecutive_failures` INTEGER NOT NULL, `prev_entry_count` INTEGER NOT NULL, `domains_added` INTEGER NOT NULL, `domains_removed` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hostname` TEXT NOT NULL, `type` TEXT NOT NULL, `redirect_ip` TEXT NOT NULL, `comment` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `is_wildcard` INTEGER NOT NULL, `is_regex` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_rules_hostname` ON `user_rules` (`hostname`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `dns_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hostname` TEXT NOT NULL, `blocked` INTEGER NOT NULL, `app_package` TEXT NOT NULL, `app_label` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `source_ip` TEXT NOT NULL, `query_type` TEXT NOT NULL, `response_time_ms` INTEGER NOT NULL, `upstream_server` TEXT NOT NULL, `cname_chain` TEXT NOT NULL, `resolved_ips` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_timestamp` ON `dns_logs` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_blocked_timestamp` ON `dns_logs` (`blocked`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_hostname` ON `dns_logs` (`hostname`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_app_package` ON `dns_logs` (`app_package`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `block_stats` (`date` TEXT NOT NULL, `blocked_count` INTEGER NOT NULL, `allowed_count` INTEGER NOT NULL, `total_queries` INTEGER NOT NULL, PRIMARY KEY(`date`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `source_ids` TEXT NOT NULL, `schedule_start` TEXT NOT NULL, `schedule_end` TEXT NOT NULL, `days_of_week` TEXT NOT NULL, `wifi_ssids` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `firewall_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` INTEGER NOT NULL, `package_name` TEXT NOT NULL, `app_label` TEXT NOT NULL, `wifi_allowed` INTEGER NOT NULL, `mobile_allowed` INTEGER NOT NULL, `vpn_allowed` INTEGER NOT NULL, `is_system` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_firewall_rules_uid` ON `firewall_rules` (`uid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `connection_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` INTEGER NOT NULL, `package_name` TEXT NOT NULL, `app_label` TEXT NOT NULL, `destination` TEXT NOT NULL, `port` INTEGER NOT NULL, `protocol` TEXT NOT NULL, `action` TEXT NOT NULL, `interface_name` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_log_timestamp` ON `connection_log` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_log_uid` ON `connection_log` (`uid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd204beb702f8537be63b238a795ae790')")
      }

      public override fun dropAllTables(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `host_sources`")
        db.execSQL("DROP TABLE IF EXISTS `user_rules`")
        db.execSQL("DROP TABLE IF EXISTS `dns_logs`")
        db.execSQL("DROP TABLE IF EXISTS `block_stats`")
        db.execSQL("DROP TABLE IF EXISTS `profiles`")
        db.execSQL("DROP TABLE IF EXISTS `firewall_rules`")
        db.execSQL("DROP TABLE IF EXISTS `connection_log`")
        val _callbacks: List<RoomDatabase.Callback>? = mCallbacks
        if (_callbacks != null) {
          for (_callback: RoomDatabase.Callback in _callbacks) {
            _callback.onDestructiveMigration(db)
          }
        }
      }

      public override fun onCreate(db: SupportSQLiteDatabase) {
        val _callbacks: List<RoomDatabase.Callback>? = mCallbacks
        if (_callbacks != null) {
          for (_callback: RoomDatabase.Callback in _callbacks) {
            _callback.onCreate(db)
          }
        }
      }

      public override fun onOpen(db: SupportSQLiteDatabase) {
        mDatabase = db
        internalInitInvalidationTracker(db)
        val _callbacks: List<RoomDatabase.Callback>? = mCallbacks
        if (_callbacks != null) {
          for (_callback: RoomDatabase.Callback in _callbacks) {
            _callback.onOpen(db)
          }
        }
      }

      public override fun onPreMigrate(db: SupportSQLiteDatabase) {
        dropFtsSyncTriggers(db)
      }

      public override fun onPostMigrate(db: SupportSQLiteDatabase) {
      }

      public override fun onValidateSchema(db: SupportSQLiteDatabase):
          RoomOpenHelper.ValidationResult {
        val _columnsHostSources: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(18)
        _columnsHostSources.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("url", TableInfo.Column("url", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("label", TableInfo.Column("label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("description", TableInfo.Column("description", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("entry_count", TableInfo.Column("entry_count", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("last_updated", TableInfo.Column("last_updated", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("last_modified_online", TableInfo.Column("last_modified_online",
            "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("etag", TableInfo.Column("etag", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("is_builtin", TableInfo.Column("is_builtin", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("size_bytes", TableInfo.Column("size_bytes", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("health", TableInfo.Column("health", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("last_error", TableInfo.Column("last_error", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("consecutive_failures", TableInfo.Column("consecutive_failures",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("prev_entry_count", TableInfo.Column("prev_entry_count", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("domains_added", TableInfo.Column("domains_added", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHostSources.put("domains_removed", TableInfo.Column("domains_removed", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHostSources: HashSet<TableInfo.ForeignKey> =
            HashSet<TableInfo.ForeignKey>(0)
        val _indicesHostSources: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(0)
        val _infoHostSources: TableInfo = TableInfo("host_sources", _columnsHostSources,
            _foreignKeysHostSources, _indicesHostSources)
        val _existingHostSources: TableInfo = read(db, "host_sources")
        if (!_infoHostSources.equals(_existingHostSources)) {
          return RoomOpenHelper.ValidationResult(false, """
              |host_sources(com.hostshield.data.model.HostSource).
              | Expected:
              |""".trimMargin() + _infoHostSources + """
              |
              | Found:
              |""".trimMargin() + _existingHostSources)
        }
        val _columnsUserRules: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(9)
        _columnsUserRules.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("hostname", TableInfo.Column("hostname", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("type", TableInfo.Column("type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("redirect_ip", TableInfo.Column("redirect_ip", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("comment", TableInfo.Column("comment", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("is_wildcard", TableInfo.Column("is_wildcard", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("is_regex", TableInfo.Column("is_regex", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserRules.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysUserRules: HashSet<TableInfo.ForeignKey> = HashSet<TableInfo.ForeignKey>(0)
        val _indicesUserRules: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(1)
        _indicesUserRules.add(TableInfo.Index("index_user_rules_hostname", true, listOf("hostname"),
            listOf("ASC")))
        val _infoUserRules: TableInfo = TableInfo("user_rules", _columnsUserRules,
            _foreignKeysUserRules, _indicesUserRules)
        val _existingUserRules: TableInfo = read(db, "user_rules")
        if (!_infoUserRules.equals(_existingUserRules)) {
          return RoomOpenHelper.ValidationResult(false, """
              |user_rules(com.hostshield.data.model.UserRule).
              | Expected:
              |""".trimMargin() + _infoUserRules + """
              |
              | Found:
              |""".trimMargin() + _existingUserRules)
        }
        val _columnsDnsLogs: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(12)
        _columnsDnsLogs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("hostname", TableInfo.Column("hostname", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("blocked", TableInfo.Column("blocked", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("app_package", TableInfo.Column("app_package", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("app_label", TableInfo.Column("app_label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("source_ip", TableInfo.Column("source_ip", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("query_type", TableInfo.Column("query_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("response_time_ms", TableInfo.Column("response_time_ms", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("upstream_server", TableInfo.Column("upstream_server", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("cname_chain", TableInfo.Column("cname_chain", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDnsLogs.put("resolved_ips", TableInfo.Column("resolved_ips", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDnsLogs: HashSet<TableInfo.ForeignKey> = HashSet<TableInfo.ForeignKey>(0)
        val _indicesDnsLogs: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(4)
        _indicesDnsLogs.add(TableInfo.Index("index_dns_logs_timestamp", false, listOf("timestamp"),
            listOf("ASC")))
        _indicesDnsLogs.add(TableInfo.Index("index_dns_logs_blocked_timestamp", false,
            listOf("blocked", "timestamp"), listOf("ASC", "ASC")))
        _indicesDnsLogs.add(TableInfo.Index("index_dns_logs_hostname", false, listOf("hostname"),
            listOf("ASC")))
        _indicesDnsLogs.add(TableInfo.Index("index_dns_logs_app_package", false,
            listOf("app_package"), listOf("ASC")))
        val _infoDnsLogs: TableInfo = TableInfo("dns_logs", _columnsDnsLogs, _foreignKeysDnsLogs,
            _indicesDnsLogs)
        val _existingDnsLogs: TableInfo = read(db, "dns_logs")
        if (!_infoDnsLogs.equals(_existingDnsLogs)) {
          return RoomOpenHelper.ValidationResult(false, """
              |dns_logs(com.hostshield.data.model.DnsLogEntry).
              | Expected:
              |""".trimMargin() + _infoDnsLogs + """
              |
              | Found:
              |""".trimMargin() + _existingDnsLogs)
        }
        val _columnsBlockStats: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(4)
        _columnsBlockStats.put("date", TableInfo.Column("date", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockStats.put("blocked_count", TableInfo.Column("blocked_count", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockStats.put("allowed_count", TableInfo.Column("allowed_count", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockStats.put("total_queries", TableInfo.Column("total_queries", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBlockStats: HashSet<TableInfo.ForeignKey> = HashSet<TableInfo.ForeignKey>(0)
        val _indicesBlockStats: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(0)
        val _infoBlockStats: TableInfo = TableInfo("block_stats", _columnsBlockStats,
            _foreignKeysBlockStats, _indicesBlockStats)
        val _existingBlockStats: TableInfo = read(db, "block_stats")
        if (!_infoBlockStats.equals(_existingBlockStats)) {
          return RoomOpenHelper.ValidationResult(false, """
              |block_stats(com.hostshield.data.model.BlockStats).
              | Expected:
              |""".trimMargin() + _infoBlockStats + """
              |
              | Found:
              |""".trimMargin() + _existingBlockStats)
        }
        val _columnsProfiles: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(8)
        _columnsProfiles.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("is_active", TableInfo.Column("is_active", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("source_ids", TableInfo.Column("source_ids", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("schedule_start", TableInfo.Column("schedule_start", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("schedule_end", TableInfo.Column("schedule_end", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("days_of_week", TableInfo.Column("days_of_week", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("wifi_ssids", TableInfo.Column("wifi_ssids", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProfiles: HashSet<TableInfo.ForeignKey> = HashSet<TableInfo.ForeignKey>(0)
        val _indicesProfiles: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(0)
        val _infoProfiles: TableInfo = TableInfo("profiles", _columnsProfiles, _foreignKeysProfiles,
            _indicesProfiles)
        val _existingProfiles: TableInfo = read(db, "profiles")
        if (!_infoProfiles.equals(_existingProfiles)) {
          return RoomOpenHelper.ValidationResult(false, """
              |profiles(com.hostshield.data.model.BlockingProfile).
              | Expected:
              |""".trimMargin() + _infoProfiles + """
              |
              | Found:
              |""".trimMargin() + _existingProfiles)
        }
        val _columnsFirewallRules: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(10)
        _columnsFirewallRules.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("uid", TableInfo.Column("uid", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("package_name", TableInfo.Column("package_name", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("app_label", TableInfo.Column("app_label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("wifi_allowed", TableInfo.Column("wifi_allowed", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("mobile_allowed", TableInfo.Column("mobile_allowed", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("vpn_allowed", TableInfo.Column("vpn_allowed", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("is_system", TableInfo.Column("is_system", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFirewallRules.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysFirewallRules: HashSet<TableInfo.ForeignKey> =
            HashSet<TableInfo.ForeignKey>(0)
        val _indicesFirewallRules: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(1)
        _indicesFirewallRules.add(TableInfo.Index("index_firewall_rules_uid", true, listOf("uid"),
            listOf("ASC")))
        val _infoFirewallRules: TableInfo = TableInfo("firewall_rules", _columnsFirewallRules,
            _foreignKeysFirewallRules, _indicesFirewallRules)
        val _existingFirewallRules: TableInfo = read(db, "firewall_rules")
        if (!_infoFirewallRules.equals(_existingFirewallRules)) {
          return RoomOpenHelper.ValidationResult(false, """
              |firewall_rules(com.hostshield.data.model.FirewallRule).
              | Expected:
              |""".trimMargin() + _infoFirewallRules + """
              |
              | Found:
              |""".trimMargin() + _existingFirewallRules)
        }
        val _columnsConnectionLog: HashMap<String, TableInfo.Column> =
            HashMap<String, TableInfo.Column>(10)
        _columnsConnectionLog.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("uid", TableInfo.Column("uid", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("package_name", TableInfo.Column("package_name", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("app_label", TableInfo.Column("app_label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("destination", TableInfo.Column("destination", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("port", TableInfo.Column("port", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("protocol", TableInfo.Column("protocol", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("action", TableInfo.Column("action", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("interface_name", TableInfo.Column("interface_name", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConnectionLog.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysConnectionLog: HashSet<TableInfo.ForeignKey> =
            HashSet<TableInfo.ForeignKey>(0)
        val _indicesConnectionLog: HashSet<TableInfo.Index> = HashSet<TableInfo.Index>(2)
        _indicesConnectionLog.add(TableInfo.Index("index_connection_log_timestamp", false,
            listOf("timestamp"), listOf("ASC")))
        _indicesConnectionLog.add(TableInfo.Index("index_connection_log_uid", false, listOf("uid"),
            listOf("ASC")))
        val _infoConnectionLog: TableInfo = TableInfo("connection_log", _columnsConnectionLog,
            _foreignKeysConnectionLog, _indicesConnectionLog)
        val _existingConnectionLog: TableInfo = read(db, "connection_log")
        if (!_infoConnectionLog.equals(_existingConnectionLog)) {
          return RoomOpenHelper.ValidationResult(false, """
              |connection_log(com.hostshield.data.model.ConnectionLogEntry).
              | Expected:
              |""".trimMargin() + _infoConnectionLog + """
              |
              | Found:
              |""".trimMargin() + _existingConnectionLog)
        }
        return RoomOpenHelper.ValidationResult(true, null)
      }
    }, "d204beb702f8537be63b238a795ae790", "e1d8b068e7cf634c80a4fecc539b1bc0")
    val _sqliteConfig: SupportSQLiteOpenHelper.Configuration =
        SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build()
    val _helper: SupportSQLiteOpenHelper = config.sqliteOpenHelperFactory.create(_sqliteConfig)
    return _helper
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: HashMap<String, String> = HashMap<String, String>(0)
    val _viewTables: HashMap<String, Set<String>> = HashMap<String, Set<String>>(0)
    return InvalidationTracker(this, _shadowTablesMap, _viewTables,
        "host_sources","user_rules","dns_logs","block_stats","profiles","firewall_rules","connection_log")
  }

  public override fun clearAllTables() {
    super.assertNotMainThread()
    val _db: SupportSQLiteDatabase = super.openHelper.writableDatabase
    try {
      super.beginTransaction()
      _db.execSQL("DELETE FROM `host_sources`")
      _db.execSQL("DELETE FROM `user_rules`")
      _db.execSQL("DELETE FROM `dns_logs`")
      _db.execSQL("DELETE FROM `block_stats`")
      _db.execSQL("DELETE FROM `profiles`")
      _db.execSQL("DELETE FROM `firewall_rules`")
      _db.execSQL("DELETE FROM `connection_log`")
      super.setTransactionSuccessful()
    } finally {
      super.endTransaction()
      _db.query("PRAGMA wal_checkpoint(FULL)").close()
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM")
      }
    }
  }

  protected override fun getRequiredTypeConverters(): Map<Class<out Any>, List<Class<out Any>>> {
    val _typeConvertersMap: HashMap<Class<out Any>, List<Class<out Any>>> =
        HashMap<Class<out Any>, List<Class<out Any>>>()
    _typeConvertersMap.put(HostSourceDao::class.java, HostSourceDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(UserRuleDao::class.java, UserRuleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DnsLogDao::class.java, DnsLogDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BlockStatsDao::class.java, BlockStatsDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ProfileDao::class.java, ProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(FirewallRuleDao::class.java,
        FirewallRuleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ConnectionLogDao::class.java,
        ConnectionLogDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecs(): Set<Class<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: HashSet<Class<out AutoMigrationSpec>> =
        HashSet<Class<out AutoMigrationSpec>>()
    return _autoMigrationSpecsSet
  }

  public override
      fun getAutoMigrations(autoMigrationSpecs: Map<Class<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = ArrayList<Migration>()
    return _autoMigrations
  }

  public override fun hostSourceDao(): HostSourceDao = _hostSourceDao.value

  public override fun userRuleDao(): UserRuleDao = _userRuleDao.value

  public override fun dnsLogDao(): DnsLogDao = _dnsLogDao.value

  public override fun blockStatsDao(): BlockStatsDao = _blockStatsDao.value

  public override fun profileDao(): ProfileDao = _profileDao.value

  public override fun firewallRuleDao(): FirewallRuleDao = _firewallRuleDao.value

  public override fun connectionLogDao(): ConnectionLogDao = _connectionLogDao.value
}
