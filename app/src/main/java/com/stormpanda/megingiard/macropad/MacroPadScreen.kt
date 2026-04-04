package com.stormpanda.megingiard.macropad

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 8.dp

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
        modifier           = modifier.fillMaxSize().background(colors.appBackground),
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

    val scope = rememberCoroutineScope()

    // Macro recording/playback state
    val macroReader      = remember { MacroRecordingReader() }
    var recordingButtonId by remember { mutableStateOf<String?>(null) }
    var playingButtonId   by remember { mutableStateOf<String?>(null) }
    var playbackJob       by remember { mutableStateOf<Job?>(null) }
    val pressedDuringPlayback = remember { mutableSetOf<Int>() }

    DisposableEffect(macroReader) {
        onDispose { macroReader.destroy() }
    }

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
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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
                                                    is PadAction.Macro -> null
                                                }
                                                if (disabledMsgRes != null) {
                                                    Toast.makeText(context, disabledMsgRes, Toast.LENGTH_SHORT).show()
                                                } else {
                                                when {
                                                    // ── Macro buttons: handled without adding to pointerMap ──
                                                    hitButton.action is PadAction.Macro -> {
                                                        val macroAction = hitButton.action as PadAction.Macro
                                                        when {
                                                            // Tap again to stop recording
                                                            recordingButtonId == hitButton.id -> {
                                                                scope.launch {
                                                                    val raw = withContext(Dispatchers.IO) { macroReader.stop() }
                                                                    val trimmed = autoTrim(raw)
                                                                    val updatedBtn = hitButton.copy(action = PadAction.Macro(trimmed))
                                                                    MacroPadState.updateProfile(
                                                                        profile.copy(buttons = profile.buttons.map { if (it.id == hitButton.id) updatedBtn else it })
                                                                    )
                                                                    recordingButtonId = null
                                                                    Toast.makeText(context, R.string.macropad_recording_saved, Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            // Another button is already recording
                                                            recordingButtonId != null -> {
                                                                Toast.makeText(context, R.string.macropad_recording_in_progress, Toast.LENGTH_SHORT).show()
                                                            }
                                                            // Cancel playback
                                                            playingButtonId == hitButton.id -> {
                                                                playbackJob?.cancel()
                                                                playbackJob = null
                                                                pressedDuringPlayback.forEach { code -> GamepadInjector.buttonUp(code) }
                                                                pressedDuringPlayback.clear()
                                                                playingButtonId = null
                                                            }
                                                            // No events recorded yet: start recording
                                                            macroAction.events.isEmpty() -> {
                                                                scope.launch {
                                                                    val devicePath = withContext(Dispatchers.IO) {
                                                                        MacroRecordingReader.findGamepadDevice() ?: MacroRecordingReader.DEFAULT_DEVICE_PATH
                                                                    }
                                                                    val started = withContext(Dispatchers.IO) { macroReader.start(context, devicePath) }
                                                                    if (started) {
                                                                        recordingButtonId = hitButton.id
                                                                        Toast.makeText(context, R.string.macropad_recording_started, Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        Toast.makeText(context, R.string.macropad_recording_failed, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                            // Has events: play back
                                                            else -> {
                                                                val job = scope.launch {
                                                                    playingButtonId = hitButton.id
                                                                    playMacro(macroAction.events, pressedDuringPlayback)
                                                                    playingButtonId = null
                                                                    playbackJob = null
                                                                }
                                                                playbackJob = job
                                                            }
                                                        }
                                                    }
                                                    // ── All other buttons ──
                                                    else -> {
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
                    isMacroRecording = recordingButtonId == btn.id,
                    isMacroPlaying   = playingButtonId == btn.id,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro playback
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Plays back a recorded macro sequence with accurate timing.
 * Cleans up any held buttons via [pressedDuringPlayback] in a finally block,
 * so cancellation (e.g. re-tap) always leaves the gamepad in a clean state.
 */
private suspend fun playMacro(
    events: List<MacroEvent>,
    pressedDuringPlayback: MutableSet<Int>,
) {
    var lastMs = 0L
    try {
        for (event in events) {
            val deltaMs = event.relativeTimeMs - lastMs
            if (deltaMs > 0) delay(deltaMs)
            lastMs = event.relativeTimeMs
            injectMacroEvent(event.input)
            when (val input = event.input) {
                is MacroInputEvent.GamepadButtonDown -> pressedDuringPlayback.add(input.code)
                is MacroInputEvent.GamepadButtonUp   -> pressedDuringPlayback.remove(input.code)
                else -> {}
            }
        }
    } finally {
        // Release any buttons still held at end or on cancellation
        pressedDuringPlayback.forEach { code -> GamepadInjector.buttonUp(code) }
        pressedDuringPlayback.clear()
    }
}

