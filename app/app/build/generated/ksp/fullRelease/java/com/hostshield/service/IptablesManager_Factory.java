package com.hostshield.service;

import android.content.Context;
import com.hostshield.data.database.FirewallRuleDao;
import com.hostshield.util.IptablesBinaryManager;
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
public final class IptablesManager_Factory implements Factory<IptablesManager> {
  private final Provider<Context> contextProvider;

  private final Provider<FirewallRuleDao> firewallRuleDaoProvider;

  private final Provider<IptablesBinaryManager> iptablesBinProvider;

  public IptablesManager_Factory(Provider<Context> contextProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider,
      Provider<IptablesBinaryManager> iptablesBinProvider) {
    this.contextProvider = contextProvider;
    this.firewallRuleDaoProvider = firewallRuleDaoProvider;
    this.iptablesBinProvider = iptablesBinProvider;
  }

  @Override
  public IptablesManager get() {
    return newInstance(contextProvider.get(), firewallRuleDaoProvider.get(), iptablesBinProvider.get());
  }

  public static IptablesManager_Factory create(Provider<Context> contextProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider,
      Provider<IptablesBinaryManager> iptablesBinProvider) {
    return new IptablesManager_Factory(contextProvider, firewallRuleDaoProvider, iptablesBinProvider);
  }

  public static IptablesManager newInstance(Context context, FirewallRuleDao firewallRuleDao,
      IptablesBinaryManager iptablesBin) {
    return new IptablesManager(context, firewallRuleDao, iptablesBin);
  }
}
