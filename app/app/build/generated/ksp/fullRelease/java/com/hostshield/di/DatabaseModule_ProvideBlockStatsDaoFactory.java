package com.hostshield.di;

import com.hostshield.data.database.BlockStatsDao;
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
public final class DatabaseModule_ProvideBlockStatsDaoFactory implements Factory<BlockStatsDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideBlockStatsDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BlockStatsDao get() {
    return provideBlockStatsDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideBlockStatsDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideBlockStatsDaoFactory(dbProvider);
  }

  public static BlockStatsDao provideBlockStatsDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideBlockStatsDao(db));
  }
}
