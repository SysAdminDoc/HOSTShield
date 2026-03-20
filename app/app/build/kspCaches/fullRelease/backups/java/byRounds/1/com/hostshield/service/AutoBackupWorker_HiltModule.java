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
    topLevelClass = AutoBackupWorker.class
)
public interface AutoBackupWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.hostshield.service.AutoBackupWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(AutoBackupWorker_AssistedFactory factory);
}
