package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
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
import com.stormpanda.megingiard.mirror.HudAutoTuneCoordinator
import com.stormpanda.megingiard.mirror.MAX_FEATHERING_PX
import com.stormpanda.megingiard.mirror.MIN_FEATHERING_PX
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
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

private const val MSE_TOP_DIM_MIN = 0.10f
private const val MSE_TOP_DIM_MAX = 0.95f
private const val MSE_TOP_DIM_STEP = 0.05f
private const val MSE_TOP_DIM_FINE_STEP = 0.01f

private const val MSE_FEATHERING_MIN = 0f
private const val MSE_FEATHERING_MAX = 10f
private const val MSE_FEATHERING_STEP = 1f

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

            val hudFilterText =
                if (cutout.hasTransparencyMask) {
                    stringResource(R.string.settings_mirror_hud_filter_transparency_mask)
                } else {
                    null
                }

            val summaryDesc =
                if (hudFilterText != null) {
                    stringResource(
                        R.string.settings_mirror_cutout_summary_with_hud_fmt,
                        smoothingText,
                        hudFilterText,
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

    // 3. HUD Isolation Filter
    val context = LocalContext.current
    val isCalibrating by HudAutoTuneCoordinator.isCalibrating.collectAsStateWithLifecycle()
    val calibrateProgress by HudAutoTuneCoordinator.progress.collectAsStateWithLifecycle()
    val remainingSeconds by HudAutoTuneCoordinator.remainingSeconds.collectAsStateWithLifecycle()
    val lastTunedPercent by HudAutoTuneCoordinator.lastTunedPercent.collectAsStateWithLifecycle()

    // 3a. Auto-Tune HUD Action Card
    if (isCalibrating) {
        val pct = (calibrateProgress * MSE_PERCENT_DIVISOR).roundToInt()
        GamepadActionCard(
            title = stringResource(R.string.settings_mirror_hud_auto_tuning_prompt, remainingSeconds),
            description = stringResource(R.string.settings_mirror_hud_auto_tune_success, "$pct%"),
            icon = Icons.Rounded.Tune,
            itemKey = "cutout_${cutout.id}_auto_tune_active",
            onClick = { HudAutoTuneCoordinator.cancelCalibration() },
        )
    } else {
        val autoTuneDesc =
            if (cutout.hasTransparencyMask) {
                val statusStr = lastTunedPercent?.let { "$it%" } ?: stringResource(R.string.settings_mirror_projection_on)
                stringResource(R.string.settings_mirror_hud_auto_tune_success, statusStr)
            } else {
                stringResource(R.string.settings_mirror_hud_auto_tune_desc)
            }

        GamepadActionCard(
            title = stringResource(R.string.settings_mirror_hud_auto_tune_title),
            description = autoTuneDesc,
            icon = Icons.Rounded.Tune,
            itemKey = "cutout_${cutout.id}_auto_tune",
            onClick = {
                HudAutoTuneCoordinator.startCalibration(context, cutout) { updatedCutout, _ ->
                    onUpdateCutout(updatedCutout, false)
                }
            },
        )

        if (cutout.hasTransparencyMask) {
            val featheringLabel =
                if (cutout.maskFeathering > 0) {
                    "${cutout.maskFeathering} px"
                } else {
                    stringResource(R.string.settings_mirror_hud_feathering_off)
                }
            GamepadSliderCard(
                title = stringResource(R.string.settings_mirror_hud_feathering_title),
                description = stringResource(R.string.settings_mirror_hud_feathering_desc),
                value = cutout.maskFeathering.toFloat(),
                valueRange = MSE_FEATHERING_MIN..MSE_FEATHERING_MAX,
                step = MSE_FEATHERING_STEP,
                fineStep = MSE_FEATHERING_STEP,
                icon = Icons.Rounded.Grain,
                valueLabel = featheringLabel,
                onValueChange = { newVal ->
                    val newFeathering = newVal.roundToInt().coerceIn(MIN_FEATHERING_PX, MAX_FEATHERING_PX)
                    AppLog.d(TAG, "Updating cutout ${cutout.id} maskFeathering: $newFeathering")
                    onUpdateCutout(cutout.copy(maskFeathering = newFeathering), false)
                },
            )

            GamepadActionCard(
                title = stringResource(R.string.settings_mirror_hud_clear_mask_title),
                description = stringResource(R.string.settings_mirror_hud_clear_mask_desc),
                icon = Icons.Rounded.Delete,
                itemKey = "cutout_${cutout.id}_clear_mask",
                onClick = {
                    CutoutMaskManager.deleteMask(context, cutout.id)
                    onUpdateCutout(cutout.copy(hasTransparencyMask = false, maskFeathering = 0), false)
                },
            )
        }
    }

    // 4. Touch Projection
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
                onUpdateCutout(cutout.copy(touchProjectionEnabled = true), true)
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
                onUpdateCutout(cutout.copy(touchProjectionEnabled = isChecked), false)
                if (isChecked) {
                    ScreenCaptureManager.setLocked(true)
                }
            },
        )
    }

    // 4. Delete Cutout Action
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

    // 4. Primary Screen HUD Dimming
    GamepadToggleCard(
        title = stringResource(R.string.settings_mirror_dim_top_screen_hud_title),
        description = stringResource(R.string.settings_mirror_dim_top_screen_hud_desc),
        checked = layout.dimTopScreenHud,
        icon = Icons.Rounded.Opacity,
        itemKey = "mirror_dim_top_screen_hud",
        onCheckedChange = { isChecked ->
            AppLog.d(TAG, "Toggling dimTopScreenHud: $isChecked")
            commitLayout { copy(dimTopScreenHud = isChecked) }
            ScreenCaptureManager.setDimTopScreenHud(isChecked)
        },
    )

    if (layout.dimTopScreenHud) {
        GamepadSliderCard(
            title = stringResource(R.string.settings_mirror_dim_top_screen_hud_opacity),
            description = stringResource(R.string.settings_mirror_dim_top_screen_hud_opacity_desc),
            value = layout.topScreenHudDimOpacity,
            valueRange = MSE_TOP_DIM_MIN..MSE_TOP_DIM_MAX,
            step = MSE_TOP_DIM_STEP,
            fineStep = MSE_TOP_DIM_FINE_STEP,
            icon = Icons.Rounded.Opacity,
            valueLabel = "${(layout.topScreenHudDimOpacity * MSE_PERCENT_DIVISOR).roundToInt()}%",
            onValueChange = { newVal ->
                AppLog.d(TAG, "Updating topScreenHudDimOpacity: $newVal")
                commitLayout { copy(topScreenHudDimOpacity = newVal) }
                ScreenCaptureManager.setTopScreenHudDimOpacity(newVal)
            },
        )
    }
}
