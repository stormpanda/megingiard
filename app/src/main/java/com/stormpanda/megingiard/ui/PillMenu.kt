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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import java.util.UUID

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
private const val PM_SCRIM_ALPHA = 0.55f

/**
 * Pill Menu overlay — appears when [AppStateManager.isPillMenuOpen] transitions to true.
 *
 * Anchored to the same [overlayAtBottom] edge as [IdlePill]. Tapping the scrim calls [onDismiss].
 *
 * Contains:
 * - Profile section: scrollable list of profile chips + "New profile" button
 * - Layout section: prev/next arrows + layout name chip + "New layout" button
 * - Action buttons: Edit Layout, Ambient Settings, Global Settings
 */
@Composable
fun PillMenu(
    visible: Boolean,
    overlayAtBottom: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val profiles by MacroPadState.profiles.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val activeLayout by MacroPadState.activeLayout.collectAsState()
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
            // ── Panel ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
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
                    onNewLayout = { showNewLayoutDialog = true },
                )

                Spacer(Modifier.height(PM_SECTION_SPACING))
                HorizontalDivider(color = colors.controlOverlayBorder)
                Spacer(Modifier.height(PM_SECTION_SPACING))

                // ── Action buttons ─────────────────────────────────────────
                ActionButton(
                    label = stringResource(R.string.pill_menu_edit_layout),
                    colors = colors,
                ) {
                    AppStateManager.setEditorActive(true)
                    onDismiss()
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    label = stringResource(R.string.pill_menu_ambient_settings),
                    colors = colors,
                ) {
                    AppStateManager.setAmbientSettingsActive(true)
                    onDismiss()
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    label = stringResource(R.string.pill_menu_global_settings),
                    colors = colors,
                    icon = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
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
        NameInputDialog(
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
        )
    }

    // ── New Layout dialog ──────────────────────────────────────────────────
    if (showNewLayoutDialog) {
        NameInputDialog(
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
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, colors: AppColors) {
    Text(
        text = text.uppercase(),
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
    onNewLayout: () -> Unit,
) {
    val enabledLayouts = activeProfile?.layouts?.filter { it.enabled } ?: emptyList()
    val hasMultiple = enabledLayouts.size > 1

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { MacroPadState.previousLayout() },
            enabled = hasMultiple,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.cd_layout_prev),
                tint = if (hasMultiple) colors.onControlOverlay
                       else colors.onControlOverlay.copy(alpha = 0.3f),
                modifier = Modifier.size(PM_NAV_ICON_SIZE),
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val layoutName = activeLayout?.name
                ?: activeProfile?.name
                ?: stringResource(R.string.pill_menu_new_layout)
            Text(
                text = layoutName,
                color = colors.onControlOverlay,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        IconButton(
            onClick = { MacroPadState.nextLayout() },
            enabled = hasMultiple,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.cd_layout_next),
                tint = if (hasMultiple) colors.onControlOverlay
                       else colors.onControlOverlay.copy(alpha = 0.3f),
                modifier = Modifier.size(PM_NAV_ICON_SIZE),
            )
        }

        Spacer(Modifier.width(4.dp))

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
private fun SelectableChip(
    text: String,
    selected: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
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
            color = if (selected) Color.White else colors.onControlOverlay,
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
private fun NameInputDialog(
    title: String,
    hint: String,
    colors: AppColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.onSurface) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(hint, color = colors.onSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.controlOverlayBorder,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.config_ok), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), color = colors.onSurface)
            }
        },
    )
}
