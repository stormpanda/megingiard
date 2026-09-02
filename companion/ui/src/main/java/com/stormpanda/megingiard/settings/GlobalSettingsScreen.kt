package com.stormpanda.megingiard.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.config.buildExportFilename
import com.stormpanda.megingiard.config.buildProfileExportFilename
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.settings.tabs.AppearanceSettingsTab
import com.stormpanda.megingiard.settings.tabs.ConfigurationSettingsTab
import com.stormpanda.megingiard.settings.tabs.CreateBackupSubPage
import com.stormpanda.megingiard.settings.tabs.CustomAccentSubPage
import com.stormpanda.megingiard.settings.tabs.DeadzonesSubPage
import com.stormpanda.megingiard.settings.tabs.DiagnosticsSettingsTab
import com.stormpanda.megingiard.settings.tabs.GS_OBTAINIUM_FALLBACK_URL
import com.stormpanda.megingiard.settings.tabs.GS_OBTAINIUM_REPO_URL
import com.stormpanda.megingiard.settings.tabs.GeneralSettingsTab
import com.stormpanda.megingiard.settings.tabs.InputSettingsTab
import com.stormpanda.megingiard.settings.tabs.RestoreBackupSubPage
import com.stormpanda.megingiard.settings.tabs.RestoreReviewSubPage
import com.stormpanda.megingiard.settings.tabs.ScrapingSettingsTab
import com.stormpanda.megingiard.settings.tabs.ShareProfileSubPage
import com.stormpanda.megingiard.settings.tabs.SteamGridDbTokenSubPage
import com.stormpanda.megingiard.settings.tabs.UpdateAvailableSubPage
import com.stormpanda.megingiard.settings.tabs.UpdatesSettingsTab
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.launchUrlOnPrimaryDisplay
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L
private const val GS_RESTORE_CONFIRM_TIMEOUT_MS = 8_000L

@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsStateWithLifecycle()
    val customAccentColorArgb by viewModel.customAccentColor.collectAsStateWithLifecycle()
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsStateWithLifecycle()
    val overlayFadeOut by viewModel.overlayFadeOut.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()
    val excludeFromRecents by viewModel.excludeFromRecents.collectAsStateWithLifecycle()
    val gamepadSwapFaceButtons by viewModel.gamepadSwapFaceButtons.collectAsStateWithLifecycle()
    val privdState by viewModel.privdState.collectAsStateWithLifecycle()
    val deadzoneLeft by viewModel.privdDeadzoneLeft.collectAsStateWithLifecycle()
    val deadzoneRight by viewModel.privdDeadzoneRight.collectAsStateWithLifecycle()
    val steamGridDbApiToken by viewModel.steamGridDbApiToken.collectAsStateWithLifecycle()
    val steamGridDbTestStatus by viewModel.steamGridDbTestStatus.collectAsStateWithLifecycle()
    val internalBackups by viewModel.internalBackups.collectAsStateWithLifecycle()

    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val latestReleaseInfo by viewModel.latestReleaseInfo.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val updateCheckError by viewModel.updateCheckError.collectAsStateWithLifecycle()

    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    val exportResult by ConfigManager.exportResult.collectAsStateWithLifecycle()
    val logReportSaveResult by LogReportManager.saveResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var subPageStack by rememberSaveable { mutableStateOf<List<SettingsSubPage>>(emptyList()) }
    val currentSubPage = subPageStack.lastOrNull()
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    val pendingInAppParsedImport by ConfigManager.pendingInAppParsedImport.collectAsStateWithLifecycle()
    val configImportError by ConfigManager.inAppImportError.collectAsStateWithLifecycle()
    val activeImportPreview = showImportPreviewDialog ?: pendingInAppParsedImport
    var lastReviewExport by remember { mutableStateOf<MegingiardExport?>(null) }
    LaunchedEffect(activeImportPreview) {
        if (activeImportPreview != null) {
            lastReviewExport = activeImportPreview
        }
    }
    var pendingUpdateReleaseUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(subPageStack) {
        if (SettingsSubPage.RESTORE_REVIEW !in subPageStack) {
            showImportPreviewDialog = null
            ConfigManager.clearInAppPendingImport()
        }
        if (SettingsSubPage.CREATE_BACKUP !in subPageStack) {
            pendingUpdateReleaseUrl = null
        }
    }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingInAppImportMode by ConfigManager.pendingInAppImportMode.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }

    var deleteCountdown by rememberSaveable(selectedCategory, subPageStack) { mutableIntStateOf(-1) }
    var isDeleteCountingDown by rememberSaveable(selectedCategory, subPageStack) { mutableStateOf(false) }

    LaunchedEffect(isDeleteCountingDown) {
        if (isDeleteCountingDown) {
            deleteCountdown = GS_RESTORE_COUNTDOWN_SECONDS
            while (deleteCountdown > 0) {
                delay(GS_RESTORE_COUNTDOWN_INTERVAL_MS)
                deleteCountdown--
            }
            isDeleteCountingDown = false
        }
    }

    LaunchedEffect(deleteCountdown) {
        if (deleteCountdown == 0) {
            delay(GS_RESTORE_CONFIRM_TIMEOUT_MS)
            if (deleteCountdown == 0) {
                deleteCountdown = -1
            }
        }
    }

    val categoryList = remember { SettingsCategory.entries }
    val activePrimaryModal by AppStateManager.activePrimaryModal.collectAsStateWithLifecycle()

    LaunchedEffect(activePrimaryModal) {
        val payload = activePrimaryModal?.payload as? PrimaryModalPayload.GlobalSettings
        if (payload != null) {
            selectedCategory = payload.category
            subPageStack = payload.subPage?.let { listOf(it) } ?: emptyList()
        }
    }

    LaunchedEffect(pendingInAppParsedImport) {
        if (pendingInAppParsedImport != null) {
            selectedCategory = SettingsCategory.CONFIGURATION
            subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP, SettingsSubPage.RESTORE_REVIEW)
        }
    }

    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            selectedCategory = categoryList.cycle(selectedCategory, direction)
            subPageStack = emptyList()
        }
    }

    GamepadTwoPaneScaffold(
        scrollableDeck = false,
        isCustomBackActive = subPageStack.isNotEmpty(),
        onCustomBack = {
            subPageStack = subPageStack.dropLast(1)
        },
        navigationKey = subPageStack,
        sidebarContent = {
            SettingsCategory.entries.forEach { category ->
                GamepadCategoryTile(
                    title = stringResource(category.titleResId),
                    icon = category.icon,
                    selected = (currentSubPage?.parentCategory ?: selectedCategory) == category,
                    onClick = {
                        selectedCategory = category
                        subPageStack = emptyList()
                    },
                )
            }
        },
        content = {
            AnimatedContent(
                targetState = subPageStack,
                transitionSpec = {
                    val direction = if (targetState.size < initialState.size) -1 else 1
                    slideInHorizontally { width -> width * direction }.togetherWith(
                        slideOutHorizontally { width -> -width * direction },
                    )
                },
                label = "SettingsSubPageAnimation",
            ) { stack ->
                val subPage = stack.lastOrNull()
                when (subPage) {
                    null -> {
                        val categoryTitle = stringResource(selectedCategory.titleResId)
                        GamepadDeck(
                            title = categoryTitle,
                            accentColor = effectiveAccent,
                        ) {
                            when (selectedCategory) {
                                SettingsCategory.GENERAL -> {
                                    GeneralSettingsTab(
                                        updateAvailable = updateAvailable,
                                        latestReleaseInfo = latestReleaseInfo,
                                        privdState = privdState,
                                        appLanguage = appLanguage,
                                        excludeFromRecents = excludeFromRecents,
                                        onNavigateToSubPage = { subPageStack = listOf(it) },
                                        onStartWelcomeTour = {
                                            AppStateManager.closeActiveModal()
                                            OnboardingWizardManager.startWizard(force = true)
                                            onBack()
                                        },
                                        onOpenPrivdSetup = {
                                            AppStateManager.closeActiveModal()
                                            AppStateManager.setPrivdSetupWizardOpen(true)
                                            onBack()
                                        },
                                        onLanguageChange = { viewModel.setAppLanguage(it) },
                                        onExcludeFromRecentsChange = { viewModel.setExcludeFromRecents(it) },
                                        onResetTutorials = {
                                            viewModel.resetAllTutorials()
                                            DialogToastManager.show(
                                                context.getString(R.string.settings_reset_tutorials_toast),
                                            )
                                        },
                                    )
                                }

                                SettingsCategory.INPUT -> {
                                    InputSettingsTab(
                                        gamepadSwapFaceButtons = gamepadSwapFaceButtons,
                                        deadzoneLeft = deadzoneLeft,
                                        deadzoneRight = deadzoneRight,
                                        onGamepadSwapFaceButtonsChange = { viewModel.setGamepadSwapFaceButtons(it) },
                                        onOpenDeadzones = { subPageStack = listOf(SettingsSubPage.DEADZONES) },
                                    )
                                }

                                SettingsCategory.APPEARANCE -> {
                                    AppearanceSettingsTab(
                                        themeMode = themeMode,
                                        accentColorArgb = accentColorArgb,
                                        customAccentColorArgb = customAccentColorArgb,
                                        overlayAtBottom = overlayAtBottom,
                                        overlayFadeOut = overlayFadeOut,
                                        onThemeModeChange = { viewModel.setThemeMode(it) },
                                        onAccentColorChange = { viewModel.setAccentColor(it) },
                                        onOpenCustomAccent = { subPageStack = listOf(SettingsSubPage.CUSTOM_ACCENT) },
                                        onOverlayAtBottomChange = { viewModel.setOverlayAtBottom(it) },
                                        onOverlayFadeOutChange = { viewModel.setOverlayFadeOut(it) },
                                    )
                                }

                                SettingsCategory.CONFIGURATION -> {
                                    ConfigurationSettingsTab(
                                        deleteCountdown = deleteCountdown,
                                        onExportBackup = { subPageStack = listOf(SettingsSubPage.CREATE_BACKUP) },
                                        onRestoreBackup = { subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP) },
                                        onShareProfile = { subPageStack = listOf(SettingsSubPage.SHARE_PROFILE) },
                                        onImportProfile = {
                                            ConfigManager.requestImport(ConfigManager.ImportMode.PROFILE_SHARE)
                                        },
                                        onDeleteCountdownClick = {
                                            when {
                                                deleteCountdown > 0 -> {}

                                                deleteCountdown == 0 -> {
                                                    MacroPadState.restoreDefaults()
                                                    DialogToastManager.show(
                                                        context.getString(R.string.settings_restore_defaults_toast),
                                                    )
                                                    deleteCountdown = -1
                                                }

                                                else -> {
                                                    isDeleteCountingDown = true
                                                }
                                            }
                                        },
                                    )
                                }

                                SettingsCategory.SCRAPING -> {
                                    ScrapingSettingsTab(
                                        steamGridDbApiToken = steamGridDbApiToken,
                                        onOpenTokenSubPage = { subPageStack = listOf(SettingsSubPage.STEAMGRIDDB_TOKEN) },
                                    )
                                }

                                SettingsCategory.UPDATES -> {
                                    var hasTriggeredManualCheck by rememberSaveable(selectedCategory) {
                                        mutableStateOf(false)
                                    }

                                    UpdatesSettingsTab(
                                        autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                        updateAvailable = updateAvailable,
                                        latestReleaseInfo = latestReleaseInfo,
                                        isCheckingUpdates = isCheckingUpdates,
                                        updateCheckError = updateCheckError,
                                        hasTriggeredManualCheck = hasTriggeredManualCheck,
                                        onAutoUpdateCheckEnabledChange = { viewModel.setAutoUpdateCheckEnabled(it) },
                                        onManualCheckClick = {
                                            if (isCheckingUpdates) return@UpdatesSettingsTab

                                            if (hasTriggeredManualCheck && updateAvailable) {
                                                subPageStack = listOf(SettingsSubPage.UPDATE_AVAILABLE)
                                            } else {
                                                hasTriggeredManualCheck = true
                                                viewModel.checkForUpdatesManually()
                                            }
                                        },
                                        onOpenObtainium = {
                                            val deepLink = "obtainium://add/${GS_OBTAINIUM_REPO_URL}"
                                            try {
                                                launchUrlOnPrimaryDisplay(context, deepLink)
                                            } catch (e: Exception) {
                                                AppLog.w(TAG, "Obtainium deep link failed: ${e.message}, falling back to browser")
                                                launchUrlOnPrimaryDisplay(context, GS_OBTAINIUM_FALLBACK_URL)
                                            }
                                            AppStateManager.closeActiveModal()
                                            onBack()
                                        },
                                    )
                                }

                                SettingsCategory.DIAGNOSTICS -> {
                                    DiagnosticsSettingsTab(
                                        logLevel = logLevel,
                                        onLogLevelChange = { viewModel.setLogLevel(it) },
                                        onSaveLogReport = { viewModel.requestSaveLogReport() },
                                    )
                                }
                            }
                        }
                    }

                    SettingsSubPage.DEADZONES -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_input),
                                    stringResource(R.string.privd_deadzone_title),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            DeadzonesSubPage(
                                deadzoneLeft = deadzoneLeft,
                                deadzoneRight = deadzoneRight,
                                onLeftChange = { viewModel.setPrivdDeadzoneLeft(it) },
                                onRightChange = { viewModel.setPrivdDeadzoneRight(it) },
                            )
                        }
                    }

                    SettingsSubPage.STEAMGRIDDB_TOKEN -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_scraping),
                                    stringResource(R.string.settings_steamgriddb_token),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            SteamGridDbTokenSubPage(
                                token = steamGridDbApiToken,
                                onTokenChange = { viewModel.setSteamGridDbApiToken(it) },
                                testStatus = steamGridDbTestStatus,
                                onTestConnection = { viewModel.testSteamGridDbConnection(steamGridDbApiToken) },
                            )
                        }
                    }

                    SettingsSubPage.CUSTOM_ACCENT -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_appearance),
                                    stringResource(R.string.settings_accent_custom_title),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            CustomAccentSubPage(
                                initialColor = Color(customAccentColorArgb),
                                onSaveColor = { newColor ->
                                    val argb = newColor.toArgb()
                                    viewModel.setCustomAccentColor(argb)
                                    viewModel.setAccentColor(argb)
                                },
                            )
                        }
                    }

                    SettingsSubPage.CREATE_BACKUP -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_config),
                                    stringResource(R.string.settings_config_export),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            CreateBackupSubPage(
                                onExport = { metadata, includeBackgrounds ->
                                    ConfigManager.requestExport(
                                        metadata = metadata,
                                        filename = buildExportFilename(metadata),
                                        includeBackgrounds = includeBackgrounds,
                                    )
                                },
                            )
                        }
                    }

                    SettingsSubPage.SHARE_PROFILE -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_config),
                                    stringResource(R.string.settings_config_export_profile),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            ShareProfileSubPage(
                                onExportProfile = { metadata, profile, includeBackgrounds ->
                                    ConfigManager.requestProfileExport(
                                        metadata = metadata,
                                        profile = profile,
                                        filename = buildProfileExportFilename(metadata, profile.name),
                                        includeBackgrounds = includeBackgrounds,
                                    )
                                },
                            )
                        }
                    }

                    SettingsSubPage.RESTORE_BACKUP -> {
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_section_config),
                                    stringResource(R.string.config_restore_dialog_title),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            RestoreBackupSubPage(
                                internalBackups = internalBackups,
                                effectiveAccent = effectiveAccent,
                                onPickExternalFile = {
                                    ConfigManager.requestImport(ConfigManager.ImportMode.BACKUP_RESTORE)
                                },
                                onSelectInternalBackup = { backup ->
                                    showImportPreviewDialog = backup.export
                                    subPageStack = subPageStack + SettingsSubPage.RESTORE_REVIEW
                                },
                            )
                        }
                    }

                    SettingsSubPage.RESTORE_REVIEW -> {
                        val reviewExport = activeImportPreview ?: lastReviewExport
                        if (reviewExport != null) {
                            GamepadDeck(
                                breadcrumbs =
                                    listOf(
                                        stringResource(R.string.settings_section_config),
                                        stringResource(R.string.config_restore_dialog_title),
                                        stringResource(R.string.config_import_review_title),
                                    ),
                                accentColor = effectiveAccent,
                            ) {
                                RestoreReviewSubPage(
                                    export = reviewExport,
                                    pendingInAppImportMode = pendingInAppImportMode,
                                    onConfirmImport = { exp, mode ->
                                        val pendingImages = ConfigManager.getPendingInAppImages()
                                        showImportPreviewDialog = null
                                        subPageStack = emptyList()
                                        coroutineScope.launch {
                                            runCatching {
                                                when (mode) {
                                                    ConfigManager.ImportMode.BACKUP_RESTORE -> {
                                                        ConfigManager.applyImport(
                                                            context,
                                                            exp,
                                                            pendingImages,
                                                        )
                                                    }

                                                    ConfigManager.ImportMode.PROFILE_SHARE -> {
                                                        ConfigManager.applyProfileImport(
                                                            context,
                                                            exp,
                                                            pendingImages,
                                                        )
                                                    }
                                                }
                                            }.onSuccess {
                                                val msgRes =
                                                    if (mode == ConfigManager.ImportMode.BACKUP_RESTORE) {
                                                        R.string.config_import_success
                                                    } else {
                                                        R.string.config_profile_import_success
                                                    }
                                                DialogToastManager.show(context.getString(msgRes))
                                            }.onFailure { e ->
                                                importError =
                                                    e.message?.takeIf { it.isNotBlank() }
                                                        ?: context.getString(R.string.config_error_unknown)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    SettingsSubPage.UPDATE_AVAILABLE -> {
                        val releaseUrl =
                            latestReleaseInfo?.htmlUrl?.ifBlank { "https://github.com/stormpanda/megingiard/releases" }
                                ?: "https://github.com/stormpanda/megingiard/releases"
                        val tagName = latestReleaseInfo?.tagName ?: ""
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_jump_updates),
                                    stringResource(R.string.update_dialog_title, tagName),
                                ),
                            accentColor = effectiveAccent,
                        ) {
                            UpdateAvailableSubPage(
                                tagName = tagName,
                                effectiveAccent = effectiveAccent,
                                onBackupAndOpen = {
                                    pendingUpdateReleaseUrl = releaseUrl
                                    selectedCategory = SettingsCategory.CONFIGURATION
                                    subPageStack = listOf(SettingsSubPage.CREATE_BACKUP)
                                },
                                onOpenDirectly = {
                                    launchUrlOnPrimaryDisplay(context, releaseUrl)
                                    AppStateManager.closeActiveModal()
                                    onBack()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
    LaunchedEffect(importError, configImportError) {
        val error = importError ?: configImportError
        if (error != null) {
            DialogToastManager.show(
                message = error,
                icon = Icons.Rounded.ErrorOutline,
                isError = true,
            )
            importError = null
            ConfigManager.setInAppImportError(null)
        }
    }
    LaunchedEffect(exportResult) {
        when (val result = exportResult) {
            is ConfigManager.ExportResult.Success -> {
                val urlToLaunch = pendingUpdateReleaseUrl
                pendingUpdateReleaseUrl = null
                val toastMsg =
                    if (result.kind is ConfigManager.ExportKind.ProfileShare) {
                        context.getString(R.string.config_profile_export_success)
                    } else {
                        context.getString(R.string.config_export_success)
                    }
                DialogToastManager.show(toastMsg)
                ConfigManager.clearExportResult()
                if (urlToLaunch != null) {
                    launchUrlOnPrimaryDisplay(context, urlToLaunch)
                    AppStateManager.closeActiveModal()
                    onBack()
                } else if (subPageStack.isNotEmpty()) {
                    subPageStack = emptyList()
                }
            }

            is ConfigManager.ExportResult.Failure -> {
                val errorMsg =
                    result.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.config_error_unknown)
                DialogToastManager.show(
                    message = errorMsg,
                    icon = Icons.Rounded.ErrorOutline,
                    isError = true,
                )
                ConfigManager.clearExportResult()
            }

            null -> {}
        }
    }
    LaunchedEffect(logReportSaveResult) {
        when (val logResult = logReportSaveResult) {
            is LogReportManager.SaveResult.Success -> {
                DialogToastManager.show(context.getString(R.string.log_report_save_success))
                LogReportManager.clearSaveResult()
            }

            is LogReportManager.SaveResult.Failure -> {
                val errorMsg =
                    logResult.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.log_report_save_error)
                DialogToastManager.show(
                    message = errorMsg,
                    icon = Icons.Rounded.ErrorOutline,
                    isError = true,
                )
                LogReportManager.clearSaveResult()
            }

            null -> {}
        }
    }
}
