package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TOUCH_AREA_ASPECT_RATIO    = 16f / 9f
private val   TOUCH_AREA_BORDER_WIDTH        = 1.dp
private val   TOUCH_INDICATOR_SIZE           = 24.dp
private const val TOUCH_INDICATOR_ALPHA      = 0.5f
private val   TOUCH_AREA_CORNER_RADIUS       = 4.dp

// Mouse mode
private const val TP_MOUSE_SENSITIVITY       = 2f
private const val TP_TAP_TIMEOUT_MS          = 200L
private const val TP_TAP_SLOP_PX             = 20f
private const val TP_CLICK_DURATION_MS       = 40L

private const val TAG = "TouchpadScreen"

@Composable
fun TouchpadScreen(modifier: Modifier = Modifier) {
    val context    = LocalContext.current
    val useMouse   by SettingsManager.touchpadUseMouse.collectAsState()
    val tapToClick by SettingsManager.touchpadTapToClick.collectAsState()
    val twoFinger  by SettingsManager.touchpadTwoFingerTap.collectAsState()

    // Start the correct injector whenever the input method changes.
    // Stops the other injector first to avoid both running simultaneously.
    LaunchedEffect(useMouse) {
        withContext(Dispatchers.IO) {
            if (useMouse) {
                AppLog.d(TAG, "starting MouseInjector (useMouse=true)")
                TouchInjector.stop()
                MouseInjector.start(context)
            } else {
                AppLog.d(TAG, "starting TouchInjector (useMouse=false)")
                MouseInjector.stop()
                TouchInjector.start(context)
            }
        }
    }

    // Stop both injectors when leaving TOUCHPAD mode.
    DisposableEffect(Unit) {
        onDispose {
            AppLog.d(TAG, "TouchpadScreen disposed → injectors stopped")
            TouchInjector.stop()
            MouseInjector.stop()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(LocalAppColors.current.touchpadBackground),
        contentAlignment = Alignment.Center
    ) {
        TouchSurface(
            useMouse   = useMouse,
            tapToClick = tapToClick,
            twoFinger  = twoFinger
        )
    }
}

@Composable
private fun TouchSurface(
    useMouse: Boolean,
    tapToClick: Boolean,
    twoFinger: Boolean,
) {
    // Pixel size of the surface — needed for normalised coordinates in touch mode.
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    // Visual indicator position: only tracked in touch mode.
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    val density         = LocalDensity.current
    val colors          = LocalAppColors.current
    val indicatorSizePx = remember(density) { with(density) { TOUCH_INDICATOR_SIZE.toPx() } }

    val overlayVisible      by AppStateManager.overlayVisible.collectAsState()
    val overlayVisibleState  = rememberUpdatedState(overlayVisible)
    val tapToClickState      = rememberUpdatedState(tapToClick)
    val twoFingerState       = rememberUpdatedState(twoFinger)

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TOUCH_AREA_ASPECT_RATIO)
            .border(TOUCH_AREA_BORDER_WIDTH, colors.touchpadIndicator, RoundedCornerShape(TOUCH_AREA_CORNER_RADIUS))
            .onGloballyPositioned { coords -> surfaceSize = coords.size }
            // Re-create the pointer handler whenever the input method changes so
            // all per-mode tracking state is cleanly initialised.
            .pointerInput(useMouse) {
                awaitPointerEventScope {
                    if (useMouse) {
                        // ── Mouse mode ────────────────────────────────────────────────
                        // Per-pointer tracking for tap detection.
                        val pressTimes:    HashMap<PointerId, Long>   = HashMap()
                        val downPositions: HashMap<PointerId, Offset>  = HashMap()
                        val movedTooFar:   HashSet<PointerId>          = HashSet()
                        val releasedAsTap: ArrayList<PointerId>        = ArrayList()
                        var primaryPointer: PointerId?                 = null

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)

                            // Block input while the carousel overlay is shown.
                            if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                                if (event.type == PointerEventType.Press && event.changes.any { !it.isConsumed }) {
                                    AppStateManager.hideOverlay()
                                }
                                event.changes.forEach { change ->
                                    if (!change.isConsumed) change.consume()
                                }
                                continue
                            }

                            for (change in event.changes) {
                                val id = change.id
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            pressTimes[id]    = System.currentTimeMillis()
                                            downPositions[id] = change.position
                                            if (primaryPointer == null) primaryPointer = id
                                            change.consume()
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        // Disqualify as tap if slop exceeded.
                                        val initPos = downPositions[id]
                                        if (initPos != null && id !in movedTooFar) {
                                            val dx = change.position.x - initPos.x
                                            val dy = change.position.y - initPos.y
                                            if (dx * dx + dy * dy > TP_TAP_SLOP_PX * TP_TAP_SLOP_PX) {
                                                movedTooFar.add(id)
                                            }
                                        }
                                        // Only the primary pointer drives cursor movement.
                                        if (id == primaryPointer) {
                                            val delta = change.positionChange()
                                            val dx = (delta.x * TP_MOUSE_SENSITIVITY).roundToInt()
                                            val dy = (delta.y * TP_MOUSE_SENSITIVITY).roundToInt()
                                            if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
                                        }
                                        change.consume()
                                    }
                                    PointerEventType.Release -> {
                                        if (!change.pressed) {
                                            val pressTime    = pressTimes.remove(id)
                                            downPositions.remove(id)
                                            val disqualified = movedTooFar.remove(id)
                                            val isTap = pressTime != null
                                                    && !disqualified
                                                    && (System.currentTimeMillis() - pressTime) < TP_TAP_TIMEOUT_MS
                                            if (id == primaryPointer) {
                                                // Hand off primary role to the next active finger (if any).
                                                primaryPointer = event.changes
                                                    .firstOrNull { it.id != id && it.pressed }?.id
                                            }
                                            if (isTap) releasedAsTap.add(id)
                                            change.consume()
                                        }
                                    }
                                    else -> Unit
                                }
                            }

                            // After all changes in this event: when every finger is up,
                            // evaluate collected taps and fire click if applicable.
                            if (event.type == PointerEventType.Release
                                && event.changes.none { it.pressed }
                            ) {
                                val tapCount = releasedAsTap.size
                                releasedAsTap.clear()
                                pressTimes.clear()
                                downPositions.clear()
                                movedTooFar.clear()
                                primaryPointer = null
                                when {
                                    tapCount == 1 && tapToClickState.value -> scope.launch {
                                        MouseInjector.leftDown()
                                        delay(TP_CLICK_DURATION_MS)
                                        MouseInjector.leftUp()
                                    }
                                    tapCount >= 2 && twoFingerState.value -> scope.launch {
                                        MouseInjector.rightDown()
                                        delay(TP_CLICK_DURATION_MS)
                                        MouseInjector.rightUp()
                                    }
                                }
                            }
                        }
                    } else {
                        // ── Touch mode (absolute coordinates) ────────────────────────
                        while (true) {
                            val event   = awaitPointerEvent(PointerEventPass.Main)
                            val pointer = event.changes.firstOrNull() ?: continue

                            if (surfaceSize == IntSize.Zero) continue

                            val nx = (pointer.position.x / surfaceSize.width).coerceIn(0f, 1f)
                            val ny = (pointer.position.y / surfaceSize.height).coerceIn(0f, 1f)

                            if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                                if (event.type == PointerEventType.Press && !pointer.isConsumed) {
                                    AppStateManager.hideOverlay()
                                }
                                pointer.consume()
                                continue
                            }

                            when (event.type) {
                                PointerEventType.Press -> {
                                    touchPos = pointer.position
                                    TouchInjector.injectTouch(TouchAction.DOWN, nx, ny)
                                    pointer.consume()
                                }
                                PointerEventType.Move -> {
                                    touchPos = pointer.position
                                    TouchInjector.injectTouch(TouchAction.MOVE, nx, ny)
                                    pointer.consume()
                                }
                                PointerEventType.Release -> {
                                    touchPos = null
                                    TouchInjector.injectTouch(TouchAction.UP, nx, ny)
                                    pointer.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
    ) {
        // Hint text: in touch mode only when no finger is down; in mouse mode always.
        val showHint = if (useMouse) true else (touchPos == null)
        if (showHint) {
            Text(
                text = stringResource(
                    if (useMouse) R.string.touchpad_hint_mouse else R.string.touchpad_hint
                ),
                color = colors.touchpadIndicator,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }

        // Touch indicator dot: only in touch mode, only while a finger is down.
        if (!useMouse) {
            val pos = touchPos
            if (pos != null) {
                Box(
                    modifier = Modifier
                        .size(TOUCH_INDICATOR_SIZE)
                        .offset {
                            IntOffset(
                                x = (pos.x - indicatorSizePx / 2f).roundToInt(),
                                y = (pos.y - indicatorSizePx / 2f).roundToInt()
                            )
                        }
                        .background(colors.fingerCircle, CircleShape)
                )
            }
        }
    }
}
