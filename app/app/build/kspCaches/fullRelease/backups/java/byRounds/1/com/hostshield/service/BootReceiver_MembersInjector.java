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
public final class BootReceiver_MembersInjector implements MembersInjector<BootReceiver> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  private final Provider<NflogReader> nflogReaderProvider;

  public BootReceiver_MembersInjector(Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<NflogReader> nflogReaderProvider) {
    this.prefsProvider = prefsProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
    this.nflogReaderProvider = nflogReaderProvider;
  }

  public static MembersInjector<BootReceiver> create(Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<NflogReader> nflogReaderProvider) {
    return new BootReceiver_MembersInjector(prefsProvider, iptablesManagerProvider, nflogReaderProvider);
  }

  @Override
  public void injectMembers(BootReceiver instance) {
    injectPrefs(instance, prefsProvider.get());
    injectIptablesManager(instance, iptablesManagerProvider.get());
    injectNflogReader(instance, nflogReaderProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.BootReceiver.prefs")
  public static void injectPrefs(BootReceiver instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }

  @InjectedFieldSignature("com.hostshield.service.BootReceiver.iptablesManager")
  public static void injectIptablesManager(BootReceiver instance, IptablesManager iptablesManager) {
    instance.iptablesManager = iptablesManager;
  }

  @InjectedFieldSignature("com.hostshield.service.BootReceiver.nflogReader")
  public static void injectNflogReader(BootReceiver instance, NflogReader nflogReader) {
    instance.nflogReader = nflogReader;
  }
}
