package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.NewLayoutOverlay
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import java.util.UUID

private const val TAG = "PillMenu"

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
internal const val PM_SCRIM_ALPHA = 0.55f
internal const val PM_NAME_DIALOG_SCRIM_ALPHA = 0.5f
internal const val PM_NAME_DIALOG_WIDTH_FRACTION = 0.85f

/**
 * Pill Menu overlay — appears when [AppStateManager.isPillMenuOpen] transitions to true.
 *
 * Two-card layout:
 * - **Bottom card** (always visible): Profile / Layout selectors + action buttons.
 * - **Top card** (only when mirroring): Ambient Settings + mirror control icon buttons.
 *
 * Tapping the scrim calls [onDismiss].
 */
@Composable
fun PillMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val profiles by MacroPadState.profiles.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
    val showMirrorControlLabels by SettingsManager.showMirrorControlLabels.collectAsState()
    val defaultProfileName = stringResource(R.string.pill_menu_new_profile)
    val defaultLayoutName = stringResource(R.string.pill_menu_new_layout)

    var showGlobalSettings by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showNewLayoutDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit  = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = PM_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // ── Top card — Mirror controls (always visible) ───────────────
            MirrorControlCard(
                colors = colors,
                isCapturing = isCapturing,
                isFrozen = isFrozen,
                isViewportEditActive = isViewportEditActive,
                isTouchProjectionActive = isTouchProjectionActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .animateEnterExit(
                        enter = slideInVertically { -it },
                        exit  = slideOutVertically { -it },
                    ),
                onBackgroundSettings = {
                    AppStateManager.setBackgroundSettingsActive(true)
                    onDismiss()
                },
                onStart = {
                    AppStateManager.requestMirrorStart()
                    onDismiss()
                },
                onStop = {
                    AppStateManager.requestMirrorStop()
                    onDismiss()
                },
                onToggleFreeze = { ScreenCaptureManager.toggleFrozen() },
                onToggleViewportEdit = {
                    AppStateManager.setViewportEditActive(true)
                    onDismiss()
                },
                onToggleTouchProjection = { ScreenCaptureManager.toggleTouchProjection() },
                showLabels = showMirrorControlLabels,
            )

            // ── Bottom card — Profiles / Layouts / Actions ─────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically { it },
                        exit  = slideOutVertically { it },
                    )
                    .fillMaxWidth()
                    .padding(horizontal = PM_PANEL_H_PADDING, vertical = PM_PANEL_V_PADDING)
                    .shadow(PM_ELEVATION, RoundedCornerShape(PM_PANEL_CORNER))
                    .clip(RoundedCornerShape(PM_PANEL_CORNER))
                    .background(colors.controlOverlay)
                    .border(PM_BORDER_WIDTH, colors.controlOverlayBorder, RoundedCornerShape(PM_PANEL_CORNER))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { } // absorb clicks — prevent scrim dismiss
                    .padding(PM_CONTENT_PADDING),
            ) {
                // ── Profile section ────────────────────────────────────────
                SectionLabel(text = stringResource(R.string.pill_menu_profile_label), colors = colors)
                Spacer(Modifier.height(6.dp))
                ProfileRow(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    colors = colors,
                    onProfileSelected = { profile ->
                        AppLog.d(TAG, "profile selected: ${profile.id}")
                        MacroPadState.setActiveProfileId(profile.id)
                        onDismiss()
                    },
                    onNewProfile = { showNewProfileDialog = true },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Layout section ─────────────────────────────────────────
                SectionLabel(text = stringResource(R.string.pill_menu_layout_label), colors = colors)
                Spacer(Modifier.height(6.dp))
                LayoutRow(
                    activeProfile = activeProfile,
                    activeLayout = activeLayout,
                    colors = colors,
                    onLayoutSelected = { layoutId ->
                        AppLog.d(TAG, "layout selected: $layoutId")
                        MacroPadState.setActiveLayoutId(layoutId)
                        onDismiss()
                    },
                    onNewLayout = { showNewLayoutDialog = true },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))
                HorizontalDivider(color = colors.controlOverlayBorder)
                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillActionChip(
                        label    = stringResource(R.string.pill_menu_edit_layout),
                        icon     = Icons.Rounded.Edit,
                        colors   = colors,
                        onClick  = { AppStateManager.setEditorActive(true); onDismiss() },
                        modifier = Modifier.weight(1f),
                    )
                    PillActionChip(
                        label    = stringResource(R.string.pill_menu_global_settings),
                        icon     = Icons.Rounded.Settings,
                        colors   = colors,
                        onClick  = { showGlobalSettings = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ── Global Settings full-screen overlay ───────────────────────────────
    AnimatedVisibility(
        visible = showGlobalSettings,
        enter = slideInVertically { it } + fadeIn(),
        exit  = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        GlobalSettingsScreen(onBack = { showGlobalSettings = false })
    }

    // ── New Profile dialog ─────────────────────────────────────────────────
    if (showNewProfileDialog) {
        InTreeNameInputDialog(
            title = stringResource(R.string.pill_menu_new_profile),
            hint = stringResource(R.string.pill_menu_profile_name_hint),
            colors = colors,
            onConfirm = { name ->
                val layoutId = UUID.randomUUID().toString()
                val profile = PadProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { defaultProfileName },
                    layouts = listOf(
                        PadLayout(
                            id = layoutId,
                            name = name.trim().ifEmpty { defaultLayoutName },
                            buttons = emptyList(),
                        )
                    ),
                    activeLayoutId = layoutId,
                    macros = emptyList(),
                )
                MacroPadState.addProfile(profile)
                showNewProfileDialog = false
                onDismiss()
            },
            onDismiss = { showNewProfileDialog = false },
            existingNames = profiles.map { it.name },
        )
    }

    // ── New Layout dialog ──────────────────────────────────────────────────
    if (showNewLayoutDialog) {
        val profile = activeProfile
        NewLayoutOverlay(
            profiles = profiles,
            existingLayoutNames = profile?.layouts?.map { it.name } ?: emptyList(),
            accentColor = colors.accent,
            onConfirm = { name, templateButtons ->
                val layout = PadLayout(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { defaultLayoutName },
                    buttons = templateButtons,
                )
                MacroPadState.addLayout(layout)
                showNewLayoutDialog = false
                onDismiss()
            },
            onDismiss = { showNewLayoutDialog = false },
        )
    }
}
