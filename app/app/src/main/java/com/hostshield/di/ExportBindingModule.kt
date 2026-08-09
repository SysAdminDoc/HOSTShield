package com.hostshield.di

import com.hostshield.util.DiagnosticExporter
import com.hostshield.util.DiagnosticPackageGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportBindingModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticPackageGenerator(
        exporter: DiagnosticExporter
    ): DiagnosticPackageGenerator
}
