package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.util.BackupRestoreUtil;
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
public final class AutoBackupWorker_Factory {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<BackupRestoreUtil> backupRestoreProvider;

  public AutoBackupWorker_Factory(Provider<AppPreferences> prefsProvider,
      Provider<BackupRestoreUtil> backupRestoreProvider) {
    this.prefsProvider = prefsProvider;
    this.backupRestoreProvider = backupRestoreProvider;
  }

  public AutoBackupWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, prefsProvider.get(), backupRestoreProvider.get());
  }

  public static AutoBackupWorker_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<BackupRestoreUtil> backupRestoreProvider) {
    return new AutoBackupWorker_Factory(prefsProvider, backupRestoreProvider);
  }

  public static AutoBackupWorker newInstance(Context context, WorkerParameters params,
      AppPreferences prefs, BackupRestoreUtil backupRestore) {
    return new AutoBackupWorker(context, params, prefs, backupRestore);
  }
}
