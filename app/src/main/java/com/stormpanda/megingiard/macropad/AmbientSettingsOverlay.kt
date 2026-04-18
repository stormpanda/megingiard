package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.SliderSettingRow
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "AmbientSettingsOverlay"

// ── Slider bounds ───────────────────────────────────────────────────────────
private const val ASO_DIM_MAX = 0.9f
private const val ASO_PERCENT_DIVISOR = 100f
private val ASO_SWATCH_SIZE = 24.dp
private val ASO_DROPDOWN_BG_ALPHA = 0.08f
private val ASO_DROPDOWN_H_PADDING = 12.dp
private val ASO_DROPDOWN_V_PADDING = 6.dp
private val ASO_DROPDOWN_CORNER = 6.dp

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

private fun VignetteShape.labelResId(): Int = when (this) {
    VignetteShape.RADIAL    -> R.string.settings_macropad_vignette_shape_radial
    VignetteShape.LETTERBOX -> R.string.settings_macropad_vignette_shape_letterbox
    VignetteShape.PILLARBOX -> R.string.settings_macropad_vignette_shape_pillarbox
}

/**
 * Full-screen overlay for per-layout ambient display settings.
 * Reads from and writes to the active [PadLayout] via [MacroPadState].
 */
@Composable
internal fun AmbientSettingsOverlay(onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val layout by MacroPadState.activeLayout.collectAsState()

    val currentLayout = layout
    if (currentLayout == null) {
        onDone()
        return
    }

    // Local slider state for smooth dragging — committed on finger up via updateLayout.
    var dimAlpha by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientDim) }
    var vignetteEnabled by remember(currentLayout.id) { mutableStateOf(currentLayout.ambientVignetteEnabled) }
    var vignetteShape by remember(currentLayout.id) { mutableStateOf(currentLayout.ambientVignetteShape) }
    var vignetteVisibleArea by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientVignetteVisibleArea) }
    var vignetteTransition by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientVignetteTransition) }
    var vignetteOpacity by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientVignetteOpacity) }
    var vignetteColorInt by remember(currentLayout.id) { mutableStateOf(currentLayout.ambientVignetteColor) }

    fun commitLayout(block: PadLayout.() -> PadLayout) {
        val updated = MacroPadState.activeLayout.value ?: return
        MacroPadState.updateLayout(updated.block())
    }

    val labelSoft = stringResource(R.string.settings_macropad_vignette_transition_soft)
    val labelHard = stringResource(R.string.settings_macropad_vignette_transition_hard)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title + Done button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pill_menu_ambient_settings),
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDone) {
                    Text(
                        stringResource(R.string.macropad_editor_done),
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = currentLayout.name,
                color = colors.onSurfaceSecondary,
                fontSize = 13.sp,
            )

            HorizontalDivider(color = colors.divider)

            // ── Dim slider ────────────────────────────────────────────────
            SliderSettingRow(
                label = stringResource(R.string.settings_macropad_dim),
                value = dimAlpha,
                valueRange = 0f..ASO_DIM_MAX,
                formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                accentColor = colors.accent,
                onValueChange = { dimAlpha = it },
                onValueChangeFinished = {
                    AppLog.d(TAG, "dim → $dimAlpha")
                    commitLayout { copy(ambientDim = dimAlpha) }
                },
            )

            HorizontalDivider(color = colors.divider)

            // ── Vignette toggle ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_macropad_vignette),
                    color = colors.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = vignetteEnabled,
                    onCheckedChange = {
                        vignetteEnabled = it
                        AppLog.d(TAG, "vignette enabled → $it")
                        commitLayout { copy(ambientVignetteEnabled = it) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                    ),
                )
            }

            if (vignetteEnabled) {
                // Shape dropdown
                AsoShapeRow(
                    currentShape = vignetteShape,
                    accentColor = colors.accent,
                    onShapeSelected = {
                        vignetteShape = it
                        AppLog.d(TAG, "vignette shape → $it")
                        commitLayout { copy(ambientVignetteShape = it) }
                    },
                )

                // Visible Area slider
                SliderSettingRow(
                    label = stringResource(R.string.settings_macropad_vignette_visible_area),
                    value = vignetteVisibleArea,
                    valueRange = 0f..1f,
                    formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                    accentColor = colors.accent,
                    onValueChange = { vignetteVisibleArea = it },
                    onValueChangeFinished = {
                        AppLog.d(TAG, "vignette visibleArea → $vignetteVisibleArea")
                        commitLayout { copy(ambientVignetteVisibleArea = vignetteVisibleArea) }
                    },
                )

                // Transition slider
                SliderSettingRow(
                    label = stringResource(R.string.settings_macropad_vignette_transition),
                    value = vignetteTransition,
                    valueRange = 0f..1f,
                    formatLabel = { v ->
                        when {
                            v <= 0f -> labelSoft
                            v >= 1f -> labelHard
                            else    -> "${(v * ASO_PERCENT_DIVISOR).toInt()}%"
                        }
                    },
                    accentColor = colors.accent,
                    onValueChange = { vignetteTransition = it },
                    onValueChangeFinished = {
                        AppLog.d(TAG, "vignette transition → $vignetteTransition")
                        commitLayout { copy(ambientVignetteTransition = vignetteTransition) }
                    },
                )

                // Opacity slider
                SliderSettingRow(
                    label = stringResource(R.string.settings_macropad_vignette_opacity),
                    value = vignetteOpacity,
                    valueRange = 0f..1f,
                    formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                    accentColor = colors.accent,
                    onValueChange = { vignetteOpacity = it },
                    onValueChangeFinished = {
                        AppLog.d(TAG, "vignette opacity → $vignetteOpacity")
                        commitLayout { copy(ambientVignetteOpacity = vignetteOpacity) }
                    },
                )

                // Color picker
                AsoColorRow(
                    vignetteColorInt = vignetteColorInt,
                    accentColor = colors.accent,
                    onColorSelected = { color ->
                        vignetteColorInt = color.toArgb()
                        AppLog.d(TAG, "vignette color → 0x${Integer.toHexString(vignetteColorInt)}")
                        commitLayout { copy(ambientVignetteColor = color.toArgb()) }
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shape dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AsoShapeRow(
    currentShape: VignetteShape,
    accentColor: Color,
    onShapeSelected: (VignetteShape) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_shape),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .background(
                        colors.onSurface.copy(alpha = ASO_DROPDOWN_BG_ALPHA),
                        RoundedCornerShape(ASO_DROPDOWN_CORNER),
                    )
                    .padding(horizontal = ASO_DROPDOWN_H_PADDING, vertical = ASO_DROPDOWN_V_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(currentShape.labelResId()),
                    color = colors.onSurface,
                    fontSize = 14.sp,
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
                modifier = Modifier.background(colors.surface),
            ) {
                VignetteShape.entries.forEach { shape ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(shape.labelResId()),
                                color = if (shape == currentShape) accentColor else colors.onSurface,
                                fontSize = 14.sp,
                            )
                        },
                        onClick = { onShapeSelected(shape); expanded = false },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color picker row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AsoColorRow(
    vignetteColorInt: Int,
    accentColor: Color,
    onColorSelected: (Color) -> Unit,
) {
    val colors = LocalAppColors.current
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ColorWheelPicker(
            initialColor = Color(vignetteColorInt),
            onColorSelected = { color ->
                onColorSelected(color)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_color),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(ASO_SWATCH_SIZE)
                .clip(CircleShape)
                .background(Color(vignetteColorInt))
                .border(1.dp, colors.accentBorder, CircleShape),
        )
    }
}
