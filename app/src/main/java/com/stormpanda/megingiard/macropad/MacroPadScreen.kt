package com.stormpanda.megingiard.macropad

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 8.dp
private val MP_SCREEN_PADDING = 4.dp

// Sensitivity of trackpoint drag: px input → mouse delta
private const val MP_TRACKPOINT_SENSITIVITY = 3f

// Sensitivity of scroll wheel drag: px per scroll unit sent
private const val MP_SCROLL_SENSITIVITY_PX = 12f

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val context     = LocalContext.current
    val profile     by MacroPadState.activeProfile.collectAsState()
    val colors      = LocalAppColors.current

    // Start injectors after the carousel overlay has closed (same pattern as KeyboardScreen)
    LaunchedEffect(Unit) {
        AppStateManager.overlayVisible.first { !it }
        withContext(Dispatchers.IO) {
            val ap = MacroPadState.activeProfile.value
            if (ap?.enableKeyboard != false) KeyInjector.start(context)
            if (ap?.enableGamepad != false) GamepadInjector.start(context)
            if (ap?.enableMouse != false) MouseInjector.start(context)
        }
    }

    // Stop all injectors when leaving MACROPAD mode
    DisposableEffect(Unit) {
        onDispose {
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
        }
    }

    Box(
        modifier           = modifier.fillMaxSize().background(colors.appBackground).padding(MP_SCREEN_PADDING),
        contentAlignment   = Alignment.Center,
    ) {
        val p = profile
        if (p == null) {
            // No profile yet
            Text(
                text      = stringResource(R.string.macropad_no_profile),
                color     = colors.onSurfaceSecondary,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(32.dp),
            )
        } else {
            PadSurface(
                profile     = p,
                accentColor = colors.accent,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadSurface(profile: PadProfile, accentColor: Color) {
    val density      = LocalDensity.current
    val context      = LocalContext.current
    val colors       = LocalAppColors.current
    var canvasSize   by remember { mutableStateOf(IntSize.Zero) }
    val overlayVisible      by AppStateManager.overlayVisible.collectAsState()
    val overlayVisibleState  = rememberUpdatedState(overlayVisible)

    // Track which button IDs are currently pressed (multi-touch)
    var pressedIds  by remember { mutableStateOf(setOf<String>()) }
    // Pointer → button/trackpoint mapping for multi-touch release
    val pointerMap  = remember { mutableMapOf<PointerId, String>() }

    // Track last trackpoint finger position for delta computation
    var lastTpPos   by remember { mutableStateOf<Offset?>(null) }
    // Scroll-wheel buttons: track the Y position when the finger went down
    val scrollStartY = remember { mutableMapOf<PointerId, Float>() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MP_CORNER_RADIUS))
                .background(colors.surface)
                .border(1.dp, colors.accentBorder, RoundedCornerShape(MP_CORNER_RADIUS))
                .onSizeChanged { canvasSize = it }
                .pointerInput(profile, canvasSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent(PointerEventPass.Main)
                            val w       = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h       = canvasSize.height.toFloat().coerceAtLeast(1f)

                            // Block input while carousel overlay is open
                            if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            event.changes.forEach { change ->
                                val id = change.id
                                val px = change.position.x
                                val py = change.position.y
                                val nx = (px / w).coerceIn(0f, 1f)
                                val ny = (py / h).coerceIn(0f, 1f)

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        // Only act on the pointer that just went down;
                                        // other pointers in this batch event are still held from before.
                                        if (!change.previousPressed) {
                                        val hitButton = profile.buttons.firstOrNull { btn ->
                                            val isTrackpoint = btn.action is PadAction.TrackpointMove
                                            val chipWidthPx = with(density) {
                                                if (isTrackpoint) (MP_BUTTON_UNIT_DP * (btn.action as PadAction.TrackpointMove).size.multiplier).toPx()
                                                else (MP_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
                                            }
                                            val chipHeightPx = with(density) {
                                                if (isTrackpoint) (MP_BUTTON_UNIT_DP * (btn.action as PadAction.TrackpointMove).size.multiplier).toPx()
                                                else (MP_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
                                            }
                                            val bx = btn.posX * w
                                            val by = btn.posY * h
                                            px >= bx - chipWidthPx / 2f && px <= bx + chipWidthPx / 2f &&
                                            py >= by - chipHeightPx / 2f && py <= by + chipHeightPx / 2f
                                        }

                                        when {
                                            hitButton != null -> {
                                                // Check if the required device is disabled
                                                val disabledMsgRes = when (hitButton.action) {
                                                    is PadAction.KeyboardKey   -> if (!profile.enableKeyboard) R.string.macropad_device_disabled_keyboard else null
                                                    is PadAction.GamepadButton -> if (!profile.enableGamepad)  R.string.macropad_device_disabled_gamepad  else null
                                                    is PadAction.MouseButton,
                                                    is PadAction.ScrollWheel,
                                                    is PadAction.TrackpointMove,
                                                    is PadAction.MouseLeftClick,
                                                    is PadAction.MouseRightClick -> if (!profile.enableMouse) R.string.macropad_device_disabled_mouse else null
                                                    is PadAction.Macro           -> null
                                                }
                                                if (disabledMsgRes != null) {
                                                    Toast.makeText(context, disabledMsgRes, Toast.LENGTH_SHORT).show()
                                                } else {
                                                pointerMap[id] = hitButton.id
                                                when {
                                                    hitButton.action is PadAction.ScrollWheel -> {
                                                        // Scroll wheel: record start Y, do not inject down
                                                        scrollStartY[id] = py
                                                    }
                                                    hitButton.action is PadAction.TrackpointMove -> {
                                                        lastTpPos = change.position
                                                    }
                                                    else -> {
                                                        pressedIds = pressedIds + hitButton.id
                                                        injectActionDown(hitButton.action)
                                                    }
                                                }
                                                } // end device-enabled check
                                            }
                                        }
                                        } // end if (!change.previousPressed)
                                        change.consume()
                                    }

                                    PointerEventType.Move -> {
                                        when (pointerMap[id]) {
                                            null -> { /* unknown pointer */ }
                                            else -> {
                                                val mappedId  = pointerMap[id]
                                                val mappedBtn = profile.buttons.firstOrNull { it.id == mappedId }
                                                when {
                                                    mappedBtn?.action is PadAction.TrackpointMove -> {
                                                        val last = lastTpPos
                                                        if (last != null) {
                                                            val delta = change.positionChange()
                                                            val dx = (delta.x * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                                                            val dy = (delta.y * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                                                            if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
                                                        }
                                                        lastTpPos = change.position
                                                        change.consume()
                                                    }
                                                    mappedBtn?.action is PadAction.ScrollWheel -> {
                                                        val startY = scrollStartY[id]
                                                        if (startY != null) {
                                                            val totalDeltaY = startY - py  // negative = scroll down
                                                            // Convert pixel distance to scroll units with velocity scaling:
                                                            // units grow quadratically with distance for natural feel
                                                            val units = (totalDeltaY / MP_SCROLL_SENSITIVITY_PX).toInt()
                                                            if (units != 0) {
                                                                MouseInjector.scrollWheel(units)
                                                                // Move start point by the consumed pixels so delta resets
                                                                scrollStartY[id] = py
                                                            }
                                                        }
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        // Only act on the pointer that actually lifted;
                                        // other pointers in this batch event are still held.
                                        if (!change.pressed) {
                                            when (val mapped = pointerMap.remove(id)) {
                                                null -> { /* unknown pointer */ }
                                                else -> {
                                                    val btn = profile.buttons.firstOrNull { it.id == mapped }
                                                    when {
                                                        btn?.action is PadAction.TrackpointMove -> {
                                                            lastTpPos = null
                                                        }
                                                        btn?.action is PadAction.ScrollWheel -> {
                                                            scrollStartY.remove(id)
                                                        }
                                                        btn != null -> {
                                                            pressedIds = pressedIds - mapped
                                                            injectActionUp(btn.action)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        change.consume()
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }
                }
        ) {
            // Render buttons
            profile.buttons.forEach { btn ->
                val isDeviceDisabled = when (btn.action) {
                    is PadAction.KeyboardKey                 -> !profile.enableKeyboard
                    is PadAction.GamepadButton               -> !profile.enableGamepad
                    is PadAction.MouseButton,
                    is PadAction.ScrollWheel,
                    is PadAction.TrackpointMove,
                    is PadAction.MouseLeftClick,
                    is PadAction.MouseRightClick             -> !profile.enableMouse
                    is PadAction.Macro                       -> false
                }
                val isPressed = btn.id in pressedIds
                PadButton(
                    btn              = btn,
                    isPressed        = isPressed,
                    canvasSize       = canvasSize,
                    accentColor      = accentColor,
                    isDeviceDisabled = isDeviceDisabled,
                )
            }
        }
    }
}


