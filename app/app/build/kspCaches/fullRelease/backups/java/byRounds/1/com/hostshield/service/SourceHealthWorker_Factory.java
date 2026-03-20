package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.hostshield.data.database.HostSourceDao;
import com.hostshield.data.source.SourceDownloader;
import dagger.internal.DaggerGenerated;
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
public final class SourceHealthWorker_Factory {
  private final Provider<HostSourceDao> sourceDaoProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  public SourceHealthWorker_Factory(Provider<HostSourceDao> sourceDaoProvider,
      Provider<SourceDownloader> downloaderProvider) {
    this.sourceDaoProvider = sourceDaoProvider;
    this.downloaderProvider = downloaderProvider;
  }

  public SourceHealthWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, sourceDaoProvider.get(), downloaderProvider.get());
  }

  public static SourceHealthWorker_Factory create(Provider<HostSourceDao> sourceDaoProvider,
      Provider<SourceDownloader> downloaderProvider) {
    return new SourceHealthWorker_Factory(sourceDaoProvider, downloaderProvider);
  }

  public static SourceHealthWorker newInstance(Context context, WorkerParameters params,
      HostSourceDao sourceDao, SourceDownloader downloader) {
    return new SourceHealthWorker(context, params, sourceDao, downloader);
  }
}
