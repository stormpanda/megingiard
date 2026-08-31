package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroStepEditDialog"

private const val MSD_DEFAULT_DURATION_MS = 100L
private const val MSD_MAX_START_TIME_BUFFER_MS = 5000L
private const val MSD_MAX_DURATION_MS = 5000L
private const val MSD_TIME_SLIDER_STEP = 25f
private const val MSD_PERCENT_SLIDER_STEP = 5f

private enum class StepType(
    val labelResId: Int,
) {
    GAMEPAD(R.string.macropad_macro_step_type_gamepad),
    JOYSTICK(R.string.macropad_macro_step_type_joystick),
    JOYSTICK_PATH(R.string.macropad_macro_step_type_joystick_path),
    DPAD(R.string.macropad_macro_step_type_dpad),
    TOUCH(R.string.macropad_macro_step_type_touch),
    TOUCH_PATH(R.string.macropad_macro_step_type_touch_path),
}

private data class DirectionOption(
    val dirX: Int,
    val dirY: Int,
    val labelRes: Int,
)

private val MSD_DIRECTIONS =
    listOf(
        DirectionOption(0, -1, R.string.macropad_macro_dir_up),
        DirectionOption(1, -1, R.string.macropad_macro_dir_up_right),
        DirectionOption(1, 0, R.string.macropad_macro_dir_right),
        DirectionOption(1, 1, R.string.macropad_macro_dir_down_right),
        DirectionOption(0, 1, R.string.macropad_macro_dir_down),
        DirectionOption(-1, 1, R.string.macropad_macro_dir_down_left),
        DirectionOption(-1, 0, R.string.macropad_macro_dir_left),
        DirectionOption(-1, -1, R.string.macropad_macro_dir_up_left),
    )

private fun findDirectionIndex(
    x: Int,
    y: Int,
): Int = MSD_DIRECTIONS.indexOfFirst { it.dirX == x && it.dirY == y }.coerceAtLeast(0)

@Composable
internal fun MacroStepEditSubPageContent(
    macroName: String,
    step: MacroStep?,
    stepIndex: Int?,
    accentColor: Color,
    suggestedStartTimeMs: Long,
    initialShiftMode: ShiftMode,
    onConfirm: (MacroStep, ShiftMode) -> Unit,
    onDiscard: () -> Unit,
    onDuplicate: ((MacroStep) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    val initialType =
        when (step) {
            is MacroStep.GamepadButtonTap -> StepType.GAMEPAD
            is MacroStep.JoystickMove -> StepType.JOYSTICK
            is MacroStep.DPadTap -> StepType.DPAD
            is MacroStep.TouchTap -> StepType.TOUCH
            is MacroStep.JoystickPath -> StepType.JOYSTICK_PATH
            is MacroStep.TouchPath -> StepType.TOUCH_PATH
            null -> StepType.GAMEPAD
        }

    val initialStartMs =
        when {
            step != null -> step.startTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            else -> suggestedStartTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    val initialDurationMs = (step?.durationMs ?: MSD_DEFAULT_DURATION_MS).coerceIn(10L, MSD_MAX_DURATION_MS).toInt()

    var stepType by remember { mutableStateOf(initialType) }
    var startMs by remember { mutableIntStateOf(initialStartMs) }
    var durMs by remember { mutableIntStateOf(initialDurationMs) }
    var shiftMode by remember { mutableStateOf(initialShiftMode) }

    // GamepadButtonTap state
    val initPreset =
        if (step is MacroStep.GamepadButtonTap) {
            GamepadKeycodes.PRESETS.firstOrNull { it.code == step.btnCode } ?: GamepadKeycodes.PRESETS.first()
        } else {
            GamepadKeycodes.PRESETS.first()
        }
    var selectedPreset by remember { mutableStateOf(initPreset) }

    // JoystickMove state
    var joyStick by remember {
        mutableStateOf(if (step is MacroStep.JoystickMove) step.stick else JoystickStick.LEFT)
    }
    val initJoyDir =
        if (step is MacroStep.JoystickMove && (step.x != 0f || step.y != 0f)) {
            val mag = sqrt(step.x * step.x + step.y * step.y)
            val dx =
                when {
                    step.x / mag > 0.35f -> 1
                    step.x / mag < -0.35f -> -1
                    else -> 0
                }
            val dy =
                when {
                    step.y / mag > 0.35f -> 1
                    step.y / mag < -0.35f -> -1
                    else -> 0
                }
            findDirectionIndex(dx, dy)
        } else {
            0
        }
    var selectedJoyDirIdx by remember { mutableIntStateOf(initJoyDir) }
    var joyMagnitude by remember {
        mutableFloatStateOf(
            if (step is MacroStep.JoystickMove) {
                sqrt(step.x * step.x + step.y * step.y).coerceIn(0.1f, 1f)
            } else {
                1f
            },
        )
    }

    // DPadTap state
    val initDpadDir =
        if (step is MacroStep.DPadTap) {
            findDirectionIndex(step.dirX, step.dirY)
        } else {
            0
        }
    var selectedDpadDirIdx by remember { mutableIntStateOf(initDpadDir) }

    // TouchTap state
    var touchXPercent by remember {
        mutableIntStateOf(
            if (step is MacroStep.TouchTap) {
                (step.normX * 100).roundToInt().coerceIn(0, 100)
            } else {
                50
            },
        )
    }
    var touchYPercent by remember {
        mutableIntStateOf(
            if (step is MacroStep.TouchTap) {
                (step.normY * 100).roundToInt().coerceIn(0, 100)
            } else {
                50
            },
        )
    }

    // Construct the active MacroStep
    val constructedStep: MacroStep =
        when (stepType) {
            StepType.GAMEPAD -> {
                MacroStep.GamepadButtonTap(
                    startTimeMs = startMs.toLong(),
                    durationMs = durMs.toLong(),
                    btnCode = selectedPreset.code,
                    label = selectedPreset.label,
                )
            }

            StepType.JOYSTICK -> {
                val dir = MSD_DIRECTIONS[selectedJoyDirIdx]
                val unitX = dir.dirX.toFloat()
                val unitY = dir.dirY.toFloat()
                val len = sqrt(unitX * unitX + unitY * unitY).coerceAtLeast(0.001f)
                val normX = (unitX / len) * joyMagnitude
                val normY = (unitY / len) * joyMagnitude
                MacroStep.JoystickMove(
                    startTimeMs = startMs.toLong(),
                    durationMs = durMs.toLong(),
                    stick = joyStick,
                    x = normX,
                    y = normY,
                )
            }

            StepType.DPAD -> {
                val dir = MSD_DIRECTIONS[selectedDpadDirIdx]
                MacroStep.DPadTap(
                    startTimeMs = startMs.toLong(),
                    durationMs = durMs.toLong(),
                    dirX = dir.dirX,
                    dirY = dir.dirY,
                )
            }

            StepType.TOUCH -> {
                MacroStep.TouchTap(
                    startTimeMs = startMs.toLong(),
                    durationMs = durMs.toLong(),
                    normX = touchXPercent / 100f,
                    normY = touchYPercent / 100f,
                )
            }

            StepType.JOYSTICK_PATH -> {
                (step as? MacroStep.JoystickPath)?.withTiming(startMs.toLong(), durMs.toLong())
                    ?: (step as? MacroStep.JoystickPath)
                    ?: MacroStep.GamepadButtonTap(startMs.toLong(), durMs.toLong(), 0, "")
            }

            StepType.TOUCH_PATH -> {
                (step as? MacroStep.TouchPath)?.withTiming(startMs.toLong(), durMs.toLong())
                    ?: (step as? MacroStep.TouchPath)
                    ?: MacroStep.TouchTap(startMs.toLong(), durMs.toLong(), 0.5f, 0.5f)
            }
        }

    val hasChanges = step == null || constructedStep != step || shiftMode != initialShiftMode
    val isConfirmEnabled = durMs > 0

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    AppLog.d(TAG, "Saving macro step: $constructedStep, shiftMode: $shiftMode")
                    onConfirm(constructedStep, shiftMode)
                }
            },
            onDiscard = {
                AppLog.d(TAG, "Discarding macro step edits")
                onDiscard()
            },
        )

    val availableTypes =
        StepType.entries.filter { type ->
            val initialIsRecorded =
                initialType == StepType.TOUCH_PATH || initialType == StepType.JOYSTICK_PATH
            if (initialIsRecorded) type == initialType else type != StepType.TOUCH_PATH && type != StepType.JOYSTICK_PATH
        }

    GamepadChoiceCard(
        title = stringResource(R.string.macro_step_action_type_title),
        description = stringResource(R.string.macro_step_action_type_desc),
        selectedText = stringResource(stepType.labelResId),
        icon = stepIcon(constructedStep),
        enabled = availableTypes.size > 1,
        onPrevious = { stepType = availableTypes.cycle(stepType, BumperDirection.PREV) },
        onNext = { stepType = availableTypes.cycle(stepType, BumperDirection.NEXT) },
        modifier = Modifier.firstDeckItem(),
    )

    // ── Action Details Section ───────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_action_details),
        color = accentColor,
    )

    when (stepType) {
        StepType.GAMEPAD -> {
            val presets = GamepadKeycodes.PRESETS
            GamepadChoiceCard(
                title = stringResource(R.string.macro_step_gamepad_button_title),
                description = stringResource(R.string.macro_step_gamepad_button_desc),
                selectedText = selectedPreset.localizedDisplayLabel(swapFaceButtons),
                icon = Icons.Rounded.SportsEsports,
                onPrevious = { selectedPreset = presets.cycle(selectedPreset, BumperDirection.PREV) },
                onNext = { selectedPreset = presets.cycle(selectedPreset, BumperDirection.NEXT) },
            )
        }

        StepType.JOYSTICK -> {
            val sticks = listOf(JoystickStick.LEFT, JoystickStick.RIGHT)
            val stickLabels =
                listOf(
                    stringResource(R.string.macropad_macro_step_stick_left),
                    stringResource(R.string.macropad_macro_step_stick_right),
                )
            val stickIdx = sticks.indexOf(joyStick).coerceAtLeast(0)
            GamepadChoiceCard(
                title = stringResource(R.string.macro_step_target_stick_title),
                description = stringResource(R.string.macro_step_target_stick_desc),
                selectedText = stickLabels[stickIdx],
                icon = Icons.Rounded.NearMe,
                onPrevious = { joyStick = sticks.cycle(joyStick, BumperDirection.PREV) },
                onNext = { joyStick = sticks.cycle(joyStick, BumperDirection.NEXT) },
            )

            GamepadChoiceCard(
                title = stringResource(R.string.macropad_macro_step_direction),
                description = stringResource(R.string.macro_step_stick_deflection_desc),
                selectedText = stringResource(MSD_DIRECTIONS[selectedJoyDirIdx].labelRes),
                icon = Icons.Rounded.NearMe,
                onPrevious = {
                    selectedJoyDirIdx = (selectedJoyDirIdx - 1 + MSD_DIRECTIONS.size) % MSD_DIRECTIONS.size
                },
                onNext = {
                    selectedJoyDirIdx = (selectedJoyDirIdx + 1) % MSD_DIRECTIONS.size
                },
            )

            GamepadSliderCard(
                title = stringResource(R.string.macropad_macro_step_magnitude, joyMagnitude),
                description = stringResource(R.string.macro_step_stick_deflection_desc),
                value = joyMagnitude * 100f,
                valueRange = 10f..100f,
                step = MSD_PERCENT_SLIDER_STEP,
                fineStep = 1f,
                valueLabel = "${(joyMagnitude * 100).roundToInt()}%",
                icon = Icons.Rounded.Tune,
                onValueChange = { joyMagnitude = (it / 100f).coerceIn(0.1f, 1f) },
            )
        }

        StepType.DPAD -> {
            GamepadChoiceCard(
                title = stringResource(R.string.macropad_macro_step_direction),
                description = stringResource(R.string.macro_step_action_type_desc),
                selectedText = stringResource(MSD_DIRECTIONS[selectedDpadDirIdx].labelRes),
                icon = Icons.Rounded.OpenWith,
                onPrevious = {
                    selectedDpadDirIdx = (selectedDpadDirIdx - 1 + MSD_DIRECTIONS.size) % MSD_DIRECTIONS.size
                },
                onNext = {
                    selectedDpadDirIdx = (selectedDpadDirIdx + 1) % MSD_DIRECTIONS.size
                },
            )
        }

        StepType.TOUCH -> {
            GamepadSliderCard(
                title = stringResource(R.string.macropad_macro_step_pos_x),
                description = stringResource(R.string.macropad_macro_step_pos_desc),
                value = touchXPercent.toFloat(),
                valueRange = 0f..100f,
                step = 1f,
                valueLabel = "$touchXPercent%",
                icon = Icons.Rounded.TouchApp,
                onValueChange = { touchXPercent = it.roundToInt().coerceIn(0, 100) },
            )

            GamepadSliderCard(
                title = stringResource(R.string.macropad_macro_step_pos_y),
                description = stringResource(R.string.macropad_macro_step_pos_desc),
                value = touchYPercent.toFloat(),
                valueRange = 0f..100f,
                step = 1f,
                valueLabel = "$touchYPercent%",
                icon = Icons.Rounded.TouchApp,
                onValueChange = { touchYPercent = it.roundToInt().coerceIn(0, 100) },
            )
        }

        StepType.JOYSTICK_PATH -> {
            val pathStep = step as? MacroStep.JoystickPath
            val stickLabel =
                stringResource(
                    if (pathStep?.stick == JoystickStick.RIGHT) {
                        R.string.macropad_macro_step_stick_right
                    } else {
                        R.string.macropad_macro_step_stick_left
                    },
                )
            GamepadFocusCard(onClick = {}) {
                GamepadCardRow(
                    title = stringResource(R.string.macropad_macro_step_path_summary_stick, stickLabel),
                    description = stringResource(R.string.macropad_macro_step_path_summary_samples, pathStep?.samples?.size ?: 0),
                    icon = Icons.Rounded.NearMe,
                )
            }
        }

        StepType.TOUCH_PATH -> {
            val touchPathStep = step as? MacroStep.TouchPath
            GamepadFocusCard(onClick = {}) {
                GamepadCardRow(
                    title = stringResource(R.string.macropad_macro_step_type_touch_path),
                    description = stringResource(R.string.macropad_macro_step_touch_path_details, touchPathStep?.samples?.size ?: 0),
                    icon = Icons.Rounded.TouchApp,
                )
            }
        }
    }

    // ── Timing & Sequence Section ────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_timing),
        color = accentColor,
    )

    val maxStartMs = (suggestedStartTimeMs + MSD_MAX_START_TIME_BUFFER_MS).coerceAtLeast(1000L).toFloat()
    GamepadSliderCard(
        title = stringResource(R.string.macropad_macro_step_start_ms),
        description = stringResource(R.string.macropad_macro_step_start_desc),
        value = startMs.toFloat(),
        valueRange = 0f..maxStartMs,
        step = MSD_TIME_SLIDER_STEP,
        fineStep = 1f,
        valueLabel = "$startMs ms",
        icon = Icons.Rounded.Schedule,
        onValueChange = { startMs = it.roundToInt().coerceAtLeast(0) },
    )

    GamepadSliderCard(
        title = stringResource(R.string.macropad_macro_step_duration_ms),
        description = stringResource(R.string.macropad_macro_step_duration_desc),
        value = durMs.toFloat(),
        valueRange = 10f..MSD_MAX_DURATION_MS.toFloat(),
        step = MSD_TIME_SLIDER_STEP,
        fineStep = 1f,
        valueLabel = "$durMs ms",
        icon = Icons.Rounded.Schedule,
        onValueChange = { durMs = it.roundToInt().coerceIn(10, MSD_MAX_DURATION_MS.toInt()) },
    )

    if (step != null) {
        val shiftModes = ShiftMode.entries
        val shiftModeLabels =
            listOf(
                stringResource(R.string.macropad_macro_editor_shift_none),
                stringResource(R.string.macropad_macro_editor_shift_start_delta),
                stringResource(R.string.macropad_macro_editor_shift_end_delta),
            )
        val shiftIdx = shiftModes.indexOf(shiftMode).coerceAtLeast(0)
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_macro_editor_shift_subsequent),
            description = stringResource(R.string.macropad_macro_step_shift_desc),
            selectedText = shiftModeLabels[shiftIdx],
            icon = Icons.Rounded.Schedule,
            onPrevious = { shiftMode = shiftModes.cycle(shiftMode, BumperDirection.PREV) },
            onNext = { shiftMode = shiftModes.cycle(shiftMode, BumperDirection.NEXT) },
        )
    }

    // ── Step Management Actions (Duplicate / Delete) ─────────────────────────
    if (step != null) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_macro_section_actions),
            color = accentColor,
        )

        if (onDuplicate != null) {
            GamepadActionCard(
                title = stringResource(R.string.macropad_macro_step_duplicate),
                description = stringResource(R.string.macropad_macro_step_duplicate_desc),
                icon = Icons.Rounded.ContentCopy,
                onClick = { onDuplicate(constructedStep) },
            )
        }
    }

    // ── Save / Save & Delete Section ─────────────────────────────────────────
    val hasDelete = step != null && onDelete != null
    GamepadSectionHeader(
        text =
            stringResource(
                if (hasDelete) {
                    R.string.macropad_editor_section_save_and_delete
                } else {
                    R.string.macropad_editor_section_save
                },
            ),
        color = accentColor,
    )

    // ── Save & Exit Action Row ───────────────────────────────────────────────
    GamepadSaveExitActionRow(
        title =
            if (step == null) {
                stringResource(R.string.macropad_editor_create_step_title)
            } else {
                stringResource(R.string.macropad_editor_save_step_title)
            },
        description =
            if (step == null) {
                stringResource(R.string.macropad_editor_create_step_desc)
            } else {
                stringResource(R.string.macropad_editor_save_step_desc)
            },
        pulseOnChanges = hasChanges,
        saveActionText =
            if (step == null) {
                stringResource(R.string.gamepad_action_create)
            } else {
                stringResource(R.string.gamepad_action_save)
            },
        saveIcon = Icons.Rounded.Save,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    // ── Step Deletion (Last Item) ────────────────────────────────────────────
    if (step != null && onDelete != null) {
        val stepNumber = (stepIndex ?: 0) + 1
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.macropad_macro_step_delete),
            confirmTitle = stringResource(R.string.macropad_macro_step_delete_confirm_title, stepNumber),
            description = stringResource(R.string.macropad_macro_step_delete_desc),
            actionText = stringResource(R.string.gamepad_action_delete),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            icon = Icons.Rounded.Delete,
            isDestructive = true,
            onConfirm = onDelete,
        )
    }
}
