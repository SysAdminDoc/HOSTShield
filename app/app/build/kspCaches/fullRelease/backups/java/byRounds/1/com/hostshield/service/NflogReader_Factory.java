package com.hostshield.service;

import android.content.Context;
import com.hostshield.data.database.ConnectionLogDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class NflogReader_Factory implements Factory<NflogReader> {
  private final Provider<Context> contextProvider;

  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  public NflogReader_Factory(Provider<Context> contextProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider) {
    this.contextProvider = contextProvider;
    this.connectionLogDaoProvider = connectionLogDaoProvider;
  }

  @Override
  public NflogReader get() {
    return newInstance(contextProvider.get(), connectionLogDaoProvider.get());
  }

  public static NflogReader_Factory create(Provider<Context> contextProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider) {
    return new NflogReader_Factory(contextProvider, connectionLogDaoProvider);
  }

  public static NflogReader newInstance(Context context, ConnectionLogDao connectionLogDao) {
    return new NflogReader(context, connectionLogDao);
  }
}
