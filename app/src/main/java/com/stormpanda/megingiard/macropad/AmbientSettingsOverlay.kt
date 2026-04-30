package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AmbientPreviewConfig
import com.stormpanda.megingiard.AmbientPreviewType
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale

private const val TAG = "AmbientSettingsOverlay"

// ── Slider bounds ───────────────────────────────────────────────────────────
private const val ASO_DIM_MAX = 0.9f
private const val ASO_PERCENT_DIVISOR = 100f
private val ASO_SWATCH_SIZE = 24.dp
private val ASO_DROPDOWN_BG_ALPHA = 0.08f
private val ASO_DROPDOWN_H_PADDING = 12.dp
private val ASO_DROPDOWN_V_PADDING = 6.dp
private val ASO_DROPDOWN_CORNER = 6.dp
private val ASO_PREVIEW_ICON_SIZE = 36.dp
private val ASO_PREVIEW_BAR_CORNER = 16.dp
private val ASO_PREVIEW_BAR_H_PADDING = 16.dp
private val ASO_SECTION_HEADER_PADDING_H = 16.dp
private val ASO_SECTION_HEADER_PADDING_V = 10.dp
private val ASO_ROW_PADDING_H = 16.dp
private val ASO_ROW_PADDING_V = 12.dp

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

private fun VignetteShape.labelResId(): Int = when (this) {
    VignetteShape.RADIAL    -> R.string.settings_macropad_vignette_shape_radial
    VignetteShape.LETTERBOX -> R.string.settings_macropad_vignette_shape_letterbox
    VignetteShape.PILLARBOX -> R.string.settings_macropad_vignette_shape_pillarbox
    VignetteShape.TOP       -> R.string.settings_macropad_vignette_shape_top
    VignetteShape.BOTTOM    -> R.string.settings_macropad_vignette_shape_bottom
    VignetteShape.LEFT      -> R.string.settings_macropad_vignette_shape_left
    VignetteShape.RIGHT     -> R.string.settings_macropad_vignette_shape_right
}

/**
 * Full-screen overlay for per-layout ambient display settings.
 * Reads from and writes to the active [PadLayout] via [MacroPadState].
 */
@Composable
internal fun AmbientSettingsOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val layout by MacroPadState.activeLayout.collectAsState()

    // Stop all uinput virtual devices while ambient settings are open.
    // MacroPadViewModel.watchInjectorLifecycle() detects isAmbientSettingsActive=false
    // and restarts injectors automatically when this screen is dismissed.
    DisposableEffect(Unit) {
        AppLog.i(TAG, "AmbientSettingsOverlay visible \u2192 stopping injectors")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        onDispose {
            AppStateManager.setAmbientPreviewConfig(null)
            AppLog.i(TAG, "AmbientSettingsOverlay dismissed \u2192 injector restart handled by MacroPadViewModel watcher")
        }
    }

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

    // Preview mode: driven by AppStateManager so the secondary screen (AmbientMacroPadOverlay)
    // can also render the preview slider.
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val isInPreview = previewConfig != null
    // Color picker state hoisted to top level so ColorWheelPicker renders as a
    // full-screen sibling of the main settings Box (not nested inside the scroll column).
    var showColorPicker by remember { mutableStateOf(false) }

    fun commitLayout(block: PadLayout.() -> PadLayout) {
        val updated = MacroPadState.activeLayout.value ?: return
        MacroPadState.updateLayout(updated.block())
    }

    val labelSoft = stringResource(R.string.settings_macropad_vignette_transition_soft)
    val labelHard = stringResource(R.string.settings_macropad_vignette_transition_hard)
    // Pre-captured for use inside onPreviewClick lambdas (non-composable context).
    val labelDim              = stringResource(R.string.settings_macropad_dim)
    val labelVignetteArea     = stringResource(R.string.settings_macropad_vignette_visible_area)
    val labelVignetteTransition = stringResource(R.string.settings_macropad_vignette_transition)
    val labelVignetteOpacity  = stringResource(R.string.settings_macropad_vignette_opacity)

    // Back: exit preview / color picker first; the system Back then closes ambient settings.
    BackHandler(enabled = isInPreview || showColorPicker) {
        when {
            isInPreview -> {
                val config = previewConfig!!
                AppLog.d(TAG, "preview ${config.type} cancelled → restoring ${config.originalValue}")
                commitLayout {
                    when (config.type) {
                        AmbientPreviewType.DIM                 -> copy(ambientDim = config.originalValue)
                        AmbientPreviewType.VIGNETTE_AREA       -> copy(ambientVignetteVisibleArea = config.originalValue)
                        AmbientPreviewType.VIGNETTE_TRANSITION -> copy(ambientVignetteTransition = config.originalValue)
                        AmbientPreviewType.VIGNETTE_OPACITY    -> copy(ambientVignetteOpacity = config.originalValue)
                    }
                }
                AppStateManager.setAmbientPreviewConfig(null)
            }
            showColorPicker -> showColorPicker = false
        }
    }

    // Re-sync local slider vars when preview ends, so the main panel reflects
    // whatever value was confirmed / cancelled from the secondary screen.
    LaunchedEffect(isInPreview) {
        if (!isInPreview) {
            val l = MacroPadState.activeLayout.value ?: return@LaunchedEffect
            dimAlpha = l.ambientDim
            vignetteVisibleArea = l.ambientVignetteVisibleArea
            vignetteTransition = l.ambientVignetteTransition
            vignetteOpacity = l.ambientVignetteOpacity
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Block pointer events on empty areas from passing through to
                // the MacroPadScreen content behind this overlay.
                awaitEachGesture {
                    awaitFirstDown()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {

        // ── Main settings panel — hidden while previewing or picking a color ──
        if (!isInPreview && !showColorPicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.appBackground),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Title + Back button — styled like GlobalSettingsScreen TopAppBar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDone) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface,
                            )
                        }
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.onSurface)) {
                                    append(stringResource(R.string.pill_menu_ambient_settings))
                                }
                                withStyle(SpanStyle(color = colors.onSurfaceSecondary)) {
                                    append(" (${currentLayout.name})")
                                }
                            },
                            style    = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    AsoSectionHeader(text = stringResource(R.string.settings_section_general))

                    // ── Dim slider ────────────────────────────────────────────
                    AsoSliderRow(
                        label = labelDim,
                        value = dimAlpha,
                        valueRange = 0f..ASO_DIM_MAX,
                        formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                        accentColor = colors.accent,
                        onValueChange = { dimAlpha = it },
                        onValueChangeFinished = {
                            AppLog.d(TAG, "dim → $dimAlpha")
                            commitLayout { copy(ambientDim = dimAlpha) }
                        },
                        onPreviewClick = {
                            AppStateManager.setAmbientPreviewConfig(AmbientPreviewConfig(
                                type = AmbientPreviewType.DIM,
                                label = labelDim,
                                originalValue = dimAlpha,
                                valueRange = 0f..ASO_DIM_MAX,
                            ))
                        },
                    )

                    AsoSectionHeader(text = stringResource(R.string.settings_macropad_vignette))

                    // ── Vignette toggle ───────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_macropad_vignette),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = vignetteEnabled,
                            onCheckedChange = {
                                vignetteEnabled = it
                                AppLog.d(TAG, "vignette enabled → $it")
                                commitLayout { copy(ambientVignetteEnabled = it) }
                            },
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
                        AsoSliderRow(
                            label = labelVignetteArea,
                            value = vignetteVisibleArea,
                            valueRange = 0f..1f,
                            formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                            accentColor = colors.accent,
                            onValueChange = { vignetteVisibleArea = it },
                            onValueChangeFinished = {
                                AppLog.d(TAG, "vignette visibleArea → $vignetteVisibleArea")
                                commitLayout { copy(ambientVignetteVisibleArea = vignetteVisibleArea) }
                            },
                            onPreviewClick = {
                                AppStateManager.setAmbientPreviewConfig(AmbientPreviewConfig(
                                    type = AmbientPreviewType.VIGNETTE_AREA,
                                    label = labelVignetteArea,
                                    originalValue = vignetteVisibleArea,
                                    valueRange = 0f..1f,
                                ))
                            },
                        )

                        // Transition slider
                        AsoSliderRow(
                            label = labelVignetteTransition,
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
                            onPreviewClick = {
                                AppStateManager.setAmbientPreviewConfig(AmbientPreviewConfig(
                                    type = AmbientPreviewType.VIGNETTE_TRANSITION,
                                    label = labelVignetteTransition,
                                    originalValue = vignetteTransition,
                                    valueRange = 0f..1f,
                                ))
                            },
                        )

                        // Opacity slider
                        AsoSliderRow(
                            label = labelVignetteOpacity,
                            value = vignetteOpacity,
                            valueRange = 0f..1f,
                            formatLabel = { "${(it * ASO_PERCENT_DIVISOR).toInt()}%" },
                            accentColor = colors.accent,
                            onValueChange = { vignetteOpacity = it },
                            onValueChangeFinished = {
                                AppLog.d(TAG, "vignette opacity → $vignetteOpacity")
                                commitLayout { copy(ambientVignetteOpacity = vignetteOpacity) }
                            },
                            onPreviewClick = {
                                AppStateManager.setAmbientPreviewConfig(AmbientPreviewConfig(
                                    type = AmbientPreviewType.VIGNETTE_OPACITY,
                                    label = labelVignetteOpacity,
                                    originalValue = vignetteOpacity,
                                    valueRange = 0f..1f,
                                ))
                            },
                        )

                        // Color picker row — opens full-screen overlay (see below)
                        AsoColorRow(
                            vignetteColorInt = vignetteColorInt,
                            accentColor = colors.accent,
                            onShowPicker = { showColorPicker = true },
                        )
                    }
                }
            }
        }

        // ── Color picker — full-screen in-tree overlay ────────────────────────
        // Rendered as a sibling of the main settings Box so it truly fills the screen.
        if (showColorPicker) {
            ColorWheelPicker(
                initialColor = Color(vignetteColorInt),
                onColorSelected = { color ->
                    vignetteColorInt = color.toArgb()
                    AppLog.d(TAG, "vignette color → 0x${Integer.toHexString(vignetteColorInt)}")
                    commitLayout { copy(ambientVignetteColor = color.toArgb()) }
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false },
            )
        }
    }
}

/**
 * Section header used in ambient settings to match the shared settings visual language.
 *
 * @param text Header text displayed in uppercase styling.
 */
@Composable
private fun AsoSectionHeader(text: String) {
    val colors = LocalAppColors.current
    Text(
        text = text.uppercase(Locale.ROOT),
        color = colors.accent,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = ASO_SECTION_HEADER_PADDING_H, vertical = ASO_SECTION_HEADER_PADDING_V),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Slider row with preview eye-button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AsoSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatLabel: (Float) -> String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    onPreviewClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
            Text(text = formatLabel(value), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            modifier = Modifier.weight(2f),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colors.onSurfaceSecondary.copy(alpha = 0.3f),
            ),
        )
        IconButton(
            onClick = onPreviewClick,
            modifier = Modifier.size(ASO_PREVIEW_ICON_SIZE),
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = stringResource(R.string.ambient_preview),
                tint = accentColor.copy(alpha = 0.7f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview bar — bottom sheet with live slider + cancel/confirm
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AsoPreviewBar(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatLabel: (Float) -> String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.surface.copy(alpha = 0.95f),
                RoundedCornerShape(topStart = ASO_PREVIEW_BAR_CORNER, topEnd = ASO_PREVIEW_BAR_CORNER),
            )
            .padding(horizontal = ASO_PREVIEW_BAR_H_PADDING, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = colors.onSurface, style = MaterialTheme.typography.labelMedium)
            Text(text = formatLabel(value), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.settings_color_cancel),
                    tint = colors.onSurfaceSecondary,
                )
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = colors.onSurfaceSecondary.copy(alpha = 0.3f),
                ),
            )
            IconButton(onClick = onConfirm) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.settings_color_apply),
                    tint = accentColor,
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
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_shape),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                                style = MaterialTheme.typography.bodyMedium,
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
    onShowPicker: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable { onShowPicker() }
            .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_macropad_vignette_color),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
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
