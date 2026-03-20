package com.hostshield.ui.screens.settings;

import com.hostshield.domain.BlocklistHolder;
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
public final class DnsLeakTestViewModel_Factory implements Factory<DnsLeakTestViewModel> {
  private final Provider<BlocklistHolder> blocklistProvider;

  public DnsLeakTestViewModel_Factory(Provider<BlocklistHolder> blocklistProvider) {
    this.blocklistProvider = blocklistProvider;
  }

  @Override
  public DnsLeakTestViewModel get() {
    return newInstance(blocklistProvider.get());
  }

  public static DnsLeakTestViewModel_Factory create(Provider<BlocklistHolder> blocklistProvider) {
    return new DnsLeakTestViewModel_Factory(blocklistProvider);
  }

  public static DnsLeakTestViewModel newInstance(BlocklistHolder blocklist) {
    return new DnsLeakTestViewModel(blocklist);
  }
}
