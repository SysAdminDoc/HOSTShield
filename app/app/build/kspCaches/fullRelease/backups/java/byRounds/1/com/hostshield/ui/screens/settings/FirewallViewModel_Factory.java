package com.hostshield.ui.screens.settings;

import com.hostshield.data.database.FirewallRuleDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.service.IptablesManager;
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
public final class FirewallViewModel_Factory implements Factory<FirewallViewModel> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<FirewallRuleDao> firewallRuleDaoProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  private final Provider<NflogReader> nflogReaderProvider;

  public FirewallViewModel_Factory(Provider<AppPreferences> prefsProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<NflogReader> nflogReaderProvider) {
    this.prefsProvider = prefsProvider;
    this.firewallRuleDaoProvider = firewallRuleDaoProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
    this.nflogReaderProvider = nflogReaderProvider;
  }

  @Override
  public FirewallViewModel get() {
    return newInstance(prefsProvider.get(), firewallRuleDaoProvider.get(), iptablesManagerProvider.get(), nflogReaderProvider.get());
  }

  public static FirewallViewModel_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<NflogReader> nflogReaderProvider) {
    return new FirewallViewModel_Factory(prefsProvider, firewallRuleDaoProvider, iptablesManagerProvider, nflogReaderProvider);
  }

  public static FirewallViewModel newInstance(AppPreferences prefs, FirewallRuleDao firewallRuleDao,
      IptablesManager iptablesManager, NflogReader nflogReader) {
    return new FirewallViewModel(prefs, firewallRuleDao, iptablesManager, nflogReader);
  }
}
