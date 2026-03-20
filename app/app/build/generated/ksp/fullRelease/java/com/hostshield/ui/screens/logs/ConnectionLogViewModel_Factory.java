package com.hostshield.ui.screens.logs;

import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.service.NflogReader;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ConnectionLogViewModel_Factory implements Factory<ConnectionLogViewModel> {
  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  private final Provider<NflogReader> nflogReaderProvider;

  public ConnectionLogViewModel_Factory(Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<NflogReader> nflogReaderProvider) {
    this.connectionLogDaoProvider = connectionLogDaoProvider;
    this.nflogReaderProvider = nflogReaderProvider;
  }

  @Override
  public ConnectionLogViewModel get() {
    return newInstance(connectionLogDaoProvider.get(), nflogReaderProvider.get());
  }

  public static ConnectionLogViewModel_Factory create(
      Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<NflogReader> nflogReaderProvider) {
    return new ConnectionLogViewModel_Factory(connectionLogDaoProvider, nflogReaderProvider);
  }

  public static ConnectionLogViewModel newInstance(ConnectionLogDao connectionLogDao,
      NflogReader nflogReader) {
    return new ConnectionLogViewModel(connectionLogDao, nflogReader);
  }
}
