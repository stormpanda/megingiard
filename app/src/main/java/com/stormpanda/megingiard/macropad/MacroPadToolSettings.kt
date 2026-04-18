package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.RememberSettingRow
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.SliderSettingRow
import com.stormpanda.megingiard.macropad.VignetteShape
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MTS_DROPDOWN_BG_ALPHA = 0.08f
private val MTS_DROPDOWN_H_PADDING = 12.dp
private val MTS_DROPDOWN_V_PADDING = 6.dp
private val MTS_DROPDOWN_CORNER = 6.dp

// ─────────────────────────────────────────────────────────────────────────────
// Enum label helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun VignetteShape.labelResId(): Int = when (this) {
    VignetteShape.RADIAL    -> R.string.settings_macropad_vignette_shape_radial
    VignetteShape.LETTERBOX -> R.string.settings_macropad_vignette_shape_letterbox
    VignetteShape.PILLARBOX -> R.string.settings_macropad_vignette_shape_pillarbox
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tool-settings panel content for the MacroPad tool.
 * Shown inside [com.stormpanda.megingiard.settings.ToolSettingsPanel].
 *
 * @param onOpenEditor  Called when the user taps "Edit Layout…" to open the full-screen editor.
 */
@Composable
fun MacroPadToolSettings(
    onOpenEditor: () -> Unit,
    onSliderDragging: ((Boolean) -> Unit)? = null,
    onRequestColorPicker: ((Color, (Color) -> Unit) -> Unit)? = null,
) {
    val profiles    by MacroPadState.profiles.collectAsState()
    val activeId    by MacroPadState.activeProfileId.collectAsState()
    val colors      = LocalAppColors.current

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (profiles.isEmpty()) {
            Text(
                text     = stringResource(R.string.macropad_no_profile),
                color    = colors.onSurfaceSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            LayoutActionButtons(accentColor = colors.accent, onOpenEditor = onOpenEditor)
            return@Column
        }

        // ── Profile picker + layout actions ───────────────────────────────
        SettingsLabel(stringResource(R.string.settings_macropad_profile), colors.accent)
        ProfileSectionRow(
            profiles    = profiles,
            activeId    = activeId,
            accentColor = colors.accent,
            onSelect    = { MacroPadState.setActiveProfileId(it) },
            onOpenEditor = onOpenEditor,
        )

        HorizontalDivider(color = colors.divider)

        // ── Ambient Display settings ────────────────────────────────────────
        AmbientSettingsSection(
            accentColor = colors.accent,
            onSliderDragging = onSliderDragging,
            onRequestColorPicker = onRequestColorPicker,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileDropdown(
    profiles:    List<PadProfile>,
    activeId:    String?,
    accentColor: Color,
    onSelect:    (String) -> Unit,
    modifier:    Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val colors = LocalAppColors.current

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = active?.name ?: "",
                color    = colors.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text  = p.name,
                            color = if (p.id == activeId) accentColor else colors.onSurface,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = { onSelect(p.id); expanded = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout action buttons (Edit + New, side by side)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutActionButtons(accentColor: Color, onOpenEditor: () -> Unit) {
    val defaultName = stringResource(R.string.macropad_editor_new_profile_name)
    Row(
        modifier             = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Edit current layout
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenEditor)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Edit,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_edit_layout),
                color      = accentColor,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 6.dp),
            )
        }

        // New layout — creates a blank profile and opens the editor
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    val newProfile = PadProfile(
                        id   = UUID.randomUUID().toString(),
                        name = defaultName,
                    )
                    MacroPadState.addProfile(newProfile)
                    MacroPadState.setActiveProfileId(newProfile.id)
                    onOpenEditor()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Add,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_new_layout),
                color      = accentColor,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile section row (dropdown + compact Edit / New buttons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionRow(
    profiles:    List<PadProfile>,
    activeId:    String?,
    accentColor: Color,
    onSelect:    (String) -> Unit,
    onOpenEditor: () -> Unit,
) {
    val defaultName = stringResource(R.string.macropad_editor_new_profile_name)
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProfileDropdown(
            profiles    = profiles,
            activeId    = activeId,
            accentColor = accentColor,
            onSelect    = onSelect,
            modifier    = Modifier.weight(1f),
        )
        // Compact Edit button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenEditor)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Edit,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_edit),
                color      = accentColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 4.dp),
            )
        }
        // Compact New button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    val newProfile = PadProfile(
                        id   = UUID.randomUUID().toString(),
                        name = defaultName,
                    )
                    MacroPadState.addProfile(newProfile)
                    MacroPadState.setActiveProfileId(newProfile.id)
                    onOpenEditor()
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Add,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_new),
                color      = accentColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ambient Display settings
// ─────────────────────────────────────────────────────────────────────────────

private const val MTS_BLUR_PERCENT_DIVISOR = 100f
private const val MTS_DIM_MAX = 0.9f
private const val MTS_VIGNETTE_VISIBLE_AREA_MAX = 1f
private const val MTS_VIGNETTE_OPACITY_MAX = 1f
private const val MTS_VIGNETTE_TRANSITION_MAX = 1f
private val MTS_VIGNETTE_SWATCH_SIZE = 24.dp

@Composable
private fun AmbientSettingsSection(
    accentColor: Color,
    onSliderDragging: ((Boolean) -> Unit)? = null,
    onRequestColorPicker: ((Color, (Color) -> Unit) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val ambientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()
    val ambientDim     by SettingsManager.macropadAmbientDim.collectAsState()
    val ambientPreview by SettingsManager.macropadAmbientPreview.collectAsState()
    val applyTheme     by SettingsManager.macropadAmbientApplyTheme.collectAsState()

    SettingsLabel(stringResource(R.string.settings_macropad_ambient), accentColor)

    // Toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_macropad_ambient),
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_macropad_ambient_hint),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = ambientEnabled,
            onCheckedChange = { scope.launch { SettingsManager.setMacropadAmbientEnabled(it) } },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }

    // Sub-settings – only shown when ambient is enabled
    if (ambientEnabled) {
        RememberSettingRow(
            label = stringResource(R.string.settings_macropad_ambient_preview),
            description = stringResource(R.string.settings_macropad_ambient_preview_hint),
            checked = ambientPreview,
            accentColor = accentColor,
            onCheckedChange = { scope.launch { SettingsManager.setMacropadAmbientPreview(it) } }
        )

        RememberSettingRow(
            label = stringResource(R.string.settings_macropad_ambient_apply_theme),
            description = stringResource(R.string.settings_macropad_ambient_apply_theme_hint),
            checked = applyTheme,
            accentColor = accentColor,
            onCheckedChange = { scope.launch { SettingsManager.setMacropadAmbientApplyTheme(it) } }
        )

        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_dim),
            value = ambientDim,
            valueRange = 0f..MTS_DIM_MAX,
            formatLabel = { "${(it * MTS_BLUR_PERCENT_DIVISOR).toInt()}%" },
            accentColor = accentColor,
            onValueChange = { SettingsManager.updateMacropadAmbientDimLive(it); onSliderDragging?.invoke(true) },
            onValueChangeFinished = { scope.launch { SettingsManager.setMacropadAmbientDim(SettingsManager.macropadAmbientDim.value) }; onSliderDragging?.invoke(false) }
        )

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = colors.divider)
        Spacer(Modifier.height(4.dp))

        VignetteSettingsSubSection(
            accentColor = accentColor,
            onSliderDragging = onSliderDragging,
            onRequestColorPicker = onRequestColorPicker,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vignette settings sub-section (shown inside AmbientSettingsSection)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VignetteSettingsSubSection(
    accentColor: Color,
    onSliderDragging: ((Boolean) -> Unit)? = null,
    onRequestColorPicker: ((Color, (Color) -> Unit) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val vignetteEnabled   by SettingsManager.macropadAmbientVignetteEnabled.collectAsState()
    val vignetteVisibleArea by SettingsManager.macropadAmbientVignetteVisibleArea.collectAsState()
    val vignetteTransition by SettingsManager.macropadAmbientVignetteTransition.collectAsState()
    val vignetteOpacity   by SettingsManager.macropadAmbientVignetteOpacity.collectAsState()
    val vignetteColor     by SettingsManager.macropadAmbientVignetteColor.collectAsState()
    val vignetteShape     by SettingsManager.macropadAmbientVignetteShape.collectAsState()

    val labelSoft = stringResource(R.string.settings_macropad_vignette_transition_soft)
    val labelHard = stringResource(R.string.settings_macropad_vignette_transition_hard)

    SettingsLabel(stringResource(R.string.settings_macropad_vignette), accentColor)

    // Enabled toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = vignetteEnabled,
            onCheckedChange = { scope.launch { SettingsManager.setMacropadAmbientVignetteEnabled(it) } },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }

    if (vignetteEnabled) {
        VignetteShapeRow(
            currentShape = vignetteShape,
            accentColor = accentColor,
            onShapeSelected = { scope.launch { SettingsManager.setMacropadAmbientVignetteShape(it) } }
        )

        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_vignette_visible_area),
            value = vignetteVisibleArea,
            valueRange = 0f..MTS_VIGNETTE_VISIBLE_AREA_MAX,
            formatLabel = { "${(it * MTS_BLUR_PERCENT_DIVISOR).toInt()}%" },
            accentColor = accentColor,
            onValueChange = { SettingsManager.updateMacropadAmbientVignetteVisibleAreaLive(it); onSliderDragging?.invoke(true) },
            onValueChangeFinished = { scope.launch { SettingsManager.setMacropadAmbientVignetteVisibleArea(SettingsManager.macropadAmbientVignetteVisibleArea.value) }; onSliderDragging?.invoke(false) }
        )

        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_vignette_transition),
            value = vignetteTransition,
            valueRange = 0f..MTS_VIGNETTE_TRANSITION_MAX,
            formatLabel = { v ->
                when {
                    v <= 0f -> labelSoft
                    v >= 1f -> labelHard
                    else    -> "${(v * MTS_BLUR_PERCENT_DIVISOR).toInt()}%"
                }
            },
            accentColor = accentColor,
            onValueChange = { SettingsManager.updateMacropadAmbientVignetteTransitionLive(it); onSliderDragging?.invoke(true) },
            onValueChangeFinished = { scope.launch { SettingsManager.setMacropadAmbientVignetteTransition(SettingsManager.macropadAmbientVignetteTransition.value) }; onSliderDragging?.invoke(false) }
        )

        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_vignette_opacity),
            value = vignetteOpacity,
            valueRange = 0f..MTS_VIGNETTE_OPACITY_MAX,
            formatLabel = { "${(it * MTS_BLUR_PERCENT_DIVISOR).toInt()}%" },
            accentColor = accentColor,
            onValueChange = { SettingsManager.updateMacropadAmbientVignetteOpacityLive(it); onSliderDragging?.invoke(true) },
            onValueChangeFinished = { scope.launch { SettingsManager.setMacropadAmbientVignetteOpacity(SettingsManager.macropadAmbientVignetteOpacity.value) }; onSliderDragging?.invoke(false) }
        )

        VignetteColorRow(
            vignetteColorInt = vignetteColor,
            accentColor = accentColor,
            colors = colors,
            onColorSelected = { scope.launch { SettingsManager.setMacropadAmbientVignetteColor(it.toArgb()) } },
            onRequestColorPicker = onRequestColorPicker,
        )
    }
}

@Composable
private fun VignetteShapeRow(
    currentShape: VignetteShape,
    accentColor: Color,
    onShapeSelected: (VignetteShape) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_shape),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .background(
                        colors.onSurface.copy(alpha = MTS_DROPDOWN_BG_ALPHA),
                        RoundedCornerShape(MTS_DROPDOWN_CORNER)
                    )
                    .padding(horizontal = MTS_DROPDOWN_H_PADDING, vertical = MTS_DROPDOWN_V_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(currentShape.labelResId()),
                    color = colors.onSurface,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                VignetteShape.entries.forEach { shape ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(shape.labelResId()),
                                color = if (shape == currentShape) accentColor else colors.onSurface,
                                fontSize = 14.sp
                            )
                        },
                        onClick = { onShapeSelected(shape); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun VignetteColorRow(
    vignetteColorInt: Int,
    accentColor: Color,
    colors: AppColors,
    onColorSelected: (Color) -> Unit,
    onRequestColorPicker: ((Color, (Color) -> Unit) -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ColorWheelPicker(
            initialColor = Color(vignetteColorInt),
            onColorSelected = { color ->
                onColorSelected(color)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onRequestColorPicker != null) {
                    onRequestColorPicker(Color(vignetteColorInt), onColorSelected)
                } else {
                    showPicker = true
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_color),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(MTS_VIGNETTE_SWATCH_SIZE)
                .clip(CircleShape)
                .background(Color(vignetteColorInt))
                .border(1.dp, colors.accentBorder, CircleShape)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsLabel(text: String, accentColor: Color) {
    Text(text, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
