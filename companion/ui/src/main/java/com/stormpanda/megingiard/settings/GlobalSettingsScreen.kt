package com.stormpanda.megingiard.settings

import android.view.KeyEvent
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalFirstContentRequester
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
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

/**
 * Sub-pages that can be drilled into within [GlobalSettingsScreen].
 */
internal enum class SettingsSubPage(
    val parentCategory: SettingsCategory,
) {
    DEADZONES(SettingsCategory.INPUT),
    STEAMGRIDDB_TOKEN(SettingsCategory.GENERAL),
    CUSTOM_ACCENT(SettingsCategory.APPEARANCE),
    CREATE_BACKUP(SettingsCategory.CONFIGURATION),
    SHARE_PROFILE(SettingsCategory.CONFIGURATION),
    RESTORE_BACKUP(SettingsCategory.CONFIGURATION),
    RESTORE_REVIEW(SettingsCategory.CONFIGURATION),
}

private const val GS_SUBPAGE_FOCUS_DELAY_MS = 50L
private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L

private const val GS_OBTAINIUM_REPO_URL = "https://github.com/stormpanda/megingiard"
private const val GS_OBTAINIUM_FALLBACK_URL = "https://github.com/ImranR98/Obtainium"

private val ACCENT_PALETTE_PRESETS =
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

    var showUpdatePromptDialog by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(subPageStack) {
        if (SettingsSubPage.RESTORE_REVIEW !in subPageStack) {
            showImportPreviewDialog = null
            ConfigManager.clearInAppPendingImport()
        }
    }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importSuccess by rememberSaveable { mutableStateOf(false) }
    var profileImportSuccess by rememberSaveable { mutableStateOf(false) }
    val pendingInAppImportMode by ConfigManager.pendingInAppImportMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showRestoreDefaultsConfirm by rememberSaveable { mutableStateOf(false) }
    var restoreCountdown by rememberSaveable { mutableStateOf(GS_RESTORE_COUNTDOWN_SECONDS) }
    LaunchedEffect(showRestoreDefaultsConfirm) {
        if (showRestoreDefaultsConfirm) {
            restoreCountdown = GS_RESTORE_COUNTDOWN_SECONDS
            while (restoreCountdown > 0) {
                delay(GS_RESTORE_COUNTDOWN_INTERVAL_MS)
                restoreCountdown--
            }
        }
    }

    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }

    val categoryList = remember { SettingsCategory.entries }

    LaunchedEffect(pendingInAppParsedImport) {
        if (pendingInAppParsedImport != null) {
            selectedCategory = SettingsCategory.CONFIGURATION
            subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP, SettingsSubPage.RESTORE_REVIEW)
        }
    }

    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            val currentIndex = categoryList.indexOf(selectedCategory).coerceAtLeast(0)
            val nextIndex =
                when (direction) {
                    BumperDirection.PREV -> (currentIndex - 1 + categoryList.size) % categoryList.size
                    BumperDirection.NEXT -> (currentIndex + 1) % categoryList.size
                }
            selectedCategory = categoryList[nextIndex]
            subPageStack = emptyList()
        }
    }

    GamepadTwoPaneScaffold(
        isCustomBackActive = subPageStack.isNotEmpty(),
        onCustomBack = {
            subPageStack = subPageStack.dropLast(1)
        },
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
            val firstContentRequester = LocalFirstContentRequester.current
            val inputModeManager = LocalInputModeManager.current
            var wasInSubPage by remember { mutableStateOf(false) }

            LaunchedEffect(subPageStack) {
                if (subPageStack.isNotEmpty()) {
                    wasInSubPage = true
                    delay(GS_SUBPAGE_FOCUS_DELAY_MS)
                    try {
                        inputModeManager?.requestInputMode(InputMode.Keyboard)
                        firstContentRequester?.requestFocus()
                    } catch (_: IllegalStateException) {
                        // Requester unattached
                    }
                } else if (wasInSubPage) {
                    wasInSubPage = false
                    delay(GS_SUBPAGE_FOCUS_DELAY_MS)
                    try {
                        inputModeManager?.requestInputMode(InputMode.Keyboard)
                        firstContentRequester?.requestFocus()
                    } catch (_: IllegalStateException) {
                        // Requester unattached
                    }
                }
            }

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
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (subPage) {
                        null -> {
                            // GENERAL
                            if (selectedCategory == SettingsCategory.GENERAL) {
                                if (updateAvailable && latestReleaseInfo != null) {
                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_update_available_banner, latestReleaseInfo?.tagName ?: ""),
                                        description = stringResource(R.string.settings_update_available_banner_desc),
                                        actionText = stringResource(R.string.settings_update_now_btn),
                                        icon = Icons.Rounded.SystemUpdate,
                                        onClick = { showUpdatePromptDialog = true },
                                        modifier = Modifier.firstDeckItem(),
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.settings_section_general).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_start_welcome_tour),
                                    description = stringResource(R.string.settings_start_welcome_tour_desc),
                                    actionText = stringResource(R.string.settings_start_welcome_tour_btn),
                                    icon = Icons.Rounded.PlayCircle,
                                    onClick = {
                                        AppStateManager.closeActiveModal()
                                        OnboardingWizardManager.startWizard(force = true)
                                        onBack()
                                    },
                                    modifier = Modifier.firstDeckItem(isFirst = !updateAvailable || latestReleaseInfo == null),
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.privd_title),
                                    description = stringResource(R.string.help_settings_privd_desc),
                                    actionText = stringResource(R.string.gamepad_action_setup),
                                    icon = Icons.Rounded.Security,
                                    onClick = {
                                        AppStateManager.closeActiveModal()
                                        AppStateManager.setPrivdSetupWizardOpen(true)
                                        onBack()
                                    },
                                )

                                val allLangs = remember { AppLanguage.entries }
                                val currentLangIdx = allLangs.indexOf(appLanguage)
                                val currentLangName =
                                    when (appLanguage) {
                                        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                                        AppLanguage.EN -> stringResource(R.string.settings_language_en)
                                        AppLanguage.DE -> stringResource(R.string.settings_language_de)
                                        AppLanguage.ZH_TW -> stringResource(R.string.settings_language_zh_tw)
                                    }

                                GamepadChoiceCard(
                                    title = stringResource(R.string.settings_language),
                                    description = stringResource(R.string.help_settings_language_desc),
                                    selectedText = currentLangName,
                                    icon = Icons.Rounded.Language,
                                    onPrevious = {
                                        viewModel.setAppLanguage(
                                            allLangs[(currentLangIdx - 1 + allLangs.size) % allLangs.size],
                                        )
                                    },
                                    onNext = { viewModel.setAppLanguage(allLangs[(currentLangIdx + 1) % allLangs.size]) },
                                )

                                GamepadToggleCard(
                                    title = stringResource(R.string.settings_exclude_from_recents),
                                    description = stringResource(R.string.settings_exclude_from_recents_desc),
                                    checked = excludeFromRecents,
                                    icon = Icons.Rounded.VisibilityOff,
                                    onCheckedChange = { viewModel.setExcludeFromRecents(it) },
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_steamgriddb_token),
                                    description =
                                        if (steamGridDbApiToken.isNotBlank()) {
                                            stringResource(R.string.settings_steamgriddb_token_configured, steamGridDbApiToken.take(6))
                                        } else {
                                            stringResource(R.string.settings_steamgriddb_token_desc)
                                        },
                                    actionText = stringResource(R.string.gamepad_action_edit),
                                    icon = Icons.Rounded.Key,
                                    onClick = { subPageStack = listOf(SettingsSubPage.STEAMGRIDDB_TOKEN) },
                                )
                            }

                            // INPUT
                            if (selectedCategory == SettingsCategory.INPUT) {
                                Text(
                                    text = stringResource(R.string.settings_section_input).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                GamepadToggleCard(
                                    title = stringResource(R.string.settings_gamepad_swap_face_buttons),
                                    description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                                    checked = gamepadSwapFaceButtons,
                                    icon = Icons.Rounded.SwapHoriz,
                                    onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                                    modifier = Modifier.firstDeckItem(isFirst = selectedCategory == SettingsCategory.INPUT),
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.privd_deadzone_title),
                                    description =
                                        stringResource(
                                            R.string.privd_deadzone_summary,
                                            (deadzoneLeft * 100f).roundToInt(),
                                            (deadzoneRight * 100f).roundToInt(),
                                        ),
                                    actionText = stringResource(R.string.gamepad_action_deadzones),
                                    icon = Icons.Rounded.Games,
                                    onClick = { subPageStack = listOf(SettingsSubPage.DEADZONES) },
                                )
                            }

                            // APPEARANCE
                            if (selectedCategory == SettingsCategory.APPEARANCE) {
                                Text(
                                    text = stringResource(R.string.settings_section_appearance).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                val allThemes = remember { ThemeMode.entries }
                                val currentThemeIdx = allThemes.indexOf(themeMode)
                                val themeDisplayName = stringResource(themeMode.displayNameResId())

                                GamepadChoiceCard(
                                    title = stringResource(R.string.settings_theme),
                                    description = stringResource(R.string.help_settings_theme_desc),
                                    selectedText = themeDisplayName,
                                    icon = Icons.Rounded.Palette,
                                    onPrevious = {
                                        viewModel.setThemeMode(
                                            allThemes[(currentThemeIdx - 1 + allThemes.size) % allThemes.size],
                                        )
                                    },
                                    onNext = { viewModel.setThemeMode(allThemes[(currentThemeIdx + 1) % allThemes.size]) },
                                    modifier = Modifier.firstDeckItem(isFirst = selectedCategory == SettingsCategory.APPEARANCE),
                                )

                                if (themeMode.supportsCustomAccent) {
                                    val isCustomAccent = accentColorArgb == customAccentColorArgb && accentColor !in ACCENT_PALETTE_PRESETS

                                    GamepadColorPaletteCard(
                                        title = stringResource(R.string.settings_accent_color),
                                        description = stringResource(R.string.settings_accent_color_desc),
                                        icon = Icons.Rounded.FormatColorFill,
                                        paletteColors = ACCENT_PALETTE_PRESETS,
                                        selectedColor = accentColor,
                                        onColorSelected = { viewModel.setAccentColor(it.toArgb()) },
                                    )

                                    GamepadActionCard(
                                        title = stringResource(R.string.settings_accent_custom_title),
                                        description = stringResource(R.string.settings_accent_custom_desc),
                                        actionText = stringResource(R.string.gamepad_action_edit),
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

                            // DATA
                            if (selectedCategory == SettingsCategory.DATA) {
                                Text(
                                    text = stringResource(R.string.settings_section_data).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_restore_defaults),
                                    description = stringResource(R.string.settings_restore_defaults_desc),
                                    actionText = stringResource(R.string.gamepad_action_restore),
                                    isDestructive = true,
                                    icon = Icons.Rounded.Restore,
                                    onClick = { showRestoreDefaultsConfirm = true },
                                    modifier = Modifier.firstDeckItem(isFirst = selectedCategory == SettingsCategory.DATA),
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_reset_tutorials),
                                    description = stringResource(R.string.settings_reset_tutorials_desc),
                                    actionText = stringResource(R.string.gamepad_action_reset),
                                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                                    onClick = {
                                        viewModel.resetAllTutorials()
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.settings_reset_tutorials_toast),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    },
                                )
                            }

                            // CONFIGURATION
                            if (selectedCategory == SettingsCategory.CONFIGURATION) {
                                Text(
                                    text = stringResource(R.string.settings_section_config).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_config_export),
                                    description = stringResource(R.string.help_settings_export_desc),
                                    actionText = stringResource(R.string.gamepad_action_setup),
                                    icon = Icons.Rounded.FileDownload,
                                    onClick = { subPageStack = listOf(SettingsSubPage.CREATE_BACKUP) },
                                    modifier =
                                        Modifier.firstDeckItem(
                                            isFirst = selectedCategory == SettingsCategory.CONFIGURATION,
                                        ),
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_config_import),
                                    description = stringResource(R.string.settings_config_import_card_desc),
                                    actionText = stringResource(R.string.gamepad_action_restore),
                                    icon = Icons.Rounded.FileUpload,
                                    onClick = { subPageStack = listOf(SettingsSubPage.RESTORE_BACKUP) },
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_config_export_profile),
                                    description = stringResource(R.string.help_settings_export_profile_desc),
                                    actionText = stringResource(R.string.gamepad_action_setup),
                                    icon = Icons.Rounded.Share,
                                    onClick = { subPageStack = listOf(SettingsSubPage.SHARE_PROFILE) },
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_config_import_profile),
                                    description = stringResource(R.string.help_settings_import_profile_desc),
                                    actionText = stringResource(R.string.gamepad_action_browse),
                                    icon = Icons.Rounded.FileDownload,
                                    onClick = {
                                        ConfigManager.requestImport(ConfigManager.ImportMode.PROFILE_SHARE)
                                    },
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_add_to_obtainium),
                                    description = stringResource(R.string.help_settings_add_to_obtainium_desc),
                                    actionText = stringResource(R.string.gamepad_action_add),
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

                            // UPDATES
                            if (selectedCategory == SettingsCategory.UPDATES) {
                                Text(
                                    text = stringResource(R.string.settings_section_updates).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                GamepadToggleCard(
                                    title = stringResource(R.string.settings_auto_update_check),
                                    description = stringResource(R.string.help_settings_auto_update_desc),
                                    checked = autoUpdateCheckEnabled,
                                    icon = Icons.Rounded.Update,
                                    onCheckedChange = { viewModel.setAutoUpdateCheckEnabled(it) },
                                    modifier = Modifier.firstDeckItem(isFirst = selectedCategory == SettingsCategory.UPDATES),
                                )

                                var hasTriggeredManualCheck by rememberSaveable(selectedCategory) {
                                    mutableStateOf(false)
                                }

                                val updateBadgeText: String
                                val updateBadgeAccent: Boolean
                                val updateBadgeHighlighted: Boolean
                                val updateBadgeDestructive: Boolean

                                when {
                                    !hasTriggeredManualCheck -> {
                                        updateBadgeText = stringResource(R.string.gamepad_action_check)
                                        updateBadgeAccent = false
                                        updateBadgeHighlighted = false
                                        updateBadgeDestructive = false
                                    }

                                    isCheckingUpdates -> {
                                        updateBadgeText = stringResource(R.string.gamepad_action_checking)
                                        updateBadgeAccent = false
                                        updateBadgeHighlighted = true
                                        updateBadgeDestructive = false
                                    }

                                    updateAvailable -> {
                                        updateBadgeText = stringResource(R.string.settings_update_now_btn)
                                        updateBadgeAccent = true
                                        updateBadgeHighlighted = false
                                        updateBadgeDestructive = false
                                    }

                                    updateCheckError != null -> {
                                        updateBadgeText = stringResource(R.string.settings_check_failed)
                                        updateBadgeAccent = false
                                        updateBadgeHighlighted = false
                                        updateBadgeDestructive = true
                                    }

                                    else -> {
                                        updateBadgeText = stringResource(R.string.settings_up_to_date)
                                        updateBadgeAccent = false
                                        updateBadgeHighlighted = false
                                        updateBadgeDestructive = false
                                    }
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
                                    actionLeadingContent = {
                                        GamepadPill(
                                            text = updateBadgeText,
                                            isAccent = updateBadgeAccent,
                                            isHighlighted = updateBadgeHighlighted,
                                            isDestructive = updateBadgeDestructive,
                                        )
                                    },
                                    onClick = {
                                        if (isCheckingUpdates) return@GamepadActionCard

                                        if (hasTriggeredManualCheck && updateAvailable) {
                                            showUpdatePromptDialog = true
                                        } else {
                                            hasTriggeredManualCheck = true
                                            viewModel.checkForUpdatesManually()
                                        }
                                    },
                                )
                            }

                            // DIAGNOSTICS
                            if (selectedCategory == SettingsCategory.DIAGNOSTICS) {
                                Text(
                                    text = stringResource(R.string.settings_section_diagnostics).uppercase(),
                                    color = effectiveAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                val allLogLevels = remember { AppLog.Level.entries }
                                val currentLogLevelIdx = allLogLevels.indexOf(logLevel)

                                GamepadChoiceCard(
                                    title = stringResource(R.string.settings_log_level),
                                    description = stringResource(R.string.help_settings_log_level_desc),
                                    selectedText = logLevel.name,
                                    icon = Icons.Rounded.BugReport,
                                    onPrevious = {
                                        viewModel.setLogLevel(
                                            allLogLevels[
                                                (currentLogLevelIdx - 1 + allLogLevels.size) %
                                                    allLogLevels.size,
                                            ],
                                        )
                                    },
                                    onNext = { viewModel.setLogLevel(allLogLevels[(currentLogLevelIdx + 1) % allLogLevels.size]) },
                                    modifier = Modifier.firstDeckItem(isFirst = selectedCategory == SettingsCategory.DIAGNOSTICS),
                                )

                                GamepadActionCard(
                                    title = stringResource(R.string.settings_save_log_report),
                                    description = stringResource(R.string.help_settings_save_log_desc),
                                    actionText = stringResource(R.string.gamepad_action_save),
                                    icon = Icons.Rounded.SaveAlt,
                                    onClick = { viewModel.requestSaveLogReport() },
                                )
                            }
                        }

                        SettingsSubPage.DEADZONES -> {
                            DeadzonesSubPage(
                                deadzoneLeft = deadzoneLeft,
                                deadzoneRight = deadzoneRight,
                                onLeftChange = { viewModel.setPrivdDeadzoneLeft(it) },
                                onRightChange = { viewModel.setPrivdDeadzoneRight(it) },
                                effectiveAccent = effectiveAccent,
                            )
                        }

                        SettingsSubPage.STEAMGRIDDB_TOKEN -> {
                            SteamGridDbTokenSubPage(
                                token = steamGridDbApiToken,
                                onTokenChange = { viewModel.setSteamGridDbApiToken(it) },
                                effectiveAccent = effectiveAccent,
                                testStatus = steamGridDbTestStatus,
                                onTestConnection = { viewModel.testSteamGridDbConnection(steamGridDbApiToken) },
                            )
                        }

                        SettingsSubPage.CUSTOM_ACCENT -> {
                            CustomAccentSubPage(
                                initialColor = customAccentColor,
                                effectiveAccent = effectiveAccent,
                                onSaveColor = { newColor ->
                                    val argb = newColor.toArgb()
                                    viewModel.setCustomAccentColor(argb)
                                    viewModel.setAccentColor(argb)
                                },
                            )
                        }

                        SettingsSubPage.CREATE_BACKUP -> {
                            CreateBackupSubPage(
                                effectiveAccent = effectiveAccent,
                                onExport = { metadata, includeBackgrounds ->
                                    ConfigManager.requestExport(
                                        metadata = metadata,
                                        filename = buildExportFilename(metadata),
                                        includeBackgrounds = includeBackgrounds,
                                    )
                                },
                            )
                        }

                        SettingsSubPage.SHARE_PROFILE -> {
                            ShareProfileSubPage(
                                effectiveAccent = effectiveAccent,
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

                        SettingsSubPage.RESTORE_BACKUP -> {
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

                        SettingsSubPage.RESTORE_REVIEW -> {
                            val reviewExport = activeImportPreview ?: lastReviewExport
                            if (reviewExport != null) {
                                RestoreReviewSubPage(
                                    export = reviewExport,
                                    effectiveAccent = effectiveAccent,
                                    pendingInAppImportMode = pendingInAppImportMode,
                                    onConfirmImport = { exp, mode ->
                                        showImportPreviewDialog = null
                                        ConfigManager.clearInAppPendingImport()
                                        subPageStack = emptyList()
                                        coroutineScope.launch {
                                            runCatching {
                                                when (mode) {
                                                    ConfigManager.ImportMode.BACKUP_RESTORE -> ConfigManager.applyImport(context, exp)
                                                    ConfigManager.ImportMode.PROFILE_SHARE -> ConfigManager.applyProfileImport(context, exp)
                                                }
                                            }.onSuccess {
                                                when (mode) {
                                                    ConfigManager.ImportMode.BACKUP_RESTORE -> importSuccess = true
                                                    ConfigManager.ImportMode.PROFILE_SHARE -> profileImportSuccess = true
                                                }
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
                }
            }
        },
    )
    if (showRestoreDefaultsConfirm) {
        InTreeConfirmDialog(
            title = stringResource(R.string.settings_restore_defaults),
            text = stringResource(R.string.settings_restore_defaults_confirm),
            confirmText =
                if (restoreCountdown > 0) {
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
    if (showUpdatePromptDialog) {
        val releaseUrl =
            latestReleaseInfo?.htmlUrl?.ifBlank { "https://github.com/stormpanda/megingiard/releases" }
                ?: "https://github.com/stormpanda/megingiard/releases"
        UpdatePromptDialog(
            tagName = latestReleaseInfo?.tagName ?: "",
            colors = colors,
            accentColor = effectiveAccent,
            onBackupAndOpen = {
                showUpdatePromptDialog = false
                selectedCategory = SettingsCategory.CONFIGURATION
                subPageStack = listOf(SettingsSubPage.CREATE_BACKUP)
                launchUrlOnPrimaryDisplay(context, releaseUrl)
            },
            onOpenDirectly = {
                showUpdatePromptDialog = false
                launchUrlOnPrimaryDisplay(context, releaseUrl)
            },
            onDismiss = { showUpdatePromptDialog = false },
        )
    }
    (importError ?: configImportError)?.let { error ->
        InTreeMessageDialog(
            title = stringResource(R.string.config_error_title),
            text = error,
            buttonText = stringResource(R.string.config_ok),
            colors = colors,
            accentColor = effectiveAccent,
            onDismiss = {
                importError = null
                ConfigManager.setInAppImportError(null)
            },
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

@Composable
private fun GamepadSubPageHeader(
    breadcrumbs: List<String>,
    accentColor: Color,
) {
    GamepadSectionHeader(
        text = breadcrumbs.joinToString("  ›  ") { it.uppercase() },
        color = accentColor,
    )
}

@Composable
private fun GamepadSubPageHeader(
    parentTitle: String,
    subPageTitle: String,
    accentColor: Color,
) {
    GamepadSubPageHeader(
        breadcrumbs = listOf(parentTitle, subPageTitle),
        accentColor = accentColor,
    )
}

@Composable
private fun SteamGridDbTokenSubPage(
    token: String,
    onTokenChange: (String) -> Unit,
    effectiveAccent: Color,
    testStatus: SteamGridDbTestStatus,
    onTestConnection: () -> Unit,
) {
    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_general),
        subPageTitle = stringResource(R.string.settings_steamgriddb_token),
        accentColor = effectiveAccent,
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.settings_steamgriddb_token),
        description = stringResource(R.string.settings_steamgriddb_token_desc),
        placeholder = stringResource(R.string.settings_steamgriddb_token_placeholder),
        value = token,
        onValueChange = onTokenChange,
        icon = Icons.Rounded.Key,
        modifier = Modifier.firstDeckItem(),
    )

    val statusBadge: @Composable () -> Unit =
        when (testStatus) {
            SteamGridDbTestStatus.IDLE -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_test_btn)) }
            }

            SteamGridDbTestStatus.TESTING -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_testing), isHighlighted = true) }
            }

            SteamGridDbTestStatus.CONNECTED -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_connected), isAccent = true) }
            }

            SteamGridDbTestStatus.INVALID_TOKEN -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_invalid), isDestructive = true) }
            }

            SteamGridDbTestStatus.OFFLINE -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_offline), isDestructive = true) }
            }

            SteamGridDbTestStatus.RATE_LIMITED -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_rate_limited), isDestructive = true) }
            }

            SteamGridDbTestStatus.UNREACHABLE -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_unreachable), isDestructive = true) }
            }

            SteamGridDbTestStatus.ERROR -> {
                { GamepadPill(text = stringResource(R.string.settings_steamgriddb_status_error), isDestructive = true) }
            }
        }

    GamepadActionCard(
        title = stringResource(R.string.settings_steamgriddb_test_title),
        description = stringResource(R.string.settings_steamgriddb_test_desc),
        icon = Icons.Rounded.Sensors,
        actionLeadingContent = statusBadge,
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
    effectiveAccent: Color,
) {
    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_input),
        subPageTitle = stringResource(R.string.privd_deadzone_title),
        accentColor = effectiveAccent,
    )

    GamepadSliderCard(
        title = stringResource(R.string.privd_deadzone_left),
        description = stringResource(R.string.help_settings_deadzone_desc),
        value = deadzoneLeft,
        valueRange = 0f..0.50f,
        step = 0.01f,
        icon = Icons.Rounded.Games,
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
        icon = Icons.Rounded.Games,
        valueLabel = "${(deadzoneRight * 100f).roundToInt()}%",
        onValueChange = onRightChange,
    )
}

@Composable
private fun CreateBackupSubPage(
    effectiveAccent: Color,
    onExport: (ExportMetadata, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_config),
        subPageTitle = stringResource(R.string.settings_config_export),
        accentColor = effectiveAccent,
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_author),
        description = stringResource(R.string.config_export_author_desc),
        placeholder = stringResource(R.string.config_export_author),
        value = author,
        onValueChange = { author = it },
        icon = Icons.Rounded.Person,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_description),
        description = stringResource(R.string.config_export_description_desc),
        placeholder = stringResource(R.string.config_export_description),
        value = description,
        onValueChange = { description = it },
        icon = Icons.AutoMirrored.Rounded.Notes,
    )

    GamepadToggleCard(
        title = stringResource(R.string.config_export_include_backgrounds),
        description = stringResource(R.string.config_export_include_backgrounds_desc),
        checked = includeBackgrounds,
        onCheckedChange = { includeBackgrounds = it },
        icon = Icons.Rounded.Wallpaper,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.help_settings_export_desc),
        actionText = stringResource(R.string.gamepad_action_export),
        icon = Icons.Rounded.FileDownload,
        onClick = {
            val metadata =
                ConfigManager.defaultMetadata(context).copy(
                    author = author.trim().ifEmpty { null },
                    description = description.trim().ifEmpty { null },
                )
            onExport(metadata, includeBackgrounds)
        },
    )
}

@Composable
private fun ShareProfileSubPage(
    effectiveAccent: Color,
    onExportProfile: (ExportMetadata, PadProfile, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val rawProfiles by MacroPadState.profiles.collectAsState()
    val profiles = remember(rawProfiles) { rawProfiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    var selectedProfileIdx by rememberSaveable(profiles) {
        val initialIdx = profiles.indexOfFirst { it.id == activeProfile?.id }.coerceAtLeast(0)
        mutableStateOf(initialIdx)
    }
    val currentProfile = profiles.getOrNull(selectedProfileIdx) ?: activeProfile ?: profiles.firstOrNull()

    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_config),
        subPageTitle = stringResource(R.string.settings_config_export_profile),
        accentColor = effectiveAccent,
    )

    if (profiles.size > 1) {
        GamepadChoiceCard(
            title = stringResource(R.string.config_profile_export_select),
            description = stringResource(R.string.config_profile_export_select_desc),
            selectedText = currentProfile?.name ?: "",
            onPrevious = {
                if (profiles.isNotEmpty()) {
                    selectedProfileIdx = (selectedProfileIdx - 1 + profiles.size) % profiles.size
                }
            },
            onNext = {
                if (profiles.isNotEmpty()) {
                    selectedProfileIdx = (selectedProfileIdx + 1) % profiles.size
                }
            },
            icon = Icons.Rounded.SportsEsports,
            modifier = Modifier.firstDeckItem(),
        )
    }

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_author),
        description = stringResource(R.string.config_export_author_desc),
        placeholder = stringResource(R.string.config_export_author),
        value = author,
        onValueChange = { author = it },
        icon = Icons.Rounded.Person,
        modifier = if (profiles.size <= 1) Modifier.firstDeckItem() else Modifier,
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_description),
        description = stringResource(R.string.config_export_description_desc),
        placeholder = stringResource(R.string.config_export_description),
        value = description,
        onValueChange = { description = it },
        icon = Icons.AutoMirrored.Rounded.Notes,
    )

    GamepadToggleCard(
        title = stringResource(R.string.config_export_include_backgrounds),
        description = stringResource(R.string.config_export_include_backgrounds_desc),
        checked = includeBackgrounds,
        onCheckedChange = { includeBackgrounds = it },
        icon = Icons.Rounded.Wallpaper,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export_profile),
        description = stringResource(R.string.help_settings_export_profile_desc),
        actionText = stringResource(R.string.gamepad_action_export),
        icon = Icons.Rounded.Share,
        enabled = currentProfile != null,
        onClick = {
            val targetProfile = currentProfile ?: return@GamepadActionCard
            val metadata =
                ConfigManager.defaultMetadata(context).copy(
                    author = author.trim().ifEmpty { null },
                    description = description.trim().ifEmpty { null },
                )
            onExportProfile(metadata, targetProfile, includeBackgrounds)
        },
    )
}

@Composable
private fun CustomAccentSubPage(
    initialColor: Color,
    effectiveAccent: Color,
    onSaveColor: (Color) -> Unit,
) {
    val initHsv =
        remember(initialColor) {
            FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
        }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[0]) }
    var sat by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[1]) }
    var bri by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[2]) }

    val workingColor by remember {
        derivedStateOf {
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
        }
    }

    val hueGradient =
        remember {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000),
                    ),
            )
        }

    val saturationGradient =
        remember(hue, bri) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, bri))),
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, bri))),
                    ),
            )
        }

    val brightnessGradient =
        remember(hue, sat) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color.Black,
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f))),
                    ),
            )
        }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_appearance),
        subPageTitle = stringResource(R.string.settings_accent_custom_title),
        accentColor = effectiveAccent,
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_hue),
        description = stringResource(R.string.settings_color_hue_desc),
        value = hue,
        valueRange = 0f..360f,
        onValueChange = { hue = it },
        valueLabel = "${hue.roundToInt()}°",
        step = 5f,
        trackBrush = hueGradient,
        thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
        icon = Icons.Rounded.ColorLens,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_saturation),
        description = stringResource(R.string.settings_color_saturation_desc),
        value = sat,
        valueRange = 0f..1f,
        onValueChange = { sat = it },
        valueLabel = "${(sat * 100).roundToInt()}%",
        step = 0.02f,
        trackBrush = saturationGradient,
        thumbColor = workingColor,
        icon = Icons.Rounded.Gradient,
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_brightness),
        description = stringResource(R.string.settings_color_brightness_desc),
        value = bri,
        valueRange = 0f..1f,
        onValueChange = { bri = it },
        valueLabel = "${(bri * 100).roundToInt()}%",
        step = 0.02f,
        trackBrush = brightnessGradient,
        thumbColor = workingColor,
        icon = Icons.Rounded.BrightnessMedium,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_color_save_title),
        description = stringResource(R.string.settings_color_save_desc),
        actionText = stringResource(R.string.gamepad_action_save),
        icon = Icons.Rounded.Colorize,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = workingColor,
                isSelected = false,
            )
        },
        onClick = {
            onSaveColor(workingColor)
        },
    )
}

@Composable
private fun RestoreBackupSubPage(
    internalBackups: List<InternalBackup>,
    effectiveAccent: Color,
    onPickExternalFile: () -> Unit,
    onSelectInternalBackup: (InternalBackup) -> Unit,
) {
    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.settings_section_config),
        subPageTitle = stringResource(R.string.config_restore_dialog_title),
        accentColor = effectiveAccent,
    )

    GamepadActionCard(
        title = stringResource(R.string.config_restore_option_external),
        description = stringResource(R.string.config_restore_option_external_sub),
        actionText = stringResource(R.string.gamepad_action_browse),
        icon = Icons.Rounded.FileDownload,
        onClick = onPickExternalFile,
        modifier = Modifier.firstDeckItem(),
    )

    if (internalBackups.isNotEmpty()) {
        GamepadSectionHeader(
            text = stringResource(R.string.config_restore_automatic_backups).uppercase(),
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
                actionText = stringResource(R.string.gamepad_action_restore),
                icon = Icons.Rounded.Restore,
                onClick = { onSelectInternalBackup(backup) },
            )
        }
    }
}

@Composable
private fun RestoreReviewSubPage(
    export: MegingiardExport,
    effectiveAccent: Color,
    pendingInAppImportMode: ConfigManager.ImportMode,
    onConfirmImport: (MegingiardExport, ConfigManager.ImportMode) -> Unit,
) {
    var isDetailsExpanded by rememberSaveable(export) { mutableStateOf(false) }
    val colors = LocalAppColors.current

    GamepadSubPageHeader(
        breadcrumbs =
            listOf(
                stringResource(R.string.settings_section_config),
                stringResource(R.string.config_restore_dialog_title),
                stringResource(R.string.config_import_review_title),
            ),
        accentColor = effectiveAccent,
    )

    val metadata = export.metadata
    val authorText = metadata.author?.ifBlank { null }
    val descText = metadata.description?.ifBlank { null }
    val tagsText = if (metadata.tags.isNotEmpty()) metadata.tags.joinToString(", ") else null

    val profilesCount = export.profiles.size
    val layoutsCount = export.profiles.sumOf { it.layouts.size }
    val macrosCount = export.profiles.sumOf { it.macros.size }
    val imageCount =
        ConfigManager.getPendingInAppImageCount().takeIf { it > 0 }
            ?: export.profiles.sumOf { p -> p.layouts.count { !it.backgroundImagePath.isNullOrEmpty() } }

    val includedSections =
        buildList {
            if ("global" in export.settings) add(stringResource(R.string.config_import_section_global))
            if ("mirror" in export.settings) add(stringResource(R.string.config_import_section_mirror))
            if ("touchpad" in export.settings) add(stringResource(R.string.config_import_section_touchpad))
            if ("keyboard" in export.settings) add(stringResource(R.string.config_import_section_keyboard))
            if ("macropad_settings" in export.settings) {
                add(stringResource(R.string.config_import_section_macropad_settings))
            }
        }

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
        actionText = stringResource(R.string.gamepad_action_restore),
        icon = Icons.Rounded.Restore,
        isDestructive = true,
        onClick = {
            onConfirmImport(export, pendingInAppImportMode)
        },
    )
}
