package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager

import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.privd.DeadzoneDialog
import com.stormpanda.megingiard.privd.PrivdSettingsCard
import com.stormpanda.megingiard.privd.PrivdSetupWizardDialog
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsState()
    val accentColor = Color(accentColorArgb)
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val showMirrorControlLabels by viewModel.showMirrorControlLabels.collectAsState()
    val showFullscreenExitHints by viewModel.showFullscreenExitHints.collectAsState()
    val mirrorAutoStart by viewModel.mirrorAutoStart.collectAsState()
    val autoSwitchProfiles by viewModel.autoSwitchProfiles.collectAsState()
    val gamepadSwapFaceButtons by viewModel.gamepadSwapFaceButtons.collectAsState()
    val internalBackups by viewModel.internalBackups.collectAsState()
    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    var showRestoreBackupDialog by rememberSaveable { mutableStateOf(false) }

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    val exportResult by ConfigManager.exportResult.collectAsState()
    val logReportSaveResult by LogReportManager.saveResult.collectAsState()

    val context = LocalContext.current
    var showExportMetadataDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivdWizard by rememberSaveable { mutableStateOf(false) }
    var showDeadzoneDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importSuccess by rememberSaveable { mutableStateOf(false) }
    var showProfileExportDialog by rememberSaveable { mutableStateOf(false) }
    var profileImportSuccess by rememberSaveable { mutableStateOf(false) }
    val pendingInAppImportMode by ConfigManager.pendingInAppImportMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
 
    var showRestoreDefaultsConfirm by rememberSaveable { mutableStateOf(false) }
    var restoreCountdown by rememberSaveable { mutableStateOf(GS_RESTORE_COUNTDOWN_SECONDS) }
    
    var isAccessibilityActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityActive = viewModel.checkAccessibilityActive(context)
            delay(1000L)
        }
    }
    LaunchedEffect(showRestoreDefaultsConfirm) {
        if (showRestoreDefaultsConfirm) {
            restoreCountdown = GS_RESTORE_COUNTDOWN_SECONDS
            while (restoreCountdown > 0) {
                delay(GS_RESTORE_COUNTDOWN_INTERVAL_MS)
                restoreCountdown--
            }
        }
    }

    var selectedSectionFilter by remember { mutableStateOf<SettingsSectionFilter?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_global_title),
                            color = colors.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionJumpRow(
                    colors = colors,
                    selectedSectionFilter = selectedSectionFilter,
                    onSelectAll = { selectedSectionFilter = null },
                    onSelectGeneral = { selectedSectionFilter = SettingsSectionFilter.GENERAL },
                    onSelectAppearance = { selectedSectionFilter = SettingsSectionFilter.APPEARANCE },
                    onSelectData = { selectedSectionFilter = SettingsSectionFilter.DATA },
                    onSelectConfig = { selectedSectionFilter = SettingsSectionFilter.CONFIGURATION },
                    onSelectPrivilegedMode = { selectedSectionFilter = SettingsSectionFilter.PRIVILEGED_MODE },
                    onSelectDiagnostics = { selectedSectionFilter = SettingsSectionFilter.DIAGNOSTICS },
                )
                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.GENERAL) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_general),
                        colors = colors,
                    ) {
                        OverlayPositionRow(
                            overlayAtBottom = overlayAtBottom,
                            onChanged = { viewModel.setOverlayAtBottom(it) }
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_show_mirror_control_labels),
                            description = stringResource(R.string.settings_show_mirror_control_labels_desc),
                            checked = showMirrorControlLabels,
                            onCheckedChange = { viewModel.setShowMirrorControlLabels(it) },
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_show_fullscreen_exit_hints),
                            description = stringResource(R.string.settings_show_fullscreen_exit_hints_desc),
                            checked = showFullscreenExitHints,
                            onCheckedChange = { viewModel.setShowFullscreenExitHints(it) },
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_mirror_auto_start),
                            description = stringResource(R.string.settings_mirror_auto_start_desc),
                            checked = mirrorAutoStart,
                            onCheckedChange = { viewModel.setMirrorAutoStart(it) },
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_auto_switch_profiles),
                            description = stringResource(R.string.settings_auto_switch_profiles_desc),
                            checked = autoSwitchProfiles,
                            onCheckedChange = { viewModel.setAutoSwitchProfiles(it) },
                        )
                        AppDivider()
                        ConfigActionRow(
                            label = stringResource(R.string.settings_accessibility_status),
                            description = stringResource(
                                if (isAccessibilityActive) R.string.settings_accessibility_status_active
                                else R.string.settings_accessibility_status_inactive
                            ),
                            accentColor = if (isAccessibilityActive) Color(0xFF4CAF50) else colors.error,
                            onClick = {
                                if (!isAccessibilityActive) {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        )
                        AppDivider()
                        LanguagePickerRow(
                            language = appLanguage,
                            accentColor = effectiveAccent,
                            onChanged = { viewModel.setAppLanguage(it) }
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_gamepad_swap_face_buttons),
                            description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                            checked = gamepadSwapFaceButtons,
                            onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.APPEARANCE) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_appearance),
                        colors = colors,
                    ) {
                        ThemePickerRow(
                            themeMode = themeMode,
                            accentColor = effectiveAccent,
                            onChanged = { viewModel.setThemeMode(it) }
                        )
                        if (themeMode.supportsCustomAccent) {
                            AppDivider()
                            AccentColorRow(
                                accentColor = accentColor,
                                onClick = { showColorPicker = true }
                            )
                        }
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.DATA) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_data),
                        colors = colors,
                    ) {
                        ConfigActionRow(
                            label = stringResource(R.string.settings_restore_defaults),
                            description = stringResource(R.string.settings_restore_defaults_desc),
                            accentColor = effectiveAccent,
                            onClick = { showRestoreDefaultsConfirm = true },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.CONFIGURATION) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_config),
                        colors = colors,
                    ) {
                        ConfigSection(
                            onShowExportDialog = { showExportMetadataDialog = true },
                            onShowRestoreBackupDialog = { showRestoreBackupDialog = true },
                            onShowProfileExportDialog = { showProfileExportDialog = true },
                            onImportPreviewReady = { showImportPreviewDialog = it },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.PRIVILEGED_MODE) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_privileged_mode),
                        colors = colors,
                    ) {
                        PrivdSettingsCard(
                            viewModel = viewModel,
                            onShowWizard = { showPrivdWizard = true },
                            onShowDeadzoneDialog = { showDeadzoneDialog = true },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.DIAGNOSTICS) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_diagnostics),
                        colors = colors,
                    ) {
                        LogLevelPickerRow(
                            logLevel = logLevel,
                            accentColor = effectiveAccent,
                            onChanged = { viewModel.setLogLevel(it) }
                        )
                        AppDivider()
                        SaveLogReportRow(
                            accentColor = effectiveAccent,
                            onClick = { viewModel.requestSaveLogReport() }
                        )
                    }
                }
            }
        }

        if (showPrivdWizard) {
            PrivdSetupWizardDialog(
                viewModel = viewModel,
                onDismiss = { showPrivdWizard = false },
            )
        }
        if (showDeadzoneDialog) {
            val deadzoneLeft by viewModel.privdDeadzoneLeft.collectAsState()
            val deadzoneRight by viewModel.privdDeadzoneRight.collectAsState()
            DeadzoneDialog(
                initialDeadzoneLeft = deadzoneLeft,
                initialDeadzoneRight = deadzoneRight,
                onConfirm = { left, right ->
                    viewModel.setPrivdDeadzoneLeft(left)
                    viewModel.setPrivdDeadzoneRight(right)
                    showDeadzoneDialog = false
                },
                onDismiss = { showDeadzoneDialog = false },
            )
        }
        if (showColorPicker) {
            ColorWheelPicker(
                initialColor = accentColor,
                onColorSelected = { color ->
                    viewModel.setAccentColor(color.toArgb())
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
        if (showRestoreBackupDialog) {
            RestoreBackupSelectionDialog(
                internalBackups = internalBackups,
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = { backup ->
                    showRestoreBackupDialog = false
                    if (backup == null) {
                        ConfigManager.requestImport(ConfigManager.ImportMode.BACKUP_RESTORE)
                    } else {
                        showImportPreviewDialog = backup.export
                    }
                },
                onDismiss = { showRestoreBackupDialog = false }
            )
        }
        if (showRestoreDefaultsConfirm) {
            InTreeConfirmDialog(
                title = stringResource(R.string.settings_restore_defaults),
                text = stringResource(R.string.settings_restore_defaults_confirm),
                confirmText = if (restoreCountdown > 0) {
                    stringResource(R.string.settings_restore_defaults_confirm_countdown, restoreCountdown)
                } else {
                    stringResource(R.string.settings_restore_defaults_confirm_button)
                },
                confirmEnabled = restoreCountdown == 0,
                dismissText = stringResource(R.string.settings_cancel),
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = {
                    showRestoreDefaultsConfirm = false
                    MacroPadState.restoreDefaults()
                },
                onDismiss = { showRestoreDefaultsConfirm = false },
            )
        }
        if (showExportMetadataDialog) {
            ExportMetadataDialog(
                defaultMetadata = ConfigManager.defaultMetadata(context),
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = { metadata ->
                    showExportMetadataDialog = false
                    ConfigManager.requestExport(
                        metadata = metadata,
                        filename = buildExportFilename(metadata),
                    )
                },
                onDismiss = { showExportMetadataDialog = false },
            )
        }
        if (showProfileExportDialog) {
            ProfileExportDialog(
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = { metadata, profile ->
                    showProfileExportDialog = false
                    ConfigManager.requestProfileExport(
                        metadata = metadata,
                        profile = profile,
                        filename = buildProfileExportFilename(metadata, profile.name),
                    )
                },
                onDismiss = { showProfileExportDialog = false },
            )
        }
        showImportPreviewDialog?.let { export ->
            ImportPreviewDialog(
                export = export,
                importMode = pendingInAppImportMode,
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = {
                    showImportPreviewDialog = null
                    val mode = pendingInAppImportMode
                    coroutineScope.launch {
                        runCatching {
                            when (mode) {
                                ConfigManager.ImportMode.BACKUP_RESTORE -> ConfigManager.applyImport(export)
                                ConfigManager.ImportMode.PROFILE_SHARE  -> ConfigManager.applyProfileImport(export)
                            }
                        }
                            .onSuccess {
                                when (mode) {
                                    ConfigManager.ImportMode.BACKUP_RESTORE -> importSuccess = true
                                    ConfigManager.ImportMode.PROFILE_SHARE  -> profileImportSuccess = true
                                }
                            }
                            .onFailure { e -> importError = e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.config_error_unknown) }
                        ConfigManager.clearInAppPendingImport()
                    }
                },
                onDismiss = {
                    showImportPreviewDialog = null
                    ConfigManager.clearInAppPendingImport()
                },
            )
        }
        importError?.let { error ->
            InTreeMessageDialog(
                title = stringResource(R.string.config_error_title),
                text = error,
                buttonText = stringResource(R.string.config_ok),
                colors = colors,
                accentColor = effectiveAccent,
                onDismiss = { importError = null },
            )
        }
        if (importSuccess) {
            InTreeMessageDialog(
                title = stringResource(R.string.config_success_title),
                text = stringResource(R.string.config_import_success),
                buttonText = stringResource(R.string.config_ok),
                colors = colors,
                accentColor = effectiveAccent,
                onDismiss = { importSuccess = false },
            )
        }
        if (profileImportSuccess) {
            InTreeMessageDialog(
                title = stringResource(R.string.config_success_title),
                text = stringResource(R.string.config_profile_import_success),
                buttonText = stringResource(R.string.config_ok),
                colors = colors,
                accentColor = effectiveAccent,
                onDismiss = { profileImportSuccess = false },
            )
        }
        when (val result = exportResult) {
            is ConfigManager.ExportResult.Success -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_success_title),
                    text = stringResource(R.string.config_export_success),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { ConfigManager.clearExportResult() },
                )
            }
            is ConfigManager.ExportResult.Failure -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_error_title),
                    text = result.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.config_error_unknown),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { ConfigManager.clearExportResult() },
                )
            }
            null -> {}
        }
        when (val logResult = logReportSaveResult) {
            is LogReportManager.SaveResult.Success -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_success_title),
                    text = stringResource(R.string.log_report_save_success),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { LogReportManager.clearSaveResult() },
                )
            }
            is LogReportManager.SaveResult.Failure -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_error_title),
                    text = logResult.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.log_report_save_error),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { LogReportManager.clearSaveResult() },
                )
            }
            null -> {}
        }
    }
}
