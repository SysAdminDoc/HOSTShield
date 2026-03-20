package com.hostshield.ui.screens.settings;

import android.app.Application;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.util.BackupRestoreUtil;
import com.hostshield.util.BatteryOptimizationUtil;
import com.hostshield.util.DiagnosticExporter;
import com.hostshield.util.ImportExportUtil;
import com.hostshield.util.PcapExporter;
import com.hostshield.util.RootUtil;
import com.hostshield.util.UpdateChecker;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<RootUtil> rootUtilProvider;

  private final Provider<ImportExportUtil> importExportProvider;

  private final Provider<BackupRestoreUtil> backupRestoreProvider;

  private final Provider<BatteryOptimizationUtil> batteryUtilProvider;

  private final Provider<PcapExporter> pcapExporterProvider;

  private final Provider<UpdateChecker> updateCheckerProvider;

  private final Provider<DiagnosticExporter> diagnosticExporterProvider;

  public SettingsViewModel_Factory(Provider<Application> applicationProvider,
      Provider<AppPreferences> prefsProvider, Provider<HostShieldRepository> repositoryProvider,
      Provider<RootUtil> rootUtilProvider, Provider<ImportExportUtil> importExportProvider,
      Provider<BackupRestoreUtil> backupRestoreProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider,
      Provider<PcapExporter> pcapExporterProvider, Provider<UpdateChecker> updateCheckerProvider,
      Provider<DiagnosticExporter> diagnosticExporterProvider) {
    this.applicationProvider = applicationProvider;
    this.prefsProvider = prefsProvider;
    this.repositoryProvider = repositoryProvider;
    this.rootUtilProvider = rootUtilProvider;
    this.importExportProvider = importExportProvider;
    this.backupRestoreProvider = backupRestoreProvider;
    this.batteryUtilProvider = batteryUtilProvider;
    this.pcapExporterProvider = pcapExporterProvider;
    this.updateCheckerProvider = updateCheckerProvider;
    this.diagnosticExporterProvider = diagnosticExporterProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(applicationProvider.get(), prefsProvider.get(), repositoryProvider.get(), rootUtilProvider.get(), importExportProvider.get(), backupRestoreProvider.get(), batteryUtilProvider.get(), pcapExporterProvider.get(), updateCheckerProvider.get(), diagnosticExporterProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<AppPreferences> prefsProvider, Provider<HostShieldRepository> repositoryProvider,
      Provider<RootUtil> rootUtilProvider, Provider<ImportExportUtil> importExportProvider,
      Provider<BackupRestoreUtil> backupRestoreProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider,
      Provider<PcapExporter> pcapExporterProvider, Provider<UpdateChecker> updateCheckerProvider,
      Provider<DiagnosticExporter> diagnosticExporterProvider) {
    return new SettingsViewModel_Factory(applicationProvider, prefsProvider, repositoryProvider, rootUtilProvider, importExportProvider, backupRestoreProvider, batteryUtilProvider, pcapExporterProvider, updateCheckerProvider, diagnosticExporterProvider);
  }

  public static SettingsViewModel newInstance(Application application, AppPreferences prefs,
      HostShieldRepository repository, RootUtil rootUtil, ImportExportUtil importExport,
      BackupRestoreUtil backupRestore, BatteryOptimizationUtil batteryUtil,
      PcapExporter pcapExporter, UpdateChecker updateChecker,
      DiagnosticExporter diagnosticExporter) {
    return new SettingsViewModel(application, prefs, repository, rootUtil, importExport, backupRestore, batteryUtil, pcapExporter, updateChecker, diagnosticExporter);
  }
}
