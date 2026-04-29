package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "GamepadRecordingOverlay"
private val GRO_PADDING = 16.dp
private val GRO_STICK_SIZE = 144.dp
private val GRO_STICK_THUMB_SIZE = 52.dp
private val GRO_DPAD_ARROW_SIZE = 48.dp
private val GRO_FACE_BUTTON_SIZE = 52.dp
private val GRO_FACE_CLUSTER_SIZE = 148.dp
private val GRO_SHOULDER_BUTTON_WIDTH = 76.dp
private val GRO_SHOULDER_BUTTON_HEIGHT = 50.dp
private val GRO_SHOULDER_SPACING = 10.dp
private val GRO_CENTER_BUTTON_WIDTH = 62.dp
private val GRO_CENTER_BUTTON_HEIGHT = 40.dp
private val GRO_CLUSTER_SPACING = 8.dp
private const val GRO_STICK_VISUAL_SCALE = 0.52f
private const val GRO_STICK_DEAD_ZONE = 0.14f
private const val GRO_INT16_MAX = 32767f

private data class FaceButtonSpec(
    val code: Int,
    val label: String,
    val alignment: Alignment,
)

@Composable
internal fun GamepadRecordingOverlay(
    state: GamepadRecordingState.Recording,
    swapFaceButtons: Boolean,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit,
    onDpadChanged: (Int, Int) -> Unit,
    onJoystickChanged: (JoystickStick, Float, Float) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    DisposableEffect(Unit) {
        AppLog.d(TAG, "visible")
        onDispose { AppLog.d(TAG, "disposed") }
    }
    val faceButtons = remember(swapFaceButtons) {
        listOf(
            FaceButtonSpec(GamepadKeycodes.BTN_NORTH, gamepadCodeDisplayShortLabel(GamepadKeycodes.BTN_NORTH, swapFaceButtons), Alignment.TopCenter),
            FaceButtonSpec(GamepadKeycodes.BTN_WEST, gamepadCodeDisplayShortLabel(GamepadKeycodes.BTN_WEST, swapFaceButtons), Alignment.CenterStart),
            FaceButtonSpec(GamepadKeycodes.BTN_EAST, gamepadCodeDisplayShortLabel(GamepadKeycodes.BTN_EAST, swapFaceButtons), Alignment.CenterEnd),
            FaceButtonSpec(GamepadKeycodes.BTN_SOUTH, gamepadCodeDisplayShortLabel(GamepadKeycodes.BTN_SOUTH, swapFaceButtons), Alignment.BottomCenter),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = GRO_PADDING, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.macropad_macro_record_gamepad_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.macropad_macro_record_gamepad_cancel),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onStop) {
                    Text(
                        text = stringResource(R.string.macropad_macro_record_gamepad_stop),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            // Controls strip — symmetric single row:
            // [LB (outer) | LT (inner)]  …  [SE | ST]  …  [RT (inner) | RB (outer)]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GRO_PADDING, vertical = GRO_CLUSTER_SPACING),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(GRO_SHOULDER_SPACING)) {
                    ShoulderButton(
                        iconName = "game_bumper_left",
                        pressed = state.pressedButtons.contains(GamepadKeycodes.BTN_TL),
                        code = GamepadKeycodes.BTN_TL,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                    ShoulderButton(
                        iconName = "game_trigger_left",
                        pressed = state.pressedButtons.contains(GamepadKeycodes.BTN_TL2),
                        code = GamepadKeycodes.BTN_TL2,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(GRO_CLUSTER_SPACING)) {
                    CenterControlButton(
                        label = stringResource(R.string.macropad_macro_record_gamepad_select),
                        code = GamepadKeycodes.BTN_SELECT,
                        isPressed = state.pressedButtons.contains(GamepadKeycodes.BTN_SELECT),
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                    CenterControlButton(
                        label = stringResource(R.string.macropad_macro_record_gamepad_start),
                        code = GamepadKeycodes.BTN_START,
                        isPressed = state.pressedButtons.contains(GamepadKeycodes.BTN_START),
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(GRO_SHOULDER_SPACING)) {
                    ShoulderButton(
                        iconName = "game_trigger_right",
                        pressed = state.pressedButtons.contains(GamepadKeycodes.BTN_TR2),
                        code = GamepadKeycodes.BTN_TR2,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                    ShoulderButton(
                        iconName = "game_bumper_right",
                        pressed = state.pressedButtons.contains(GamepadKeycodes.BTN_TR),
                        code = GamepadKeycodes.BTN_TR,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                }
            }
            // Main area — two equal-width columns.
            // Both sticks rendered at the top of their column (same Y level).
            // D-Pad and Face-Cluster rendered at the bottom (same Y level).
            // SpaceBetween distributes remaining height between them symmetrically.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = GRO_PADDING, end = GRO_PADDING, bottom = GRO_CLUSTER_SPACING),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    StickSurface(
                        x = state.leftStickX,
                        y = state.leftStickY,
                        onChanged = { x, y -> onJoystickChanged(JoystickStick.LEFT, x, y) },
                    )
                    DpadButtons(
                        dirX = state.dpadDirectionX,
                        dirY = state.dpadDirectionY,
                        onChanged = onDpadChanged,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    StickSurface(
                        x = state.rightStickX,
                        y = state.rightStickY,
                        onChanged = { x, y -> onJoystickChanged(JoystickStick.RIGHT, x, y) },
                    )
                    FaceButtonCluster(
                        buttons = faceButtons,
                        pressedButtons = state.pressedButtons,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                    )
                }
            }
            // Bottom row: L3 and R3 at equal edge distances
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GRO_PADDING, end = GRO_PADDING, bottom = GRO_PADDING),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PressableIconButton(
                    code = GamepadKeycodes.BTN_THUMBL,
                    isPressed = state.pressedButtons.contains(GamepadKeycodes.BTN_THUMBL),
                    onButtonDown = onButtonDown,
                    onButtonUp = onButtonUp,
                    size = GRO_FACE_BUTTON_SIZE,
                    label = stringResource(R.string.macropad_macro_record_gamepad_l3),
                )
                PressableIconButton(
                    code = GamepadKeycodes.BTN_THUMBR,
                    isPressed = state.pressedButtons.contains(GamepadKeycodes.BTN_THUMBR),
                    onButtonDown = onButtonDown,
                    onButtonUp = onButtonUp,
                    size = GRO_FACE_BUTTON_SIZE,
                    label = stringResource(R.string.macropad_macro_record_gamepad_r3),
                )
            }
        }
    }
}

@Composable
private fun FaceButtonCluster(
    buttons: List<FaceButtonSpec>,
    pressedButtons: Set<Int>,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit,
) {
    Box(modifier = Modifier.size(GRO_FACE_CLUSTER_SIZE)) {
        buttons.forEach { spec ->
            Box(
                modifier = Modifier
                    .align(spec.alignment)
                    .padding(4.dp),
            ) {
                PressableIconButton(
                    code = spec.code,
                    isPressed = pressedButtons.contains(spec.code),
                    onButtonDown = onButtonDown,
                    onButtonUp = onButtonUp,
                    size = GRO_FACE_BUTTON_SIZE,
                    label = spec.label,
                )
            }
        }
    }
}

@Composable
private fun ShoulderButton(
    iconName: String,
    pressed: Boolean,
    code: Int,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    PressableSurface(
        modifier = Modifier
            .width(GRO_SHOULDER_BUTTON_WIDTH)
            .height(GRO_SHOULDER_BUTTON_HEIGHT),
        pressed = pressed,
        onPress = { onButtonDown(code) },
        onRelease = { onButtonUp(code) },
    ) {
        MaterialSymbol(
            name = iconName,
            size = 28.dp,
            tint = if (pressed) colors.onAccent else colors.accent,
            filled = pressed,
        )
    }
}

@Composable
private fun CenterControlButton(
    label: String,
    code: Int,
    isPressed: Boolean,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    PressableSurface(
        modifier = Modifier
            .width(GRO_CENTER_BUTTON_WIDTH)
            .height(GRO_CENTER_BUTTON_HEIGHT),
        pressed = isPressed,
        onPress = { onButtonDown(code) },
        onRelease = { onButtonUp(code) },
    ) {
        Text(
            text = label,
            color = if (isPressed) colors.onAccent else colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

// D-Pad as 4 individual arrow buttons in a cross/plus layout
@Composable
private fun DpadButtons(
    dirX: Int,
    dirY: Int,
    onChanged: (Int, Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val upPressed = dirY < 0 && dirX == 0
    val downPressed = dirY > 0 && dirX == 0
    val leftPressed = dirX < 0 && dirY == 0
    val rightPressed = dirX > 0 && dirY == 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PressableSurface(
            modifier = Modifier.size(GRO_DPAD_ARROW_SIZE),
            pressed = upPressed,
            onPress = { onChanged(0, -1) },
            onRelease = { onChanged(0, 0) },
        ) {
            MaterialSymbol(
                name = "keyboard_arrow_up",
                size = 28.dp,
                tint = if (upPressed) colors.onAccent else colors.accent,
                filled = upPressed,
            )
        }
        Row {
            PressableSurface(
                modifier = Modifier.size(GRO_DPAD_ARROW_SIZE),
                pressed = leftPressed,
                onPress = { onChanged(-1, 0) },
                onRelease = { onChanged(0, 0) },
            ) {
                MaterialSymbol(
                    name = "keyboard_arrow_left",
                    size = 28.dp,
                    tint = if (leftPressed) colors.onAccent else colors.accent,
                    filled = leftPressed,
                )
            }
            Spacer(Modifier.size(GRO_DPAD_ARROW_SIZE))
            PressableSurface(
                modifier = Modifier.size(GRO_DPAD_ARROW_SIZE),
                pressed = rightPressed,
                onPress = { onChanged(1, 0) },
                onRelease = { onChanged(0, 0) },
            ) {
                MaterialSymbol(
                    name = "keyboard_arrow_right",
                    size = 28.dp,
                    tint = if (rightPressed) colors.onAccent else colors.accent,
                    filled = rightPressed,
                )
            }
        }
        PressableSurface(
            modifier = Modifier.size(GRO_DPAD_ARROW_SIZE),
            pressed = downPressed,
            onPress = { onChanged(0, 1) },
            onRelease = { onChanged(0, 0) },
        ) {
            MaterialSymbol(
                name = "keyboard_arrow_down",
                size = 28.dp,
                tint = if (downPressed) colors.onAccent else colors.accent,
                filled = downPressed,
            )
        }
    }
}

@Composable
private fun StickSurface(
    surfaceSize: Dp = GRO_STICK_SIZE,
    x: Float,
    y: Float,
    onChanged: (Float, Float) -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(surfaceSize)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    onChangedFromPosition(
                        position = down.position,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        onChanged = onChanged,
                    )
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            onChanged(0f, 0f)
                            change.consume()
                            break
                        }
                        onChangedFromPosition(
                            position = change.position,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            onChanged = onChanged,
                        )
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            drawCircle(
                color = colors.controlOverlayBorder,
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )
            drawCircle(
                color = colors.accent.copy(alpha = 0.14f),
                radius = radius,
                center = center,
            )
            val thumbOffset = Offset(
                x = center.x + (x * radius * GRO_STICK_VISUAL_SCALE),
                y = center.y + (y * radius * GRO_STICK_VISUAL_SCALE),
            )
            drawCircle(
                color = colors.onSurface.copy(alpha = 0.15f),
                radius = GRO_STICK_THUMB_SIZE.toPx() / 2f,
                center = thumbOffset,
            )
            drawCircle(
                color = colors.accent,
                radius = (GRO_STICK_THUMB_SIZE.toPx() / 2f) - 4.dp.toPx(),
                center = thumbOffset,
            )
        }
    }
}

@Composable
private fun PressableIconButton(
    code: Int,
    isPressed: Boolean,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit,
    size: androidx.compose.ui.unit.Dp,
    label: String,
) {
    val colors = LocalAppColors.current
    PressableSurface(
        modifier = Modifier.size(size),
        pressed = isPressed,
        onPress = { onButtonDown(code) },
        onRelease = { onButtonUp(code) },
        shape = CircleShape,
    ) {
        Text(
            text = label,
            color = if (isPressed) colors.onAccent else colors.accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PressableSurface(
    modifier: Modifier,
    pressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) colors.accent else colors.surface)
            .border(1.dp, if (pressed) colors.accent else colors.controlOverlayBorder, shape)
            .pointerInput(onPress, onRelease) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            onRelease()
                            change?.consume()
                            break
                        }
                        change.consume()
                    } while (true)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun onChangedFromPosition(
    position: Offset,
    width: Float,
    height: Float,
    onChanged: (Float, Float) -> Unit,
) {
    val radius = min(width, height) / 2f
    val dx = position.x - (width / 2f)
    val dy = position.y - (height / 2f)
    val magnitude = sqrt(dx * dx + dy * dy)
    if (magnitude <= radius * GRO_STICK_DEAD_ZONE) {
        onChanged(0f, 0f)
        return
    }
    val clampedMagnitude = min(magnitude, radius)
    val angle = atan2(dy, dx)
    val normX = ((cos(angle) * clampedMagnitude) / radius).coerceIn(-1f, 1f)
    val normY = ((sin(angle) * clampedMagnitude) / radius).coerceIn(-1f, 1f)
    onChanged(normX, normY)
}

internal fun normalizedAxisValue(value: Float): Int =
    (value.coerceIn(-1f, 1f) * GRO_INT16_MAX).roundToInt().coerceIn(-32767, 32767)
