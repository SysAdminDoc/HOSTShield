package com.hostshield.di;

import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.data.database.HostShieldDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideConnectionLogDaoFactory implements Factory<ConnectionLogDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideConnectionLogDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ConnectionLogDao get() {
    return provideConnectionLogDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideConnectionLogDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideConnectionLogDaoFactory(dbProvider);
  }

  public static ConnectionLogDao provideConnectionLogDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideConnectionLogDao(db));
  }
}
