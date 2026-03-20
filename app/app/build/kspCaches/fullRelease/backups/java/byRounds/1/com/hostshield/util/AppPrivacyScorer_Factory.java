package com.hostshield.util;

import com.hostshield.data.database.DnsLogDao;
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
public final class AppPrivacyScorer_Factory implements Factory<AppPrivacyScorer> {
  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<SuspiciousTldDetector> suspiciousTldDetectorProvider;

  public AppPrivacyScorer_Factory(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<SuspiciousTldDetector> suspiciousTldDetectorProvider) {
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.suspiciousTldDetectorProvider = suspiciousTldDetectorProvider;
  }

  @Override
  public AppPrivacyScorer get() {
    return newInstance(dnsLogDaoProvider.get(), suspiciousTldDetectorProvider.get());
  }

  public static AppPrivacyScorer_Factory create(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<SuspiciousTldDetector> suspiciousTldDetectorProvider) {
    return new AppPrivacyScorer_Factory(dnsLogDaoProvider, suspiciousTldDetectorProvider);
  }

  public static AppPrivacyScorer newInstance(DnsLogDao dnsLogDao,
      SuspiciousTldDetector suspiciousTldDetector) {
    return new AppPrivacyScorer(dnsLogDao, suspiciousTldDetector);
  }
}
