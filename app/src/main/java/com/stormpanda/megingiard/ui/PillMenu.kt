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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "PillMenu"

// ── Dimensions ──────────────────────────────────────────────────────────────
private val PM_PANEL_H_PADDING = 8.dp
private val PM_PANEL_V_PADDING = 6.dp
private val PM_PANEL_CORNER = 16.dp
private val PM_BORDER_WIDTH = 1.dp
private val PM_ELEVATION = 8.dp
private val PM_CONTENT_PADDING = 16.dp
private val PM_SECTION_SPACING = 10.dp
private val PM_LABEL_SIZE = 11.sp
private val PM_ACTION_BUTTON_CORNER = 10.dp
private val PM_ACTION_BUTTON_H_PADDING = 12.dp
private val PM_ACTION_BUTTON_V_PADDING = 8.dp
private val PM_CHIP_CORNER = 20.dp
private val PM_CHIP_H_PADDING = 12.dp
private val PM_CHIP_V_PADDING = 6.dp
private val PM_CHIP_SPACING = 6.dp
private val PM_NAV_ICON_SIZE = 20.dp
private val PM_MIRROR_ICON_SIZE = 22.dp
private val PM_MIRROR_BUTTON_SIZE = 48.dp
private val PM_MIRROR_LABELED_BUTTON_WIDTH = 72.dp
private val PM_MIRROR_CARD_V_PADDING = 10.dp
private const val PM_SCRIM_ALPHA = 0.55f
private const val PM_NAME_DIALOG_SCRIM_ALPHA = 0.5f
private const val PM_NAME_DIALOG_WIDTH_FRACTION = 0.85f

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

    // Stop all uinput virtual devices while the menu is visible.
    // keyinjector_arm64 registers as a hardware keyboard via uinput, which causes
    // Android to suppress the soft IME — making text fields un-typeable.
    if (visible) {
        DisposableEffect(Unit) {
            AppLog.d(TAG, "PillMenu visible → stopping injectors for soft IME")
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
            onDispose {
                val ap = MacroPadState.activeProfile.value
                AppLog.d(TAG, "PillMenu dismissed → restarting injectors")
                CoroutineScope(Dispatchers.IO).launch {
                    if (ap?.enableKeyboard != false) KeyInjector.start(context)
                    if (ap?.enableGamepad != false) GamepadInjector.start(context)
                    if (ap?.enableMouse != false) MouseInjector.start(context)
                }
            }
        }
    }

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
                onAmbientSettings = {
                    AppStateManager.setAmbientSettingsActive(true)
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
                        MacroPadState.setActiveLayoutId(layoutId)
                        onDismiss()
                    },
                    onNewLayout = { showNewLayoutDialog = true },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))
                HorizontalDivider(color = colors.controlOverlayBorder)
                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Action buttons ─────────────────────────────────────────
                ActionButton(
                    label = stringResource(R.string.pill_menu_edit_layout),
                    colors = colors,
                    icon = {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.pill_menu_edit_layout),
                            tint = colors.onControlOverlay,
                            modifier = Modifier.size(PM_NAV_ICON_SIZE),
                        )
                    },
                ) {
                    AppStateManager.setEditorActive(true)
                    onDismiss()
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    label = stringResource(R.string.pill_menu_global_settings),
                    colors = colors,
                    icon = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.pill_menu_global_settings),
                            tint = colors.onControlOverlay,
                            modifier = Modifier.size(PM_NAV_ICON_SIZE),
                        )
                    },
                ) {
                    showGlobalSettings = true
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
        InTreeNameInputDialog(
            title = stringResource(R.string.pill_menu_new_layout),
            hint = stringResource(R.string.pill_menu_layout_name_hint),
            colors = colors,
            onConfirm = { name ->
                val layout = PadLayout(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { defaultLayoutName },
                    buttons = emptyList(),
                )
                MacroPadState.addLayout(layout)
                showNewLayoutDialog = false
                onDismiss()
            },
            onDismiss = { showNewLayoutDialog = false },
            existingNames = activeProfile?.layouts?.map { it.name } ?: emptyList(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, colors: AppColors) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = colors.onControlOverlay.copy(alpha = 0.55f),
        fontSize = PM_LABEL_SIZE,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun ProfileRow(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    colors: AppColors,
    onProfileSelected: (PadProfile) -> Unit,
    onNewProfile: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isActive = profile.id == activeProfile?.id
                SelectableChip(
                    text = profile.name,
                    selected = isActive,
                    colors = colors,
                    contentDescription = profile.name,
                    onClick = { onProfileSelected(profile) },
                )
            }
        }
        IconButton(
            onClick = onNewProfile,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.pill_menu_new_profile),
                tint = colors.onControlOverlay,
                modifier = Modifier.size(PM_NAV_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun LayoutRow(
    activeProfile: PadProfile?,
    activeLayout: PadLayout?,
    colors: AppColors,
    onLayoutSelected: (String) -> Unit,
    onNewLayout: () -> Unit,
) {
    val enabledLayouts = activeProfile?.layouts?.filter { it.enabled } ?: emptyList()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
        ) {
            items(enabledLayouts, key = { it.id }) { layout ->
                SelectableChip(
                    text = layout.name,
                    selected = layout.id == activeLayout?.id,
                    colors = colors,
                    contentDescription = stringResource(R.string.pill_menu_layout_chip_cd, layout.name),
                    onClick = { onLayoutSelected(layout.id) },
                )
            }
        }

        IconButton(
            onClick = onNewLayout,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.pill_menu_new_layout),
                tint = colors.onControlOverlay,
                modifier = Modifier.size(PM_NAV_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun MirrorControlCard(
    colors: AppColors,
    isCapturing: Boolean,
    isFrozen: Boolean,
    isViewportEditActive: Boolean,
    isTouchProjectionActive: Boolean,
    modifier: Modifier = Modifier,
    onAmbientSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleFreeze: () -> Unit,
    onToggleViewportEdit: () -> Unit,
    onToggleTouchProjection: () -> Unit,
    showLabels: Boolean,
) {
    Row(
        modifier = modifier
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
            .padding(horizontal = PM_CONTENT_PADDING, vertical = PM_MIRROR_CARD_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ambient Settings button (left)
        Text(
            text = stringResource(R.string.pill_menu_ambient_settings),
            color = colors.onControlOverlay,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .background(colors.navPillBody.copy(alpha = 0.35f))
                .clickable(onClick = onAmbientSettings)
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        )

        Spacer(Modifier.weight(1f))

        // Mirror control icon buttons (right)
        if (isCapturing) {
            MirrorControlIconButton(
                icon = Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.cd_stop_mirroring),
                label = stringResource(R.string.mirror_control_label_stop),
                tint = colors.onControlOverlay,
                enabled = true,
                showLabel = showLabels,
                colors = colors,
                onClick = onStop,
            )
        } else {
            MirrorControlIconButton(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.cd_start_mirroring),
                label = stringResource(R.string.mirror_control_label_start),
                tint = colors.onControlOverlay,
                enabled = true,
                showLabel = showLabels,
                colors = colors,
                onClick = onStart,
            )
        }
        MirrorControlIconButton(
            icon = if (isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = stringResource(
                if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze,
            ),
            label = stringResource(
                if (isFrozen) R.string.mirror_control_label_unfreeze else R.string.mirror_control_label_freeze,
            ),
            tint = if (isFrozen) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleFreeze,
        )
        MirrorControlIconButton(
            icon = Icons.Rounded.CropFree,
            contentDescription = stringResource(R.string.cd_viewport_edit),
            label = stringResource(R.string.mirror_control_label_viewport),
            tint = if (isViewportEditActive) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleViewportEdit,
        )
        MirrorControlIconButton(
            icon = Icons.Rounded.TouchApp,
            contentDescription = stringResource(
                if (isTouchProjectionActive) R.string.cd_touch_projection_on
                else R.string.cd_touch_projection_off,
            ),
            label = stringResource(R.string.mirror_control_label_projection),
            tint = if (isTouchProjectionActive) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleTouchProjection,
        )
    }
}

@Composable
private fun MirrorControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    tint: Color,
    enabled: Boolean,
    showLabel: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (showLabel) PM_MIRROR_LABELED_BUTTON_WIDTH else PM_MIRROR_BUTTON_SIZE),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(PM_MIRROR_BUTTON_SIZE),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else colors.onControlOverlay.copy(alpha = 0.3f),
                modifier = Modifier.size(PM_MIRROR_ICON_SIZE),
            )
        }
        if (showLabel) {
            Text(
                text = label,
                color = if (enabled) colors.onControlOverlay else colors.onControlOverlay.copy(alpha = 0.4f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    colors: AppColors,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .semantics {
                this.selected = selected
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .clip(RoundedCornerShape(PM_CHIP_CORNER))
            .background(
                if (selected) colors.accent.copy(alpha = 0.85f)
                else colors.navPillBody.copy(alpha = 0.5f),
            )
            .border(
                PM_BORDER_WIDTH,
                if (selected) colors.accent else colors.controlOverlayBorder,
                RoundedCornerShape(PM_CHIP_CORNER),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = PM_CHIP_H_PADDING, vertical = PM_CHIP_V_PADDING),
    ) {
        Text(
            text = text,
            color = if (selected) colors.onAccent else colors.onControlOverlay,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    colors: AppColors,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
            .background(colors.navPillBody.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = colors.onControlOverlay,
            fontSize = 14.sp,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (icon != null) icon()
    }
}

@Composable
private fun InTreeNameInputDialog(
    title: String,
    hint: String,
    colors: AppColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    existingNames: List<String>,
    currentName: String? = null,
) {
    var name by remember { mutableStateOf("") }
    val normalizedName = name.trim()
    val isDuplicate = existingNames.any { existing ->
        !existing.equals(currentName?.trim(), ignoreCase = true) &&
            existing.equals(normalizedName, ignoreCase = true)
    }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val dismissContentDescription = stringResource(R.string.pill_menu_dismiss_dialog)
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = PM_NAME_DIALOG_SCRIM_ALPHA))
                .semantics { contentDescription = dismissContentDescription }
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(PM_NAME_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(PM_PANEL_CORNER))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.forEach { change ->
                                if (!change.isConsumed) change.consume()
                            }
                        }
                    }
                }
                .padding(PM_CONTENT_PADDING),
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(hint, color = colors.onSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!hasError) onConfirm(normalizedName) }),
                isError = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.controlOverlayBorder,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_close), color = colors.onSurface)
                }
                TextButton(onClick = { onConfirm(normalizedName) }, enabled = !hasError) {
                    Text(stringResource(R.string.config_ok), color = colors.accent)
                }
            }
        }
    }
}
