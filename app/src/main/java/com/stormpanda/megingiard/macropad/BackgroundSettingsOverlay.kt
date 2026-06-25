package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.stormpanda.megingiard.ui.blockPointerEvents
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppTextField
import androidx.compose.material.icons.rounded.Edit
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt


private const val TAG = "BackgroundSettingsOverlay"

// ── Slider bounds ───────────────────────────────────────────────────────────
private const val ASO_DIM_MAX = 0.9f
private const val ASO_PERCENT_DIVISOR = 100f
private val ASO_SWATCH_SIZE = 24.dp
private val ASO_DROPDOWN_H_PADDING = 12.dp
private val ASO_DROPDOWN_V_PADDING = 6.dp
private val ASO_PREVIEW_ICON_SIZE = 36.dp
private val ASO_PREVIEW_BAR_CORNER = 16.dp
private val ASO_PREVIEW_BAR_H_PADDING = 16.dp
private val ASO_SECTION_HEADER_PADDING_H = 16.dp
private val ASO_SECTION_HEADER_PADDING_V = 10.dp
private val ASO_ROW_PADDING_H = 16.dp
private val ASO_ROW_PADDING_V = 12.dp
private val ASO_EDIT_ICON_SIZE = 28.dp
private val ASO_EDIT_ICON_INNER_SIZE = 18.dp
private val ASO_SPACING_8 = 8.dp
private val ASO_CUTOUT_ROW_V_PADDING = 4.dp

private const val ASO_SMOOTHING_OFF = 0f
private const val ASO_SMOOTHING_LIGHT = 1f
private const val ASO_SMOOTHING_MEDIUM = 2f
private const val ASO_SMOOTHING_STRONG = 3f

private const val ASO_SMOOTHING_VAL_LIGHT = 75
private const val ASO_SMOOTHING_VAL_MEDIUM = 80
private const val ASO_SMOOTHING_VAL_STRONG = 85

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────


/**
 * Full-screen overlay for per-layout ambient display settings.
 * Reads from and writes to the active [PadLayout] via [MacroPadState].
 */
@Composable
internal fun BackgroundSettingsOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val layout by MacroPadState.activeLayout.collectAsState()

    // Stop all uinput virtual devices while ambient settings are open.
    // MacroPadViewModel.watchInjectorLifecycle() detects isBackgroundSettingsActive=false
    // and restarts injectors automatically when this screen is dismissed.
    DisposableEffect(Unit) {
        AppLog.i(TAG, "BackgroundSettingsOverlay visible \u2192 stopping injectors")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        onDispose {
            AppStateManager.setAmbientPreviewConfig(null)
            AppLog.i(TAG, "BackgroundSettingsOverlay dismissed → injector restart handled by MacroPadViewModel watcher")
        }
    }

    val currentLayout = layout
    if (currentLayout == null) {
        onDone()
        return
    }

    // Local slider state for smooth dragging — committed on finger up via updateLayout.
    var dimAlpha by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientDim) }
    var edgeBlendWidth by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.mirrorEdgeBlendWidth) }
    var renamingCutout by remember { mutableStateOf<ScreenCutout?>(null) }
    var renameText by remember(renamingCutout) { mutableStateOf(renamingCutout?.name ?: "") }
    val localSmoothingValues = remember(currentLayout.id) { mutableStateMapOf<String, Float>() }

    // Preview mode: driven by AppStateManager so the secondary screen (BackgroundMacroPadOverlay)
    // can also render the preview slider.
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val isInPreview = previewConfig != null

    fun commitLayout(block: PadLayout.() -> PadLayout) {
        val updated = MacroPadState.activeLayout.value ?: return
        MacroPadState.updateLayout(updated.block())
    }

    // Pre-captured for use inside onPreviewClick lambdas (non-composable context).
    val labelDim              = stringResource(R.string.settings_macropad_dim)
    val labelEdgeBlending     = stringResource(R.string.mirror_edge_blend_label)

    // Back: exit preview first; otherwise close background settings.
    BackHandler(enabled = true) {
        if (isInPreview) {
            val config = previewConfig!!
            AppLog.d(TAG, "preview ${config.type} cancelled → restoring ${config.originalValue}")
            commitLayout {
                when (config.type) {
                    AmbientPreviewType.DIM -> copy(ambientDim = config.originalValue)
                    AmbientPreviewType.EDGE_BLENDING -> copy(mirrorEdgeBlendWidth = config.originalValue)
                }
            }
            AppStateManager.setAmbientPreviewConfig(null)
        } else {
            onDone()
        }
    }

    // Re-sync local slider vars when preview ends, so the main panel reflects
    // whatever value was confirmed / cancelled from the secondary screen.
    LaunchedEffect(isInPreview) {
        if (!isInPreview) {
            val l = MacroPadState.activeLayout.value ?: return@LaunchedEffect
            dimAlpha = l.ambientDim
            edgeBlendWidth = l.mirrorEdgeBlendWidth
        }
    }

    var showAmbientHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockPointerEvents(),
    ) {

        // ── Main settings panel — hidden while previewing ──
        if (!isInPreview) {
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
                        HelpIconButton(onClick = { showAmbientHelp = true })
                    }

                    AsoSectionHeader(text = stringResource(R.string.settings_section_general))

                    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
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

                        AppDivider()

                        AsoSliderRow(
                            label = labelEdgeBlending,
                            value = edgeBlendWidth,
                            valueRange = 0f..100f,
                            formatLabel = { v ->
                                when (v.roundToInt()) {
                                    in 0..12 -> context.getString(R.string.mirror_edge_blend_strength_off)
                                    in 13..37 -> context.getString(R.string.mirror_edge_blend_strength_light)
                                    in 38..62 -> context.getString(R.string.mirror_edge_blend_strength_medium)
                                    in 63..87 -> context.getString(R.string.mirror_edge_blend_strength_strong)
                                    else -> context.getString(R.string.mirror_edge_blend_strength_max)
                                }
                            },
                            accentColor = colors.accent,
                            onValueChange = { value ->
                                val idx = (value / 25f).roundToInt().coerceIn(0, 4)
                                edgeBlendWidth = idx * 25f
                            },
                            onValueChangeFinished = {
                                AppLog.d(TAG, "edge blend → $edgeBlendWidth")
                                commitLayout { copy(mirrorEdgeBlendWidth = edgeBlendWidth) }
                            },
                            onPreviewClick = {
                                AppStateManager.setAmbientPreviewConfig(AmbientPreviewConfig(
                                    type = AmbientPreviewType.EDGE_BLENDING,
                                    label = labelEdgeBlending,
                                    originalValue = edgeBlendWidth,
                                    valueRange = 0f..100f,
                                ))
                            },
                        )

                        AppDivider()

                        AppSettingsRow {
                            Column(modifier = Modifier.weight(1f).padding(end = ASO_SPACING_8)) {
                                Text(
                                    text = stringResource(R.string.settings_mirror_follow_touch),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.settings_mirror_follow_touch_desc),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            val selectedCutoutId = currentLayout.mirrorCutouts.find { it.followTouch }?.id
                            val dropdownOptions = remember(currentLayout.mirrorCutouts) {
                                listOf<String?>(null) + currentLayout.mirrorCutouts.map { it.id }
                            }
                            AppDropdown(
                                selected = selectedCutoutId,
                                options = dropdownOptions,
                                optionText = { id ->
                                    if (id == null) {
                                        stringResource(R.string.settings_mirror_follow_touch_off)
                                    } else {
                                        val defaultName = stringResource(R.string.settings_mirror_cutout_default)
                                        currentLayout.mirrorCutouts.find { it.id == id }?.name?.ifBlank { defaultName } ?: defaultName
                                    }
                                },
                                onSelected = { id ->
                                    AppLog.d(TAG, "mirrorFollowCutoutId → $id")
                                    val updatedCutouts = currentLayout.mirrorCutouts.map { c ->
                                        c.copy(followTouch = (c.id == id))
                                    }
                                    commitLayout {
                                        copy(
                                            mirrorCutouts = updatedCutouts,
                                            mirrorFollowActive = (id != null)
                                        )
                                    }
                                    ScreenCaptureManager.setFollowActive(id != null, persist = false)
                                }
                            )
                        }
                    }

                    AsoSectionHeader(text = stringResource(R.string.settings_section_cutouts))

                    if (currentLayout.mirrorCutouts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_mirror_no_cutouts),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
                            currentLayout.mirrorCutouts.forEachIndexed { index, cutout ->
                                if (index > 0) {
                                    AppDivider()
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ASO_ROW_PADDING_H, vertical = ASO_ROW_PADDING_V)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = ASO_SPACING_8)
                                    ) {
                                        Text(
                                            text = cutout.name.ifBlank { stringResource(R.string.settings_mirror_cutout_default_name_fmt, index + 1) },
                                            color = colors.onSurface,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { renamingCutout = cutout },
                                            modifier = Modifier.size(ASO_EDIT_ICON_SIZE)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = stringResource(R.string.macropad_editor_rename),
                                                tint = colors.accent,
                                                modifier = Modifier.size(ASO_EDIT_ICON_INNER_SIZE)
                                            )
                                        }
                                    }

                                    // Motion Smoothing Slider Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = ASO_CUTOUT_ROW_V_PADDING),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val cutoutValue = if (cutout.motionSmoothing) {
                                            when (cutout.motionSmoothingStrength) {
                                                ASO_SMOOTHING_VAL_LIGHT -> ASO_SMOOTHING_LIGHT
                                                ASO_SMOOTHING_VAL_MEDIUM -> ASO_SMOOTHING_MEDIUM
                                                ASO_SMOOTHING_VAL_STRONG -> ASO_SMOOTHING_STRONG
                                                else -> ASO_SMOOTHING_STRONG
                                            }
                                        } else {
                                            ASO_SMOOTHING_OFF
                                        }
                                        val sliderValue = localSmoothingValues[cutout.id] ?: cutoutValue
                                        Column(modifier = Modifier.weight(1f).padding(end = ASO_SPACING_8)) {
                                            Text(
                                                text = stringResource(R.string.settings_mirror_follow_smoothing),
                                                color = colors.onSurface,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            val strengthText = when (sliderValue.roundToInt()) {
                                                ASO_SMOOTHING_OFF.toInt() -> stringResource(R.string.mirror_smoothing_strength_off)
                                                ASO_SMOOTHING_LIGHT.toInt() -> stringResource(R.string.mirror_smoothing_strength_light)
                                                ASO_SMOOTHING_MEDIUM.toInt() -> stringResource(R.string.mirror_smoothing_strength_medium)
                                                ASO_SMOOTHING_STRONG.toInt() -> stringResource(R.string.mirror_smoothing_strength_strong)
                                                else -> stringResource(R.string.mirror_smoothing_strength_off)
                                            }
                                            Text(
                                                text = strengthText,
                                                color = colors.onSurfaceSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        Slider(
                                            modifier = Modifier.weight(1.5f),
                                            value = sliderValue,
                                            onValueChange = { newValue ->
                                                val idx = newValue.roundToInt().coerceIn(ASO_SMOOTHING_OFF.toInt(), ASO_SMOOTHING_STRONG.toInt())
                                                localSmoothingValues[cutout.id] = idx.toFloat()
                                            },
                                            onValueChangeFinished = {
                                                val valFloat = localSmoothingValues[cutout.id] ?: cutoutValue
                                                val idx = valFloat.roundToInt().coerceIn(ASO_SMOOTHING_OFF.toInt(), ASO_SMOOTHING_STRONG.toInt())
                                                val isSmooth = idx > 0
                                                val strength = when (idx) {
                                                    ASO_SMOOTHING_LIGHT.toInt() -> ASO_SMOOTHING_VAL_LIGHT
                                                    ASO_SMOOTHING_MEDIUM.toInt() -> ASO_SMOOTHING_VAL_MEDIUM
                                                    ASO_SMOOTHING_STRONG.toInt() -> ASO_SMOOTHING_VAL_STRONG
                                                    else -> cutout.motionSmoothingStrength
                                                }
                                                AppLog.d(TAG, "cutout '${cutout.name}' smoothing slider finished → $idx (strength: $strength)")
                                                val updatedCutouts = currentLayout.mirrorCutouts.map { c ->
                                                    if (c.id == cutout.id) {
                                                        c.copy(
                                                            motionSmoothing = isSmooth,
                                                            motionSmoothingStrength = strength
                                                        )
                                                    } else c
                                                }
                                                commitLayout {
                                                    copy(mirrorCutouts = updatedCutouts)
                                                }
                                            },
                                            valueRange = ASO_SMOOTHING_OFF..ASO_SMOOTHING_STRONG,
                                            steps = 2,
                                        )
                                    }

                                    // Touch Projection Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = ASO_CUTOUT_ROW_V_PADDING),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.settings_mirror_touch_projection),
                                                color = colors.onSurface,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_mirror_touch_projection_desc),
                                                color = colors.onSurfaceSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        Switch(
                                            checked = cutout.touchProjectionEnabled,
                                            onCheckedChange = { isChecked ->
                                                AppLog.d(TAG, "cutout '${cutout.name}' touchProjectionEnabled → $isChecked")
                                                val updatedCutouts = currentLayout.mirrorCutouts.map { c ->
                                                    if (c.id == cutout.id) c.copy(touchProjectionEnabled = isChecked) else c
                                                }
                                                commitLayout { copy(mirrorCutouts = updatedCutouts) }
                                                if (isChecked) {
                                                    ScreenCaptureManager.setLocked(true)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingCutout != null) {
        val targetCutout = renamingCutout!!
        InlineDialogOverlay(
            title = stringResource(R.string.mirror_editor_rename_cutout),
            onDismiss = { renamingCutout = null },
            widthFraction = 0.8f,
            buttonsRow = {
                TextButton(onClick = { renamingCutout = null }) {
                    Text(
                        text = stringResource(R.string.macropad_editor_cancel),
                        color = colors.onSurfaceSecondary
                    )
                }
                val newName = renameText.trim()
                TextButton(
                    onClick = {
                        val updatedCutouts = currentLayout.mirrorCutouts.map { c ->
                            if (c.id == targetCutout.id) c.copy(name = newName) else c
                        }
                        commitLayout { copy(mirrorCutouts = updatedCutouts) }
                        renamingCutout = null
                    },
                    enabled = true
                ) {
                    Text(
                        text = stringResource(R.string.macropad_editor_done),
                        color = colors.accent
                    )
                }
            }
        ) {
            AppTextField(
                value = renameText,
                onValueChange = { renameText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.mirror_editor_cutout_name_hint),
                        color = colors.onSurfaceSecondary
                    )
                }
            )
        }
    }

    BackgroundSettingsHelpModal(
        visible = showAmbientHelp,
        onDismiss = { showAmbientHelp = false },
    )
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
        color = colors.sectionHeaderColor,
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
    AppSettingsRow {
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

@Composable
private fun BackgroundSettingsHelpModal(visible: Boolean, onDismiss: () -> Unit) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_ambient_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_ambient_intro))

        HelpSection(stringResource(R.string.help_ambient_section_display))
        HelpEntry(
            icon = Icons.Rounded.Visibility,
            label = stringResource(R.string.help_ambient_dim_label),
            description = stringResource(R.string.help_ambient_dim_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_ambient_blend_label),
            description = stringResource(R.string.help_ambient_blend_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_ambient_smoothing_label),
            description = stringResource(R.string.help_ambient_smoothing_desc),
        )

        HelpSection(stringResource(R.string.help_ambient_section_cutouts))
        HelpEntry(
            label = stringResource(R.string.help_ambient_cutouts_label),
            description = stringResource(R.string.help_ambient_cutouts_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Visibility,
            label = stringResource(R.string.help_ambient_preview_label),
            description = stringResource(R.string.help_ambient_preview_desc),
        )
    }
}
