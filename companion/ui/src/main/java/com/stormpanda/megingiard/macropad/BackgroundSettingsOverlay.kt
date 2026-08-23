package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "BackgroundSettingsOverlay"

private const val ASO_DIM_MAX = 0.9f
private const val ASO_DIM_STEP = 0.05f
private const val ASO_PERCENT_DIVISOR = 100f
private val ASO_PREVIEW_BAR_CORNER = 16.dp
private val ASO_PREVIEW_BAR_H_PADDING = 16.dp

private const val ASO_SMOOTHING_OFF = 0f
private const val ASO_SMOOTHING_LIGHT = 1f
private const val ASO_SMOOTHING_MEDIUM = 2f
private const val ASO_SMOOTHING_STRONG = 3f

private const val ASO_SMOOTHING_VAL_LIGHT = 75
private const val ASO_SMOOTHING_VAL_MEDIUM = 80
private const val ASO_SMOOTHING_VAL_STRONG = 85

private sealed interface AmbientCategory {
    data object General : AmbientCategory

    data class Cutout(
        val id: String,
    ) : AmbientCategory
}

@Composable
internal fun BackgroundSettingsOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val layout by MacroPadState.activeLayout.collectAsState()

    DisposableEffect(Unit) {
        AppLog.i(TAG, "BackgroundSettingsOverlay visible")
        onDispose {
            AmbientPreviewManager.setConfig(null)
            AppLog.i(TAG, "BackgroundSettingsOverlay dismissed")
        }
    }

    val currentLayout = layout ?: return

    var dimAlpha by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.ambientDim) }
    var edgeBlendWidth by remember(currentLayout.id) { mutableFloatStateOf(currentLayout.mirrorEdgeBlendWidth) }
    var pendingProjectionCutout by remember { mutableStateOf<ScreenCutout?>(null) }
    val previewConfig by AmbientPreviewManager.config.collectAsState()
    val isInPreview = previewConfig != null

    var selectedCategory by remember { mutableStateOf<AmbientCategory>(AmbientCategory.General) }

    LaunchedEffect(currentLayout.mirrorCutouts) {
        val current = selectedCategory
        if (current is AmbientCategory.Cutout && currentLayout.mirrorCutouts.none { it.id == current.id }) {
            selectedCategory = AmbientCategory.General
        }
    }

    val categories =
        remember(currentLayout.mirrorCutouts) {
            listOf(AmbientCategory.General) + currentLayout.mirrorCutouts.map { AmbientCategory.Cutout(it.id) }
        }

    LaunchedEffect(categories) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            val currentIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
            val nextIndex =
                when (direction) {
                    BumperDirection.PREV -> (currentIndex - 1 + categories.size) % categories.size
                    BumperDirection.NEXT -> (currentIndex + 1) % categories.size
                }
            selectedCategory = categories[nextIndex]
        }
    }

    fun commitLayout(block: PadLayout.() -> PadLayout) {
        val updated = MacroPadState.activeLayout.value ?: return
        MacroPadState.updateLayout(updated.block())
    }

    val labelDim = stringResource(R.string.settings_macropad_dim)
    val labelEdgeBlending = stringResource(R.string.mirror_edge_blend_label)

    BackHandler(enabled = true) {
        if (isInPreview) {
            val config = previewConfig!!
            AppLog.d(TAG, "preview ${config.type} cancelled -> restoring ${config.originalValue}")
            commitLayout {
                when (config.type) {
                    AmbientPreviewType.DIM -> copy(ambientDim = config.originalValue)
                    AmbientPreviewType.EDGE_BLENDING -> copy(mirrorEdgeBlendWidth = config.originalValue)
                }
            }
            AmbientPreviewManager.setConfig(null)
        } else {
            onDone()
        }
    }

    LaunchedEffect(isInPreview) {
        if (!isInPreview) {
            val l = MacroPadState.activeLayout.value ?: return@LaunchedEffect
            dimAlpha = l.ambientDim
            edgeBlendWidth = l.mirrorEdgeBlendWidth
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .blockPointerEvents(),
    ) {
        if (!isInPreview) {
            GamepadTwoPaneScaffold(
                scrollableDeck = false,
                sidebarContent = {
                    GamepadCategoryTile(
                        title = stringResource(R.string.settings_section_general),
                        icon = Icons.Rounded.Tune,
                        selected = selectedCategory is AmbientCategory.General,
                        onClick = { selectedCategory = AmbientCategory.General },
                    )

                    if (currentLayout.mirrorCutouts.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .background(
                                        color = colors.surface.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                    ).border(
                                        width = 1.dp,
                                        color = colors.subduedBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    ).padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = colors.onSurfaceSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_mirror_no_cutouts),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        currentLayout.mirrorCutouts.forEachIndexed { index, cutout ->
                            val cutoutTitle =
                                cutout.name.ifBlank {
                                    stringResource(R.string.settings_mirror_cutout_default_name_fmt, index + 1)
                                }
                            GamepadCategoryTile(
                                title = cutoutTitle,
                                icon = Icons.Rounded.Crop,
                                selected = selectedCategory == AmbientCategory.Cutout(cutout.id),
                                onClick = { selectedCategory = AmbientCategory.Cutout(cutout.id) },
                            )
                        }
                    }
                },
                content = {
                    when (val category = selectedCategory) {
                        is AmbientCategory.General -> {
                            GamepadDeck(
                                title = stringResource(R.string.settings_section_general),
                                accentColor = colors.accent,
                            ) {
                                GamepadStepperCard(
                                    modifier = Modifier.firstDeckItem(),
                                    title = labelDim,
                                    description = stringResource(R.string.help_ambient_dim_desc),
                                    valueText = "${(dimAlpha * ASO_PERCENT_DIVISOR).toInt()}%",
                                    icon = Icons.Rounded.Opacity,
                                    onDecrement = {
                                        val newVal = (dimAlpha - ASO_DIM_STEP).coerceIn(0f, ASO_DIM_MAX)
                                        dimAlpha = newVal
                                        commitLayout { copy(ambientDim = newVal) }
                                    },
                                    onIncrement = {
                                        val newVal = (dimAlpha + ASO_DIM_STEP).coerceIn(0f, ASO_DIM_MAX)
                                        dimAlpha = newVal
                                        commitLayout { copy(ambientDim = newVal) }
                                    },
                                )

                                val blendIndex = (edgeBlendWidth / 25f).roundToInt().coerceIn(0, 4)
                                val blendLabels =
                                    listOf(
                                        stringResource(R.string.mirror_edge_blend_strength_off),
                                        stringResource(R.string.mirror_edge_blend_strength_light),
                                        stringResource(R.string.mirror_edge_blend_strength_medium),
                                        stringResource(R.string.mirror_edge_blend_strength_strong),
                                        stringResource(R.string.mirror_edge_blend_strength_max),
                                    )
                                GamepadChoiceCard(
                                    title = labelEdgeBlending,
                                    description = stringResource(R.string.help_ambient_blend_desc),
                                    selectedText = blendLabels[blendIndex],
                                    icon = Icons.Rounded.Grain,
                                    onPrevious = {
                                        val newIdx = (blendIndex - 1 + blendLabels.size) % blendLabels.size
                                        edgeBlendWidth = newIdx * 25f
                                        commitLayout { copy(mirrorEdgeBlendWidth = edgeBlendWidth) }
                                    },
                                    onNext = {
                                        val newIdx = (blendIndex + 1) % blendLabels.size
                                        edgeBlendWidth = newIdx * 25f
                                        commitLayout { copy(mirrorEdgeBlendWidth = edgeBlendWidth) }
                                    },
                                )

                                // Follow Touch Cutout target
                                val followCutoutId = currentLayout.mirrorCutouts.find { it.followTouch }?.id
                                val cutoutsList = currentLayout.mirrorCutouts
                                val followOptions = listOf<String?>(null) + cutoutsList.map { it.id }
                                val followIdx = followOptions.indexOf(followCutoutId).coerceAtLeast(0)
                                val followSelectedText =
                                    if (followCutoutId == null) {
                                        stringResource(R.string.settings_mirror_follow_touch_off)
                                    } else {
                                        val defName = stringResource(R.string.settings_mirror_cutout_default)
                                        cutoutsList.find { it.id == followCutoutId }?.name?.ifBlank { defName } ?: defName
                                    }

                                GamepadChoiceCard(
                                    title = stringResource(R.string.settings_mirror_follow_touch),
                                    description = stringResource(R.string.settings_mirror_follow_touch_desc),
                                    selectedText = followSelectedText,
                                    icon = Icons.Rounded.TouchApp,
                                    enabled = cutoutsList.isNotEmpty(),
                                    onPrevious = {
                                        if (followOptions.isNotEmpty()) {
                                            val newIdx = (followIdx - 1 + followOptions.size) % followOptions.size
                                            val newId = followOptions[newIdx]
                                            val updated = currentLayout.mirrorCutouts.map { it.copy(followTouch = (it.id == newId)) }
                                            commitLayout { copy(mirrorCutouts = updated, mirrorFollowActive = (newId != null)) }
                                            ScreenCaptureManager.setFollowActive(newId != null, persist = false)
                                        }
                                    },
                                    onNext = {
                                        if (followOptions.isNotEmpty()) {
                                            val newIdx = (followIdx + 1) % followOptions.size
                                            val newId = followOptions[newIdx]
                                            val updated = currentLayout.mirrorCutouts.map { it.copy(followTouch = (it.id == newId)) }
                                            commitLayout { copy(mirrorCutouts = updated, mirrorFollowActive = (newId != null)) }
                                            ScreenCaptureManager.setFollowActive(newId != null, persist = false)
                                        }
                                    },
                                )
                            }
                        }

                        is AmbientCategory.Cutout -> {
                            val cutoutIndex = currentLayout.mirrorCutouts.indexOfFirst { it.id == category.id }
                            val cutout = currentLayout.mirrorCutouts.getOrNull(cutoutIndex)
                            if (cutout != null) {
                                val cutoutTitle =
                                    cutout.name.ifBlank {
                                        stringResource(R.string.settings_mirror_cutout_default_name_fmt, cutoutIndex + 1)
                                    }
                                GamepadDeck(
                                    title = cutoutTitle,
                                    accentColor = colors.accent,
                                ) {
                                    GamepadTextFieldCard(
                                        modifier = Modifier.firstDeckItem(),
                                        title = stringResource(R.string.macropad_cutout_rename_title),
                                        description = stringResource(R.string.macropad_cutout_rename_desc),
                                        value = cutout.name,
                                        placeholder = stringResource(R.string.mirror_editor_cutout_name_hint),
                                        icon = Icons.Rounded.Edit,
                                        itemKey = "cutout_${cutout.id}_rename",
                                        onValueChange = { newName ->
                                            val trimmed = newName.trim()
                                            val updated =
                                                currentLayout.mirrorCutouts.map {
                                                    if (it.id == cutout.id) it.copy(name = trimmed) else it
                                                }
                                            commitLayout { copy(mirrorCutouts = updated) }
                                        },
                                    )

                                    // Smoothing Carousel
                                    val smoothingModes =
                                        listOf(
                                            stringResource(R.string.mirror_smoothing_strength_off),
                                            stringResource(R.string.mirror_smoothing_strength_light),
                                            stringResource(R.string.mirror_smoothing_strength_medium),
                                            stringResource(R.string.mirror_smoothing_strength_strong),
                                        )
                                    val currentSmoothIdx =
                                        if (cutout.motionSmoothing) {
                                            when (cutout.motionSmoothingStrength) {
                                                ASO_SMOOTHING_VAL_LIGHT -> 1
                                                ASO_SMOOTHING_VAL_MEDIUM -> 2
                                                ASO_SMOOTHING_VAL_STRONG -> 3
                                                else -> 3
                                            }
                                        } else {
                                            0
                                        }

                                    GamepadChoiceCard(
                                        title = stringResource(R.string.settings_mirror_follow_smoothing),
                                        description = stringResource(R.string.settings_mirror_follow_smoothing_desc),
                                        selectedText = smoothingModes[currentSmoothIdx],
                                        icon = Icons.Rounded.Tune,
                                        itemKey = "cutout_${cutout.id}_smoothing",
                                        onPrevious = {
                                            val nextIdx = (currentSmoothIdx - 1 + smoothingModes.size) % smoothingModes.size
                                            val isSmooth = nextIdx > 0
                                            val strength =
                                                when (nextIdx) {
                                                    1 -> ASO_SMOOTHING_VAL_LIGHT
                                                    2 -> ASO_SMOOTHING_VAL_MEDIUM
                                                    else -> ASO_SMOOTHING_VAL_STRONG
                                                }
                                            val updated =
                                                currentLayout.mirrorCutouts.map {
                                                    if (it.id ==
                                                        cutout.id
                                                    ) {
                                                        it.copy(motionSmoothing = isSmooth, motionSmoothingStrength = strength)
                                                    } else {
                                                        it
                                                    }
                                                }
                                            commitLayout { copy(mirrorCutouts = updated) }
                                        },
                                        onNext = {
                                            val nextIdx = (currentSmoothIdx + 1) % smoothingModes.size
                                            val isSmooth = nextIdx > 0
                                            val strength =
                                                when (nextIdx) {
                                                    1 -> ASO_SMOOTHING_VAL_LIGHT
                                                    2 -> ASO_SMOOTHING_VAL_MEDIUM
                                                    else -> ASO_SMOOTHING_VAL_STRONG
                                                }
                                            val updated =
                                                currentLayout.mirrorCutouts.map {
                                                    if (it.id ==
                                                        cutout.id
                                                    ) {
                                                        it.copy(motionSmoothing = isSmooth, motionSmoothingStrength = strength)
                                                    } else {
                                                        it
                                                    }
                                                }
                                            commitLayout { copy(mirrorCutouts = updated) }
                                        },
                                    )

                                    // Touch Projection
                                    GamepadToggleCard(
                                        title = stringResource(R.string.settings_mirror_touch_projection),
                                        description = stringResource(R.string.settings_mirror_touch_projection_desc),
                                        checked = cutout.touchProjectionEnabled,
                                        icon = Icons.Rounded.Mouse,
                                        itemKey = "cutout_${cutout.id}_projection",
                                        onCheckedChange = { isChecked ->
                                            if (isChecked && currentLayout.backgroundTouchpad.enabled) {
                                                pendingProjectionCutout = cutout
                                            } else {
                                                val updated =
                                                    currentLayout.mirrorCutouts.map {
                                                        if (it.id == cutout.id) it.copy(touchProjectionEnabled = isChecked) else it
                                                    }
                                                commitLayout { copy(mirrorCutouts = updated) }
                                                if (isChecked) {
                                                    ScreenCaptureManager.setLocked(true)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        // Projection conflict alert
        if (pendingProjectionCutout != null) {
            val targetCutout = pendingProjectionCutout!!
            AppAlertDialog(
                onDismissRequest = { pendingProjectionCutout = null },
                title = {
                    Text(
                        text = stringResource(R.string.macropad_projection_conflict_touchpad_title),
                        color = colors.onSurface,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.macropad_projection_conflict_touchpad_body),
                        color = colors.onSurfaceSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updatedCutouts =
                                currentLayout.mirrorCutouts.map { c ->
                                    if (c.id == targetCutout.id) c.copy(touchProjectionEnabled = true) else c
                                }
                            commitLayout {
                                copy(
                                    mirrorCutouts = updatedCutouts,
                                    backgroundTouchpad = backgroundTouchpad.copy(enabled = false),
                                )
                            }
                            ScreenCaptureManager.setLocked(true)
                            pendingProjectionCutout = null
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.macropad_projection_conflict_touchpad_confirm),
                            color = colors.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingProjectionCutout = null }) {
                        Text(text = stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                    }
                },
            )
        }
    }
}

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
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.subduedBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatLabel(value),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors =
                    SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.macropad_editor_done), color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
