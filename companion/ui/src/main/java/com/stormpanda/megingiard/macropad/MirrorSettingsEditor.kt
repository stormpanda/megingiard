package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.mirror.CutoutMaskManager
import com.stormpanda.megingiard.mirror.CutoutSnapBackMode
import com.stormpanda.megingiard.mirror.MAX_SENSITIVITY
import com.stormpanda.megingiard.mirror.MAX_TRANSLUCENCY
import com.stormpanda.megingiard.mirror.MIN_SENSITIVITY
import com.stormpanda.megingiard.mirror.MIN_TRANSLUCENCY
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.mirror.VisualAutoTuneCoordinator
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "MirrorSettingsEditor"

private const val MSE_DIM_MAX = 0.9f
private const val MSE_DIM_STEP = 0.05f
private const val MSE_PERCENT_DIVISOR = 100f

private const val MSE_EDGE_BLEND_MIN = 0f
private const val MSE_EDGE_BLEND_MAX = 100f
private const val MSE_EDGE_BLEND_STEP = 5f

private const val MSE_SMOOTHING_VAL_LIGHT = 75
private const val MSE_SMOOTHING_VAL_MEDIUM = 80
private const val MSE_SMOOTHING_VAL_STRONG = 85

private const val MSE_TRANSLUCENCY_MIN = 0f
private const val MSE_TRANSLUCENCY_MAX = 100f
private const val MSE_TRANSLUCENCY_STEP = 1f

private const val MSE_SENSITIVITY_MIN = 0f
private const val MSE_SENSITIVITY_MAX = 255f
private const val MSE_SENSITIVITY_STEP = 1f

@Composable
internal fun MirrorDeck(
    profile: PadProfile,
    layout: PadLayout,
    accentColor: Color,
    onArrangeCutouts: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onEditCutout: (ScreenCutout) -> Unit,
) {
    AppLog.d(TAG, "MirrorDeck composition for profile=${profile.id}, layout=${layout.id}")
    val colors = LocalAppColors.current
    val firstItemFocusRequester = remember { FocusRequester() }

    // ── 1. Top action card: Edit Screen Mirroring Layout on secondary canvas ─
    GamepadActionCard(
        title = stringResource(R.string.mirror_editor_arrange_cutouts_title),
        description = stringResource(R.string.mirror_editor_arrange_cutouts_desc),
        icon = Icons.Rounded.Crop,
        onClick = onArrangeCutouts,
        modifier = Modifier.firstDeckItem().focusRequester(firstItemFocusRequester),
    )

    // ── 2. Directly under Arrange Cutouts with no headline: Advanced Settings ───
    GamepadActionCard(
        title = stringResource(R.string.settings_mirror_advanced_title),
        description = stringResource(R.string.settings_mirror_advanced_desc),
        icon = Icons.Rounded.Tune,
        onClick = onOpenAdvancedSettings,
    )

    // ── 3. Cutouts Section ───────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.settings_mirror_cutouts_header),
        color = accentColor,
    )

    if (layout.mirrorCutouts.isEmpty()) {
        GamepadInfoBox(
            text = stringResource(R.string.settings_mirror_no_cutouts),
            icon = Icons.Rounded.Info,
            iconTint = colors.onSurfaceSecondary,
        )
    } else {
        layout.mirrorCutouts.forEachIndexed { index, cutout ->
            val cutoutTitle =
                cutout.name.ifBlank {
                    stringResource(R.string.settings_mirror_cutout_default_name_fmt, index + 1)
                }

            val smoothingText =
                if (cutout.motionSmoothing) {
                    when (cutout.motionSmoothingStrength) {
                        MSE_SMOOTHING_VAL_LIGHT -> stringResource(R.string.mirror_smoothing_strength_light)
                        MSE_SMOOTHING_VAL_MEDIUM -> stringResource(R.string.mirror_smoothing_strength_medium)
                        MSE_SMOOTHING_VAL_STRONG -> stringResource(R.string.mirror_smoothing_strength_strong)
                        else -> stringResource(R.string.mirror_smoothing_strength_off)
                    }
                } else {
                    stringResource(R.string.mirror_smoothing_strength_off)
                }

            val projectionText =
                if (cutout.touchProjectionEnabled) {
                    stringResource(R.string.settings_mirror_projection_on)
                } else {
                    stringResource(R.string.settings_mirror_projection_off)
                }

            val filterText =
                if (cutout.hasTransparencyMask) {
                    stringResource(R.string.settings_mirror_cutout_filter_transparency_mask)
                } else {
                    null
                }

            val summaryDesc =
                if (filterText != null) {
                    stringResource(
                        R.string.settings_mirror_cutout_summary_with_filter_fmt,
                        smoothingText,
                        filterText,
                        projectionText,
                    )
                } else {
                    stringResource(
                        R.string.settings_mirror_cutout_summary_fmt,
                        smoothingText,
                        projectionText,
                    )
                }

            GamepadActionCard(
                title = cutoutTitle,
                description = summaryDesc,
                icon = Icons.Rounded.Crop,
                onClick = { onEditCutout(cutout) },
            )
        }
    }
}

@Composable
internal fun CutoutSettingsSubPageContent(
    cutout: ScreenCutout,
    layout: PadLayout,
    accentColor: Color,
    onUpdateCutout: (ScreenCutout, disableTouchpad: Boolean) -> Unit,
    onDeleteCutout: (String) -> Unit,
    onOpenAdvancedCutoutSettings: () -> Unit,
) {
    AppLog.d(TAG, "CutoutSettingsSubPageContent: cutout=${cutout.id}")
    val colors = LocalAppColors.current

    val projectionFocusRequester = remember(cutout.id) { FocusRequester() }
    var restoreFocusTrigger by remember(cutout.id) { mutableIntStateOf(0) }

    LaunchedEffect(restoreFocusTrigger) {
        if (restoreFocusTrigger > 0) {
            try {
                projectionFocusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus requester not attached
            }
        }
    }

    if (layout.backgroundTouchpad.enabled) {
        GamepadInfoBox(
            text = stringResource(R.string.layout_settings_touchpad_incompatible_warning),
            description = stringResource(R.string.macropad_projection_conflict_touchpad_body),
            icon = Icons.Rounded.Warning,
            iconTint = colors.error,
        )
    }

    // 1. Cutout Name
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
            onUpdateCutout(cutout.copy(name = trimmed), false)
        },
    )

    // 2. Motion Smoothing
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
                MSE_SMOOTHING_VAL_LIGHT -> 1
                MSE_SMOOTHING_VAL_MEDIUM -> 2
                MSE_SMOOTHING_VAL_STRONG -> 3
                else -> 0
            }
        } else {
            0
        }

    val applySmoothIdx: (Int) -> Unit = { newIdx ->
        val isSmooth = newIdx > 0
        val strength =
            when (newIdx) {
                1 -> MSE_SMOOTHING_VAL_LIGHT
                2 -> MSE_SMOOTHING_VAL_MEDIUM
                3 -> MSE_SMOOTHING_VAL_STRONG
                else -> MSE_SMOOTHING_VAL_MEDIUM
            }
        onUpdateCutout(cutout.copy(motionSmoothing = isSmooth, motionSmoothingStrength = strength), false)
    }

    GamepadChoiceCard(
        title = stringResource(R.string.settings_mirror_follow_smoothing),
        description = stringResource(R.string.settings_mirror_follow_smoothing_desc),
        selectedText = smoothingModes[currentSmoothIdx],
        icon = Icons.Rounded.Grain,
        itemKey = "cutout_${cutout.id}_smoothing",
        onPrevious = { applySmoothIdx((currentSmoothIdx - 1 + smoothingModes.size) % smoothingModes.size) },
        onNext = { applySmoothIdx((currentSmoothIdx + 1) % smoothingModes.size) },
    )

    // 3. Touch Projection
    if (!cutout.touchProjectionEnabled && layout.backgroundTouchpad.enabled) {
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.settings_mirror_touch_projection),
            confirmTitle = stringResource(R.string.macropad_projection_conflict_touchpad_title),
            description = stringResource(R.string.settings_mirror_touch_projection_desc),
            confirmDescription = stringResource(R.string.macropad_projection_conflict_touchpad_body),
            actionText = stringResource(R.string.gamepad_action_enable),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            icon = Icons.Rounded.TouchApp,
            isDestructive = true,
            itemKey = "cutout_${cutout.id}_projection",
            cardFocusRequester = projectionFocusRequester,
            onConfirm = {
                onUpdateCutout(cutout.copy(touchProjectionEnabled = true, interactivePanZoom = false), true)
                ScreenCaptureManager.setLocked(true)
                restoreFocusTrigger++
            },
        )
    } else {
        GamepadToggleCard(
            title = stringResource(R.string.settings_mirror_touch_projection),
            description = stringResource(R.string.settings_mirror_touch_projection_desc),
            checked = cutout.touchProjectionEnabled,
            icon = Icons.Rounded.TouchApp,
            itemKey = "cutout_${cutout.id}_projection",
            cardFocusRequester = projectionFocusRequester,
            onCheckedChange = { isChecked ->
                onUpdateCutout(
                    cutout.copy(
                        touchProjectionEnabled = isChecked,
                        interactivePanZoom = if (isChecked) false else cutout.interactivePanZoom,
                    ),
                    false,
                )
                if (isChecked) {
                    ScreenCaptureManager.setLocked(true)
                }
            },
        )
    }

    // 4. Interactive Viewport (Pan & Zoom)
    GamepadToggleCard(
        title = stringResource(R.string.settings_cutout_interactive_viewport_title),
        description = stringResource(R.string.settings_cutout_interactive_viewport_desc),
        checked = cutout.interactivePanZoom,
        icon = Icons.Rounded.ZoomIn,
        itemKey = "cutout_${cutout.id}_interactive_viewport",
        onCheckedChange = { isChecked ->
            onUpdateCutout(
                cutout.copy(
                    interactivePanZoom = isChecked,
                    touchProjectionEnabled = if (isChecked) false else cutout.touchProjectionEnabled,
                ),
                false,
            )
        },
    )

    if (cutout.interactivePanZoom) {
        val snapBackModes =
            listOf(
                stringResource(R.string.settings_cutout_snap_back_off),
                stringResource(R.string.settings_cutout_snap_back_instant),
            )
        val currentSnapBackIdx = if (cutout.snapBackMode == CutoutSnapBackMode.OFF) 0 else 1

        GamepadChoiceCard(
            title = stringResource(R.string.settings_cutout_snap_back_title),
            description = stringResource(R.string.settings_cutout_snap_back_desc),
            selectedText = snapBackModes[currentSnapBackIdx],
            icon = Icons.Rounded.Replay,
            itemKey = "cutout_${cutout.id}_snap_back_mode",
            onPrevious = {
                val newMode = if (currentSnapBackIdx == 0) CutoutSnapBackMode.INSTANT else CutoutSnapBackMode.OFF
                onUpdateCutout(cutout.copy(snapBackMode = newMode), false)
            },
            onNext = {
                val newMode = if (currentSnapBackIdx == 0) CutoutSnapBackMode.INSTANT else CutoutSnapBackMode.OFF
                onUpdateCutout(cutout.copy(snapBackMode = newMode), false)
            },
        )
    }

    // 5. Render Above Mask
    GamepadToggleCard(
        title = stringResource(R.string.settings_cutout_render_above_mask_title),
        description = stringResource(R.string.settings_cutout_render_above_mask_desc),
        checked = cutout.renderAboveMask,
        icon = Icons.Rounded.Layers,
        itemKey = "cutout_${cutout.id}_render_above_mask",
        onCheckedChange = { isChecked ->
            onUpdateCutout(cutout.copy(renderAboveMask = isChecked), false)
        },
    )

    // 6. Advanced Cutout Settings (HUD / UI Isolation)
    GamepadActionCard(
        title = stringResource(R.string.settings_cutout_advanced_title),
        description = stringResource(R.string.settings_cutout_advanced_desc),
        icon = Icons.Rounded.Tune,
        itemKey = "cutout_${cutout.id}_advanced_settings",
        onClick = onOpenAdvancedCutoutSettings,
    )

    // 6. Delete Cutout Action
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_actions),
        color = accentColor,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_delete_cutout_title),
        confirmTitle = stringResource(R.string.macropad_delete_cutout_title),
        description = stringResource(R.string.macropad_delete_cutout_desc),
        confirmDescription = stringResource(R.string.macropad_delete_cutout_desc),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        icon = Icons.Rounded.Delete,
        isDestructive = true,
        itemKey = "cutout_${cutout.id}_delete",
        onConfirm = {
            onDeleteCutout(cutout.id)
        },
    )
}

@Composable
internal fun CutoutAdvancedSettingsSubPageContent(
    cutout: ScreenCutout,
    accentColor: Color,
    onUpdateCutout: (ScreenCutout) -> Unit,
) {
    AppLog.d(TAG, "CutoutAdvancedSettingsSubPageContent: cutout=${cutout.id}")
    val context = LocalContext.current
    val isCalibrating by VisualAutoTuneCoordinator.isCalibrating.collectAsStateWithLifecycle()
    val lastTunedPercent by VisualAutoTuneCoordinator.lastTunedPercent.collectAsStateWithLifecycle()
    var calibrationRevision by remember { mutableIntStateOf(0) }

    if (isCalibrating) {
        GamepadActionCard(
            modifier = Modifier.firstDeckItem(),
            title = stringResource(R.string.settings_mirror_auto_tuning_in_progress),
            description = stringResource(R.string.settings_mirror_auto_tuning_secondary_prompt),
            icon = Icons.Rounded.Tune,
            itemKey = "cutout_${cutout.id}_auto_tune_active",
            onClick = { VisualAutoTuneCoordinator.cancelCalibration() },
        )
    } else {
        val isCalibrated =
            remember(cutout.id, cutout.hasTransparencyMask, calibrationRevision) {
                CutoutMaskManager.hasMask(context, cutout.id) || cutout.hasTransparencyMask
            }

        if (isCalibrated) {
            val autoTuneDesc =
                lastTunedPercent?.let {
                    stringResource(R.string.settings_mirror_auto_tune_success, "$it%")
                } ?: stringResource(R.string.settings_cutout_recalibrate_smart_desc)

            GamepadActionCard(
                modifier = Modifier.firstDeckItem(),
                title = stringResource(R.string.settings_cutout_recalibrate_smart_title),
                description = autoTuneDesc,
                icon = Icons.Rounded.Tune,
                itemKey = "cutout_${cutout.id}_recalibrate_smart",
                onClick = {
                    VisualAutoTuneCoordinator.startCalibration(context, cutout) { updatedCutout, _ ->
                        calibrationRevision++
                        onUpdateCutout(updatedCutout)
                    }
                },
            )
        } else {
            GamepadActionCard(
                modifier = Modifier.firstDeckItem(),
                title = stringResource(R.string.settings_cutout_convert_smart_title),
                description = stringResource(R.string.settings_cutout_convert_smart_desc),
                icon = Icons.Rounded.FilterCenterFocus,
                itemKey = "cutout_${cutout.id}_convert_smart",
                onClick = {
                    VisualAutoTuneCoordinator.startCalibration(context, cutout) { updatedCutout, _ ->
                        calibrationRevision++
                        onUpdateCutout(updatedCutout)
                    }
                },
            )
        }

        // ── 2. Mask Settings & Fine-Tuning (if hasTransparencyMask) ──
        if (cutout.hasTransparencyMask) {
            val sensitivityPct =
                if (MSE_SENSITIVITY_MAX > 0f) {
                    (cutout.maskSensitivity * MSE_PERCENT_DIVISOR / MSE_SENSITIVITY_MAX).roundToInt()
                } else {
                    0
                }
            val sensitivityLabel =
                stringResource(
                    R.string.settings_cutout_sensitivity_value_fmt,
                    sensitivityPct,
                    cutout.maskSensitivity,
                )
            GamepadSliderCard(
                title = stringResource(R.string.settings_cutout_sensitivity_title),
                description = stringResource(R.string.settings_cutout_sensitivity_desc),
                value = cutout.maskSensitivity.toFloat(),
                valueRange = MSE_SENSITIVITY_MIN..MSE_SENSITIVITY_MAX,
                step = MSE_SENSITIVITY_STEP,
                fineStep = MSE_SENSITIVITY_STEP,
                icon = Icons.Rounded.Tune,
                valueLabel = sensitivityLabel,
                onValueChange = { newVal ->
                    val newSens = newVal.roundToInt().coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
                    AppLog.d(TAG, "Updating cutout ${cutout.id} maskSensitivity: $newSens")
                    onUpdateCutout(cutout.copy(maskSensitivity = newSens))
                },
            )

            val translucencyLabel =
                if (cutout.maskTranslucency > 0) {
                    "${cutout.maskTranslucency}%"
                } else {
                    stringResource(R.string.settings_mirror_cutout_translucency_off)
                }
            GamepadSliderCard(
                title = stringResource(R.string.settings_cutout_translucency_title),
                description = stringResource(R.string.settings_cutout_translucency_desc),
                value = cutout.maskTranslucency.toFloat(),
                valueRange = MSE_TRANSLUCENCY_MIN..MSE_TRANSLUCENCY_MAX,
                step = MSE_TRANSLUCENCY_STEP,
                fineStep = MSE_TRANSLUCENCY_STEP,
                icon = Icons.Rounded.Layers,
                valueLabel = translucencyLabel,
                onValueChange = { newVal ->
                    val newTranslucency = newVal.roundToInt().coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
                    AppLog.d(TAG, "Updating cutout ${cutout.id} maskTranslucency: $newTranslucency%")
                    onUpdateCutout(cutout.copy(maskTranslucency = newTranslucency))
                },
            )

            GamepadSectionHeader(
                text = stringResource(R.string.settings_cutout_features_section),
                color = accentColor,
            )

            GamepadToggleCard(
                title = stringResource(R.string.settings_cutout_cavity_healing_title),
                description = stringResource(R.string.settings_cutout_cavity_healing_desc),
                icon = Icons.Rounded.Shield,
                checked = cutout.maskCavityHealing,
                itemKey = "cutout_${cutout.id}_cavity_healing",
                onCheckedChange = { isChecked ->
                    AppLog.d(TAG, "Updating cutout ${cutout.id} maskCavityHealing: $isChecked")
                    onUpdateCutout(cutout.copy(maskCavityHealing = isChecked))
                },
            )

            GamepadToggleCard(
                title = stringResource(R.string.settings_cutout_render_static_title),
                description = stringResource(R.string.settings_cutout_render_static_desc),
                icon = Icons.Rounded.Image,
                checked = cutout.renderAsStaticAsset,
                itemKey = "cutout_${cutout.id}_render_static",
                onCheckedChange = { isChecked ->
                    AppLog.d(TAG, "Updating cutout ${cutout.id} renderAsStaticAsset: $isChecked")
                    onUpdateCutout(cutout.copy(renderAsStaticAsset = isChecked))
                },
            )
        }

        // ── 3. Actions Section ──────────────────────────────────────────────
        if (cutout.hasTransparencyMask || isCalibrated) {
            GamepadSectionHeader(
                text = stringResource(R.string.macropad_editor_section_actions),
                color = accentColor,
            )

            GamepadTwoStepConfirmCard(
                title = stringResource(R.string.settings_cutout_remove_smart_title),
                confirmTitle = stringResource(R.string.settings_cutout_remove_smart_title),
                description = stringResource(R.string.settings_cutout_remove_smart_desc),
                confirmDescription = stringResource(R.string.settings_cutout_remove_smart_desc),
                actionText = stringResource(R.string.gamepad_action_delete),
                confirmActionText = stringResource(R.string.gamepad_action_confirm),
                icon = Icons.Rounded.Delete,
                isDestructive = true,
                itemKey = "cutout_${cutout.id}_remove_smart",
                onConfirm = {
                    CutoutMaskManager.deleteMask(context, cutout.id)
                    calibrationRevision++
                    onUpdateCutout(
                        cutout.copy(
                            hasTransparencyMask = false,
                            maskTranslucency = 0,
                            renderAsStaticAsset = false,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
internal fun MirrorAdvancedSettingsSubPageContent(
    layout: PadLayout,
    accentColor: Color,
) {
    AppLog.d(TAG, "MirrorAdvancedSettingsSubPageContent composition for layout=${layout.id}")

    fun commitLayout(block: PadLayout.() -> PadLayout) {
        val updated = MacroPadState.activeLayout.value ?: return
        MacroPadState.updateLayout(updated.block())
    }

    // 1. Ambient Dimming
    GamepadSliderCard(
        modifier = Modifier.firstDeckItem(),
        title = stringResource(R.string.settings_macropad_dim),
        description = stringResource(R.string.help_ambient_dim_desc),
        value = layout.ambientDim,
        valueRange = 0f..MSE_DIM_MAX,
        step = MSE_DIM_STEP,
        fineStep = 0.01f,
        icon = Icons.Rounded.Opacity,
        valueLabel = "${(layout.ambientDim * MSE_PERCENT_DIVISOR).roundToInt()}%",
        onValueChange = { newVal ->
            AppLog.d(TAG, "Updating ambientDim: $newVal")
            commitLayout { copy(ambientDim = newVal) }
        },
    )

    // 2. Edge Blending Width
    val edgeBlendLabel =
        if (layout.mirrorEdgeBlendWidth.roundToInt() == 0) {
            stringResource(R.string.mirror_edge_blend_strength_off)
        } else {
            "${layout.mirrorEdgeBlendWidth.roundToInt()} dp"
        }
    GamepadSliderCard(
        title = stringResource(R.string.mirror_edge_blend_label),
        description = stringResource(R.string.help_ambient_blend_desc),
        value = layout.mirrorEdgeBlendWidth,
        valueRange = MSE_EDGE_BLEND_MIN..MSE_EDGE_BLEND_MAX,
        step = MSE_EDGE_BLEND_STEP,
        fineStep = 1f,
        icon = Icons.Rounded.Grain,
        valueLabel = edgeBlendLabel,
        onValueChange = { newVal ->
            AppLog.d(TAG, "Updating mirrorEdgeBlendWidth: $newVal")
            commitLayout { copy(mirrorEdgeBlendWidth = newVal) }
        },
    )

    // 3. Follow Touch Target Cutout
    val followCutoutId = layout.mirrorCutouts.find { it.followTouch }?.id
    val cutoutsList = layout.mirrorCutouts
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
                AppLog.d(TAG, "Setting followTouch target: $newId")
                val updated = layout.mirrorCutouts.map { it.copy(followTouch = (it.id == newId)) }
                commitLayout { copy(mirrorCutouts = updated, mirrorFollowActive = (newId != null)) }
                ScreenCaptureManager.setFollowActive(newId != null, persist = false)
            }
        },
        onNext = {
            if (followOptions.isNotEmpty()) {
                val newIdx = (followIdx + 1) % followOptions.size
                val newId = followOptions[newIdx]
                AppLog.d(TAG, "Setting followTouch target: $newId")
                val updated = layout.mirrorCutouts.map { it.copy(followTouch = (it.id == newId)) }
                commitLayout { copy(mirrorCutouts = updated, mirrorFollowActive = (newId != null)) }
                ScreenCaptureManager.setFollowActive(newId != null, persist = false)
            }
        },
    )
}
