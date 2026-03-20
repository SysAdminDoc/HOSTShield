package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class LogCleanupWorker_Factory {
  private final Provider<DnsLogDao> logDaoProvider;

  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  private final Provider<AppPreferences> prefsProvider;

  public LogCleanupWorker_Factory(Provider<DnsLogDao> logDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider, Provider<AppPreferences> prefsProvider) {
    this.logDaoProvider = logDaoProvider;
    this.connectionLogDaoProvider = connectionLogDaoProvider;
    this.prefsProvider = prefsProvider;
  }

  public LogCleanupWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, logDaoProvider.get(), connectionLogDaoProvider.get(), prefsProvider.get());
  }

  public static LogCleanupWorker_Factory create(Provider<DnsLogDao> logDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider, Provider<AppPreferences> prefsProvider) {
    return new LogCleanupWorker_Factory(logDaoProvider, connectionLogDaoProvider, prefsProvider);
  }

  public static LogCleanupWorker newInstance(Context context, WorkerParameters params,
      DnsLogDao logDao, ConnectionLogDao connectionLogDao, AppPreferences prefs) {
    return new LogCleanupWorker(context, params, logDao, connectionLogDao, prefs);
  }
}
