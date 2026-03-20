package com.hostshield.di;

import com.hostshield.data.database.HostShieldDatabase;
import com.hostshield.data.database.UserRuleDao;
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
public final class DatabaseModule_ProvideUserRuleDaoFactory implements Factory<UserRuleDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideUserRuleDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public UserRuleDao get() {
    return provideUserRuleDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideUserRuleDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideUserRuleDaoFactory(dbProvider);
  }

  public static UserRuleDao provideUserRuleDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideUserRuleDao(db));
  }
}
