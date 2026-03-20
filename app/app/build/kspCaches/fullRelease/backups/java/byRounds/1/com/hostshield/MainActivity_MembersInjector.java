package com.hostshield;

import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.util.RootUtil;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<RootUtil> rootUtilProvider;

  public MainActivity_MembersInjector(Provider<AppPreferences> prefsProvider,
      Provider<RootUtil> rootUtilProvider) {
    this.prefsProvider = prefsProvider;
    this.rootUtilProvider = rootUtilProvider;
  }

  public static MembersInjector<MainActivity> create(Provider<AppPreferences> prefsProvider,
      Provider<RootUtil> rootUtilProvider) {
    return new MainActivity_MembersInjector(prefsProvider, rootUtilProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectPrefs(instance, prefsProvider.get());
    injectRootUtil(instance, rootUtilProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.MainActivity.prefs")
  public static void injectPrefs(MainActivity instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }

  @InjectedFieldSignature("com.hostshield.MainActivity.rootUtil")
  public static void injectRootUtil(MainActivity instance, RootUtil rootUtil) {
    instance.rootUtil = rootUtil;
  }
}
