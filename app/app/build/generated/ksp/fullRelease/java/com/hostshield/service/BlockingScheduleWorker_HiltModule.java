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
    topLevelClass = BlockingScheduleWorker.class
)
public interface BlockingScheduleWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.hostshield.service.BlockingScheduleWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      BlockingScheduleWorker_AssistedFactory factory);
}
