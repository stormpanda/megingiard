package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "BackgroundSettingsOverlay"

private const val ASO_DIM_MAX = 0.9f
private const val ASO_DIM_STEP = 0.05f
private const val ASO_PERCENT_DIVISOR = 100f

private const val ASO_EDGE_BLEND_MIN = 0f
private const val ASO_EDGE_BLEND_MAX = 100f
private const val ASO_EDGE_BLEND_STEP = 5f

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

    GamepadTwoPaneScaffold(
        modifier = Modifier.fillMaxSize(),
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
                        GamepadSliderCard(
                            modifier = Modifier.firstDeckItem(),
                            title = labelDim,
                            description = stringResource(R.string.help_ambient_dim_desc),
                            value = dimAlpha,
                            valueRange = 0f..ASO_DIM_MAX,
                            step = ASO_DIM_STEP,
                            icon = Icons.Rounded.Opacity,
                            valueLabel = "${(dimAlpha * ASO_PERCENT_DIVISOR).roundToInt()}%",
                            onValueChange = { newVal ->
                                dimAlpha = newVal
                                commitLayout { copy(ambientDim = newVal) }
                            },
                        )

                        val edgeBlendLabel =
                            if (edgeBlendWidth.roundToInt() == 0) {
                                stringResource(R.string.mirror_edge_blend_strength_off)
                            } else {
                                "${edgeBlendWidth.roundToInt()} dp"
                            }
                        GamepadSliderCard(
                            title = labelEdgeBlending,
                            description = stringResource(R.string.help_ambient_blend_desc),
                            value = edgeBlendWidth,
                            valueRange = ASO_EDGE_BLEND_MIN..ASO_EDGE_BLEND_MAX,
                            step = ASO_EDGE_BLEND_STEP,
                            icon = Icons.Rounded.Grain,
                            valueLabel = edgeBlendLabel,
                            onValueChange = { newVal ->
                                edgeBlendWidth = newVal
                                commitLayout { copy(mirrorEdgeBlendWidth = newVal) }
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
