package com.stormpanda.megingiard.macropad

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val SLI_FULL_PERCENT = 100
private const val SLI_PERCENT_SCALE = 100f

internal fun dirArrow(
    dirX: Int,
    dirY: Int,
): String =
    when {
        dirX > 0 && dirY < 0 -> "↗"
        dirX > 0 && dirY == 0 -> "→"
        dirX > 0 && dirY > 0 -> "↘"
        dirX == 0 && dirY < 0 -> "↑"
        dirX == 0 && dirY > 0 -> "↓"
        dirX < 0 && dirY < 0 -> "↖"
        dirX < 0 && dirY == 0 -> "←"
        dirX < 0 && dirY > 0 -> "↙"
        else -> "·"
    }

private fun joyDirArrow(
    x: Float,
    y: Float,
): String {
    val mag = sqrt(x * x + y * y)
    if (mag < 0.1f) return "·"
    val nx =
        when {
            x / mag > 0.5f -> 1
            x / mag < -0.5f -> -1
            else -> 0
        }
    val ny =
        when {
            y / mag > 0.5f -> 1
            y / mag < -0.5f -> -1
            else -> 0
        }
    return dirArrow(nx, ny)
}

internal fun shortStepLabel(
    step: MacroStep,
    swapFaceButtons: Boolean,
    tapLabel: String,
    gestureLabel: String,
): String =
    when (step) {
        is MacroStep.GamepadButtonTap -> {
            gamepadCodeDisplayShortLabel(step.btnCode, swapFaceButtons)
        }

        is MacroStep.JoystickMove -> {
            val stick = if (step.stick == JoystickStick.LEFT) "L" else "R"
            "$stick${joyDirArrow(step.x, step.y)}"
        }

        is MacroStep.DPadTap -> {
            dirArrow(step.dirX, step.dirY)
        }

        is MacroStep.TouchTap -> {
            tapLabel
        }

        is MacroStep.JoystickPath -> {
            val stick = if (step.stick == JoystickStick.LEFT) "L" else "R"
            "$stick↻"
        }

        is MacroStep.TouchPath -> {
            gestureLabel
        }

        is MacroStep.KeyboardKeyTap -> {
            val modPrefix =
                step.modifiers
                    .mapNotNull { mod ->
                        MODIFIER_PRESETS.firstOrNull { it.first == mod }?.second
                    }.joinToString("+")
            if (modPrefix.isNotBlank()) {
                "$modPrefix+${step.label}"
            } else {
                step.label
            }
        }

        is MacroStep.TriggerPress -> {
            val name = if (step.trigger == GamepadTrigger.LEFT) "L2" else "R2"
            val pct = (step.pressure.coerceIn(0f, 1f) * SLI_PERCENT_SCALE).roundToInt()
            if (pct < SLI_FULL_PERCENT) "$name ($pct%)" else name
        }

        is MacroStep.TriggerPath -> {
            val name = if (step.trigger == GamepadTrigger.LEFT) "L2" else "R2"
            "$name↻"
        }
    }

internal fun stepIcon(step: MacroStep): ImageVector =
    when (step) {
        is MacroStep.GamepadButtonTap -> Icons.Rounded.SportsEsports
        is MacroStep.JoystickMove, is MacroStep.JoystickPath -> Icons.Rounded.NearMe
        is MacroStep.DPadTap -> Icons.Rounded.OpenWith
        is MacroStep.TouchTap, is MacroStep.TouchPath -> Icons.Rounded.TouchApp
        is MacroStep.KeyboardKeyTap -> Icons.Rounded.Keyboard
        is MacroStep.TriggerPress, is MacroStep.TriggerPath -> Icons.Rounded.Tune
    }

internal fun stepTypeLabelRes(step: MacroStep): Int =
    when (step) {
        is MacroStep.GamepadButtonTap -> R.string.macropad_macro_step_type_gamepad
        is MacroStep.JoystickMove -> R.string.macropad_macro_step_type_joystick
        is MacroStep.DPadTap -> R.string.macropad_macro_step_type_dpad
        is MacroStep.TouchTap -> R.string.macropad_macro_step_type_touch
        is MacroStep.JoystickPath -> R.string.macropad_macro_step_type_joystick_path
        is MacroStep.TouchPath -> R.string.macropad_macro_step_type_touch_path
        is MacroStep.KeyboardKeyTap -> R.string.macropad_macro_step_type_keyboard
        is MacroStep.TriggerPress -> R.string.macropad_macro_step_type_trigger
        is MacroStep.TriggerPath -> R.string.macropad_macro_step_type_trigger_path
    }

@Composable
internal fun stepTypeLabel(step: MacroStep): String = stringResource(stepTypeLabelRes(step))

internal fun stepTypeLabel(
    step: MacroStep,
    context: Context,
): String = context.getString(stepTypeLabelRes(step))

internal fun stepActionDescription(
    step: MacroStep,
    swapFaceButtons: Boolean,
    context: Context,
): String =
    when (step) {
        is MacroStep.GamepadButtonTap -> {
            gamepadCodeDisplayLabel(step.btnCode, swapFaceButtons, context)
        }

        is MacroStep.JoystickMove -> {
            val stickLabel =
                if (step.stick == JoystickStick.LEFT) {
                    context.getString(R.string.macropad_macro_step_stick_left)
                } else {
                    context.getString(R.string.macropad_macro_step_stick_right)
                }
            val magPercent = (sqrt(step.x * step.x + step.y * step.y).coerceIn(0f, 1f) * SLI_PERCENT_SCALE).toInt()
            "$stickLabel ${joyDirArrow(step.x, step.y)} ($magPercent%)"
        }

        is MacroStep.DPadTap -> {
            dirArrow(step.dirX, step.dirY)
        }

        is MacroStep.TouchTap -> {
            "X: ${(step.normX * SLI_PERCENT_SCALE).toInt()}%, Y: ${(step.normY * SLI_PERCENT_SCALE).toInt()}%"
        }

        is MacroStep.JoystickPath -> {
            val stickLabel =
                if (step.stick == JoystickStick.LEFT) {
                    context.getString(R.string.macropad_macro_step_stick_left)
                } else {
                    context.getString(R.string.macropad_macro_step_stick_right)
                }
            context.getString(R.string.macropad_macro_step_joystick_path_short, stickLabel, step.samples.size)
        }

        is MacroStep.TouchPath -> {
            context.getString(R.string.macropad_macro_step_short_samples_count, step.samples.size)
        }

        is MacroStep.KeyboardKeyTap -> {
            val modText =
                step.modifiers
                    .mapNotNull { mod ->
                        MODIFIER_PRESETS.firstOrNull { it.first == mod }?.second
                    }.joinToString(", ")
            if (modText.isNotBlank()) {
                "${step.label} ($modText)"
            } else {
                step.label
            }
        }

        is MacroStep.TriggerPress -> {
            val trigLabel =
                if (step.trigger == GamepadTrigger.LEFT) {
                    context.getString(R.string.macropad_macro_step_trigger_left)
                } else {
                    context.getString(R.string.macropad_macro_step_trigger_right)
                }
            val pct = (step.pressure.coerceIn(0f, 1f) * SLI_PERCENT_SCALE).roundToInt()
            "$trigLabel ($pct%)"
        }

        is MacroStep.TriggerPath -> {
            val trigLabel =
                if (step.trigger == GamepadTrigger.LEFT) {
                    context.getString(R.string.macropad_macro_step_trigger_left)
                } else {
                    context.getString(R.string.macropad_macro_step_trigger_right)
                }
            context.getString(R.string.macropad_macro_step_joystick_path_short, trigLabel, step.samples.size)
        }
    }

@Composable
internal fun stepActionDescription(
    step: MacroStep,
    swapFaceButtons: Boolean,
): String = stepActionDescription(step, swapFaceButtons, LocalContext.current)
