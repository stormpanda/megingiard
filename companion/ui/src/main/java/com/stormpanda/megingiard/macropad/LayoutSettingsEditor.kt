package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.CutoutMaskManager
import com.stormpanda.megingiard.mirror.HudAutoTuneCoordinator
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import com.stormpanda.megingiard.ui.toHexLabel
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val TAG = "LayoutSettingsEditor"
private const val MS_PER_FRAME = 16
private const val PERCENT_DIVISOR = 100f

@Composable
private fun describeColorOption(
    option: ColorOption,
    resolvedColor: Color,
): String =
    when (option) {
        is ColorOption.Neutral -> stringResource(R.string.layout_settings_color_neutral)
        is ColorOption.Accent -> stringResource(R.string.layout_settings_color_accent)
        is ColorOption.Custom -> resolvedColor.toHexLabel()
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditLayoutSubPageContent(
    layout: PadLayout,
    savedLayout: PadLayout,
    existingNames: List<String>,
    accentColor: Color,
    onNameChange: (String) -> Unit,
    onInvisibleButtonsChange: (Boolean) -> Unit,
    onOpenAutomaticLayoutSwitching: () -> Unit,
    onOpenColorSubMenu: (target: LayoutColorTarget) -> Unit,
    onOpenTouchpadSettings: () -> Unit,
    onDeleteLayout: () -> Unit,
    onDiscard: () -> Unit = {},
    onSaveColors: (textColor: ColorOption, borderColor: ColorOption, bgColor: ColorOption) -> Unit,
) {
    val colors = LocalAppColors.current
    var nameText by remember(savedLayout.id, savedLayout.name) { mutableStateOf(savedLayout.name) }

    LaunchedEffect(savedLayout.id) {
        AppLog.d(TAG, "EditLayoutSubPageContent opened for layout: ${savedLayout.name} (${savedLayout.id})")
    }

    LaunchedEffect(Unit) {
        snapshotFlow { layout }
            .collectLatest { inFlightLayout ->
                MacroPadState.setPreviewLayout(inFlightLayout)
            }
    }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate

    val hasColorChanges =
        layout.buttonTextColor != savedLayout.buttonTextColor ||
            layout.buttonBorderColor != savedLayout.buttonBorderColor ||
            layout.buttonBgColor != savedLayout.buttonBgColor

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasColorChanges,
            onSave = {
                onSaveColors(
                    layout.buttonTextColor,
                    layout.buttonBorderColor,
                    layout.buttonBgColor,
                )
            },
            onDiscard = onDiscard,
        )

    val globalAccentInt by SettingsManager.accentColor.collectAsStateWithLifecycle()
    val globalAccentColor = Color(globalAccentInt)

    val currentResolvedText = resolveColorOption(layout.buttonTextColor, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentResolvedBorder = resolveColorOption(layout.buttonBorderColor, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentResolvedBg = resolveBgColorOption(layout.buttonBgColor, globalAccentColor)

    val savedResolvedText = resolveColorOption(savedLayout.buttonTextColor, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val savedResolvedBorder = resolveColorOption(savedLayout.buttonBorderColor, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val savedResolvedBg = resolveBgColorOption(savedLayout.buttonBgColor, globalAccentColor)

    GamepadTextFieldCard(
        title = stringResource(R.string.quick_menu_layout_name_hint),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_layout_name_desc)
            },
        placeholder = stringResource(R.string.quick_menu_layout_name_placeholder),
        value = nameText,
        onValueChange = {
            nameText = it
            val trimmed = it.trim()
            if (trimmed.isNotEmpty() && !existingNames.any { n -> n.equals(trimmed, ignoreCase = true) }) {
                onNameChange(trimmed)
            }
        },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSectionHeader(
        text = stringResource(R.string.layout_settings_auto_switch_section_title),
        color = accentColor,
    )

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_auto_switch_title),
        description =
            if (layout.visualAnchor.enabled) {
                stringResource(R.string.layout_settings_auto_switch_enabled_desc)
            } else {
                stringResource(R.string.layout_settings_auto_switch_disabled_desc)
            },
        icon = Icons.Rounded.Anchor,
        itemKey = "layout_${layout.id}_auto_switch",
        onClick = onOpenAutomaticLayoutSwitching,
    )

    GamepadSectionHeader(
        text = stringResource(R.string.layout_settings_colors_section_title),
        color = accentColor,
    )

    data class TargetConfig(
        val target: EditorColorTarget,
        val colorOption: ColorOption,
        val resolvedColor: Color,
        val icon: ImageVector,
    )
    val colorTargets =
        listOf(
            TargetConfig(EditorColorTarget.TEXT, layout.buttonTextColor, currentResolvedText, Icons.Rounded.FormatColorText),
            TargetConfig(EditorColorTarget.BORDER, layout.buttonBorderColor, currentResolvedBorder, Icons.Rounded.Palette),
            TargetConfig(EditorColorTarget.BG, layout.buttonBgColor, currentResolvedBg, Icons.Rounded.FormatColorFill),
        )
    colorTargets.forEach { item ->
        GamepadActionCard(
            title = stringResource(item.target.titleResId),
            description = describeColorOption(item.colorOption, item.resolvedColor),
            icon = item.icon,
            actionLeadingContent = {
                SwordsButtonPreview(
                    textColor = if (item.target == EditorColorTarget.TEXT) item.resolvedColor else Color.Transparent,
                    borderColor = if (item.target == EditorColorTarget.BORDER) item.resolvedColor else Color.Transparent,
                    bgColor = if (item.target == EditorColorTarget.BG) item.resolvedColor else Color.Transparent,
                    isIconOnly = item.target == EditorColorTarget.TEXT,
                )
            },
            onClick = { onOpenColorSubMenu(item.target) },
        )
    }

    ColorPreviewInfoBox(
        title = stringResource(R.string.macropad_editor_color_preview_title),
        description = stringResource(R.string.macropad_editor_color_preview_desc),
        savedPreview = {
            SwordsButtonPreview(
                textColor = savedResolvedText,
                borderColor = savedResolvedBorder,
                bgColor = savedResolvedBg,
                isIconOnly = false,
            )
        },
        currentPreview = {
            SwordsButtonPreview(
                textColor = currentResolvedText,
                borderColor = currentResolvedBorder,
                bgColor = currentResolvedBg,
                isIconOnly = false,
            )
        },
    )

    // ── Save & Exit Action Row ───────────────────────────────────────────────
    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_save_button_colors_title),
        description = stringResource(R.string.macropad_editor_save_button_colors_desc),
        pulseOnChanges = hasColorChanges,
        saveActionText = stringResource(R.string.gamepad_action_confirm),
        saveIcon = Icons.Rounded.Save,
        enabled = true,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_visibility_behavior),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = savedLayout.invisibleButtons,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = onInvisibleButtonsChange,
    )

    // ── Touchpad Section ─────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.settings_touchpad_title),
        color = accentColor,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_touchpad_title),
        description = stringResource(R.string.macropad_editor_touchpad_desc),
        icon = Icons.Rounded.Mouse,
        onClick = onOpenTouchpadSettings,
    )

    // ── Actions Section ───────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_actions),
        color = accentColor,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_editor_delete_layout),
        confirmTitle = stringResource(R.string.macropad_layout_delete_confirm_title, savedLayout.name),
        description = stringResource(R.string.macropad_editor_delete_layout_desc, savedLayout.name),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        isDestructive = true,
        icon = Icons.Rounded.Delete,
        onConfirm = onDeleteLayout,
    )
}

@Composable
internal fun AutomaticLayoutSwitchingSubPageContent(
    layout: PadLayout,
    accentColor: Color,
    onUpdateLayout: (PadLayout) -> Unit,
) {
    AppLog.d(TAG, "AutomaticLayoutSwitchingSubPageContent for layout: ${layout.name} (${layout.id})")
    val context = LocalContext.current
    val isCalibrating by HudAutoTuneCoordinator.isCalibrating.collectAsStateWithLifecycle()
    val calibrateProgress by HudAutoTuneCoordinator.progress.collectAsStateWithLifecycle()
    val remainingSeconds by HudAutoTuneCoordinator.remainingSeconds.collectAsStateWithLifecycle()
    var calibrationRevision by remember { mutableIntStateOf(0) }

    if (isCalibrating) {
        val pct = (calibrateProgress * PERCENT_DIVISOR).roundToInt()
        GamepadActionCard(
            modifier = Modifier.firstDeckItem(),
            title = stringResource(R.string.settings_mirror_hud_auto_tuning_prompt, remainingSeconds),
            description = stringResource(R.string.settings_mirror_hud_auto_tune_success, "$pct%"),
            icon = Icons.Rounded.Tune,
            itemKey = "layout_${layout.id}_auto_tune_active",
            onClick = { HudAutoTuneCoordinator.cancelCalibration() },
        )
    } else {
        GamepadToggleCard(
            modifier = Modifier.firstDeckItem(),
            title = stringResource(R.string.layout_settings_visual_anchor_title),
            description = stringResource(R.string.layout_settings_visual_anchor_desc),
            checked = layout.visualAnchor.enabled,
            icon = Icons.Rounded.Anchor,
            itemKey = "layout_${layout.id}_visual_anchor_toggle",
            onCheckedChange = { isChecked ->
                onUpdateLayout(
                    layout.copy(
                        visualAnchor = layout.visualAnchor.copy(enabled = isChecked),
                    ),
                )
            },
        )

        if (layout.visualAnchor.enabled) {
            GamepadActionCard(
                title = stringResource(R.string.layout_settings_visual_anchor_position_title),
                description = stringResource(R.string.layout_settings_visual_anchor_position_desc),
                icon = Icons.Rounded.FilterCenterFocus,
                itemKey = "layout_${layout.id}_position_anchor",
                onClick = {
                    AppStateManager.openPrimaryModal(
                        PrimaryModalConfig(
                            type = PrimaryModalType.ANCHOR_SELECTOR,
                            payload = PrimaryModalPayload.AnchorSelector(layoutId = layout.id),
                        ),
                    )
                },
            )

            val isCalibrated =
                remember(layout.id, calibrationRevision) {
                    CutoutMaskManager.isLayoutAnchorCalibrated(context, layout.id)
                }
            val signature =
                remember(layout.id, calibrationRevision) {
                    CutoutMaskManager.getLayoutAnchorSignature(context, layout.id)
                }

            val calibTitle =
                if (isCalibrated) {
                    stringResource(R.string.layout_settings_visual_anchor_recalibrate_title)
                } else {
                    stringResource(R.string.layout_settings_visual_anchor_calibrate_title)
                }
            val calibDesc =
                if (isCalibrated) {
                    stringResource(R.string.layout_settings_visual_anchor_recalibrate_desc)
                } else {
                    stringResource(R.string.layout_settings_visual_anchor_calibrate_desc)
                }

            GamepadActionCard(
                title = calibTitle,
                description = calibDesc,
                icon = Icons.Rounded.Tune,
                itemKey = "layout_${layout.id}_calibrate_anchor",
                onClick = {
                    HudAutoTuneCoordinator.startLayoutAnchorCalibration(context, layout) { updatedLayout, _ ->
                        calibrationRevision++
                        onUpdateLayout(updatedLayout)
                    }
                },
            )

            if (isCalibrated && signature != null && signature.points.isNotEmpty()) {
                GamepadInfoBox(
                    text = stringResource(R.string.layout_settings_visual_anchor_status_calibrated, signature.points.size),
                    icon = Icons.Rounded.Anchor,
                    iconTint = accentColor,
                )
            }

            val delayFrames =
                layout.visualAnchor.streamDelayFrames.coerceIn(
                    MIN_LAYOUT_STREAM_DELAY_FRAMES,
                    MAX_LAYOUT_STREAM_DELAY_FRAMES,
                )
            val delayLabel =
                stringResource(
                    R.string.settings_cutout_stream_delay_frames,
                    delayFrames,
                    delayFrames * MS_PER_FRAME,
                )
            GamepadSliderCard(
                title = stringResource(R.string.settings_cutout_stream_delay_title),
                description = stringResource(R.string.layout_settings_visual_anchor_stream_delay_desc),
                value = delayFrames.toFloat(),
                valueRange = MIN_LAYOUT_STREAM_DELAY_FRAMES.toFloat()..MAX_LAYOUT_STREAM_DELAY_FRAMES.toFloat(),
                step = 1f,
                fineStep = 1f,
                icon = Icons.Rounded.Schedule,
                valueLabel = delayLabel,
                onValueChange = { newVal ->
                    val newDelay =
                        newVal.roundToInt().coerceIn(
                            MIN_LAYOUT_STREAM_DELAY_FRAMES,
                            MAX_LAYOUT_STREAM_DELAY_FRAMES,
                        )
                    AppLog.d(TAG, "Updating layout ${layout.id} streamDelayFrames: $newDelay")
                    onUpdateLayout(
                        layout.copy(
                            visualAnchor = layout.visualAnchor.copy(streamDelayFrames = newDelay),
                        ),
                    )
                },
            )

            GamepadToggleCard(
                title = stringResource(R.string.layout_settings_visual_anchor_blur_title),
                description = stringResource(R.string.layout_settings_visual_anchor_blur_desc),
                checked = layout.visualAnchor.blurCutoutsOnLoss,
                icon = Icons.Rounded.BlurOn,
                itemKey = "layout_${layout.id}_blur_on_loss",
                onCheckedChange = { isChecked ->
                    onUpdateLayout(
                        layout.copy(
                            visualAnchor = layout.visualAnchor.copy(blurCutoutsOnLoss = isChecked),
                        ),
                    )
                },
            )

            if (isCalibrated) {
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_editor_section_actions),
                    color = accentColor,
                )

                GamepadTwoStepConfirmCard(
                    title = stringResource(R.string.layout_settings_visual_anchor_remove_title),
                    confirmTitle = stringResource(R.string.layout_settings_visual_anchor_remove_title),
                    description = stringResource(R.string.layout_settings_visual_anchor_remove_desc),
                    confirmDescription = stringResource(R.string.layout_settings_visual_anchor_remove_desc),
                    actionText = stringResource(R.string.gamepad_action_delete),
                    confirmActionText = stringResource(R.string.gamepad_action_confirm),
                    icon = Icons.Rounded.Delete,
                    isDestructive = true,
                    itemKey = "layout_${layout.id}_remove_anchor",
                    onConfirm = {
                        CutoutMaskManager.deleteLayoutAnchorSignature(context, layout.id)
                        calibrationRevision++
                        onUpdateLayout(
                            layout.copy(
                                visualAnchor = layout.visualAnchor.copy(enabled = false),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun LayoutColorSubPageContent(
    layout: PadLayout,
    savedLayout: PadLayout?,
    target: LayoutColorTarget,
    accentColor: Color,
    onColorOptionChanged: (ColorOption) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, inFlightLayout: PadLayout) -> Unit,
) {
    ColorOptionSubPageContent(
        currentOption = layout.getColorOption(target),
        layoutDefaultOption = null,
        defaultNeutralColor = target.defaultNeutralColor,
        isBgTarget = target == EditorColorTarget.BG,
        selectColorWheelTitle = stringResource(target.selectWheelTitleResId),
        colorWheelBreadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_layout),
                stringResource(R.string.macropad_editor_appearance_title),
                stringResource(target.titleResId),
                stringResource(R.string.gamepad_action_custom_color),
            ),
        onColorOptionChanged = { option -> option?.let(onColorOptionChanged) },
        onOpenColorWheel = { title, breadcrumbs, initialColor ->
            onOpenColorWheel(title, breadcrumbs, initialColor, layout)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewLayoutSubPageContent(
    existingNames: List<String>,
    accentColor: Color,
    onDiscard: () -> Unit = {},
    onCreate: (name: String) -> Unit,
) {
    val defaultLayoutName = stringResource(R.string.macropad_editor_new_layout_default_name)
    val initialLayoutName =
        remember(existingNames) {
            existingNames.nextUniqueName(defaultLayoutName)
        }
    var nameText by remember { mutableStateOf(initialLayoutName) }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = true,
            onSave = {
                if (isConfirmEnabled) {
                    onCreate(normalizedName)
                }
            },
            onDiscard = onDiscard,
        )

    GamepadTextFieldCard(
        title = stringResource(R.string.quick_menu_layout_name_hint),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_layout_name_desc)
            },
        placeholder = stringResource(R.string.quick_menu_layout_name_placeholder),
        value = nameText,
        onValueChange = { nameText = it },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
    )

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_create_layout_title),
        description = stringResource(R.string.macropad_editor_create_layout_desc),
        pulseOnChanges = true,
        saveActionText = stringResource(R.string.gamepad_action_create),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )
}
