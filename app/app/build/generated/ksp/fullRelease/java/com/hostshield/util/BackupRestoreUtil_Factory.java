package com.hostshield.util;

import com.hostshield.data.database.FirewallRuleDao;
import com.hostshield.data.database.HostSourceDao;
import com.hostshield.data.database.ProfileDao;
import com.hostshield.data.database.UserRuleDao;
import com.hostshield.data.preferences.AppPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class BackupRestoreUtil_Factory implements Factory<BackupRestoreUtil> {
  private final Provider<HostSourceDao> hostSourceDaoProvider;

  private final Provider<UserRuleDao> userRuleDaoProvider;

  private final Provider<ProfileDao> profileDaoProvider;

  private final Provider<FirewallRuleDao> firewallRuleDaoProvider;

  private final Provider<AppPreferences> prefsProvider;

  public BackupRestoreUtil_Factory(Provider<HostSourceDao> hostSourceDaoProvider,
      Provider<UserRuleDao> userRuleDaoProvider, Provider<ProfileDao> profileDaoProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider, Provider<AppPreferences> prefsProvider) {
    this.hostSourceDaoProvider = hostSourceDaoProvider;
    this.userRuleDaoProvider = userRuleDaoProvider;
    this.profileDaoProvider = profileDaoProvider;
    this.firewallRuleDaoProvider = firewallRuleDaoProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public BackupRestoreUtil get() {
    return newInstance(hostSourceDaoProvider.get(), userRuleDaoProvider.get(), profileDaoProvider.get(), firewallRuleDaoProvider.get(), prefsProvider.get());
  }

  public static BackupRestoreUtil_Factory create(Provider<HostSourceDao> hostSourceDaoProvider,
      Provider<UserRuleDao> userRuleDaoProvider, Provider<ProfileDao> profileDaoProvider,
      Provider<FirewallRuleDao> firewallRuleDaoProvider, Provider<AppPreferences> prefsProvider) {
    return new BackupRestoreUtil_Factory(hostSourceDaoProvider, userRuleDaoProvider, profileDaoProvider, firewallRuleDaoProvider, prefsProvider);
  }

  public static BackupRestoreUtil newInstance(HostSourceDao hostSourceDao, UserRuleDao userRuleDao,
      ProfileDao profileDao, FirewallRuleDao firewallRuleDao, AppPreferences prefs) {
    return new BackupRestoreUtil(hostSourceDao, userRuleDao, profileDao, firewallRuleDao, prefs);
  }
}
