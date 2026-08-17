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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.primaryOverlayFocusable
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroStepEditDialog"

private const val MSD_GRID_CELL_SIZE = 48
private const val MSD_GRID_SPACING = 6
private const val MSD_DEFAULT_DURATION_MS = 100L

private enum class StepType { GAMEPAD, JOYSTICK, JOYSTICK_PATH, DPAD, TOUCH, TOUCH_PATH }

private val MSD_DIR_LABELS: Map<Pair<Int, Int>, String> =
    mapOf(
        Pair(-1, -1) to "↖",
        Pair(0, -1) to "↑",
        Pair(1, -1) to "↗",
        Pair(-1, 0) to "←",
        Pair(1, 0) to "→",
        Pair(-1, 1) to "↙",
        Pair(0, 1) to "↓",
        Pair(1, 1) to "↘",
    )

@Composable
internal fun MacroStepEditSubPageContent(
    macroName: String,
    step: MacroStep?,
    accentColor: Color,
    suggestedStartTimeMs: Long,
    initialShiftMode: ShiftMode,
    onConfirm: (MacroStep, ShiftMode) -> Unit,
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
    val initialDurationMs = (step?.durationMs ?: MSD_DEFAULT_DURATION_MS).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    var stepType by remember { mutableStateOf(initialType) }
    var startMs by remember { mutableIntStateOf(initialStartMs) }
    var durMs by remember { mutableIntStateOf(initialDurationMs) }

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
            Pair(
                when {
                    step.x / mag > 0.5f -> 1
                    step.x / mag < -0.5f -> -1
                    else -> 0
                },
                when {
                    step.y / mag > 0.5f -> 1
                    step.y / mag < -0.5f -> -1
                    else -> 0
                },
            )
        } else {
            Pair(1, 0)
        }
    var joyDirX by remember { mutableIntStateOf(initJoyDir.first) }
    var joyDirY by remember { mutableIntStateOf(initJoyDir.second) }
    var joyMagnitude by remember {
        mutableFloatStateOf(
            if (step is MacroStep.JoystickMove) {
                sqrt(step.x * step.x + step.y * step.y).coerceIn(0f, 1f)
            } else {
                1f
            },
        )
    }

    // DPadTap state
    var dpadDirX by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirX else 0) }
    var dpadDirY by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirY else -1) }
    var shiftMode by remember { mutableStateOf(initialShiftMode) }

    val isConfirmEnabled =
        durMs > 0 &&
            when (stepType) {
                StepType.GAMEPAD -> true
                StepType.JOYSTICK -> !(joyDirX == 0 && joyDirY == 0)
                StepType.JOYSTICK_PATH -> step is MacroStep.JoystickPath
                StepType.DPAD -> !(dpadDirX == 0 && dpadDirY == 0)
                StepType.TOUCH -> true
                StepType.TOUCH_PATH -> step is MacroStep.TouchPath
            }

    val subPageTitle =
        stringResource(
            if (step == null) {
                R.string.macropad_macro_step_new
            } else {
                R.string.macropad_macro_step_edit
            },
        )

    GamepadSubPageHeader(
        breadcrumbs =
            listOf(
                stringResource(R.string.macropad_macro_list_title),
                macroName,
                subPageTitle,
            ),
        accentColor = accentColor,
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GamepadSectionHeader(
            text = stringResource(R.string.macro_step_action_type_title),
            color = accentColor,
        )

        val availableTypes =
            StepType.entries.filter { type ->
                val initialIsRecorded =
                    initialType == StepType.TOUCH || initialType == StepType.TOUCH_PATH || initialType == StepType.JOYSTICK_PATH
                val typeIsRecorded = type == StepType.TOUCH || type == StepType.TOUCH_PATH || type == StepType.JOYSTICK_PATH
                if (initialIsRecorded) type == initialType else !typeIsRecorded
            }
        val currentTypeIdx = availableTypes.indexOf(stepType).coerceAtLeast(0)
        val typeLabels =
            availableTypes.map { type ->
                when (type) {
                    StepType.GAMEPAD -> stringResource(R.string.macropad_macro_step_type_gamepad)
                    StepType.JOYSTICK -> stringResource(R.string.macropad_macro_step_type_joystick)
                    StepType.JOYSTICK_PATH -> stringResource(R.string.macropad_macro_step_type_joystick_path)
                    StepType.DPAD -> stringResource(R.string.macropad_macro_step_type_dpad)
                    StepType.TOUCH -> stringResource(R.string.macropad_macro_step_type_touch)
                    StepType.TOUCH_PATH -> stringResource(R.string.macropad_macro_step_type_touch_path)
                }
            }

        GamepadChoiceCard(
            title = stringResource(R.string.macro_step_action_type_title),
            description = stringResource(R.string.macro_step_action_type_desc),
            selectedText = typeLabels[currentTypeIdx],
            icon = Icons.Rounded.SportsEsports,
            enabled = availableTypes.size > 1,
            onPrevious = {
                val nextIdx = (currentTypeIdx - 1 + availableTypes.size) % availableTypes.size
                stepType = availableTypes[nextIdx]
            },
            onNext = {
                val nextIdx = (currentTypeIdx + 1) % availableTypes.size
                stepType = availableTypes[nextIdx]
            },
        )

        when (stepType) {
            StepType.GAMEPAD -> {
                val presets = GamepadKeycodes.PRESETS
                val presetIdx = presets.indexOf(selectedPreset).coerceAtLeast(0)
                GamepadChoiceCard(
                    title = stringResource(R.string.macro_step_gamepad_button_title),
                    description = stringResource(R.string.macro_step_gamepad_button_desc),
                    selectedText = selectedPreset.localizedDisplayLabel(swapFaceButtons),
                    icon = Icons.Rounded.SportsEsports,
                    onPrevious = {
                        val nextIdx = (presetIdx - 1 + presets.size) % presets.size
                        selectedPreset = presets[nextIdx]
                    },
                    onNext = {
                        val nextIdx = (presetIdx + 1) % presets.size
                        selectedPreset = presets[nextIdx]
                    },
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
                    icon = Icons.Rounded.SportsEsports,
                    onPrevious = {
                        val nextIdx = (stickIdx - 1 + sticks.size) % sticks.size
                        joyStick = sticks[nextIdx]
                    },
                    onNext = {
                        val nextIdx = (stickIdx + 1) % sticks.size
                        joyStick = sticks[nextIdx]
                    },
                )

                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_macro_step_direction),
                    color = accentColor,
                )
                DirectionGrid(
                    selectedX = joyDirX,
                    selectedY = joyDirY,
                    accentColor = accentColor,
                    onSelect = { x, y ->
                        joyDirX = x
                        joyDirY = y
                    },
                )

                GamepadStepperCard(
                    title = stringResource(R.string.macropad_macro_step_magnitude, joyMagnitude),
                    description = stringResource(R.string.macro_step_stick_deflection_desc),
                    valueText = "${(joyMagnitude * 100).roundToInt()}%",
                    icon = Icons.Rounded.SportsEsports,
                    onDecrement = {
                        joyMagnitude = (joyMagnitude - 0.1f).coerceIn(0.1f, 1.0f)
                    },
                    onIncrement = {
                        joyMagnitude = (joyMagnitude + 0.1f).coerceIn(0.1f, 1.0f)
                    },
                )
            }

            StepType.DPAD -> {
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_macro_step_direction),
                    color = accentColor,
                )
                DirectionGrid(
                    selectedX = dpadDirX,
                    selectedY = dpadDirY,
                    accentColor = accentColor,
                    onSelect = { x, y ->
                        dpadDirX = x
                        dpadDirY = y
                    },
                )
            }

            StepType.TOUCH -> {
                val touchStep = step as? MacroStep.TouchTap
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_macro_step_touch_position),
                    color = accentColor,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "X: ${"%.3f".format(touchStep?.normX ?: 0f)}",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Y: ${"%.3f".format(touchStep?.normY ?: 0f)}",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            StepType.JOYSTICK_PATH -> {
                val pathStep = step as? MacroStep.JoystickPath
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_macro_step_path_readonly),
                    color = accentColor,
                )
                val stickLabel =
                    stringResource(
                        if (pathStep?.stick == JoystickStick.RIGHT) {
                            R.string.macropad_macro_step_stick_right
                        } else {
                            R.string.macropad_macro_step_stick_left
                        },
                    )
                Text(
                    stringResource(R.string.macropad_macro_step_path_summary_stick, stickLabel),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.macropad_macro_step_path_summary_samples,
                        pathStep?.samples?.size ?: 0,
                    ),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            StepType.TOUCH_PATH -> {
                val touchPathStep = step as? MacroStep.TouchPath
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_macro_step_touch_path_readonly),
                    color = accentColor,
                )
                if (touchPathStep != null) {
                    Text(
                        stringResource(R.string.macropad_macro_step_touch_path_details, touchPathStep.samples.size),
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen editor for creating or editing a single [MacroStep].
 */
@Composable
internal fun MacroStepEditDialog(
    step: MacroStep?,
    accentColor: Color,
    suggestedStartTimeMs: Long,
    initialShiftMode: ShiftMode,
    onConfirm: (MacroStep, ShiftMode) -> Unit,
    onDismiss: () -> Unit,
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
    val initialDurationMs = (step?.durationMs ?: MSD_DEFAULT_DURATION_MS).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    var stepType by remember { mutableStateOf(initialType) }
    var startMs by remember { mutableIntStateOf(initialStartMs) }
    var durMs by remember { mutableIntStateOf(initialDurationMs) }

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
            Pair(
                when {
                    step.x / mag > 0.5f -> 1
                    step.x / mag < -0.5f -> -1
                    else -> 0
                },
                when {
                    step.y / mag > 0.5f -> 1
                    step.y / mag < -0.5f -> -1
                    else -> 0
                },
            )
        } else {
            Pair(1, 0)
        }
    var joyDirX by remember { mutableIntStateOf(initJoyDir.first) }
    var joyDirY by remember { mutableIntStateOf(initJoyDir.second) }
    var joyMagnitude by remember {
        mutableFloatStateOf(
            if (step is MacroStep.JoystickMove) {
                sqrt(step.x * step.x + step.y * step.y).coerceIn(0f, 1f)
            } else {
                1f
            },
        )
    }

    // DPadTap state
    var dpadDirX by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirX else 0) }
    var dpadDirY by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirY else -1) }
    var shiftMode by remember { mutableStateOf(initialShiftMode) }

    val isConfirmEnabled =
        durMs > 0 &&
            when (stepType) {
                StepType.GAMEPAD -> true
                StepType.JOYSTICK -> !(joyDirX == 0 && joyDirY == 0)
                StepType.JOYSTICK_PATH -> step is MacroStep.JoystickPath
                StepType.DPAD -> !(dpadDirX == 0 && dpadDirY == 0)
                StepType.TOUCH -> true
                StepType.TOUCH_PATH -> step is MacroStep.TouchPath
            }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .blockPointerEvents(),
    ) {
        val topBarTitle =
            stringResource(
                if (step == null) {
                    R.string.macropad_macro_step_new
                } else {
                    R.string.macropad_macro_step_edit
                },
            )
        FullScreenTopBar(
            title = topBarTitle,
            onDismiss = {
                AppLog.d(TAG, "MacroStepEditDialog: dismissed")
                onDismiss()
            },
        ) {
            TextButton(
                onClick = {
                    val builtStep =
                        when (stepType) {
                            StepType.GAMEPAD -> {
                                MacroStep.GamepadButtonTap(
                                    startTimeMs = startMs.toLong(),
                                    durationMs = durMs.toLong().coerceAtLeast(1L),
                                    btnCode = selectedPreset.code,
                                    label = selectedPreset.displayShortLabel(swapFaceButtons),
                                )
                            }

                            StepType.JOYSTICK -> {
                                val norm = sqrt((joyDirX * joyDirX + joyDirY * joyDirY).toFloat())
                                MacroStep.JoystickMove(
                                    startTimeMs = startMs.toLong(),
                                    durationMs = durMs.toLong().coerceAtLeast(1L),
                                    stick = joyStick,
                                    x = if (norm > 0f) joyDirX / norm * joyMagnitude else 0f,
                                    y = if (norm > 0f) joyDirY / norm * joyMagnitude else 0f,
                                )
                            }

                            StepType.DPAD -> {
                                MacroStep.DPadTap(
                                    startTimeMs = startMs.toLong(),
                                    durationMs = durMs.toLong().coerceAtLeast(1L),
                                    dirX = dpadDirX,
                                    dirY = dpadDirY,
                                )
                            }

                            StepType.TOUCH -> {
                                (step as MacroStep.TouchTap).copy(
                                    startTimeMs = startMs.toLong(),
                                    durationMs = durMs.toLong().coerceAtLeast(1L),
                                )
                            }

                            StepType.JOYSTICK_PATH -> {
                                (step as MacroStep.JoystickPath).copy(
                                    startTimeMs = startMs.toLong(),
                                    durationMs = durMs.toLong().coerceAtLeast(1L),
                                )
                            }

                            StepType.TOUCH_PATH -> {
                                (step as MacroStep.TouchPath).let { tp ->
                                    tp.copy(
                                        startTimeMs = startMs.toLong(),
                                        durationMs =
                                            durMs.toLong().coerceAtLeast(
                                                tp.samples.maxOfOrNull { it.offsetMs }?.coerceAtLeast(1L) ?: 1L,
                                            ),
                                    )
                                }
                            }
                        }
                    AppLog.d(
                        TAG,
                        "MacroStepEditDialog: confirmed step type=${builtStep::class.simpleName} start=${builtStep.startTimeMs} duration=${builtStep.durationMs}",
                    )
                    onConfirm(builtStep, shiftMode)
                },
                enabled = isConfirmEnabled,
            ) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GamepadSectionHeader(
                text = stringResource(R.string.macro_step_action_type_title),
                color = accentColor,
            )

            val availableTypes =
                StepType.entries.filter { type ->
                    val initialIsRecorded =
                        initialType == StepType.TOUCH || initialType == StepType.TOUCH_PATH || initialType == StepType.JOYSTICK_PATH
                    val typeIsRecorded = type == StepType.TOUCH || type == StepType.TOUCH_PATH || type == StepType.JOYSTICK_PATH
                    if (initialIsRecorded) type == initialType else !typeIsRecorded
                }
            val currentTypeIdx = availableTypes.indexOf(stepType).coerceAtLeast(0)
            val typeLabels =
                availableTypes.map { type ->
                    when (type) {
                        StepType.GAMEPAD -> stringResource(R.string.macropad_macro_step_type_gamepad)
                        StepType.JOYSTICK -> stringResource(R.string.macropad_macro_step_type_joystick)
                        StepType.JOYSTICK_PATH -> stringResource(R.string.macropad_macro_step_type_joystick_path)
                        StepType.DPAD -> stringResource(R.string.macropad_macro_step_type_dpad)
                        StepType.TOUCH -> stringResource(R.string.macropad_macro_step_type_touch)
                        StepType.TOUCH_PATH -> stringResource(R.string.macropad_macro_step_type_touch_path)
                    }
                }

            GamepadChoiceCard(
                title = stringResource(R.string.macro_step_action_type_title),
                description = stringResource(R.string.macro_step_action_type_desc),
                selectedText = typeLabels[currentTypeIdx],
                icon = Icons.Rounded.SportsEsports,
                enabled = availableTypes.size > 1,
                onPrevious = {
                    val nextIdx = (currentTypeIdx - 1 + availableTypes.size) % availableTypes.size
                    stepType = availableTypes[nextIdx]
                },
                onNext = {
                    val nextIdx = (currentTypeIdx + 1) % availableTypes.size
                    stepType = availableTypes[nextIdx]
                },
            )

            when (stepType) {
                StepType.GAMEPAD -> {
                    val presets = GamepadKeycodes.PRESETS
                    val presetIdx = presets.indexOf(selectedPreset).coerceAtLeast(0)
                    GamepadChoiceCard(
                        title = stringResource(R.string.macro_step_gamepad_button_title),
                        description = stringResource(R.string.macro_step_gamepad_button_desc),
                        selectedText = selectedPreset.localizedDisplayLabel(swapFaceButtons),
                        icon = Icons.Rounded.SportsEsports,
                        onPrevious = {
                            val nextIdx = (presetIdx - 1 + presets.size) % presets.size
                            selectedPreset = presets[nextIdx]
                        },
                        onNext = {
                            val nextIdx = (presetIdx + 1) % presets.size
                            selectedPreset = presets[nextIdx]
                        },
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
                        icon = Icons.Rounded.SportsEsports,
                        onPrevious = {
                            val nextIdx = (stickIdx - 1 + sticks.size) % sticks.size
                            joyStick = sticks[nextIdx]
                        },
                        onNext = {
                            val nextIdx = (stickIdx + 1) % sticks.size
                            joyStick = sticks[nextIdx]
                        },
                    )

                    GamepadSectionHeader(
                        text = stringResource(R.string.macropad_macro_step_direction),
                        color = accentColor,
                    )
                    DirectionGrid(
                        selectedX = joyDirX,
                        selectedY = joyDirY,
                        accentColor = accentColor,
                        onSelect = { x, y ->
                            joyDirX = x
                            joyDirY = y
                        },
                    )

                    GamepadStepperCard(
                        title = stringResource(R.string.macropad_macro_step_magnitude, joyMagnitude),
                        description = stringResource(R.string.macro_step_stick_deflection_desc),
                        valueText = "${(joyMagnitude * 100).roundToInt()}%",
                        icon = Icons.Rounded.SportsEsports,
                        onDecrement = {
                            joyMagnitude = (joyMagnitude - 0.1f).coerceIn(0.1f, 1.0f)
                        },
                        onIncrement = {
                            joyMagnitude = (joyMagnitude + 0.1f).coerceIn(0.1f, 1.0f)
                        },
                    )
                }

                StepType.DPAD -> {
                    GamepadSectionHeader(
                        text = stringResource(R.string.macropad_macro_step_direction),
                        color = accentColor,
                    )
                    DirectionGrid(
                        selectedX = dpadDirX,
                        selectedY = dpadDirY,
                        accentColor = accentColor,
                        onSelect = { x, y ->
                            dpadDirX = x
                            dpadDirY = y
                        },
                    )
                }

                StepType.TOUCH -> {
                    val touchStep = step as? MacroStep.TouchTap
                    GamepadSectionHeader(
                        text = stringResource(R.string.macropad_macro_step_touch_position),
                        color = accentColor,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "X: ${"%.3f".format(touchStep?.normX ?: 0f)}",
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Y: ${"%.3f".format(touchStep?.normY ?: 0f)}",
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                StepType.JOYSTICK_PATH -> {
                    val pathStep = step as? MacroStep.JoystickPath
                    GamepadSectionHeader(
                        text = stringResource(R.string.macropad_macro_step_path_readonly),
                        color = accentColor,
                    )
                    val stickLabel =
                        stringResource(
                            if (pathStep?.stick == JoystickStick.RIGHT) {
                                R.string.macropad_macro_step_stick_right
                            } else {
                                R.string.macropad_macro_step_stick_left
                            },
                        )
                    Text(
                        stringResource(R.string.macropad_macro_step_path_summary_stick, stickLabel),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.macropad_macro_step_path_summary_samples,
                            pathStep?.samples?.size ?: 0,
                        ),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                StepType.TOUCH_PATH -> {
                    val touchPathStep = step as? MacroStep.TouchPath
                    GamepadSectionHeader(
                        text = stringResource(R.string.macropad_macro_step_touch_path_readonly),
                        color = accentColor,
                    )
                    if (touchPathStep != null) {
                        Text(
                            stringResource(R.string.macropad_macro_step_touch_path_details, touchPathStep.samples.size),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            GamepadSectionHeader(
                text = stringResource(R.string.macropad_macro_section_timing),
                color = accentColor,
            )

            // Timing
            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_step_start_ms),
                description = stringResource(R.string.macro_step_offset_desc),
                valueText = "$startMs ms",
                icon = Icons.Rounded.Timer,
                onDecrement = {
                    startMs = (startMs - 50).coerceAtLeast(0)
                },
                onIncrement = {
                    startMs += 50
                },
            )

            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_step_duration_ms),
                description = stringResource(R.string.macro_step_hold_duration_desc),
                valueText = "$durMs ms",
                icon = Icons.Rounded.Timer,
                onDecrement = {
                    durMs = (durMs - 25).coerceAtLeast(10)
                },
                onIncrement = {
                    durMs += 25
                },
            )

            val shiftModes = listOf(ShiftMode.NONE, ShiftMode.START_DELTA, ShiftMode.END_DELTA)
            val shiftLabels =
                listOf(
                    stringResource(R.string.macropad_macro_editor_shift_none),
                    stringResource(R.string.macropad_macro_editor_shift_start_delta),
                    stringResource(R.string.macropad_macro_editor_shift_end_delta),
                )
            val shiftIdx = shiftModes.indexOf(shiftMode).coerceAtLeast(0)
            GamepadChoiceCard(
                title = stringResource(R.string.macropad_macro_editor_shift_subsequent),
                description = stringResource(R.string.macro_step_ripple_desc),
                selectedText = shiftLabels[shiftIdx],
                icon = Icons.Rounded.Repeat,
                onPrevious = {
                    val nextIdx = (shiftIdx - 1 + shiftModes.size) % shiftModes.size
                    shiftMode = shiftModes[nextIdx]
                },
                onNext = {
                    val nextIdx = (shiftIdx + 1) % shiftModes.size
                    shiftMode = shiftModes[nextIdx]
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DirectionGrid(
    selectedX: Int,
    selectedY: Int,
    accentColor: Color,
    onSelect: (Int, Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(MSD_GRID_SPACING.dp)) {
        listOf(-1, 0, 1).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MSD_GRID_SPACING.dp)) {
                listOf(-1, 0, 1).forEach { col ->
                    val isCenter = col == 0 && row == 0
                    val isSelected = !isCenter && col == selectedX && row == selectedY
                    var isFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier =
                            Modifier
                                .size(MSD_GRID_CELL_SIZE.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isCenter -> Color.Transparent
                                        isSelected -> accentColor.copy(alpha = 0.35f)
                                        else -> colors.surface
                                    },
                                ).border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color =
                                        when {
                                            isCenter -> Color.Transparent
                                            isSelected -> accentColor
                                            else -> colors.subduedBorder
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                ).then(
                                    if (isCenter) {
                                        Modifier
                                    } else {
                                        Modifier.primaryOverlayFocusable(
                                            onClick = { onSelect(col, row) },
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isCenter) {
                            Text(
                                text = MSD_DIR_LABELS[Pair(col, row)] ?: "",
                                color = if (isSelected || isFocused) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
