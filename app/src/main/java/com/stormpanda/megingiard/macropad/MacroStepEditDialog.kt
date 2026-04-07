package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MSD_GRID_CELL_SIZE        = 44
private const val MSD_GRID_SPACING          = 4
private const val MSD_DEFAULT_DURATION_MS   = 100L
private const val MSD_TIMING_MAX_MS         = 10000
private const val MSD_TIMING_STEP_MS        = 100
private const val MSD_TIMING_FINE_MS        = 10
private const val MSD_TIMING_SLIDER_STEPS   = 99

// ─────────────────────────────────────────────────────────────────────────────
// Step type (editor-internal)
// ─────────────────────────────────────────────────────────────────────────────

private enum class StepType { GAMEPAD, JOYSTICK, DPAD }

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
    onConfirm:   (MacroStep) -> Unit,
    onDismiss:   () -> Unit,
) {
    val colors = LocalAppColors.current

    // ── Derive initial state from the existing step ───────────────────────────
    val initialType = when (step) {
        is MacroStep.GamepadButtonTap -> StepType.GAMEPAD
        is MacroStep.JoystickMove     -> StepType.JOYSTICK
        is MacroStep.DPadTap          -> StepType.DPAD
        null                          -> StepType.GAMEPAD
    }
    var stepType  by remember { mutableStateOf(initialType) }
    var startMs by remember { mutableIntStateOf((step?.startTimeMs ?: 0L).toInt().coerceIn(0, MSD_TIMING_MAX_MS)) }
    var durMs   by remember { mutableIntStateOf((step?.durationMs ?: MSD_DEFAULT_DURATION_MS).toInt().coerceIn(0, MSD_TIMING_MAX_MS)) }

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

    // ── Confirm guard ─────────────────────────────────────────────────────────
    val isConfirmEnabled = durMs > 0 && when (stepType) {
        StepType.GAMEPAD  -> true
        StepType.JOYSTICK -> !(joyDirX == 0 && joyDirY == 0)
        StepType.DPAD     -> !(dpadDirX == 0 && dpadDirY == 0)
    }

    // ── Full-screen layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        // ── Top bar ────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
            Text(
                text = stringResource(
                    if (step == null) R.string.macropad_macro_step_new
                    else             R.string.macropad_macro_step_edit
                ),
                color      = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
                textAlign  = TextAlign.Center,
            )
            TextButton(
                onClick = {
                    val builtStep = when (stepType) {
                        StepType.GAMEPAD -> MacroStep.GamepadButtonTap(
                            startTimeMs = startMs.toLong(),
                            durationMs  = durMs.toLong().coerceAtLeast(1L),
                            btnCode     = selectedPreset.code,
                            label       = selectedPreset.shortLabel,
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
                    }
                    onConfirm(builtStep)
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
                    val selected = type == stepType
                    val labelRes = when (type) {
                        StepType.GAMEPAD  -> R.string.macropad_macro_step_type_gamepad
                        StepType.JOYSTICK -> R.string.macropad_macro_step_type_joystick
                        StepType.DPAD     -> R.string.macropad_macro_step_type_dpad
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) accentColor.copy(alpha = 0.2f)
                                else          Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (selected) accentColor else accentColor.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { stepType = type }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text     = stringResource(labelRes),
                            color    = if (selected) accentColor else colors.onSurfaceSecondary,
                            fontSize = 13.sp,
                        )
                    }
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
                                selectedPreset.label,
                                color    = accentColor,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
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
                                            preset.label,
                                            color    = if (preset.code == selectedPreset.code) accentColor else colors.onSurface,
                                            fontSize = 14.sp,
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
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.macropad_macro_step_direction),
                        color    = colors.onSurfaceSecondary,
                        fontSize = 12.sp,
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
                        fontSize = 12.sp,
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
                        fontSize = 12.sp,
                    )
                    DirectionGrid(
                        selectedX   = dpadDirX,
                        selectedY   = dpadDirY,
                        accentColor = accentColor,
                        onSelect    = { x, y -> dpadDirX = x; dpadDirY = y },
                    )
                }
            }

            // Timing
            MsdTimingRow(
                label       = stringResource(R.string.macropad_macro_step_start_ms),
                valueMs     = startMs,
                accentColor = accentColor,
                onChange    = { startMs = it },
            )
            MsdTimingRow(
                label       = stringResource(R.string.macropad_macro_step_duration_ms),
                valueMs     = durMs,
                accentColor = accentColor,
                onChange    = { durMs = it },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Timing row — label + current value + slider + +fine button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MsdTimingRow(
    label:       String,
    valueMs:     Int,
    accentColor: Color,
    onChange:    (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(label, color = colors.onSurfaceSecondary, fontSize = 12.sp)
            Text("$valueMs ms", color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value         = valueMs.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, MSD_TIMING_MAX_MS)) },
                valueRange    = 0f..MSD_TIMING_MAX_MS.toFloat(),
                steps         = MSD_TIMING_SLIDER_STEPS,
                colors        = SliderDefaults.colors(
                    thumbColor       = accentColor,
                    activeTrackColor = accentColor,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange((valueMs + MSD_TIMING_FINE_MS).coerceAtMost(MSD_TIMING_MAX_MS)) }) {
                Text("+${MSD_TIMING_FINE_MS}ms", color = accentColor, fontSize = 12.sp)
            }
        }
    }
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
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
