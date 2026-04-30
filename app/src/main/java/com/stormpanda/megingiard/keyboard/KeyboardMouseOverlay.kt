package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.ui.LocalAppColors

@Suppress("unused")
private const val TAG = "KeyboardMouseOverlay"

// ---------------------------------------------------------------------------
// Mouse overlay constants
// ---------------------------------------------------------------------------
internal val KB_MOUSE_BTN_W = 60.dp        // MacroPad MP_BUTTON_UNIT_DP × 1
internal val KB_MOUSE_BTN_H = 120.dp       // MacroPad MP_BUTTON_UNIT_DP × 2
internal val KB_MOUSE_BTN_SHAPE = RoundedCornerShape(percent = 50)
internal val KB_MOUSE_BTN_GAP = 8.dp
internal const val KB_MOUSE_BTN_NORMAL_ALPHA = 0.25f
internal const val KB_MOUSE_BTN_PRESSED_ALPHA = 0.80f
internal const val KB_MOUSE_BTN_PRESS_ANIM_MS = 80
internal const val KB_MOUSE_BTN_RELEASE_ANIM_MS = 160
internal val KB_MOUSE_BTN_1X1 = 60.dp     // 1×1 circle: MMB, scroll wheel
internal const val KB_SCROLL_SENSITIVITY_PX = 12f

// ---------------------------------------------------------------------------
// Mouse button column (trackpoint overlay)
// ---------------------------------------------------------------------------

/**
 * A vertical 1×2 block of LMB + RMB buttons placed at a configurable edge of the
 * trackpoint overlay. Events are consumed in the Initial pass so the outer keyboard
 * handler (Main pass) never sees them and cannot accidentally close the overlay.
 */
@Composable
internal fun MouseButtonColumn(
    accentColor: Color,
    onLmbDown: () -> Unit,
    onLmbUp: () -> Unit,
    onRmbDown: () -> Unit,
    onRmbUp: () -> Unit,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val btnColumn = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        ) {
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_left),
                accentColor = accentColor,
                onDown = onLmbDown,
                onUp = onLmbUp,
            )
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_middle),
                accentColor = accentColor,
                onDown = { MouseInjector.middleDown() },
                onUp = { MouseInjector.middleUp() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_right),
                accentColor = accentColor,
                onDown = onRmbDown,
                onUp = onRmbUp,
            )
        }
    }
    val scrollColumn = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        ) {
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_4),
                accentColor = accentColor,
                onDown = { MouseInjector.mouse4Down() },
                onUp = { MouseInjector.mouse4Up() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
            ScrollWheelButton(accentColor = accentColor)
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_5),
                accentColor = accentColor,
                onDown = { MouseInjector.mouse5Down() },
                onUp = { MouseInjector.mouse5Up() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mirrored) {
            scrollColumn()
            btnColumn()
        } else {
            btnColumn()
            scrollColumn()
        }
    }
}

// ---------------------------------------------------------------------------
// Single mouse button
// ---------------------------------------------------------------------------

/**
 * A single pressable mouse button. Pointer events are consumed using
 * [PointerEventPass.Initial] so they are handled before the outer keyboard
 * [PointerEventPass.Main] handler, preventing accidental overlay closure.
 */
@Composable
internal fun MouseButton(
    label: String,
    accentColor: Color,
    onDown: () -> Unit,
    onUp: () -> Unit,
    width: Dp = KB_MOUSE_BTN_W,
    height: Dp = KB_MOUSE_BTN_H,
    shape: Shape = KB_MOUSE_BTN_SHAPE,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val alpha by animateFloatAsState(
        targetValue = if (pressed) KB_MOUSE_BTN_PRESSED_ALPHA else KB_MOUSE_BTN_NORMAL_ALPHA,
        animationSpec = tween(if (pressed) KB_MOUSE_BTN_PRESS_ANIM_MS else KB_MOUSE_BTN_RELEASE_ANIM_MS),
        label = "mouseBtnAlpha",
    )
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    // Only consume events for pointers that *started* within this button.
                    // Pointers originating outside (e.g. the trackpoint finger) are ignored
                    // so the outer handler can still close the overlay on trackpoint release.
                    val activePids = mutableSetOf<PointerId>()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val pid = change.id
                            when (event.type) {
                                PointerEventType.Press -> if (!change.previousPressed) {
                                    if (change.position.x in 0f..size.width.toFloat() &&
                                        change.position.y in 0f..size.height.toFloat()) {
                                        activePids += pid
                                        pressed = true
                                        onDown()
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> if (!change.pressed && pid in activePids) {
                                    activePids -= pid
                                    if (activePids.isEmpty()) pressed = false
                                    onUp()
                                    change.consume()
                                }
                                PointerEventType.Move -> if (pid in activePids) {
                                    change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ---------------------------------------------------------------------------
// Scroll wheel button
// ---------------------------------------------------------------------------

/**
 * A draggable scroll wheel button. Vertical drag accumulates and sends
 * [MouseInjector.scrollWheel] events every [KB_SCROLL_SENSITIVITY_PX] pixels.
 * Matches the MacroPad scroll wheel design (1×1 circle, accentColor alpha face).
 */
@Composable
internal fun ScrollWheelButton(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pressed) KB_MOUSE_BTN_PRESSED_ALPHA else KB_MOUSE_BTN_NORMAL_ALPHA,
        animationSpec = tween(if (pressed) KB_MOUSE_BTN_PRESS_ANIM_MS else KB_MOUSE_BTN_RELEASE_ANIM_MS),
        label = "scrollWheelAlpha",
    )
    Box(
        modifier = modifier
            .size(KB_MOUSE_BTN_W, KB_MOUSE_BTN_H)
            .clip(KB_MOUSE_BTN_SHAPE)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, KB_MOUSE_BTN_SHAPE)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val activePids = mutableSetOf<PointerId>()
                    val accumY = mutableMapOf<PointerId, Float>()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val pid = change.id
                            when (event.type) {
                                PointerEventType.Press -> if (!change.previousPressed) {
                                    if (change.position.x in 0f..size.width.toFloat() &&
                                        change.position.y in 0f..size.height.toFloat()) {
                                        activePids += pid
                                        accumY[pid] = 0f
                                        pressed = true
                                        change.consume()
                                    }
                                }
                                PointerEventType.Move -> if (pid in activePids) {
                                    val accumulated = (accumY[pid] ?: 0f) + change.positionChange().y
                                    val units = (accumulated / KB_SCROLL_SENSITIVITY_PX).toInt()
                                    accumY[pid] = accumulated - units * KB_SCROLL_SENSITIVITY_PX
                                    if (units != 0) MouseInjector.scrollWheel(units)
                                    change.consume()
                                }
                                PointerEventType.Release -> if (!change.pressed && pid in activePids) {
                                    activePids -= pid
                                    accumY -= pid
                                    if (activePids.isEmpty()) pressed = false
                                    change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ScrollWheelFace(accentColor = accentColor)
    }
}

@Composable
internal fun ScrollWheelFace(accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = accentColor,                   modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accentColor,                   modifier = Modifier.size(18.dp))
    }
}
