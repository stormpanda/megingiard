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
import com.stormpanda.megingiard.BuildConfig
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdManager
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
import com.stormpanda.megingiard.update.UpdateManager
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

private const val GS_RELEASES_FALLBACK_URL = "$GS_OBTAINIUM_REPO_URL/releases"

@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by SettingsManager.accentColor.collectAsStateWithLifecycle()
    val customAccentColorArgb by SettingsManager.customAccentColor.collectAsStateWithLifecycle()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsStateWithLifecycle()
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsStateWithLifecycle()
    val themeMode by SettingsManager.themeMode.collectAsStateWithLifecycle()
    val appLanguage by SettingsManager.appLanguage.collectAsStateWithLifecycle()
    val logLevel by SettingsManager.logLevel.collectAsStateWithLifecycle()
    val excludeFromRecents by SettingsManager.excludeFromRecents.collectAsStateWithLifecycle()
    val gamepadSwapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()
    val kbLayout by KeyboardSettings.kbLayout.collectAsStateWithLifecycle()
    val kbTouchpadEnabled by KeyboardSettings.kbTouchpadEnabled.collectAsStateWithLifecycle()
    val kbAutoOpenOnFocus by KeyboardSettings.kbAutoOpenOnFocus.collectAsStateWithLifecycle()
    val privdState by PrivdManager.state.collectAsStateWithLifecycle()
    val deadzoneLeft by MacroPadSettings.deadzoneLeft.collectAsStateWithLifecycle()
    val deadzoneRight by MacroPadSettings.deadzoneRight.collectAsStateWithLifecycle()
    val steamGridDbApiToken by SettingsManager.steamGridDbApiToken.collectAsStateWithLifecycle()
    val steamGridDbTestStatus by viewModel.steamGridDbTestStatus.collectAsStateWithLifecycle()
    val internalBackups by SettingsManager.internalBackups.collectAsStateWithLifecycle()

    val autoUpdateCheckEnabled by UpdateManager.autoUpdateCheckEnabled.collectAsStateWithLifecycle()
    val updateAvailable by UpdateManager.updateAvailable.collectAsStateWithLifecycle()
    val latestReleaseInfo by UpdateManager.latestReleaseInfo.collectAsStateWithLifecycle()
    val isCheckingUpdates by UpdateManager.isChecking.collectAsStateWithLifecycle()
    val updateCheckError by UpdateManager.checkError.collectAsStateWithLifecycle()
    val isRootBridgeAvailable by viewModel.isRootBridgeAvailable.collectAsStateWithLifecycle()

    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    val exportResult by ConfigManager.exportResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.checkPrivilegedModeStatus()
    }
    var subPageStack by rememberSaveable { mutableStateOf<List<SettingsSubPage>>(emptyList()) }
    val currentSubPage = subPageStack.lastOrNull()
    val pendingInAppParsedImport by ConfigManager.pendingInAppParsedImport.collectAsStateWithLifecycle()
    val configImportError by ConfigManager.inAppImportError.collectAsStateWithLifecycle()
    var lastReviewExport by remember { mutableStateOf<MegingiardExport?>(null) }
    LaunchedEffect(subPageStack) {
        if (SettingsSubPage.RESTORE_REVIEW !in subPageStack) {
            ConfigManager.clearInAppPendingImport()
        }
    }
    val pendingInAppImportMode by ConfigManager.pendingInAppImportMode.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
    var hasTriggeredManualCheck by rememberSaveable { mutableStateOf(false) }

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
        val parsed = pendingInAppParsedImport
        if (parsed != null) {
            lastReviewExport = parsed.export
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
                                        onConnectPrivd = {
                                            viewModel.privdConnect(context)
                                        },
                                        isRootBridgeAvailable = isRootBridgeAvailable == true,
                                        onLanguageChange = { SettingsManager.setAppLanguage(it) },
                                        onExcludeFromRecentsChange = { SettingsManager.setExcludeFromRecents(it) },
                                        onResetTutorials = {
                                            SettingsManager.resetAllTutorials()
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
                                        kbLayout = kbLayout,
                                        kbTouchpadEnabled = kbTouchpadEnabled,
                                        kbAutoOpenOnFocus = kbAutoOpenOnFocus,
                                        onGamepadSwapFaceButtonsChange = { MacroPadSettings.setGamepadSwapFaceButtons(it) },
                                        onOpenDeadzones = { subPageStack = listOf(SettingsSubPage.DEADZONES) },
                                        onKbLayoutChange = { KeyboardSettings.setKbLayout(it) },
                                        onKbTouchpadEnabledChange = { KeyboardSettings.setKbTouchpadEnabled(it) },
                                        onKbAutoOpenOnFocusChange = { KeyboardSettings.setKbAutoOpenOnFocus(it) },
                                    )
                                }

                                SettingsCategory.APPEARANCE -> {
                                    AppearanceSettingsTab(
                                        themeMode = themeMode,
                                        accentColorArgb = accentColorArgb,
                                        customAccentColorArgb = customAccentColorArgb,
                                        overlayAtBottom = overlayAtBottom,
                                        overlayFadeOut = overlayFadeOut,
                                        onThemeModeChange = { SettingsManager.setThemeMode(it) },
                                        onAccentColorChange = { SettingsManager.setAccentColor(it) },
                                        onOpenCustomAccent = { subPageStack = listOf(SettingsSubPage.CUSTOM_ACCENT) },
                                        onOverlayAtBottomChange = { SettingsManager.setOverlayAtBottom(it) },
                                        onOverlayFadeOutChange = { SettingsManager.setOverlayFadeOut(it) },
                                    )
                                }

                                SettingsCategory.CONFIGURATION -> {
                                    ConfigurationSettingsTab(
                                        onExportBackup = { subPageStack = listOf(SettingsSubPage.CREATE_BACKUP) },
                                        onRestoreBackup = { subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP) },
                                        onShareProfile = { subPageStack = listOf(SettingsSubPage.SHARE_PROFILE) },
                                        onImportProfile = {
                                            ConfigManager.requestImport(ConfigManager.ImportMode.PROFILE_SHARE)
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
                                    UpdatesSettingsTab(
                                        autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                        updateAvailable = updateAvailable,
                                        latestReleaseInfo = latestReleaseInfo,
                                        isCheckingUpdates = isCheckingUpdates,
                                        updateCheckError = updateCheckError,
                                        hasTriggeredManualCheck = hasTriggeredManualCheck,
                                        onAutoUpdateCheckEnabledChange = { UpdateManager.setAutoUpdateCheckEnabled(it) },
                                        onManualCheckClick = {
                                            if (isCheckingUpdates) return@UpdatesSettingsTab

                                            if (updateAvailable) {
                                                subPageStack = listOf(SettingsSubPage.UPDATE_AVAILABLE)
                                            } else {
                                                hasTriggeredManualCheck = true
                                                UpdateManager.checkForUpdates(
                                                    force = true,
                                                    currentVersion = BuildConfig.VERSION_NAME,
                                                )
                                            }
                                        },
                                        onOpenObtainium = {
                                            if (!launchUrlOnPrimaryDisplay(context, "obtainium://add/$GS_OBTAINIUM_REPO_URL")) {
                                                AppLog.w(TAG, "Obtainium deep link failed, falling back to browser")
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
                                        onLogLevelChange = { SettingsManager.setLogLevel(it) },
                                        onSaveLogReport = { LogReportManager.requestSaveReport() },
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
                        ) {
                            DeadzonesSubPage(
                                deadzoneLeft = deadzoneLeft,
                                deadzoneRight = deadzoneRight,
                                onLeftChange = { MacroPadSettings.setDeadzoneLeft(it) },
                                onRightChange = { MacroPadSettings.setDeadzoneRight(it) },
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
                        ) {
                            CustomAccentSubPage(
                                initialColor = Color(customAccentColorArgb),
                                onSaveColor = { newColor ->
                                    val argb = newColor.toArgb()
                                    SettingsManager.setCustomAccentColor(argb)
                                    SettingsManager.setAccentColor(argb)
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
                        ) {
                            CreateBackupSubPage(
                                onExport = { metadata, includeBackgrounds ->
                                    ConfigManager.requestExport(
                                        metadata = metadata,
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
                        ) {
                            ShareProfileSubPage(
                                onExportProfile = { metadata, profile, includeBackgrounds ->
                                    ConfigManager.requestProfileExport(
                                        metadata = metadata,
                                        profile = profile,
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
                        ) {
                            RestoreBackupSubPage(
                                internalBackups = internalBackups,
                                effectiveAccent = effectiveAccent,
                                onPickExternalFile = {
                                    ConfigManager.requestImport(ConfigManager.ImportMode.BACKUP_RESTORE)
                                },
                                onSelectInternalBackup = { backup ->
                                    ConfigManager.setPendingInAppImportMode(ConfigManager.ImportMode.BACKUP_RESTORE)
                                    ConfigManager.setInAppParsedImport(backup.export)
                                    subPageStack = subPageStack + SettingsSubPage.RESTORE_REVIEW
                                },
                            )
                        }
                    }

                    SettingsSubPage.RESTORE_REVIEW -> {
                        val reviewExport = pendingInAppParsedImport?.export ?: lastReviewExport
                        if (reviewExport != null) {
                            GamepadDeck(
                                breadcrumbs =
                                    listOf(
                                        stringResource(R.string.settings_section_config),
                                        stringResource(R.string.config_restore_dialog_title),
                                        stringResource(R.string.config_import_review_title),
                                    ),
                            ) {
                                RestoreReviewSubPage(
                                    export = reviewExport,
                                    pendingInAppImportMode = pendingInAppImportMode,
                                    onConfirmImport = { exp, mode ->
                                        val pendingImages = pendingInAppParsedImport?.images.orEmpty()
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
                                                val errorMsg =
                                                    e.message?.takeIf { it.isNotBlank() }
                                                        ?: context.getString(R.string.config_error_unknown)
                                                DialogToastManager.show(
                                                    message = errorMsg,
                                                    icon = Icons.Rounded.ErrorOutline,
                                                    isError = true,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    SettingsSubPage.UPDATE_AVAILABLE -> {
                        val releaseUrl =
                            latestReleaseInfo?.htmlUrl?.ifBlank { GS_RELEASES_FALLBACK_URL }
                                ?: GS_RELEASES_FALLBACK_URL
                        val tagName = latestReleaseInfo?.tagName ?: ""
                        GamepadDeck(
                            breadcrumbs =
                                listOf(
                                    stringResource(R.string.settings_jump_updates),
                                    stringResource(R.string.update_dialog_title, tagName),
                                ),
                        ) {
                            UpdateAvailableSubPage(
                                tagName = tagName,
                                effectiveAccent = effectiveAccent,
                                onBackupAndOpen = {
                                    subPageStack = subPageStack + SettingsSubPage.CREATE_BACKUP
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
    LaunchedEffect(configImportError) {
        val error = configImportError
        if (error != null) {
            DialogToastManager.show(
                message = error,
                icon = Icons.Rounded.ErrorOutline,
                isError = true,
            )
            ConfigManager.setInAppImportError(null)
        }
    }
    LaunchedEffect(exportResult) {
        when (val result = exportResult) {
            is ConfigManager.ExportResult.Success -> {
                val toastMsg =
                    if (result.kind is ConfigManager.ExportKind.ProfileShare) {
                        context.getString(R.string.config_profile_export_success)
                    } else {
                        context.getString(R.string.config_export_success)
                    }
                DialogToastManager.show(toastMsg)
                ConfigManager.clearExportResult()
                if (SettingsSubPage.UPDATE_AVAILABLE in subPageStack) {
                    val releaseUrl =
                        latestReleaseInfo?.htmlUrl?.ifBlank { GS_RELEASES_FALLBACK_URL }
                            ?: GS_RELEASES_FALLBACK_URL
                    launchUrlOnPrimaryDisplay(context, releaseUrl)
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
}
