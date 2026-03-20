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
public final class HostShieldTileService_MembersInjector implements MembersInjector<HostShieldTileService> {
  private final Provider<AppPreferences> prefsProvider;

  public HostShieldTileService_MembersInjector(Provider<AppPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  public static MembersInjector<HostShieldTileService> create(
      Provider<AppPreferences> prefsProvider) {
    return new HostShieldTileService_MembersInjector(prefsProvider);
  }

  @Override
  public void injectMembers(HostShieldTileService instance) {
    injectPrefs(instance, prefsProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.HostShieldTileService.prefs")
  public static void injectPrefs(HostShieldTileService instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }
}
