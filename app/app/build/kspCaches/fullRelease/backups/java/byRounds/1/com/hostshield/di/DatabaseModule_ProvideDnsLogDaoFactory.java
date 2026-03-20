package com.hostshield.di;

import com.hostshield.data.database.DnsLogDao;
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
public final class DatabaseModule_ProvideDnsLogDaoFactory implements Factory<DnsLogDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideDnsLogDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DnsLogDao get() {
    return provideDnsLogDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideDnsLogDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideDnsLogDaoFactory(dbProvider);
  }

  public static DnsLogDao provideDnsLogDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDnsLogDao(db));
  }
}
