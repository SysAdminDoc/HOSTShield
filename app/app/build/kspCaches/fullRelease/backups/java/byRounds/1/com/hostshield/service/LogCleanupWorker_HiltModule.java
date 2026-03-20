package com.hostshield.service;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = LogCleanupWorker.class
)
public interface LogCleanupWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.hostshield.service.LogCleanupWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(LogCleanupWorker_AssistedFactory factory);
}
