package com.stormpanda.megingiard.settings

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.BuildConfig
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.config.buildExportFilename
import com.stormpanda.megingiard.config.buildProfileExportFilename
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.ColorWheelSubPageContent
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdConstants
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalFirstContentRequester
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.launchUrlOnPrimaryDisplay
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import com.stormpanda.megingiard.viewmodel.SteamGridDbTestStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

private const val TAG = "GlobalSettingsScreen"

private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L
private const val GS_RESTORE_CONFIRM_TIMEOUT_MS = 8_000L

private const val GS_OBTAINIUM_REPO_URL = "https://github.com/stormpanda/megingiard"
private const val GS_OBTAINIUM_FALLBACK_URL = "https://github.com/ImranR98/Obtainium"

private val GS_ACCENT_PALETTE_PRESETS =
    listOf(
        Color(0xFFFF5252), // Red
        Color(0xFFFF7043), // Deep Orange
        Color(0xFFFFA726), // Orange
        Color(0xFFFFCA28), // Amber
        Color(0xFF66BB6A), // Green
        Color(0xFF26A69A), // Teal
        Color(0xFF29B6F6), // Light Blue
        Color(0xFF42A5F5), // Blue
        Color(0xFF7E57C2), // Deep Purple
        Color(0xFFEC407A), // Pink
    )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsState()
    val accentColor = Color(accentColorArgb)
    val customAccentColorArgb by viewModel.customAccentColor.collectAsState()
    val customAccentColor = Color(customAccentColorArgb)
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val overlayFadeOut by viewModel.overlayFadeOut.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val excludeFromRecents by viewModel.excludeFromRecents.collectAsState()
    val gamepadSwapFaceButtons by viewModel.gamepadSwapFaceButtons.collectAsState()
    val privdState by viewModel.privdState.collectAsState()
    val deadzoneLeft by viewModel.privdDeadzoneLeft.collectAsState()
    val deadzoneRight by viewModel.privdDeadzoneRight.collectAsState()
    val steamGridDbApiToken by viewModel.steamGridDbApiToken.collectAsState()
    val steamGridDbTestStatus by viewModel.steamGridDbTestStatus.collectAsState()
    val internalBackups by viewModel.internalBackups.collectAsState()

    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val latestReleaseInfo by viewModel.latestReleaseInfo.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val lastUpdateCheckTime by viewModel.lastUpdateCheckTime.collectAsState()
    val updateCheckError by viewModel.updateCheckError.collectAsState()

    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    val exportResult by ConfigManager.exportResult.collectAsState()
    val logReportSaveResult by LogReportManager.saveResult.collectAsState()

    val context = LocalContext.current
    var subPageStack by rememberSaveable { mutableStateOf<List<SettingsSubPage>>(emptyList()) }
    val currentSubPage = subPageStack.lastOrNull()
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    val pendingInAppParsedImport by ConfigManager.pendingInAppParsedImport.collectAsState()
    val configImportError by ConfigManager.inAppImportError.collectAsState()
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
    val pendingInAppImportMode by ConfigManager.pendingInAppImportMode.collectAsState()
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
    val activePrimaryModal by AppStateManager.activePrimaryModal.collectAsState()

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
                    val isBackTransition = targetState.size < initialState.size
                    if (isBackTransition) {
                        slideInHorizontally { width -> -width }.togetherWith(
                            slideOutHorizontally { width -> width },
                        )
                    } else {
                        slideInHorizontally { width -> width }.togetherWith(
                            slideOutHorizontally { width -> -width },
                        )
                    }
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
                                    if (updateAvailable && latestReleaseInfo != null) {
                                        GamepadActionCard(
                                            title =
                                                stringResource(
                                                    R.string.settings_update_available_banner,
                                                    latestReleaseInfo?.tagName ?: "",
                                                ),
                                            description = stringResource(R.string.settings_update_available_banner_desc),
                                            icon = Icons.Rounded.SystemUpdate,
                                            onClick = { subPageStack = listOf(SettingsSubPage.UPDATE_AVAILABLE) },
                                            modifier = Modifier.firstDeckItem(),
                                        )
                                    }

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_start_welcome_tour),
                                        description = stringResource(R.string.settings_start_welcome_tour_desc),
                                        icon = Icons.Rounded.PlayCircle,
                                        onClick = {
                                            AppStateManager.closeActiveModal()
                                            OnboardingWizardManager.startWizard(force = true)
                                            onBack()
                                        },
                                        modifier = Modifier.firstDeckItem(isFirst = !updateAvailable || latestReleaseInfo == null),
                                    )

                                    val isPrivdRunning = privdState == PrivdState.RUNNING
                                    GamepadActionCard(
                                        title = stringResource(R.string.privd_title),
                                        description = stringResource(R.string.help_settings_privd_desc),
                                        icon = Icons.Rounded.Security,
                                        trailingContent = {
                                            GamepadPill(
                                                text =
                                                    if (isPrivdRunning) {
                                                        stringResource(
                                                            R.string.privd_status_running_version,
                                                            PrivdConstants.PRIVD_VERSION,
                                                        )
                                                    } else {
                                                        stringResource(R.string.gamepad_toggle_off)
                                                    },
                                                isAccent = isPrivdRunning,
                                            )
                                        },
                                        onClick = {
                                            AppStateManager.closeActiveModal()
                                            AppStateManager.setPrivdSetupWizardOpen(true)
                                            onBack()
                                        },
                                    )

                                    GamepadChoiceCard(
                                        title = stringResource(R.string.settings_language),
                                        description = stringResource(R.string.help_settings_language_desc),
                                        selectedText = stringResource(appLanguage.displayNameResId()),
                                        icon = Icons.Rounded.Language,
                                        onPrevious = {
                                            viewModel.setAppLanguage(
                                                AppLanguage.entries.cycle(appLanguage, BumperDirection.PREV),
                                            )
                                        },
                                        onNext = { viewModel.setAppLanguage(AppLanguage.entries.cycle(appLanguage, BumperDirection.NEXT)) },
                                    )

                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_exclude_from_recents),
                                        description = stringResource(R.string.settings_exclude_from_recents_desc),
                                        checked = excludeFromRecents,
                                        icon = Icons.Rounded.VisibilityOff,
                                        onCheckedChange = { viewModel.setExcludeFromRecents(it) },
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_reset_tutorials),
                                        description = stringResource(R.string.settings_reset_tutorials_desc),
                                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                                        onClick = {
                                            viewModel.resetAllTutorials()
                                            DialogToastManager.show(
                                                context.getString(R.string.settings_reset_tutorials_toast),
                                            )
                                        },
                                    )
                                }

                                SettingsCategory.INPUT -> {
                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_gamepad_swap_face_buttons),
                                        description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                                        checked = gamepadSwapFaceButtons,
                                        icon = Icons.Rounded.SwapHoriz,
                                        onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                                        modifier = Modifier.firstDeckItem(),
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.privd_deadzone_title),
                                        description = stringResource(R.string.help_settings_deadzone_desc),
                                        actionText =
                                            stringResource(
                                                R.string.privd_deadzone_summary,
                                                (deadzoneLeft * 100f).roundToInt(),
                                                (deadzoneRight * 100f).roundToInt(),
                                            ),
                                        icon = Icons.Rounded.Games,
                                        onClick = { subPageStack = listOf(SettingsSubPage.DEADZONES) },
                                    )
                                }

                                SettingsCategory.APPEARANCE -> {
                                    GamepadChoiceCard(
                                        title = stringResource(R.string.settings_theme),
                                        description = stringResource(R.string.help_settings_theme_desc),
                                        selectedText = stringResource(themeMode.displayNameResId()),
                                        icon = Icons.Rounded.Palette,
                                        onPrevious = { viewModel.setThemeMode(ThemeMode.entries.cycle(themeMode, BumperDirection.PREV)) },
                                        onNext = { viewModel.setThemeMode(ThemeMode.entries.cycle(themeMode, BumperDirection.NEXT)) },
                                        modifier = Modifier.firstDeckItem(),
                                    )

                                    if (themeMode.supportsCustomAccent) {
                                        val isCustomAccent =
                                            accentColorArgb == customAccentColorArgb && accentColor !in GS_ACCENT_PALETTE_PRESETS

                                        GamepadColorPaletteCard(
                                            title = stringResource(R.string.settings_accent_color),
                                            description = stringResource(R.string.settings_accent_color_desc),
                                            icon = Icons.Rounded.FormatColorFill,
                                            paletteColors = GS_ACCENT_PALETTE_PRESETS,
                                            selectedColor = accentColor,
                                            onColorSelected = { viewModel.setAccentColor(it.toArgb()) },
                                        )

                                        GamepadActionCard(
                                            title = stringResource(R.string.settings_accent_custom_title),
                                            description = stringResource(R.string.settings_accent_custom_desc),
                                            icon = Icons.Rounded.Colorize,
                                            actionLeadingContent = {
                                                GamepadColorSwatch(
                                                    color = customAccentColor,
                                                    isSelected = isCustomAccent,
                                                )
                                            },
                                            onClick = { subPageStack = listOf(SettingsSubPage.CUSTOM_ACCENT) },
                                        )
                                    }

                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_overlay_position),
                                        description = stringResource(R.string.help_settings_overlay_position_desc),
                                        checked = overlayAtBottom,
                                        icon = Icons.Rounded.VerticalAlignBottom,
                                        onCheckedChange = { viewModel.setOverlayAtBottom(it) },
                                    )

                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_overlay_fade_out),
                                        description = stringResource(R.string.settings_overlay_fade_out_desc),
                                        checked = overlayFadeOut,
                                        icon = Icons.Rounded.Animation,
                                        onCheckedChange = { viewModel.setOverlayFadeOut(it) },
                                    )
                                }

                                SettingsCategory.CONFIGURATION -> {
                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_config_export),
                                        description = stringResource(R.string.help_settings_export_desc),
                                        icon = Icons.Rounded.FileDownload,
                                        onClick = { subPageStack = listOf(SettingsSubPage.CREATE_BACKUP) },
                                        modifier = Modifier.firstDeckItem(),
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_config_import),
                                        description = stringResource(R.string.settings_config_import_card_desc),
                                        icon = Icons.Rounded.FileUpload,
                                        onClick = { subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP) },
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_config_export_profile),
                                        description = stringResource(R.string.help_settings_export_profile_desc),
                                        icon = Icons.Rounded.Share,
                                        onClick = { subPageStack = listOf(SettingsSubPage.SHARE_PROFILE) },
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_config_import_profile),
                                        description = stringResource(R.string.help_settings_import_profile_desc),
                                        icon = Icons.Rounded.FileUpload,
                                        onClick = {
                                            ConfigManager.requestImport(ConfigManager.ImportMode.PROFILE_SHARE)
                                        },
                                    )

                                    val deleteBadgeText =
                                        when {
                                            deleteCountdown > 0 -> {
                                                stringResource(
                                                    R.string.settings_restore_defaults_countdown,
                                                    deleteCountdown,
                                                )
                                            }

                                            deleteCountdown == 0 -> {
                                                stringResource(R.string.gamepad_action_confirm)
                                            }

                                            else -> {
                                                stringResource(R.string.gamepad_action_delete)
                                            }
                                        }

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_restore_defaults),
                                        description = stringResource(R.string.settings_restore_defaults_desc),
                                        actionLeadingContent = {
                                            GamepadPill(
                                                text = deleteBadgeText,
                                                isDestructive = true,
                                                isAccent = deleteCountdown == 0,
                                                isHighlighted = deleteCountdown >= 0,
                                            )
                                        },
                                        isDestructive = true,
                                        icon = Icons.Rounded.Delete,
                                        onClick = {
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
                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_steamgriddb_token),
                                        description =
                                            if (steamGridDbApiToken.isNotBlank()) {
                                                stringResource(R.string.settings_steamgriddb_token_configured, steamGridDbApiToken.take(6))
                                            } else {
                                                stringResource(R.string.settings_steamgriddb_token_desc)
                                            },
                                        icon = Icons.Rounded.Key,
                                        onClick = { subPageStack = listOf(SettingsSubPage.STEAMGRIDDB_TOKEN) },
                                        modifier = Modifier.firstDeckItem(),
                                    )
                                }

                                SettingsCategory.UPDATES -> {
                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_auto_update_check),
                                        description = stringResource(R.string.help_settings_auto_update_desc),
                                        checked = autoUpdateCheckEnabled,
                                        icon = Icons.Rounded.Update,
                                        onCheckedChange = { viewModel.setAutoUpdateCheckEnabled(it) },
                                        modifier = Modifier.firstDeckItem(),
                                    )

                                    var hasTriggeredManualCheck by rememberSaveable(selectedCategory) {
                                        mutableStateOf(false)
                                    }

                                    val updateBadgeText =
                                        when {
                                            !hasTriggeredManualCheck -> null
                                            isCheckingUpdates -> stringResource(R.string.gamepad_action_checking)
                                            updateAvailable -> stringResource(R.string.settings_update_now_btn)
                                            updateCheckError != null -> stringResource(R.string.settings_check_failed)
                                            else -> stringResource(R.string.settings_up_to_date)
                                        }

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_check_for_updates),
                                        description =
                                            if (hasTriggeredManualCheck && updateAvailable) {
                                                stringResource(R.string.settings_update_available_tag, latestReleaseInfo?.tagName ?: "")
                                            } else {
                                                stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME)
                                            },
                                        icon = Icons.Rounded.Refresh,
                                        actionLeadingContent =
                                            updateBadgeText?.let { text ->
                                                {
                                                    GamepadPill(
                                                        text = text,
                                                        isAccent = updateAvailable,
                                                        isHighlighted = isCheckingUpdates,
                                                        isDestructive = updateCheckError != null,
                                                    )
                                                }
                                            },
                                        onClick = {
                                            if (isCheckingUpdates) return@GamepadActionCard

                                            if (hasTriggeredManualCheck && updateAvailable) {
                                                subPageStack = listOf(SettingsSubPage.UPDATE_AVAILABLE)
                                            } else {
                                                hasTriggeredManualCheck = true
                                                viewModel.checkForUpdatesManually()
                                            }
                                        },
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_add_to_obtainium),
                                        description = stringResource(R.string.help_settings_add_to_obtainium_desc),
                                        icon = Icons.Rounded.Download,
                                        onClick = {
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
                                    GamepadChoiceCard(
                                        title = stringResource(R.string.settings_log_level),
                                        description = stringResource(R.string.help_settings_log_level_desc),
                                        selectedText = logLevel.name,
                                        icon = Icons.Rounded.BugReport,
                                        onPrevious = { viewModel.setLogLevel(AppLog.Level.entries.cycle(logLevel, BumperDirection.PREV)) },
                                        onNext = { viewModel.setLogLevel(AppLog.Level.entries.cycle(logLevel, BumperDirection.NEXT)) },
                                        modifier = Modifier.firstDeckItem(),
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_save_log_report),
                                        description = stringResource(R.string.help_settings_save_log_desc),
                                        icon = Icons.Rounded.SaveAlt,
                                        onClick = { viewModel.requestSaveLogReport() },
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
                                initialColor = customAccentColor,
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

@Composable
private fun SteamGridDbTokenSubPage(
    token: String,
    onTokenChange: (String) -> Unit,
    testStatus: SteamGridDbTestStatus,
    onTestConnection: () -> Unit,
) {
    GamepadTextFieldCard(
        title = stringResource(R.string.settings_steamgriddb_token),
        description = stringResource(R.string.settings_steamgriddb_token_desc),
        placeholder = stringResource(R.string.settings_steamgriddb_token_placeholder),
        value = token,
        onValueChange = onTokenChange,
        icon = Icons.Rounded.Key,
        modifier = Modifier.firstDeckItem(),
    )

    val isDestructive =
        testStatus != SteamGridDbTestStatus.IDLE &&
            testStatus != SteamGridDbTestStatus.TESTING &&
            testStatus != SteamGridDbTestStatus.CONNECTED

    GamepadActionCard(
        title = stringResource(R.string.settings_steamgriddb_test_title),
        description = stringResource(R.string.settings_steamgriddb_test_desc),
        icon = Icons.Rounded.Sensors,
        actionLeadingContent = {
            GamepadPill(
                text = stringResource(testStatus.labelResId),
                isAccent = testStatus == SteamGridDbTestStatus.CONNECTED,
                isHighlighted = testStatus == SteamGridDbTestStatus.TESTING,
                isDestructive = isDestructive,
            )
        },
        enabled = true,
        onClick = {
            if (token.isNotBlank() && testStatus != SteamGridDbTestStatus.TESTING) {
                onTestConnection()
            }
        },
    )
}

@Composable
private fun DeadzonesSubPage(
    deadzoneLeft: Float,
    deadzoneRight: Float,
    onLeftChange: (Float) -> Unit,
    onRightChange: (Float) -> Unit,
) {
    GamepadSliderCard(
        title = stringResource(R.string.privd_deadzone_left),
        description = stringResource(R.string.help_settings_deadzone_desc),
        value = deadzoneLeft,
        valueRange = 0f..0.50f,
        step = 0.01f,
        icon = Icons.Rounded.NearMe,
        valueLabel = "${(deadzoneLeft * 100f).roundToInt()}%",
        onValueChange = onLeftChange,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSliderCard(
        title = stringResource(R.string.privd_deadzone_right),
        description = stringResource(R.string.help_settings_deadzone_desc),
        value = deadzoneRight,
        valueRange = 0f..0.50f,
        step = 0.01f,
        icon = Icons.Rounded.NearMe,
        valueLabel = "${(deadzoneRight * 100f).roundToInt()}%",
        onValueChange = onRightChange,
    )
}

@Composable
private fun ExportMetadataForm(
    author: String,
    onAuthorChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    includeBackgrounds: Boolean,
    onIncludeBackgroundsChange: (Boolean) -> Unit,
    isFirstDeckItem: Boolean = false,
) {
    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_author),
        description = stringResource(R.string.config_export_author_desc),
        placeholder = stringResource(R.string.config_export_author),
        value = author,
        onValueChange = onAuthorChange,
        icon = Icons.Rounded.Person,
        modifier = Modifier.firstDeckItem(isFirst = isFirstDeckItem),
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_description),
        description = stringResource(R.string.config_export_description_desc),
        placeholder = stringResource(R.string.config_export_description),
        value = description,
        onValueChange = onDescriptionChange,
        icon = Icons.AutoMirrored.Rounded.Notes,
    )

    GamepadToggleCard(
        title = stringResource(R.string.config_export_include_backgrounds),
        description = stringResource(R.string.config_export_include_backgrounds_desc),
        checked = includeBackgrounds,
        onCheckedChange = onIncludeBackgroundsChange,
        icon = Icons.Rounded.Wallpaper,
    )
}

private fun buildExportMetadata(
    context: Context,
    author: String,
    description: String,
): ExportMetadata =
    ConfigManager.defaultMetadata(context).copy(
        author = author.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
    )

@Composable
private fun CreateBackupSubPage(onExport: (ExportMetadata, Boolean) -> Unit) {
    val context = LocalContext.current
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    ExportMetadataForm(
        author = author,
        onAuthorChange = { author = it },
        description = description,
        onDescriptionChange = { description = it },
        includeBackgrounds = includeBackgrounds,
        onIncludeBackgroundsChange = { includeBackgrounds = it },
        isFirstDeckItem = true,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.help_settings_export_desc),
        icon = Icons.Rounded.FileDownload,
        onClick = {
            onExport(buildExportMetadata(context, author, description), includeBackgrounds)
        },
    )
}

@Composable
private fun ShareProfileSubPage(onExportProfile: (ExportMetadata, PadProfile, Boolean) -> Unit) {
    val context = LocalContext.current
    val rawProfiles by MacroPadState.profiles.collectAsState()
    val profiles = remember(rawProfiles) { rawProfiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    var selectedProfile by remember(profiles, activeProfile) {
        mutableStateOf(profiles.firstOrNull { it.id == activeProfile?.id } ?: activeProfile ?: profiles.firstOrNull())
    }
    val currentProfile = selectedProfile ?: activeProfile ?: profiles.firstOrNull()

    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    if (profiles.size > 1) {
        GamepadChoiceCard(
            title = stringResource(R.string.config_profile_export_select),
            description = stringResource(R.string.config_profile_export_select_desc),
            selectedText = currentProfile?.name ?: "",
            onPrevious = { currentProfile?.let { selectedProfile = profiles.cycle(it, BumperDirection.PREV) } },
            onNext = { currentProfile?.let { selectedProfile = profiles.cycle(it, BumperDirection.NEXT) } },
            icon = Icons.Rounded.SportsEsports,
            modifier = Modifier.firstDeckItem(),
        )
    }

    ExportMetadataForm(
        author = author,
        onAuthorChange = { author = it },
        description = description,
        onDescriptionChange = { description = it },
        includeBackgrounds = includeBackgrounds,
        onIncludeBackgroundsChange = { includeBackgrounds = it },
        isFirstDeckItem = profiles.size <= 1,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export_profile),
        description = stringResource(R.string.help_settings_export_profile_desc),
        icon = Icons.Rounded.Share,
        enabled = currentProfile != null,
        onClick = {
            val targetProfile = currentProfile ?: return@GamepadActionCard
            onExportProfile(buildExportMetadata(context, author, description), targetProfile, includeBackgrounds)
        },
    )
}

@Composable
private fun CustomAccentSubPage(
    initialColor: Color,
    onSaveColor: (Color) -> Unit,
) {
    ColorWheelSubPageContent(
        initialColor = initialColor,
        showAlphaSlider = false,
        onSaveColor = onSaveColor,
    )
}

@Composable
private fun RestoreBackupSubPage(
    internalBackups: List<InternalBackup>,
    effectiveAccent: Color,
    onPickExternalFile: () -> Unit,
    onSelectInternalBackup: (InternalBackup) -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.config_restore_option_external),
        description = stringResource(R.string.config_restore_option_external_sub),
        icon = Icons.Rounded.FileDownload,
        onClick = onPickExternalFile,
        modifier = Modifier.firstDeckItem(),
    )

    if (internalBackups.isNotEmpty()) {
        GamepadSectionHeader(
            text = stringResource(R.string.config_restore_automatic_backups),
            color = effectiveAccent,
        )

        internalBackups.forEach { backup ->
            val profilesCount = backup.export.profiles.size
            val layoutsCount = backup.export.profiles.sumOf { it.layouts.size }
            val macrosCount = backup.export.profiles.sumOf { it.macros.size }

            val subtitle =
                stringResource(
                    R.string.config_restore_option_internal_sub,
                    profilesCount,
                    layoutsCount,
                    macrosCount,
                )

            val formattedTime =
                remember(backup.timestampMs) {
                    val instant = Instant.ofEpochMilli(backup.timestampMs)
                    val dateTime = instant.atZone(ZoneId.systemDefault())
                    val formatter =
                        DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm", Locale.getDefault())
                    dateTime.format(formatter)
                }

            GamepadActionCard(
                title = formattedTime,
                description = subtitle,
                icon = Icons.Rounded.Restore,
                onClick = { onSelectInternalBackup(backup) },
            )
        }
    }
}

@Composable
private fun RestoreReviewSubPage(
    export: MegingiardExport,
    pendingInAppImportMode: ConfigManager.ImportMode,
    onConfirmImport: (MegingiardExport, ConfigManager.ImportMode) -> Unit,
) {
    var isDetailsExpanded by rememberSaveable(export) { mutableStateOf(false) }
    val colors = LocalAppColors.current

    val metadata = export.metadata
    val authorText = metadata.author?.ifBlank { null }
    val descText = metadata.description?.ifBlank { null }
    val tagsText = metadata.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val profilesCount = export.profiles.size
    val layoutsCount = export.profiles.sumOf { it.layouts.size }
    val macrosCount = export.profiles.sumOf { it.macros.size }
    val imageCount =
        ConfigManager.getPendingInAppImageCount().takeIf { it > 0 }
            ?: export.profiles.sumOf { p -> p.layouts.count { !it.backgroundImagePath.isNullOrEmpty() } }

    val includedSections =
        listOf(
            "global" to R.string.config_import_section_global,
            "mirror" to R.string.config_import_section_mirror,
            "touchpad" to R.string.config_import_section_touchpad,
            "keyboard" to R.string.config_import_section_keyboard,
            "macropad_settings" to R.string.config_import_section_macropad_settings,
        ).filter { it.first in export.settings }.map { stringResource(it.second) }

    val summarySubtitle =
        buildString {
            append(
                "$profilesCount ${if (profilesCount == 1) "profile" else "profiles"} • $layoutsCount ${if (layoutsCount == 1) "layout" else "layouts"} • $macrosCount ${if (macrosCount == 1) "macro" else "macros"}",
            )
            if (imageCount > 0) {
                append(" • $imageCount background${if (imageCount == 1) "" else "s"}")
            }
        }

    GamepadFocusCard(
        onClick = { isDetailsExpanded = !isDetailsExpanded },
        modifier = Modifier.firstDeckItem().animateContentSize(),
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GamepadCardRow(
                title =
                    authorText?.let { stringResource(R.string.config_import_meta_author, it) }
                        ?: stringResource(R.string.config_import_archive_title),
                description = summarySubtitle,
                icon = Icons.Rounded.Inventory2,
                isFocused = isFocused,
                trailingContent = {
                    GamepadPill(
                        text =
                            if (isDetailsExpanded) {
                                stringResource(R.string.config_import_hide_details)
                            } else {
                                stringResource(R.string.config_import_show_details)
                            },
                    )
                },
            )

            if (isDetailsExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!descText.isNullOrBlank()) {
                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                        )
                    }
                    if (!tagsText.isNullOrBlank()) {
                        Text(
                            text = "${stringResource(R.string.config_import_tags_label)}: $tagsText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (metadata.exportedAt.isNotBlank()) {
                        Text(
                            text = metadata.exportedAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (includedSections.isNotEmpty() && pendingInAppImportMode == ConfigManager.ImportMode.BACKUP_RESTORE) {
                        Text(
                            text = "${stringResource(R.string.config_import_sections_label)}: ${includedSections.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }

    val warningText =
        when (pendingInAppImportMode) {
            ConfigManager.ImportMode.BACKUP_RESTORE -> stringResource(R.string.config_import_warning_backup)
            ConfigManager.ImportMode.PROFILE_SHARE -> stringResource(R.string.config_import_warning_profile)
        }

    GamepadActionCard(
        title = stringResource(R.string.config_import_confirm),
        description = warningText,
        icon = Icons.Rounded.Restore,
        isDestructive = true,
        onClick = {
            onConfirmImport(export, pendingInAppImportMode)
        },
    )
}

@Composable
private fun UpdateAvailableSubPage(
    tagName: String,
    effectiveAccent: Color,
    onBackupAndOpen: () -> Unit,
    onOpenDirectly: () -> Unit,
) {
    GamepadInfoBox(
        text = stringResource(R.string.update_dialog_title, tagName),
        description = stringResource(R.string.update_dialog_message, tagName),
        icon = Icons.Rounded.SystemUpdate,
        iconTint = effectiveAccent,
    )

    GamepadActionCard(
        title = stringResource(R.string.update_dialog_btn_backup_and_open),
        description = stringResource(R.string.update_dialog_backup_and_open_desc),
        icon = Icons.Rounded.SaveAlt,
        onClick = onBackupAndOpen,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.update_dialog_btn_open_directly),
        description = stringResource(R.string.update_dialog_open_directly_desc),
        icon = Icons.Rounded.OpenInBrowser,
        onClick = onOpenDirectly,
    )
}
