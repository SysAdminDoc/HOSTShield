package com.hostshield.service;

import com.hostshield.data.preferences.AppPreferences;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AutomationReceiver_MembersInjector implements MembersInjector<AutomationReceiver> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  public AutomationReceiver_MembersInjector(Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider) {
    this.prefsProvider = prefsProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
  }

  public static MembersInjector<AutomationReceiver> create(Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider) {
    return new AutomationReceiver_MembersInjector(prefsProvider, iptablesManagerProvider);
  }

  @Override
  public void injectMembers(AutomationReceiver instance) {
    injectPrefs(instance, prefsProvider.get());
    injectIptablesManager(instance, iptablesManagerProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.AutomationReceiver.prefs")
  public static void injectPrefs(AutomationReceiver instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }

  @InjectedFieldSignature("com.hostshield.service.AutomationReceiver.iptablesManager")
  public static void injectIptablesManager(AutomationReceiver instance,
      IptablesManager iptablesManager) {
    instance.iptablesManager = iptablesManager;
  }
}
