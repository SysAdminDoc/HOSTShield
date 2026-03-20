package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
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
public final class BlockingScheduleWorker_Factory {
  private final Provider<AppPreferences> prefsProvider;

  public BlockingScheduleWorker_Factory(Provider<AppPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  public BlockingScheduleWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, prefsProvider.get());
  }

  public static BlockingScheduleWorker_Factory create(Provider<AppPreferences> prefsProvider) {
    return new BlockingScheduleWorker_Factory(prefsProvider);
  }

  public static BlockingScheduleWorker newInstance(Context context, WorkerParameters params,
      AppPreferences prefs) {
    return new BlockingScheduleWorker(context, params, prefs);
  }
}
