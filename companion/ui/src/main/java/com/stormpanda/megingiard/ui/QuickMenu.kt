package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenshotTarget
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalType

private const val TAG = "QuickMenu"

// ── Dimensions ──────────────────────────────────────────────────────────────
internal val PM_PANEL_H_PADDING = 8.dp
internal val PM_PANEL_V_PADDING = 6.dp
internal val PM_PANEL_CORNER = 16.dp
internal val PM_BORDER_WIDTH = 1.dp
internal val PM_ELEVATION = 8.dp
internal val PM_CONTENT_PADDING = 16.dp
internal val PM_SECTION_SPACING = 10.dp
internal val PM_ACTION_BUTTON_CORNER = 10.dp
internal val PM_ACTION_BUTTON_H_PADDING = 12.dp
internal val PM_ACTION_BUTTON_V_PADDING = 8.dp
internal val PM_CHIP_SPACING = 6.dp
internal val PM_NAV_ICON_SIZE = 20.dp
internal val PM_MIRROR_ICON_SIZE = 22.dp
internal val PM_MIRROR_BUTTON_SIZE = 48.dp
internal val PM_MIRROR_LABELED_BUTTON_WIDTH = 72.dp
internal val PM_MIRROR_CARD_V_PADDING = 10.dp
internal val PM_SCREEN_MIRRORING_ICON_SIZE = 16.dp
internal val PM_SCREEN_MIRRORING_SPACER_W = 6.dp
internal val PM_SECTION_TITLE_SPACING = 6.dp
internal val PM_ACTION_ROW_SPACING = 8.dp
internal const val PM_SCRIM_ALPHA = 0.55f
internal const val PM_NAME_DIALOG_SCRIM_ALPHA = 0.5f
internal const val PM_NAME_DIALOG_WIDTH_FRACTION = 0.85f

/**
 * Quick Menu overlay — appears when [AppStateManager.isQuickMenuOpen] transitions to true.
 *
 * Two-card layout:
 * - **Bottom card** (always visible): Profile / Layout selectors + action buttons.
 * - **Top card** (only when mirroring): Ambient Settings + mirror control icon buttons.
 *
 * Tapping the scrim calls [onDismiss].
 */

@Composable
fun QuickMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val menuBezelBrush = rememberBezelBrush()
    val profiles by MacroPadState.profiles.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val companionViewMode by AppStateManager.companionViewMode.collectAsState()
    val showIntegrationHome by AppStateManager.showIntegrationHome.collectAsState()
    val privdState by PrivdClient.state.collectAsState()
    val isPrivdConnected = privdState == PrivdConnectionState.CONNECTED
    val showGlobalSettings by AppStateManager.isGlobalSettingsOpen.collectAsState()
    var showQuickMenuHelp by remember { mutableStateOf(false) }
    var showShutOffConfirm by remember { mutableStateOf(false) }
    var autoShimmerTrigger by remember { mutableIntStateOf(0) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = PM_SCRIM_ALPHA))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            AppStateManager.closeQuickMenu()
                            onDismiss()
                        })
                    },
        ) {
            // ── Top card — Mirror controls (always visible) ───────────────
            MirrorControlCard(
                colors = colors,
                isCapturing = isCapturing,
                isFrozen = isFrozen,
                isTopScreenshotEnabled = isCapturing || isPrivdConnected,
                isBottomScreenshotEnabled = true,
                isBothScreenshotEnabled = isCapturing || isPrivdConnected,
                isCompanionHub = showIntegrationHome,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .animateEnterExit(
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                        ),
                onStart = {
                    AppStateManager.requestMirrorStart()
                },
                onStop = {
                    AppStateManager.requestMirrorStop()
                    onDismiss()
                },
                onToggleFreeze = { ScreenCaptureManager.toggleFrozen() },
                onTakeTopScreenshot = { ScreenCaptureManager.requestScreenshot(ScreenshotTarget.TOP) },
                onTakeBottomScreenshot = { ScreenCaptureManager.requestScreenshot(ScreenshotTarget.BOTTOM) },
                onTakeBothScreenshot = { ScreenCaptureManager.requestScreenshot(ScreenshotTarget.BOTH) },
            )

            // ── Bottom card — Profiles / Layouts / Actions ─────────────────
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ).fillMaxWidth()
                        .padding(horizontal = PM_PANEL_H_PADDING, vertical = PM_PANEL_V_PADDING)
                        .shadow(PM_ELEVATION, RoundedCornerShape(PM_PANEL_CORNER))
                        .clip(RoundedCornerShape(PM_PANEL_CORNER))
                        .background(colors.controlOverlay)
                        .border(PM_BORDER_WIDTH, brush = menuBezelBrush, shape = RoundedCornerShape(PM_PANEL_CORNER))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { } // absorb clicks — prevent scrim dismiss
                        .padding(PM_CONTENT_PADDING),
            ) {
                // ── Profile section ────────────────────────────────────────
                SectionLabel(text = stringResource(R.string.quick_menu_profile_label), colors = colors)
                Spacer(Modifier.height(PM_SECTION_TITLE_SPACING))
                ProfileRow(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    colors = colors,
                    onProfileSelected = { profile ->
                        AppLog.d(TAG, "profile selected: ${profile.id}")
                        MacroPadState.setActiveProfileId(profile.id)
                        val currentMode = AppStateManager.companionViewMode.value
                        val focusedPkg = AppStateManager.focusedAppPackageName.value
                        val focusedRom = AppStateManager.focusedRomPath.value
                        val matchesFocused = profile.matches(focusedPkg, focusedRom, isActiveProfile = true)
                        if (currentMode != CompanionViewMode.AUTO || !matchesFocused) {
                            AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                        }
                    },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Layout section ─────────────────────────────────────────
                SectionLabel(text = stringResource(R.string.quick_menu_layout_label), colors = colors)
                Spacer(Modifier.height(PM_SECTION_TITLE_SPACING))
                LayoutRow(
                    activeProfile = activeProfile,
                    activeLayout = activeLayout,
                    colors = colors,
                    onLayoutSelected = { layoutId ->
                        AppLog.d(TAG, "layout selected: $layoutId")
                        MacroPadState.setActiveLayoutId(layoutId)
                        val profile = MacroPadState.activeProfile.value
                        val currentMode = AppStateManager.companionViewMode.value
                        val focusedPkg = AppStateManager.focusedAppPackageName.value
                        val focusedRom = AppStateManager.focusedRomPath.value
                        val matchesFocused = profile != null && profile.matches(focusedPkg, focusedRom, isActiveProfile = true)
                        if (currentMode != CompanionViewMode.AUTO || !matchesFocused) {
                            AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                        }
                    },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))
                HorizontalDivider(color = colors.controlOverlayBorder)
                Spacer(Modifier.height(PM_SECTION_SPACING))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PM_ACTION_ROW_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!showIntegrationHome) {
                        QuickMenuActionChip(
                            label = stringResource(R.string.quick_menu_show_dashboard),
                            painter = painterResource(R.drawable.ic_megingiard_logo),
                            colors = colors,
                            onClick = {
                                AppStateManager.setCompanionViewMode(CompanionViewMode.DASHBOARD)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        QuickMenuActionChip(
                            label = stringResource(R.string.quick_menu_show_macropad),
                            icon = Icons.Rounded.Gamepad,
                            colors = colors,
                            onClick = {
                                val profile = MacroPadState.activeProfile.value
                                val currentMode = AppStateManager.companionViewMode.value
                                val focusedPkg = AppStateManager.focusedAppPackageName.value
                                val focusedRom = AppStateManager.focusedRomPath.value
                                val matchesFocused = profile != null && profile.matches(focusedPkg, focusedRom, isActiveProfile = true)
                                if (currentMode != CompanionViewMode.AUTO || !matchesFocused) {
                                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    MagicalAutoToggleChip(
                        active = companionViewMode == CompanionViewMode.AUTO,
                        shimmerTrigger = autoShimmerTrigger,
                        colors = colors,
                        onClick = {
                            if (companionViewMode == CompanionViewMode.AUTO) {
                                val targetMode = if (showIntegrationHome) CompanionViewMode.DASHBOARD else CompanionViewMode.MACROPAD
                                AppStateManager.setCompanionViewMode(targetMode, isAutoSwitchButton = true)
                            } else {
                                autoShimmerTrigger++
                                AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
                            }
                        },
                        modifier = Modifier.wrapContentWidth(),
                    )
                }

                Spacer(Modifier.height(PM_SECTION_SPACING))
                HorizontalDivider(color = colors.controlOverlayBorder)
                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PM_ACTION_ROW_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickMenuActionChip(
                        label = stringResource(R.string.quick_menu_edit_layout),
                        icon = Icons.Rounded.Edit,
                        colors = colors,
                        onClick = {
                            AppStateManager.setEditorActive(true)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    QuickMenuActionChip(
                        label = stringResource(R.string.quick_menu_global_settings),
                        icon = Icons.Rounded.Settings,
                        colors = colors,
                        onClick = {
                            AppStateManager.setGlobalSettingsOpen(true)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ShutOffIconButton(
                        colors = colors,
                        onClick = { showShutOffConfirm = true },
                    )
                    QuickMenuIconButton(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.help_open_cd),
                        colors = colors,
                        onClick = { showQuickMenuHelp = true },
                    )
                }
            }
        }
    }

    if (showShutOffConfirm) {
        ShutOffConfirmDialog(
            colors = colors,
            onConfirm = {
                showShutOffConfirm = false
                AppStateManager.requestShutOff()
                onDismiss()
            },
            onDismiss = { showShutOffConfirm = false },
        )
    }

    QuickMenuHelpModal(
        visible = showQuickMenuHelp,
        onDismiss = { showQuickMenuHelp = false },
    )
}

@Composable
private fun QuickMenuHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_quickmenu_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_quickmenu_intro))

        HelpSection(stringResource(R.string.help_quickmenu_section_mirror))
        HelpEntry(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.help_quickmenu_start_label),
            description = stringResource(R.string.help_quickmenu_start_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Pause,
            label = stringResource(R.string.help_quickmenu_freeze_label),
            description = stringResource(R.string.help_quickmenu_freeze_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.quick_menu_screen_mirroring),
            description = stringResource(R.string.help_quickmenu_viewport_desc),
        )
        HelpEntry(
            symbolName = "splitscreen_bottom",
            label = stringResource(R.string.help_quickmenu_screenshot_top_label),
            description = stringResource(R.string.help_quickmenu_screenshot_top_desc),
        )
        HelpEntry(
            symbolName = "splitscreen_top",
            label = stringResource(R.string.help_quickmenu_screenshot_bottom_label),
            description = stringResource(R.string.help_quickmenu_screenshot_bottom_desc),
        )
        HelpEntry(
            symbolName = "splitscreen",
            label = stringResource(R.string.help_quickmenu_screenshot_both_label),
            description = stringResource(R.string.help_quickmenu_screenshot_both_desc),
        )

        HelpSection(stringResource(R.string.help_quickmenu_section_macropad))
        HelpEntry(
            label = stringResource(R.string.quick_menu_profile_label),
            description = stringResource(R.string.help_quickmenu_profiles_desc),
        )
        HelpEntry(
            label = stringResource(R.string.quick_menu_layout_label),
            description = stringResource(R.string.help_quickmenu_layouts_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.AutoFixHigh,
            label = stringResource(R.string.help_quickmenu_auto_switch_label),
            description = stringResource(R.string.help_quickmenu_auto_switch_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Gamepad,
            label = stringResource(R.string.help_quickmenu_view_switch_label),
            description = stringResource(R.string.help_quickmenu_view_switch_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.quick_menu_edit_layout),
            description = stringResource(R.string.help_quickmenu_edit_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.quick_menu_global_settings),
            description = stringResource(R.string.help_quickmenu_settings_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.PowerSettingsNew,
            label = stringResource(R.string.help_quickmenu_shut_off_label),
            description = stringResource(R.string.help_quickmenu_shut_off_desc),
        )
        HelpEntry(
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            label = stringResource(R.string.help_quickmenu_help_label),
            description = stringResource(R.string.help_quickmenu_help_desc),
        )
    }
}
