package com.hostshield.di;

import com.hostshield.data.database.HostShieldDatabase;
import com.hostshield.data.database.ProfileDao;
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
public final class DatabaseModule_ProvideProfileDaoFactory implements Factory<ProfileDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideProfileDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ProfileDao get() {
    return provideProfileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideProfileDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideProfileDaoFactory(dbProvider);
  }

  public static ProfileDao provideProfileDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideProfileDao(db));
  }
}
