package com.stormpanda.megingiard.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import android.app.ActivityOptions
import androidx.compose.ui.text.font.FontWeight
import com.stormpanda.megingiard.ui.AppTextField
import android.view.Display
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager

import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.privd.DeadzoneDialog
import com.stormpanda.megingiard.privd.PrivdSettingsCard
import com.stormpanda.megingiard.privd.PrivdSetupWizardDialog
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L

private const val GS_OBTAINIUM_REPO_URL = "https://github.com/stormpanda/megingiard"
private const val GS_OBTAINIUM_FALLBACK_URL = "https://github.com/ImranR98/Obtainium"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsState()
    val accentColor = Color(accentColorArgb)
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val overlayFadeOut by viewModel.overlayFadeOut.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val autoSwitchProfiles by viewModel.autoSwitchProfiles.collectAsState()
    val excludeFromRecents by viewModel.excludeFromRecents.collectAsState()
    val gamepadSwapFaceButtons by viewModel.gamepadSwapFaceButtons.collectAsState()
    val steamGridDbApiToken by viewModel.steamGridDbApiToken.collectAsState()
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
    
    var showSettingsHelp by rememberSaveable { mutableStateOf(false) }
    var isAccessibilityActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = viewModel.checkAccessibilityActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    actions = {
                        HelpIconButton(onClick = { showSettingsHelp = true })
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
                    onSelectAutomation = { selectedSectionFilter = SettingsSectionFilter.AUTOMATION },
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
                        LanguagePickerRow(
                            language = appLanguage,
                            accentColor = effectiveAccent,
                            onChanged = { viewModel.setAppLanguage(it) }
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_exclude_from_recents),
                            description = stringResource(R.string.settings_exclude_from_recents_desc),
                            checked = excludeFromRecents,
                            onCheckedChange = { viewModel.setExcludeFromRecents(it) },
                        )
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_gamepad_swap_face_buttons),
                            description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                            checked = gamepadSwapFaceButtons,
                            onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                        )
                        AppDivider()
                        SteamGridDbTokenRow(
                            token = steamGridDbApiToken,
                            onTokenChanged = { viewModel.setSteamGridDbApiToken(it) },
                            accentColor = effectiveAccent,
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

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.AUTOMATION) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_automation),
                        colors = colors,
                    ) {
                        AppSettingsRow(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val options = ActivityOptions.makeBasic()
                                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                context.startActivity(intent, options.toBundle())
                            }
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.settings_accessibility_status),
                                        color = colors.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                if (isAccessibilityActive) colors.actionColorSystem else colors.onSurfaceSecondary,
                                                CircleShape
                                            )
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        if (isAccessibilityActive) R.string.privd_status_running
                                        else R.string.privd_status_off
                                    ),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.settings_accessibility_status_desc),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!isAccessibilityActive) {
                                IconButton(
                                    onClick = {
                                        isAccessibilityActive = viewModel.checkAccessibilityActive(context)
                                        AppLog.d(TAG, "Manual refresh: Accessibility active = $isAccessibilityActive")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh status",
                                        tint = colors.onSurfaceSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = effectiveAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        AppDivider()
                        RememberSettingRow(
                            label = stringResource(R.string.settings_auto_switch_profiles),
                            description = stringResource(R.string.settings_auto_switch_profiles_desc),
                            checked = autoSwitchProfiles,
                            onCheckedChange = { viewModel.setAutoSwitchProfiles(it) },
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
                        AppDivider()
                        OverlayPositionRow(
                            overlayAtBottom = overlayAtBottom,
                            onChanged = { viewModel.setOverlayAtBottom(it) }
                        )
                        AppDivider()
                        OverlayFadeOutRow(
                            fadeEnabled = overlayFadeOut,
                            onChanged = { viewModel.setOverlayFadeOut(it) }
                        )
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
                        AppDivider()
                        ConfigActionRow(
                            label = stringResource(R.string.settings_reset_tutorials),
                            description = stringResource(R.string.settings_reset_tutorials_desc),
                            accentColor = effectiveAccent,
                            onClick = {
                                viewModel.resetAllTutorials()
                                Toast.makeText(context, context.getString(R.string.settings_reset_tutorials_toast), Toast.LENGTH_SHORT).show()
                            },
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
                            onAddToObtainium = {
                                val deepLink = "obtainium://add/${GS_OBTAINIUM_REPO_URL}"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    val options = ActivityOptions.makeBasic()
                                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                    context.startActivity(intent, options.toBundle())
                                    AppLog.d(TAG, "Launched Obtainium deep link: $deepLink")
                                } catch (e: Exception) {
                                    AppLog.w(TAG, "Obtainium deep link failed: ${e.message}, falling back to browser")
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GS_OBTAINIUM_FALLBACK_URL)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val options = ActivityOptions.makeBasic()
                                        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                        context.startActivity(browserIntent, options.toBundle())
                                    } catch (ex: Exception) {
                                        AppLog.e(TAG, "Failed to open browser fallback: ${ex.message}")
                                    }
                                }
                            }
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

    GlobalSettingsHelpModal(
        visible = showSettingsHelp,
        onDismiss = { showSettingsHelp = false },
    )
}

@Composable
private fun GlobalSettingsHelpModal(visible: Boolean, onDismiss: () -> Unit) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_settings_intro))

        HelpSection(stringResource(R.string.settings_section_general))
        HelpEntry(
            label = stringResource(R.string.settings_language),
            description = stringResource(R.string.help_settings_language_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_exclude_from_recents),
            description = stringResource(R.string.help_settings_recents_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_gamepad_swap_face_buttons),
            description = stringResource(R.string.help_settings_gamepad_swap_desc),
        )

        HelpSection(stringResource(R.string.settings_section_privileged_mode))
        HelpEntry(
            label = stringResource(R.string.privd_title),
            description = stringResource(R.string.help_settings_privd_desc),
        )
        HelpEntry(
            label = stringResource(R.string.privd_show_reconnect_prompt),
            description = stringResource(R.string.help_settings_reconnect_prompt_desc),
        )
        HelpEntry(
            label = stringResource(R.string.privd_deadzone_title),
            description = stringResource(R.string.help_settings_deadzone_desc),
        )

        HelpSection(stringResource(R.string.settings_section_automation))
        HelpEntry(
            label = stringResource(R.string.settings_accessibility_status),
            description = stringResource(R.string.help_settings_accessibility_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_auto_switch_profiles),
            description = stringResource(R.string.help_settings_auto_profile_desc),
        )

        HelpSection(stringResource(R.string.settings_section_appearance))
        HelpEntry(
            label = stringResource(R.string.settings_theme),
            description = stringResource(R.string.help_settings_theme_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_accent_color),
            description = stringResource(R.string.help_settings_accent_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_overlay_position),
            description = stringResource(R.string.help_settings_overlay_position_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_overlay_fade_out),
            description = stringResource(R.string.help_settings_overlay_fade_out_desc),
        )

        HelpSection(stringResource(R.string.settings_section_data))
        HelpEntry(
            label = stringResource(R.string.settings_restore_defaults),
            description = stringResource(R.string.help_settings_restore_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_reset_tutorials),
            description = stringResource(R.string.help_settings_reset_tutorials_desc),
        )

        HelpSection(stringResource(R.string.settings_section_config))
        HelpEntry(
            label = stringResource(R.string.settings_config_export),
            description = stringResource(R.string.help_settings_export_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_config_import),
            description = stringResource(R.string.help_settings_import_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_config_export_profile),
            description = stringResource(R.string.help_settings_export_profile_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_config_import_profile),
            description = stringResource(R.string.help_settings_import_profile_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_add_to_obtainium),
            description = stringResource(R.string.help_settings_add_to_obtainium_desc),
        )



        HelpSection(stringResource(R.string.settings_section_diagnostics))
        HelpEntry(
            label = stringResource(R.string.settings_log_level),
            description = stringResource(R.string.help_settings_log_level_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_save_log_report),
            description = stringResource(R.string.help_settings_save_log_desc),
        )
    }
}

@Composable
private fun SteamGridDbTokenRow(
    token: String,
    onTokenChanged: (String) -> Unit,
    accentColor: Color,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_steamgriddb_token),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_steamgriddb_token_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AppTextField(
            value = token,
            onValueChange = onTokenChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.settings_steamgriddb_token_placeholder),
                    color = colors.onSurfaceSecondary
                )
            },
            singleLine = true
        )
    }
}
