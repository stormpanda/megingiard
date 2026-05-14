package com.stormpanda.megingiard.macropad

import android.content.Context
import android.os.SystemClock
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.MacroPadViewModel

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 0.dp
// Shared with PadCanvas so the editor canvas is pixel-identical to use mode.
internal val MP_SCREEN_PADDING = 0.dp

// Sensitivity of trackpoint drag: px input → mouse delta
private const val MP_TRACKPOINT_SENSITIVITY = 3f

// Sensitivity of scroll wheel drag: px per scroll unit sent
private const val MP_SCROLL_SENSITIVITY_PX = 12f
private const val MP_DISABLED_FEEDBACK_HIDE_MS = 1800L
private const val MP_DISABLED_FEEDBACK_RATE_LIMIT_MS = 650L

// Dynamic haptic interval bounds: faster movement → shorter interval
private const val MP_HAPTIC_MIN_INTERVAL_MS = 50L
private const val MP_HAPTIC_MAX_INTERVAL_MS = 333L
private const val MP_HAPTIC_BASE_SPEED = 2000f

private const val TAG = "MacroPadScreen"

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val viewModel: MacroPadViewModel = viewModel()
    val context     = LocalContext.current
    val profile     by viewModel.activeProfile.collectAsState()
    val layout      by viewModel.activeLayout.collectAsState()
    val colors      = LocalAppColors.current
    var disabledFeedback by remember { mutableStateOf<DisabledReason?>(null) }
    var disabledFeedbackTrigger by remember { mutableIntStateOf(0) }
    var lastFeedbackAtMs by remember { mutableLongStateOf(0L) }

    // Single watcher that starts/stops injectors reactively based on all modal flags
    LaunchedEffect(Unit) {
        viewModel.watchInjectorLifecycle(context)
    }

    // Stop all injectors and reset peek state when leaving MACROPAD mode
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopInjectors()
        }
    }

    Box(
        modifier           = modifier.fillMaxSize().background(colors.appBackground).padding(MP_SCREEN_PADDING),
        contentAlignment   = Alignment.Center,
    ) {
        val p = profile
        val l = layout
        if (p == null || l == null) {
            // No profile yet
            Text(
                text      = stringResource(R.string.macropad_no_profile),
                color     = colors.onSurfaceSecondary,
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(32.dp),
            )
        } else {
            PadSurface(
                profile     = p,
                layout      = l,
                accentColor = colors.accent,
                onDisabledActionFeedback = { reason ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFeedbackAtMs < MP_DISABLED_FEEDBACK_RATE_LIMIT_MS) return@PadSurface
                    lastFeedbackAtMs = now
                    disabledFeedback = reason
                    disabledFeedbackTrigger += 1
                    AppLog.d(TAG, "show disabled action feedback: $reason")
                },
            )
        }

        val reason = disabledFeedback
        if (reason != null) {
            val feedbackText = when (reason) {
                DisabledReason.KEYBOARD -> stringResource(R.string.macropad_device_disabled_keyboard)
                DisabledReason.GAMEPAD  -> stringResource(R.string.macropad_device_disabled_gamepad)
                DisabledReason.MOUSE    -> stringResource(R.string.macropad_device_disabled_mouse)
            }
            Text(
                text = feedbackText,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.8f)
                    .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    LaunchedEffect(disabledFeedbackTrigger) {
        if (disabledFeedbackTrigger == 0) return@LaunchedEffect
        delay(MP_DISABLED_FEEDBACK_HIDE_MS)
        disabledFeedback = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadSurface(
    profile: PadProfile,
    layout: PadLayout,
    accentColor: Color,
    isPeekActive: Boolean = false,
    transparentBackground: Boolean = false,
    neutralStyle: Boolean = false,
    onDisabledActionFeedback: (DisabledReason) -> Unit = {},
) {
    val viewModel: MacroPadViewModel = viewModel()
    val density      = LocalDensity.current
    val colors       = LocalAppColors.current
    val context      = LocalContext.current
    val vibrator     = remember { context.getSystemService(Vibrator::class.java) }
    val canvasSizeState = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val isPillMenuOpen      by viewModel.isPillMenuOpen.collectAsState()
    val isPillMenuOpenState  = rememberUpdatedState(isPillMenuOpen)
    val hapticLastMsByButton = remember { mutableMapOf<String, Long>() }

    // Create hit-test engine with density-aware dp→px converter and haptic callback
    val engine = remember(profile, layout) {
        viewModel.createHitTestEngine(
            buttonUnitDpToPx = { dpValue -> with(density) { dpValue.dp.toPx() } },
            onHapticFeedback = { buttonId, strength, customDurationMs, customAmplitude, magnitude ->
                if (strength == HapticStrength.OFF) return@createHitTestEngine
                val now = SystemClock.elapsedRealtime()
                // magnitude == 0f → discrete event (button press or scroll batch), fire immediately.
                // magnitude  > 0f → continuous trackpoint motion, interval shrinks with speed.
                val intervalMs = if (magnitude <= 0f) 0L
                    else (MP_HAPTIC_BASE_SPEED / magnitude).toLong()
                        .coerceIn(MP_HAPTIC_MIN_INTERVAL_MS, MP_HAPTIC_MAX_INTERVAL_MS)
                val last = hapticLastMsByButton[buttonId] ?: 0L
                if (now - last >= intervalMs) {
                    hapticLastMsByButton[buttonId] = now
                    triggerHaptic(vibrator, strength, customDurationMs, customAmplitude)
                }
            },
        )
    }

    // Track which button IDs are currently pressed (from engine)
    val pressedIds by engine.pressedIds.collectAsState()
    // Track running macro IDs to drive the pulse animation
    val runningMacroIds by MacroExecutor.runningMacroIds.collectAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MP_CORNER_RADIUS))
                .background(if (transparentBackground) Color.Transparent else colors.macroPadSurface)
                .onSizeChanged { canvasSizeState.value = it }
                .pointerInput(profile, layout, canvasSizeState.value) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent(PointerEventPass.Main)
                            val canvasSize = canvasSizeState.value
                            val w       = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h       = canvasSize.height.toFloat().coerceAtLeast(1f)

                            // Block input while pill menu overlay is open
                            if (isPillMenuOpenState.value && event.type != PointerEventType.Release) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            event.changes.forEach { change ->
                                val id = change.id.value

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            val disabledBtn = engine.onPress(
                                                id, change.position.x, change.position.y,
                                                w, h, layout.buttons, profile, isPeekActive
                                            )
                                            if (disabledBtn != null) {
                                                val reason = MacroPadHitTestEngine.deviceDisabledReason(
                                                    disabledBtn.action, profile
                                                )
                                                if (reason != null) {
                                                    onDisabledActionFeedback(reason)
                                                }
                                            }
                                        }
                                        change.consume()
                                    }

                                    PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        engine.onMove(id, change.position.x, change.position.y, delta.x, delta.y, layout.buttons, profile)
                                        change.consume()
                                    }

                                    PointerEventType.Release -> {
                                        if (!change.pressed) {
                                            engine.onRelease(id, layout.buttons, profile)
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
            // Render buttons (filtered by peek state)
            val visibleButtons = if (isPeekActive) {
                layout.buttons.filter { it.action is PadAction.AmbientPeek }
            } else {
                layout.buttons
            }
            visibleButtons.forEach { btn ->
                val isDeviceDisabled = MacroPadHitTestEngine.isDeviceDisabled(btn.action, profile)
                val isPressed = btn.id in pressedIds
                val isRunning = btn.action is PadAction.Macro &&
                    (btn.action as PadAction.Macro).macroId in runningMacroIds
                PadButton(
                    btn              = btn,
                    isPressed        = isPressed,
                    canvasSize       = canvasSizeState.value,
                    accentColor      = accentColor,
                    isDeviceDisabled = isDeviceDisabled,
                    neutralStyle     = neutralStyle,
                    isRunning        = isRunning,
                )
            }
        }
    }
}
