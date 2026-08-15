package com.stormpanda.megingiard.settings

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.view.Display
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.config.buildExportFilename
import com.stormpanda.megingiard.config.buildProfileExportFilename
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.DeadzoneDialog
import com.stormpanda.megingiard.privd.PrivdSetupWizardDialog
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteGrid
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.primaryOverlayFocusable
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

private const val GS_RESTORE_COUNTDOWN_SECONDS = 5
private const val GS_RESTORE_COUNTDOWN_INTERVAL_MS = 1_000L

private const val GS_OBTAINIUM_REPO_URL = "https://github.com/stormpanda/megingiard"
private const val GS_OBTAINIUM_FALLBACK_URL = "https://github.com/ImranR98/Obtainium"

private val GS_KOFI_BUTTON_HEIGHT = 32.dp
private val GS_KOFI_CORNER = 8.dp

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

@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    showHelp: Boolean = false,
    onDismissHelp: () -> Unit = {},
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsState()
    val accentColor = Color(accentColorArgb)
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
    val internalBackups by viewModel.internalBackups.collectAsState()

    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val latestReleaseInfo by viewModel.latestReleaseInfo.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()

    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    var showRestoreBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdatePromptDialog by rememberSaveable { mutableStateOf(false) }
    var showSteamGridDbDialog by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showPresetPaletteDialog by rememberSaveable { mutableStateOf(false) }
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
    val effectiveShowHelp = showHelp || showSettingsHelp
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

    val filterList =
        remember {
            listOf(
                null,
                SettingsSectionFilter.GENERAL,
                SettingsSectionFilter.INPUT,
                SettingsSectionFilter.APPEARANCE,
                SettingsSectionFilter.DATA,
                SettingsSectionFilter.CONFIGURATION,
                SettingsSectionFilter.UPDATES,
                SettingsSectionFilter.DIAGNOSTICS,
            )
        }

    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            val currentIndex = filterList.indexOf(selectedSectionFilter).coerceAtLeast(0)
            val nextIndex =
                when (direction) {
                    BumperDirection.PREV -> (currentIndex - 1 + filterList.size) % filterList.size
                    BumperDirection.NEXT -> (currentIndex + 1) % filterList.size
                }
            selectedSectionFilter = filterList[nextIndex]
        }
    }

    GamepadTwoPaneScaffold(
        sidebarContent = {
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_all),
                icon = Icons.Rounded.Dashboard,
                selected = selectedSectionFilter == null,
                onClick = { selectedSectionFilter = null },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_general),
                icon = Icons.Rounded.Tune,
                selected = selectedSectionFilter == SettingsSectionFilter.GENERAL,
                onClick = { selectedSectionFilter = SettingsSectionFilter.GENERAL },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_input),
                icon = Icons.Rounded.Gamepad,
                selected = selectedSectionFilter == SettingsSectionFilter.INPUT,
                onClick = { selectedSectionFilter = SettingsSectionFilter.INPUT },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_appearance),
                icon = Icons.Rounded.Palette,
                selected = selectedSectionFilter == SettingsSectionFilter.APPEARANCE,
                onClick = { selectedSectionFilter = SettingsSectionFilter.APPEARANCE },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_data),
                icon = Icons.Rounded.Storage,
                selected = selectedSectionFilter == SettingsSectionFilter.DATA,
                onClick = { selectedSectionFilter = SettingsSectionFilter.DATA },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_config),
                icon = Icons.Rounded.Build,
                selected = selectedSectionFilter == SettingsSectionFilter.CONFIGURATION,
                onClick = { selectedSectionFilter = SettingsSectionFilter.CONFIGURATION },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_updates),
                icon = Icons.Rounded.SystemUpdate,
                selected = selectedSectionFilter == SettingsSectionFilter.UPDATES,
                onClick = { selectedSectionFilter = SettingsSectionFilter.UPDATES },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_diagnostics),
                icon = Icons.Rounded.HealthAndSafety,
                selected = selectedSectionFilter == SettingsSectionFilter.DIAGNOSTICS,
                onClick = { selectedSectionFilter = SettingsSectionFilter.DIAGNOSTICS },
            )
        },
        content = {
            if (updateAvailable && latestReleaseInfo != null) {
                GamepadActionCard(
                    title = stringResource(R.string.settings_update_available_banner, latestReleaseInfo?.tagName ?: ""),
                    description = stringResource(R.string.settings_update_available_banner_desc),
                    actionText = stringResource(R.string.settings_update_now_btn),
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = { showUpdatePromptDialog = true },
                )
            }

            // GENERAL
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.GENERAL) {
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
                )

                GamepadActionCard(
                    title = stringResource(R.string.privd_title),
                    description = stringResource(R.string.help_settings_privd_desc),
                    actionText = "Setup",
                    icon = Icons.Rounded.Security,
                    onClick = { showPrivdWizard = true },
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
                    onPrevious = { viewModel.setAppLanguage(allLangs[(currentLangIdx - 1 + allLangs.size) % allLangs.size]) },
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
                            "API Token Configured (${steamGridDbApiToken.take(6)}...)"
                        } else {
                            stringResource(R.string.settings_steamgriddb_token_desc)
                        },
                    actionText = "Edit",
                    icon = Icons.Rounded.Key,
                    onClick = { showSteamGridDbDialog = true },
                )
            }

            // INPUT
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.INPUT) {
                Text(
                    text = stringResource(R.string.settings_section_input).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                GamepadToggleCard(
                    title = stringResource(R.string.settings_gamepad_swap_face_buttons),
                    description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                    checked = gamepadSwapFaceButtons,
                    icon = Icons.Rounded.SwapHoriz,
                    onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                )

                GamepadActionCard(
                    title = stringResource(R.string.privd_deadzone_title),
                    description = "Left: $deadzoneLeft% • Right: $deadzoneRight%",
                    actionText = "Deadzones",
                    icon = Icons.Rounded.Games,
                    onClick = { showDeadzoneDialog = true },
                )
            }

            // APPEARANCE
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.APPEARANCE) {
                Text(
                    text = stringResource(R.string.settings_section_appearance).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                val allThemes = remember { ThemeMode.entries }
                val currentThemeIdx = allThemes.indexOf(themeMode)
                val themeDisplayName = stringResource(themeMode.displayNameResId())

                GamepadChoiceCard(
                    title = stringResource(R.string.settings_theme),
                    description = stringResource(R.string.help_settings_theme_desc),
                    selectedText = themeDisplayName,
                    icon = Icons.Rounded.Palette,
                    onPrevious = { viewModel.setThemeMode(allThemes[(currentThemeIdx - 1 + allThemes.size) % allThemes.size]) },
                    onNext = { viewModel.setThemeMode(allThemes[(currentThemeIdx + 1) % allThemes.size]) },
                )

                if (themeMode.supportsCustomAccent) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .border(1.dp, colors.subduedBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_accent_color),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GamepadColorPaletteGrid(
                            paletteColors = ACCENT_PALETTE_PRESETS,
                            selectedColor = accentColor,
                            onColorSelected = { viewModel.setAccentColor(it.toArgb()) },
                        )
                    }

                    GamepadActionCard(
                        title = "Custom Accent Color Wheel",
                        description = "Pick any custom RGB/HSV accent color",
                        actionText = "Wheel",
                        icon = Icons.Rounded.Colorize,
                        onClick = { showColorPicker = true },
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
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.DATA) {
                Text(
                    text = stringResource(R.string.settings_section_data).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_restore_defaults),
                    description = stringResource(R.string.settings_restore_defaults_desc),
                    actionText = "Restore",
                    isDestructive = true,
                    icon = Icons.Rounded.Restore,
                    onClick = { showRestoreDefaultsConfirm = true },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_reset_tutorials),
                    description = stringResource(R.string.settings_reset_tutorials_desc),
                    actionText = "Reset",
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
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.CONFIGURATION) {
                Text(
                    text = stringResource(R.string.settings_section_config).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_config_export),
                    description = stringResource(R.string.help_settings_export_desc),
                    actionText = "Export",
                    icon = Icons.Rounded.FileDownload,
                    onClick = { showExportMetadataDialog = true },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_config_import),
                    description = "Restore configuration from an existing .mgrd backup",
                    actionText = "Restore",
                    icon = Icons.Rounded.FileUpload,
                    onClick = { showRestoreBackupDialog = true },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_config_export_profile),
                    description = stringResource(R.string.help_settings_export_profile_desc),
                    actionText = "Export",
                    icon = Icons.Rounded.Share,
                    onClick = { showProfileExportDialog = true },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_add_to_obtainium),
                    description = stringResource(R.string.help_settings_add_to_obtainium_desc),
                    actionText = "Add",
                    icon = Icons.Rounded.Download,
                    onClick = {
                        val deepLink = "obtainium://add/${GS_OBTAINIUM_REPO_URL}"
                        try {
                            val intent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            val options = ActivityOptions.makeBasic()
                            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                            context.startActivity(intent, options.toBundle())
                            AppLog.d(TAG, "Launched Obtainium deep link: $deepLink")
                        } catch (e: Exception) {
                            AppLog.w(TAG, "Obtainium deep link failed: ${e.message}, falling back to browser")
                            try {
                                val browserIntent =
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GS_OBTAINIUM_FALLBACK_URL)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                val options = ActivityOptions.makeBasic()
                                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                context.startActivity(browserIntent, options.toBundle())
                            } catch (ex: Exception) {
                                AppLog.e(TAG, "Failed to open browser fallback: ${ex.message}")
                            }
                        }
                    },
                )
            }

            // UPDATES
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.UPDATES) {
                Text(
                    text = stringResource(R.string.settings_section_updates).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                GamepadToggleCard(
                    title = stringResource(R.string.settings_auto_update_check),
                    description = stringResource(R.string.help_settings_auto_update_desc),
                    checked = autoUpdateCheckEnabled,
                    icon = Icons.Rounded.Update,
                    onCheckedChange = { viewModel.setAutoUpdateCheckEnabled(it) },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_check_for_updates),
                    description =
                        if (updateAvailable) {
                            "Update available: ${latestReleaseInfo?.tagName}"
                        } else {
                            "Version ${BuildConfig.VERSION_NAME}"
                        },
                    actionText = if (isCheckingUpdates) "Checking..." else "Check",
                    icon = Icons.Rounded.Refresh,
                    onClick = { viewModel.checkForUpdatesManually() },
                )
            }

            // DIAGNOSTICS
            if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.DIAGNOSTICS) {
                Text(
                    text = stringResource(R.string.settings_section_diagnostics).uppercase(),
                    color = effectiveAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (selectedSectionFilter == null) 8.dp else 0.dp),
                )

                val allLogLevels = remember { AppLog.Level.entries }
                val currentLogLevelIdx = allLogLevels.indexOf(logLevel)

                GamepadChoiceCard(
                    title = stringResource(R.string.settings_log_level),
                    description = stringResource(R.string.help_settings_log_level_desc),
                    selectedText = logLevel.name,
                    icon = Icons.Rounded.BugReport,
                    onPrevious = { viewModel.setLogLevel(allLogLevels[(currentLogLevelIdx - 1 + allLogLevels.size) % allLogLevels.size]) },
                    onNext = { viewModel.setLogLevel(allLogLevels[(currentLogLevelIdx + 1) % allLogLevels.size]) },
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_save_log_report),
                    description = stringResource(R.string.help_settings_save_log_desc),
                    actionText = "Save",
                    icon = Icons.Rounded.SaveAlt,
                    onClick = { viewModel.requestSaveLogReport() },
                )
            }
        },
    )

    if (showPrivdWizard) {
        PrivdSetupWizardDialog(
            viewModel = viewModel,
            onDismiss = { showPrivdWizard = false },
        )
    }
    if (showDeadzoneDialog) {
        val deadzoneLeftVal by viewModel.privdDeadzoneLeft.collectAsState()
        val deadzoneRightVal by viewModel.privdDeadzoneRight.collectAsState()
        DeadzoneDialog(
            initialDeadzoneLeft = deadzoneLeftVal,
            initialDeadzoneRight = deadzoneRightVal,
            onConfirm = { left, right ->
                viewModel.setPrivdDeadzoneLeft(left)
                viewModel.setPrivdDeadzoneRight(right)
                showDeadzoneDialog = false
            },
            onDismiss = { showDeadzoneDialog = false },
        )
    }
    if (showSteamGridDbDialog) {
        SteamGridDbTokenDialog(
            initialToken = steamGridDbApiToken,
            colors = colors,
            accentColor = effectiveAccent,
            onConfirm = {
                viewModel.setSteamGridDbApiToken(it)
                showSteamGridDbDialog = false
            },
            onDismiss = { showSteamGridDbDialog = false },
        )
    }
    if (showColorPicker) {
        ColorWheelPicker(
            initialColor = accentColor,
            onColorSelected = { color ->
                viewModel.setAccentColor(color.toArgb())
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
    if (showPresetPaletteDialog) {
        PresetAccentPaletteDialog(
            currentAccent = accentColor,
            colors = colors,
            onColorSelected = { color ->
                viewModel.setAccentColor(color.toArgb())
                showPresetPaletteDialog = false
            },
            onDismiss = { showPresetPaletteDialog = false },
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
            onDismiss = { showRestoreBackupDialog = false },
        )
    }
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
    if (showExportMetadataDialog) {
        ExportMetadataDialog(
            defaultMetadata = ConfigManager.defaultMetadata(context),
            colors = colors,
            accentColor = effectiveAccent,
            onConfirm = { metadata, includeBackgrounds ->
                showExportMetadataDialog = false
                ConfigManager.requestExport(
                    metadata = metadata,
                    filename = buildExportFilename(metadata),
                    includeBackgrounds = includeBackgrounds,
                )
            },
            onDismiss = { showExportMetadataDialog = false },
        )
    }
    if (showUpdatePromptDialog) {
        UpdatePromptDialog(
            tagName = latestReleaseInfo?.tagName ?: "",
            colors = colors,
            accentColor = effectiveAccent,
            onBackupAndOpen = {
                showUpdatePromptDialog = false
                showExportMetadataDialog = true
                val url =
                    latestReleaseInfo?.htmlUrl?.ifBlank { "https://github.com/stormpanda/megingiard/releases" }
                        ?: "https://github.com/stormpanda/megingiard/releases"
                try {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    val options = ActivityOptions.makeBasic()
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    context.startActivity(intent, options.toBundle())
                    AppLog.d(TAG, "Launched release URL on top display: $url")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to launch release URL: ${e.message}")
                }
            },
            onOpenDirectly = {
                showUpdatePromptDialog = false
                val url =
                    latestReleaseInfo?.htmlUrl?.ifBlank { "https://github.com/stormpanda/megingiard/releases" }
                        ?: "https://github.com/stormpanda/megingiard/releases"
                try {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    val options = ActivityOptions.makeBasic()
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    context.startActivity(intent, options.toBundle())
                    AppLog.d(TAG, "Launched release URL on top display: $url")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to launch release URL: ${e.message}")
                }
            },
            onDismiss = { showUpdatePromptDialog = false },
        )
    }
    if (showProfileExportDialog) {
        ProfileExportDialog(
            colors = colors,
            accentColor = effectiveAccent,
            onConfirm = { metadata, profile, includeBackgrounds ->
                showProfileExportDialog = false
                ConfigManager.requestProfileExport(
                    metadata = metadata,
                    profile = profile,
                    filename = buildProfileExportFilename(metadata, profile.name),
                    includeBackgrounds = includeBackgrounds,
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
                            ConfigManager.ImportMode.BACKUP_RESTORE -> ConfigManager.applyImport(context, export)
                            ConfigManager.ImportMode.PROFILE_SHARE -> ConfigManager.applyProfileImport(context, export)
                        }
                    }.onSuccess {
                        when (mode) {
                            ConfigManager.ImportMode.BACKUP_RESTORE -> importSuccess = true
                            ConfigManager.ImportMode.PROFILE_SHARE -> profileImportSuccess = true
                        }
                    }.onFailure { e ->
                        importError =
                            e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.config_error_unknown)
                    }
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

    GlobalSettingsHelpModal(
        visible = effectiveShowHelp,
        onDismiss = {
            showSettingsHelp = false
            onDismissHelp()
        },
    )
}

@Composable
private fun SteamGridDbTokenDialog(
    initialToken: String,
    colors: AppColors,
    accentColor: Color,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf(initialToken) }

    AppModalDialog(
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.settings_steamgriddb_token),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_steamgriddb_token_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(
            value = token,
            onValueChange = { token = it },
            placeholder = { Text(stringResource(R.string.settings_steamgriddb_token_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadActionCard(
                title = "",
                actionText = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.width(100.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            GamepadActionCard(
                title = "",
                actionText = stringResource(R.string.config_ok),
                onClick = { onConfirm(token.trim()) },
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

@Composable
private fun GlobalSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_settings_intro))

        HelpSection(stringResource(R.string.settings_section_general))
        HelpEntry(
            label = stringResource(R.string.settings_start_welcome_tour),
            description = stringResource(R.string.settings_start_welcome_tour_desc),
        )
        HelpEntry(
            label = stringResource(R.string.privd_title),
            description = stringResource(R.string.help_settings_privd_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_language),
            description = stringResource(R.string.help_settings_language_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_exclude_from_recents),
            description = stringResource(R.string.help_settings_recents_desc),
        )

        HelpSection(stringResource(R.string.settings_section_input))
        HelpEntry(
            label = stringResource(R.string.settings_gamepad_swap_face_buttons),
            description = stringResource(R.string.help_settings_gamepad_swap_desc),
        )
        HelpEntry(
            label = stringResource(R.string.privd_deadzone_title),
            description = stringResource(R.string.help_settings_deadzone_desc),
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

        HelpSection(stringResource(R.string.settings_section_updates))
        HelpEntry(
            label = stringResource(R.string.settings_auto_update_check),
            description = stringResource(R.string.help_settings_auto_update_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_check_for_updates),
            description = stringResource(R.string.help_settings_check_updates_desc),
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
