package com.hostshield.di;

import com.hostshield.data.database.HostShieldDatabase;
import com.hostshield.data.database.HostSourceDao;
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
public final class DatabaseModule_ProvideHostSourceDaoFactory implements Factory<HostSourceDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideHostSourceDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public HostSourceDao get() {
    return provideHostSourceDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideHostSourceDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideHostSourceDaoFactory(dbProvider);
  }

  public static HostSourceDao provideHostSourceDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideHostSourceDao(db));
  }
}
