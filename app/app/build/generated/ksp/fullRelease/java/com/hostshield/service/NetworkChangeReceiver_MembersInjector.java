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
public final class NetworkChangeReceiver_MembersInjector implements MembersInjector<NetworkChangeReceiver> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  public NetworkChangeReceiver_MembersInjector(Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider) {
    this.prefsProvider = prefsProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
  }

  public static MembersInjector<NetworkChangeReceiver> create(
      Provider<AppPreferences> prefsProvider, Provider<IptablesManager> iptablesManagerProvider) {
    return new NetworkChangeReceiver_MembersInjector(prefsProvider, iptablesManagerProvider);
  }

  @Override
  public void injectMembers(NetworkChangeReceiver instance) {
    injectPrefs(instance, prefsProvider.get());
    injectIptablesManager(instance, iptablesManagerProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.NetworkChangeReceiver.prefs")
  public static void injectPrefs(NetworkChangeReceiver instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }

  @InjectedFieldSignature("com.hostshield.service.NetworkChangeReceiver.iptablesManager")
  public static void injectIptablesManager(NetworkChangeReceiver instance,
      IptablesManager iptablesManager) {
    instance.iptablesManager = iptablesManager;
  }
}
