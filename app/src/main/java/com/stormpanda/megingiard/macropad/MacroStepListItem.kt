package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.sqrt

private const val TAG = "MacroStepListItem"
private const val MTE_PADDING = 16

private fun dirArrow(dirX: Int, dirY: Int): String = when {
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

private fun joyDirArrow(x: Float, y: Float): String {
    val mag = sqrt(x * x + y * y)
    if (mag < 0.1f) return "·"
    val nx = when {
        x / mag > 0.5f -> 1
        x / mag < -0.5f -> -1
        else -> 0
    }
    val ny = when {
        y / mag > 0.5f -> 1
        y / mag < -0.5f -> -1
        else -> 0
    }
    return dirArrow(nx, ny)
}

internal fun shortStepLabel(step: MacroStep, swapFaceButtons: Boolean, tapLabel: String, gestureLabel: String): String = when (step) {
    is MacroStep.GamepadButtonTap -> gamepadCodeDisplayShortLabel(step.btnCode, swapFaceButtons)
    is MacroStep.JoystickMove -> {
        val stick = if (step.stick == JoystickStick.LEFT) "L" else "R"
        "$stick${joyDirArrow(step.x, step.y)}"
    }
    is MacroStep.DPadTap -> dirArrow(step.dirX, step.dirY)
    is MacroStep.TouchTap -> tapLabel
    is MacroStep.JoystickPath -> {
        val stick = if (step.stick == JoystickStick.LEFT) "L" else "R"
        "$stick↻"
    }
    is MacroStep.TouchPath -> gestureLabel
}

internal fun stepColor(
    step: MacroStep,
    accentColor: Color,
    joystickColor: Color,
    dpadColor: Color,
    touchColor: Color,
): Color = when (step) {
    is MacroStep.GamepadButtonTap -> accentColor
    is MacroStep.JoystickMove -> joystickColor
    is MacroStep.DPadTap -> dpadColor
    is MacroStep.TouchTap -> touchColor
    is MacroStep.JoystickPath -> joystickColor
    is MacroStep.TouchPath -> touchColor
}

@Composable
internal fun StepListItem(
    index: Int,
    step: MacroStep,
    accentColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
    val joystickColor = colors.actionColorGamepad
    val dpadColor = colors.actionColorSystem
    val touchColor = MaterialTheme.colorScheme.tertiary
    val typeLabel = stringResource(
        when (step) {
            is MacroStep.GamepadButtonTap -> R.string.macropad_macro_step_type_gamepad
            is MacroStep.JoystickMove -> R.string.macropad_macro_step_type_joystick
            is MacroStep.DPadTap -> R.string.macropad_macro_step_type_dpad
            is MacroStep.TouchTap -> R.string.macropad_macro_step_type_touch
            is MacroStep.JoystickPath -> R.string.macropad_macro_step_type_joystick_path
            is MacroStep.TouchPath -> R.string.macropad_macro_step_type_touch_path
        },
    )
    val description = when (step) {
        is MacroStep.GamepadButtonTap -> gamepadCodeDisplayLabel(step.btnCode, swapFaceButtons)
        is MacroStep.JoystickMove -> {
            val stickLabel = if (step.stick == JoystickStick.LEFT) "L" else "R"
            "$stickLabel ${joyDirArrow(step.x, step.y)}"
        }
        is MacroStep.DPadTap -> dirArrow(step.dirX, step.dirY)
        is MacroStep.TouchTap -> "${"%.2f".format(step.normX)}, ${"%.2f".format(step.normY)}"
        is MacroStep.JoystickPath -> {
            val stickLabel = if (step.stick == JoystickStick.LEFT) "L" else "R"
            stringResource(R.string.macropad_macro_step_joystick_path_short, stickLabel, step.samples.size)
        }
        is MacroStep.TouchPath -> stringResource(R.string.macropad_macro_step_short_samples_count, step.samples.size)
    }
    val indicatorColor = stepColor(step, accentColor, joystickColor, dpadColor, touchColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = MTE_PADDING.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${index + 1}. $typeLabel: $description",
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.macropad_macro_step_timing, step.startTimeMs, step.durationMs),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_macro_step_edit),
                tint = colors.onSurfaceSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.macropad_editor_delete_button),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}
