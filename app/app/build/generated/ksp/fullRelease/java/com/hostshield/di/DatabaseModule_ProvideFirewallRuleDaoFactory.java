package com.hostshield.di;

import com.hostshield.data.database.FirewallRuleDao;
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
public final class DatabaseModule_ProvideFirewallRuleDaoFactory implements Factory<FirewallRuleDao> {
  private final Provider<HostShieldDatabase> dbProvider;

  public DatabaseModule_ProvideFirewallRuleDaoFactory(Provider<HostShieldDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public FirewallRuleDao get() {
    return provideFirewallRuleDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideFirewallRuleDaoFactory create(
      Provider<HostShieldDatabase> dbProvider) {
    return new DatabaseModule_ProvideFirewallRuleDaoFactory(dbProvider);
  }

  public static FirewallRuleDao provideFirewallRuleDao(HostShieldDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideFirewallRuleDao(db));
  }
}
