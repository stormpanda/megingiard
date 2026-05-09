package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.stormpanda.megingiard.ui.blockPointerEvents
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroStepEditDialog"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MSD_GRID_CELL_SIZE        = 44
private const val MSD_GRID_SPACING          = 4
private const val MSD_DEFAULT_DURATION_MS   = 100L
private const val MSD_DEFAULT_NEW_STEP_START_OFFSET_MS = 2_000L
private const val MSD_TIMING_BASE_START_MAX_MS = 10_000
private const val MSD_TIMING_BASE_DURATION_MAX_MS = 1_000
private const val MSD_TIMING_SCALE_STEP_MS = 1_000
private const val MSD_TIMING_SLIDER_INCREMENT_MS = 100
private const val MSD_TIMING_DELTA_MINUS_HUNDRED_MS = -100
private const val MSD_TIMING_DELTA_MINUS_TEN_MS = -10
private const val MSD_TIMING_DELTA_MINUS_ONE_MS = -1
private const val MSD_TIMING_DELTA_PLUS_ONE_MS = 1
private const val MSD_TIMING_DELTA_PLUS_TEN_MS = 10
private const val MSD_TIMING_DELTA_PLUS_HUNDRED_MS = 100
private const val MSD_TIMING_DELTA_PLUS_THOUSAND_MS = 1_000
private const val MSD_TIMING_DELTA_BUTTON_MIN_WIDTH = 30
private const val MSD_TIMING_DELTA_BUTTON_MIN_HEIGHT = 28
private const val MSD_TIMING_DELTA_BUTTON_H_PADDING = 4
private const val MSD_TIMING_DELTA_BUTTON_V_PADDING = 2
// ─────────────────────────────────────────────────────────────────────────────
// Step type (editor-internal)
// ─────────────────────────────────────────────────────────────────────────────

private enum class StepType { GAMEPAD, JOYSTICK, JOYSTICK_PATH, DPAD, TOUCH }

// ─────────────────────────────────────────────────────────────────────────────
// Direction arrow labels keyed by (col, row) in the 3×3 grid.
// col ∈ {−1, 0, +1} maps to dirX; row ∈ {−1, 0, +1} maps to dirY.
// Center (0, 0) is intentionally absent — that cell is rendered invisible.
// ─────────────────────────────────────────────────────────────────────────────

private val MSD_DIR_LABELS: Map<Pair<Int, Int>, String> = mapOf(
    Pair(-1, -1) to "↖",
    Pair( 0, -1) to "↑",
    Pair( 1, -1) to "↗",
    Pair(-1,  0) to "←",
    Pair( 1,  0) to "→",
    Pair(-1,  1) to "↙",
    Pair( 0,  1) to "↓",
    Pair( 1,  1) to "↘",
)

// ─────────────────────────────────────────────────────────────────────────────
// Dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen editor for creating or editing a single [MacroStep].
 *
 * @param step        Existing step to edit, or `null` to create a new one.
 * @param accentColor Accent color from the calling screen.
 * @param onConfirm   Called with the completed [MacroStep] when the user confirms.
 * @param onDismiss   Called when the user cancels or otherwise requests dismissal
 *                    from this screen's UI.
 */
@Composable
internal fun MacroStepEditDialog(
    step:        MacroStep?,
    accentColor: Color,
    suggestedStartTimeMs: Long,
    initialShiftMode: ShiftMode,
    onConfirm:   (MacroStep, ShiftMode) -> Unit,
    onDismiss:   () -> Unit,
) {
    val colors = LocalAppColors.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    // ── Derive initial state from the existing step ───────────────────────────
    val initialType = when (step) {
        is MacroStep.GamepadButtonTap -> StepType.GAMEPAD
        is MacroStep.JoystickMove     -> StepType.JOYSTICK
        is MacroStep.DPadTap          -> StepType.DPAD
        is MacroStep.TouchTap         -> StepType.TOUCH
        is MacroStep.JoystickPath     -> StepType.JOYSTICK_PATH
        null                          -> StepType.GAMEPAD
    }

    val initialStartMs = when {
        step != null -> clampLongToNonNegativeInt(step.startTimeMs)
        suggestedStartTimeMs > 0L -> clampLongToNonNegativeInt(suggestedStartTimeMs)
        else -> clampLongToNonNegativeInt(MSD_DEFAULT_NEW_STEP_START_OFFSET_MS)
    }
    val initialDurationMs = clampLongToNonNegativeInt(step?.durationMs ?: MSD_DEFAULT_DURATION_MS)

    var stepType  by remember { mutableStateOf(initialType) }
    var startMs by remember { mutableIntStateOf(initialStartMs) }
    var durMs by remember { mutableIntStateOf(initialDurationMs) }
    var startMaxMs by remember {
        mutableIntStateOf(expandScaleToFit(MSD_TIMING_BASE_START_MAX_MS, initialStartMs))
    }
    var durationMaxMs by remember {
        mutableIntStateOf(expandScaleToFit(MSD_TIMING_BASE_DURATION_MAX_MS, initialDurationMs))
    }

    // GamepadButtonTap state
    val initPreset = if (step is MacroStep.GamepadButtonTap)
        GamepadKeycodes.PRESETS.firstOrNull { it.code == step.btnCode } ?: GamepadKeycodes.PRESETS.first()
    else
        GamepadKeycodes.PRESETS.first()
    var selectedPreset by remember { mutableStateOf(initPreset) }

    // JoystickMove state
    var joyStick by remember {
        mutableStateOf(if (step is MacroStep.JoystickMove) step.stick else JoystickStick.LEFT)
    }
    val initJoyDir = if (step is MacroStep.JoystickMove && (step.x != 0f || step.y != 0f)) {
        val mag = sqrt(step.x * step.x + step.y * step.y)
        Pair(
            when { step.x / mag > 0.5f ->  1; step.x / mag < -0.5f -> -1; else -> 0 },
            when { step.y / mag > 0.5f ->  1; step.y / mag < -0.5f -> -1; else -> 0 },
        )
    } else {
        Pair(1, 0) // default: right
    }
    var joyDirX      by remember { mutableIntStateOf(initJoyDir.first) }
    var joyDirY      by remember { mutableIntStateOf(initJoyDir.second) }
    var joyMagnitude by remember {
        mutableFloatStateOf(
            if (step is MacroStep.JoystickMove)
                sqrt(step.x * step.x + step.y * step.y).coerceIn(0f, 1f)
            else
                1f
        )
    }

    // DPadTap state
    var dpadDirX by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirX else 0) }
    var dpadDirY by remember { mutableIntStateOf(if (step is MacroStep.DPadTap) step.dirY else -1) }
    var shiftMode by remember { mutableStateOf(initialShiftMode) }

    // ── Confirm guard ─────────────────────────────────────────────────────────
    val isConfirmEnabled = durMs > 0 && when (stepType) {
        StepType.GAMEPAD       -> true
        StepType.JOYSTICK      -> !(joyDirX == 0 && joyDirY == 0)
        StepType.JOYSTICK_PATH -> step is MacroStep.JoystickPath
        StepType.DPAD          -> !(dpadDirX == 0 && dpadDirY == 0)
        StepType.TOUCH         -> true
    }

    // ── Full-screen layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .blockPointerEvents(),
    ) {
        val topBarTitle = stringResource(
            if (step == null) R.string.macropad_macro_step_new
            else             R.string.macropad_macro_step_edit
        )
        FullScreenTopBar(title = topBarTitle, onDismiss = onDismiss) {
            TextButton(
                onClick = {
                    val builtStep = when (stepType) {
                        StepType.GAMEPAD -> MacroStep.GamepadButtonTap(
                            startTimeMs = startMs.toLong(),
                            durationMs  = durMs.toLong().coerceAtLeast(1L),
                            btnCode     = selectedPreset.code,
                            label       = selectedPreset.displayShortLabel(swapFaceButtons),
                        )
                        StepType.JOYSTICK -> {
                            val norm = sqrt((joyDirX * joyDirX + joyDirY * joyDirY).toFloat())
                            MacroStep.JoystickMove(
                                startTimeMs = startMs.toLong(),
                                durationMs  = durMs.toLong().coerceAtLeast(1L),
                                stick       = joyStick,
                                x           = if (norm > 0f) joyDirX / norm * joyMagnitude else 0f,
                                y           = if (norm > 0f) joyDirY / norm * joyMagnitude else 0f,
                            )
                        }
                        StepType.DPAD -> MacroStep.DPadTap(
                            startTimeMs = startMs.toLong(),
                            durationMs  = durMs.toLong().coerceAtLeast(1L),
                            dirX        = dpadDirX,
                            dirY        = dpadDirY,
                        )
                        StepType.TOUCH -> (step as MacroStep.TouchTap).copy(
                            startTimeMs = startMs.toLong(),
                            durationMs  = durMs.toLong().coerceAtLeast(1L),
                        )
                        StepType.JOYSTICK_PATH -> (step as MacroStep.JoystickPath).copy(
                            startTimeMs = startMs.toLong(),
                            durationMs  = durMs.toLong().coerceAtLeast(1L),
                        )
                    }
                    onConfirm(builtStep, shiftMode)
                },
                enabled = isConfirmEnabled,
            ) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color      = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Scrollable form body ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepType.entries.forEach { type ->
                    /* TOUCH and JOYSTICK_PATH are recorded — only show their chip when editing
                       a step of that type, and hide them when editing any other step. */
                    val initialIsRecorded = initialType == StepType.TOUCH || initialType == StepType.JOYSTICK_PATH
                    val typeIsRecorded = type == StepType.TOUCH || type == StepType.JOYSTICK_PATH
                    if (initialIsRecorded && type != initialType) return@forEach
                    if (!initialIsRecorded && typeIsRecorded) return@forEach
                    val selected = type == stepType
                    val labelRes = when (type) {
                        StepType.GAMEPAD       -> R.string.macropad_macro_step_type_gamepad
                        StepType.JOYSTICK      -> R.string.macropad_macro_step_type_joystick
                        StepType.JOYSTICK_PATH -> R.string.macropad_macro_step_type_joystick_path
                        StepType.DPAD          -> R.string.macropad_macro_step_type_dpad
                        StepType.TOUCH         -> R.string.macropad_macro_step_type_touch
                    }
                    val symbolName = when (type) {
                        StepType.GAMEPAD       -> "sports_esports"
                        StepType.JOYSTICK      -> "joystick"
                        StepType.JOYSTICK_PATH -> "joystick"
                        StepType.DPAD          -> "gamepad"
                        StepType.TOUCH         -> "touch_app"
                    }
                    StepTypeChip(
                        text = stringResource(labelRes),
                        symbolName = symbolName,
                        selected = selected,
                        enabled = !typeIsRecorded,
                        onClick = { stepType = type },
                    )
                }
            }

            // Type-specific content
            when (stepType) {
                StepType.GAMEPAD -> {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedPreset.localizedDisplayLabel(swapFaceButtons),
                                color    = accentColor,
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                        }
                        DropdownMenu(
                            expanded         = expanded,
                            onDismissRequest = { expanded = false },
                            modifier         = Modifier.background(colors.surface),
                        ) {
                            GamepadKeycodes.PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            preset.localizedDisplayLabel(swapFaceButtons),
                                            color    = if (preset.code == selectedPreset.code) accentColor else colors.onSurface,
                                            style    = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    onClick = { selectedPreset = preset; expanded = false },
                                )
                            }
                        }
                    }
                }

                StepType.JOYSTICK -> {
                    // Stick selector chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            JoystickStick.LEFT  to stringResource(R.string.macropad_macro_step_stick_left),
                            JoystickStick.RIGHT to stringResource(R.string.macropad_macro_step_stick_right),
                        ).forEach { (stick, stickLabel) ->
                            val sel = joyStick == stick
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (sel) accentColor.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (sel) accentColor else accentColor.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable { joyStick = stick }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    stickLabel,
                                    color    = if (sel) accentColor else colors.onSurfaceSecondary,
                                    style    = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.macropad_macro_step_direction),
                        color    = colors.onSurfaceSecondary,
                        style    = MaterialTheme.typography.bodySmall,
                    )
                    DirectionGrid(
                        selectedX   = joyDirX,
                        selectedY   = joyDirY,
                        accentColor = accentColor,
                        onSelect    = { x, y -> joyDirX = x; joyDirY = y },
                    )

                    Text(
                        stringResource(R.string.macropad_macro_step_magnitude, joyMagnitude),
                        color    = colors.onSurfaceSecondary,
                        style    = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value         = joyMagnitude,
                        onValueChange = { joyMagnitude = it },
                        valueRange    = 0f..1f,
                        colors        = SliderDefaults.colors(
                            thumbColor       = accentColor,
                            activeTrackColor = accentColor,
                        ),
                    )
                }

                StepType.DPAD -> {
                    Text(
                        stringResource(R.string.macropad_macro_step_direction),
                        color    = colors.onSurfaceSecondary,
                        style    = MaterialTheme.typography.bodySmall,
                    )
                    DirectionGrid(
                        selectedX   = dpadDirX,
                        selectedY   = dpadDirY,
                        accentColor = accentColor,
                        onSelect    = { x, y -> dpadDirX = x; dpadDirY = y },
                    )
                }

                StepType.TOUCH -> {
                    val touchStep = step as? MacroStep.TouchTap
                    Text(
                        stringResource(R.string.macropad_macro_step_touch_position),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
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
                    Text(
                        stringResource(R.string.macropad_macro_step_path_readonly),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val stickLabel = stringResource(
                        if (pathStep?.stick == JoystickStick.RIGHT)
                            R.string.macropad_macro_step_stick_right
                        else
                            R.string.macropad_macro_step_stick_left
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
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Timing
            MsdTimingRow(
                label       = stringResource(R.string.macropad_macro_step_start_ms),
                valueMs     = startMs,
                sliderMaxMs = startMaxMs,
                accentColor = accentColor,
                onSliderMaxChange = { startMaxMs = it },
                onChange    = { startMs = it },
            )
            MsdTimingRow(
                label       = stringResource(R.string.macropad_macro_step_duration_ms),
                valueMs     = durMs,
                sliderMaxMs = durationMaxMs,
                accentColor = accentColor,
                onSliderMaxChange = { durationMaxMs = it },
                onChange    = { durMs = it },
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.macropad_macro_editor_shift_subsequent),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ShiftMode.NONE        to stringResource(R.string.macropad_macro_editor_shift_none),
                        ShiftMode.START_DELTA to stringResource(R.string.macropad_macro_editor_shift_start_delta),
                        ShiftMode.END_DELTA   to stringResource(R.string.macropad_macro_editor_shift_end_delta),
                    ).forEach { (mode, label) ->
                        AppSelectableChip(
                            text     = label,
                            selected = shiftMode == mode,
                            onClick  = { shiftMode = mode },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTypeChip(
    text: String,
    symbolName: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AppSelectableChip(
        text    = text,
        selected = selected,
        enabled  = enabled,
        onClick  = onClick,
        leadingIcon = { color ->
            MaterialSymbol(
                name = symbolName,
                size = 18.dp,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Timing row — label + current value + slider + delta buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MsdTimingRow(
    label:       String,
    valueMs:     Int,
    sliderMaxMs: Int,
    accentColor: Color,
    onSliderMaxChange: (Int) -> Unit,
    onChange:    (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    fun applyDelta(deltaMs: Int) {
        val nextValue = (valueMs + deltaMs).coerceAtLeast(0)
        val nextMax = expandScaleToFit(sliderMaxMs, nextValue)
        if (nextMax != sliderMaxMs) onSliderMaxChange(nextMax)
        onChange(nextValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(label, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
            Text("$valueMs ms", color = colors.onSurface, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_MINUS_HUNDRED_MS) {
                applyDelta(MSD_TIMING_DELTA_MINUS_HUNDRED_MS)
            }
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_MINUS_TEN_MS) {
                applyDelta(MSD_TIMING_DELTA_MINUS_TEN_MS)
            }
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_MINUS_ONE_MS) {
                applyDelta(MSD_TIMING_DELTA_MINUS_ONE_MS)
            }
            Slider(
                value         = valueMs.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, sliderMaxMs)) },
                valueRange    = 0f..sliderMaxMs.toFloat(),
                steps = ((sliderMaxMs / MSD_TIMING_SLIDER_INCREMENT_MS) - 1).coerceAtLeast(0),
                colors        = SliderDefaults.colors(
                    thumbColor       = accentColor,
                    activeTrackColor = accentColor,
                ),
                modifier = Modifier.weight(1f),
            )
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_PLUS_ONE_MS) {
                applyDelta(MSD_TIMING_DELTA_PLUS_ONE_MS)
            }
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_PLUS_TEN_MS) {
                applyDelta(MSD_TIMING_DELTA_PLUS_TEN_MS)
            }
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_PLUS_HUNDRED_MS) {
                applyDelta(MSD_TIMING_DELTA_PLUS_HUNDRED_MS)
            }
            MsdTimingDeltaButton(accentColor = accentColor, deltaMs = MSD_TIMING_DELTA_PLUS_THOUSAND_MS) {
                applyDelta(MSD_TIMING_DELTA_PLUS_THOUSAND_MS)
            }
        }
    }
}

@Composable
private fun MsdTimingDeltaButton(
    accentColor: Color,
    deltaMs: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(
            horizontal = MSD_TIMING_DELTA_BUTTON_H_PADDING.dp,
            vertical = MSD_TIMING_DELTA_BUTTON_V_PADDING.dp,
        ),
        modifier = Modifier.defaultMinSize(
            minWidth = MSD_TIMING_DELTA_BUTTON_MIN_WIDTH.dp,
            minHeight = MSD_TIMING_DELTA_BUTTON_MIN_HEIGHT.dp,
        ),
    ) {
        Text(
            text = stringResource(R.string.macropad_macro_step_timing_delta, deltaMs),
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun expandScaleToFit(currentMaxMs: Int, requiredValueMs: Int): Int {
    var maxMs = currentMaxMs.coerceAtLeast(MSD_TIMING_SCALE_STEP_MS)
    while (requiredValueMs > maxMs) {
        maxMs += MSD_TIMING_SCALE_STEP_MS
    }
    return maxMs
}

private fun clampLongToNonNegativeInt(value: Long): Int {
    return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

// ─────────────────────────────────────────────────────────────────────────────
// Direction grid — 3×3 clickable cells; center cell is invisible placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DirectionGrid(
    selectedX:   Int,
    selectedY:   Int,
    accentColor: Color,
    onSelect:    (Int, Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(MSD_GRID_SPACING.dp)) {
        listOf(-1, 0, 1).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MSD_GRID_SPACING.dp)) {
                listOf(-1, 0, 1).forEach { col ->
                    val isCenter   = col == 0 && row == 0
                    val isSelected = !isCenter && col == selectedX && row == selectedY
                    Box(
                        modifier = Modifier
                            .size(MSD_GRID_CELL_SIZE.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isCenter   -> Color.Transparent
                                    isSelected -> accentColor.copy(alpha = 0.3f)
                                    else       -> colors.appBackground
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isCenter   -> Color.Transparent
                                    isSelected -> accentColor
                                    else       -> accentColor.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(6.dp),
                            )
                            .then(
                                if (isCenter) Modifier
                                else Modifier.clickable { onSelect(col, row) }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isCenter) {
                            Text(
                                text     = MSD_DIR_LABELS[Pair(col, row)] ?: "",
                                color    = if (isSelected) accentColor else colors.onSurface,
                                style    = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
