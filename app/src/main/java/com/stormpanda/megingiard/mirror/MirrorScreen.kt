package com.stormpanda.megingiard.mirror

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.CarouselOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CONTROL_BUTTON_SIZE = 72.dp
private val CONTROL_ICON_SIZE = 36.dp
private val CONTROL_BUTTON_GAP = 16.dp
private val CONTROL_PILL_H_PADDING = 12.dp
private val CONTROL_PILL_V_PADDING = 10.dp
private val CONTROL_PILL_BG = Color.Black.copy(alpha = 0.8f)
private val MR_SWIPE_EDGE_ZONE = 40.dp
private val MR_SWIPE_THRESHOLD = 25.dp
private val MR_TOUCH_INDICATOR_SIZE = 24.dp
private const val MR_TOUCH_INDICATOR_ALPHA = 0.5f
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 5f
private const val SNAP_BACK_THRESHOLD = 1.15f

@Composable
fun MirrorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
    val isLocked by ScreenCaptureManager.isLocked.collectAsState()
    val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val animScale = remember { Animatable(ZOOM_MIN) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }

    val showControls by AppStateManager.overlayVisible.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val edgeZonePx = with(density) { MR_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MR_SWIPE_THRESHOLD.toPx() }

    // Local visibility for the control button row — shown on any touch (or edge-swipe
    // when touch projection is active), independently of the carousel overlay timer.
    var showButtons by remember { mutableStateOf(false) }
    var buttonTriggerCount by remember { mutableIntStateOf(0) }
    val isTouchingState by AppStateManager.isTouching.collectAsState()
    val overlayTimeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()

    // Visual indicator dot that follows the finger during touch projection.
    var touchIndicatorPos by remember { mutableStateOf<Offset?>(null) }

    // Measured pixel size of the gesture Box, used for touch projection
    // coordinate mapping and edge-zone calculations inside pointerInput blocks.
    var gestureBoxSize by remember { mutableStateOf(IntSize.Zero) }

    // Start / stop the native touch injector when projection is toggled.
    // LaunchedEffect fires once on first composition too, so if projecting when
    // returning to MIRROR mode after a carousel switch the injector auto-restarts.
    LaunchedEffect(isTouchProjectionActive) {
        if (isTouchProjectionActive) {
            TouchInjector.start(context)
        } else {
            TouchInjector.stop()
            touchIndicatorPos = null
        }
    }

    // Ensure the injector is stopped whenever MirrorScreen leaves the composition
    // (e.g. carousel switch away from MIRROR mode).
    DisposableEffect(Unit) {
        onDispose { TouchInjector.stop() }
    }

    // Auto-hide timer for the control button row (independent of carousel overlay timer).
    LaunchedEffect(buttonTriggerCount, isTouchingState, overlayTimeoutMs) {
        if (showButtons) {
            if (isTouchingState) return@LaunchedEffect
            delay(overlayTimeoutMs)
            showButtons = false
        }
    }

    // When capturing starts, restore persisted session state from DataStore (viewport,
    // lock, touch projection) and then sync Animatable values.  The suspend call to
    // restoreMirrorSessionState() ensures ScreenCaptureManager has the saved values
    // BEFORE we read them into the Animatables — and before the snapshotFlow below
    // can overwrite them with stale defaults.
    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            SettingsManager.restoreMirrorSessionState()
            val s = ScreenCaptureManager.scale.value
            val ox = ScreenCaptureManager.offsetX.value
            val oy = ScreenCaptureManager.offsetY.value
            if (s != animScale.value) animScale.snapTo(s)
            if (ox != animOffsetX.value) animOffsetX.snapTo(ox)
            if (oy != animOffsetY.value) animOffsetY.snapTo(oy)
        }
    }

    // Sync animated transform values to ScreenCaptureManager so MirrorPresentation's
    // SurfaceView can apply the same transform.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(animScale.value, animOffsetX.value, animOffsetY.value) }
            .collectLatest { (scale, ox, oy) ->
                ScreenCaptureManager.setScale(scale)
                ScreenCaptureManager.setOffsetX(ox)
                ScreenCaptureManager.setOffsetY(oy)
            }
    }

    // Outer Box: gesture surface and CarouselOverlay are siblings so overlay touch
    // events are not intercepted by the gesture detectors below.
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> gestureBoxSize = coords.size }
                // Pass 1 — Initial pass: tracks isTouching, shows control buttons (with
                // touch-projection-aware logic), and detects edge-zone swipe for overlay.
                .pointerInput(overlayAtBottom, isTouchProjectionActive) {
                    awaitPointerEventScope {
                        var swipeStartY = Float.NaN
                        var swipeTriggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press -> {
                                    AppStateManager.setTouching(true)
                                    val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                    val nearEdge = if (overlayAtBottom) {
                                        y >= size.height - edgeZonePx
                                    } else {
                                        y <= edgeZonePx
                                    }
                                    // When touch projection is active, only edge-zone
                                    // touches reveal the control buttons — normal touches
                                    // are forwarded to the primary display silently.
                                    if (!isTouchProjectionActive || nearEdge) {
                                        showButtons = true
                                        buttonTriggerCount++
                                    }
                                    swipeStartY = if (nearEdge) y else Float.NaN
                                    swipeTriggered = false
                                }
                                PointerEventType.Move -> {
                                    if (!swipeStartY.isNaN() && !swipeTriggered) {
                                        val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                        val delta = if (overlayAtBottom) {
                                            swipeStartY - y
                                        } else {
                                            y - swipeStartY
                                        }
                                        if (delta >= swipeThresholdPx) {
                                            AppStateManager.triggerOverlay()
                                            swipeTriggered = true
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (!event.changes.any { it.pressed }) {
                                        AppStateManager.setTouching(false)
                                        AppStateManager.setPillExpanded(false)
                                    }
                                    swipeStartY = Float.NaN
                                    swipeTriggered = false
                                }
                                else -> {}
                            }
                        }
                    }
                }
                // Pass 2 — Double-tap resets zoom/pan; blocked when locked.
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isLocked) {
                                coroutineScope.launch { animScale.animateTo(ZOOM_MIN) }
                                coroutineScope.launch { animOffsetX.animateTo(0f) }
                                coroutineScope.launch { animOffsetY.animateTo(0f) }
                            }
                        }
                    )
                }
                // Pass 3 — Pinch-zoom and pan; entirely disabled when locked.
                // Using isLocked as key so the block exits immediately when lock is engaged.
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    while (true) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            coroutineScope.launch {
                                val newScale = (animScale.value * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                                animScale.snapTo(newScale)
                                val maxX = (surfaceWidth * (newScale - 1f)) / 2f
                                val maxY = (surfaceHeight * (newScale - 1f)) / 2f
                                animOffsetX.snapTo((animOffsetX.value + pan.x).coerceIn(-maxX, maxX))
                                animOffsetY.snapTo((animOffsetY.value + pan.y).coerceIn(-maxY, maxY))
                            }
                        }
                        // Snap back to 1× when a pinch-out drops below the comfort threshold.
                        if (animScale.value < SNAP_BACK_THRESHOLD) {
                            coroutineScope.launch { animScale.animateTo(ZOOM_MIN) }
                            coroutineScope.launch { animOffsetX.animateTo(0f) }
                            coroutineScope.launch { animOffsetY.animateTo(0f) }
                        }
                    }
                }
                // Pass 4 — Touch projection: intercepts Main-pass events and forwards them
                // to the primary display via the native touch injector.  Because this block
                // is last in the modifier chain it processes Main-pass events FIRST, before
                // the pinch/tap detectors above, and can consume them selectively.
                // Touches inside the edge zone are never forwarded — the user needs that zone
                // to trigger the overlay and turn off projection / lock.
                // Button taps are skipped via pointer.isConsumed (buttons consume at Main pass
                // before this outer handler receives the event).
                .pointerInput(isTouchProjectionActive, overlayAtBottom) {
                    if (!isTouchProjectionActive) return@pointerInput
                    awaitPointerEventScope {
                        var gestureInEdgeZone = false
                        // True only when we successfully forwarded a DOWN to the primary display.
                        // Prevents orphaned MOVE/UP injections when a gesture was never started
                        // (e.g. the Press was consumed by a button or landed in the edge zone).
                        var gestureStarted = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pointer = event.changes.firstOrNull() ?: continue
                            if (gestureBoxSize == IntSize.Zero) continue

                            val touchX = pointer.position.x
                            val touchY = pointer.position.y
                            val scW = gestureBoxSize.width.toFloat()
                            val scH = gestureBoxSize.height.toFloat()

                            when (event.type) {
                                PointerEventType.Press -> {
                                    gestureStarted = false
                                    val nearEdge = if (overlayAtBottom) {
                                        touchY >= gestureBoxSize.height - edgeZonePx
                                    } else {
                                        touchY <= edgeZonePx
                                    }
                                    gestureInEdgeZone = nearEdge
                                    if (nearEdge) continue
                                    // If a button or other child consumed this press, don't forward.
                                    if (pointer.isConsumed) continue

                                    val (nx, ny) = projectCoordinates(
                                        touchX, touchY, scW, scH,
                                        ScreenCaptureManager.surfaceWidth.value,
                                        ScreenCaptureManager.surfaceHeight.value,
                                        ScreenCaptureManager.scale.value,
                                        ScreenCaptureManager.offsetX.value,
                                        ScreenCaptureManager.offsetY.value
                                    ) ?: continue
                                    touchIndicatorPos = pointer.position
                                    TouchInjector.injectTouch(TouchAction.DOWN, nx, ny)
                                    pointer.consume()
                                    gestureStarted = true
                                }
                                PointerEventType.Move -> {
                                    if (gestureInEdgeZone || !gestureStarted) continue

                                    val coords = projectCoordinates(
                                        touchX, touchY, scW, scH,
                                        ScreenCaptureManager.surfaceWidth.value,
                                        ScreenCaptureManager.surfaceHeight.value,
                                        ScreenCaptureManager.scale.value,
                                        ScreenCaptureManager.offsetX.value,
                                        ScreenCaptureManager.offsetY.value
                                    )
                                    if (coords == null) {
                                        // Finger moved out of content area — send a clamped UP so
                                        // the target app doesn't have a dangling touch.
                                        touchIndicatorPos = null
                                        val sw = ScreenCaptureManager.surfaceWidth.value
                                        val sh = ScreenCaptureManager.surfaceHeight.value
                                        val sc = ScreenCaptureManager.scale.value
                                        val ox = ScreenCaptureManager.offsetX.value
                                        val oy = ScreenCaptureManager.offsetY.value
                                        val svX = ((touchX - scW / 2f - ox) / sc + sw / 2f).coerceIn(0f, sw)
                                        val svY = ((touchY - scH / 2f - oy) / sc + sh / 2f).coerceIn(0f, sh)
                                        TouchInjector.injectTouch(TouchAction.UP, svX / sw, svY / sh)
                                        pointer.consume()
                                        gestureStarted = false
                                        continue
                                    }
                                    val (nx, ny) = coords
                                    touchIndicatorPos = pointer.position
                                    TouchInjector.injectTouch(TouchAction.MOVE, nx, ny)
                                    pointer.consume()
                                }
                                PointerEventType.Release -> {
                                    touchIndicatorPos = null
                                    if (!gestureInEdgeZone && gestureStarted) {
                                        // Send UP at the release position so the target app
                                        // cleans up its state. Clamp if the finger left the
                                        // content area during the release motion.
                                        val sw = ScreenCaptureManager.surfaceWidth.value
                                        val sh = ScreenCaptureManager.surfaceHeight.value
                                        val sc = ScreenCaptureManager.scale.value
                                        val ox = ScreenCaptureManager.offsetX.value
                                        val oy = ScreenCaptureManager.offsetY.value
                                        val coords = projectCoordinates(
                                            touchX, touchY, scW, scH, sw, sh, sc, ox, oy
                                        )
                                        val nx = coords?.first
                                            ?: ((touchX - scW / 2f - ox) / sc + sw / 2f).coerceIn(0f, sw) / sw
                                        val ny = coords?.second
                                            ?: ((touchY - scH / 2f - oy) / sc + sh / 2f).coerceIn(0f, sh) / sh
                                        TouchInjector.injectTouch(TouchAction.UP, nx, ny)
                                        pointer.consume()
                                    }
                                    gestureInEdgeZone = false
                                    gestureStarted = false
                                }
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            val scale by ScreenCaptureManager.scale.collectAsState()
            val offsetX by ScreenCaptureManager.offsetX.collectAsState()
            val offsetY by ScreenCaptureManager.offsetY.collectAsState()

            if (isFrozen && frozenBitmap != null) {
                Image(
                    bitmap = frozenBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_frozen_image),
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                )
            }

            if (!isCapturing) {
                Text(
                    text = stringResource(R.string.mirror_waiting_permission),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Touch indicator dot: follows the finger while touch projection is active.
            val indicatorPos = touchIndicatorPos
            if (indicatorPos != null) {
                val indicatorSizePx = with(density) { MR_TOUCH_INDICATOR_SIZE.toPx() }
                Box(
                    modifier = Modifier
                        .size(MR_TOUCH_INDICATOR_SIZE)
                        .offset {
                            IntOffset(
                                x = (indicatorPos.x - indicatorSizePx / 2f).roundToInt(),
                                y = (indicatorPos.y - indicatorSizePx / 2f).roundToInt()
                            )
                        }
                        .background(Color.White.copy(alpha = MR_TOUCH_INDICATOR_ALPHA), CircleShape)
                )
            }

            AnimatedVisibility(
                visible = (showControls || showButtons) && isCapturing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(CONTROL_PILL_BG, RoundedCornerShape(50))
                            .padding(
                                horizontal = CONTROL_PILL_H_PADDING,
                                vertical = CONTROL_PILL_V_PADDING
                            ),
                        horizontalArrangement = Arrangement.spacedBy(CONTROL_BUTTON_GAP)
                    ) {
                        // Stop
                        IconButton(
                            onClick = {
                                SettingsManager.saveMirrorSessionState()
                                context.stopService(Intent(context, ScreenCaptureService::class.java))
                                ScreenCaptureManager.setCapturing(false)
                                ScreenCaptureManager.resetMirrorSessionState()
                                AppStateManager.setUserDeclinedCapture(true)
                            },
                            modifier = Modifier
                                .size(CONTROL_BUTTON_SIZE)
                                .background(accentColor, RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.cd_stop_mirroring),
                                tint = Color.White,
                                modifier = Modifier.size(CONTROL_ICON_SIZE)
                            )
                        }

                        // Freeze / Unfreeze — disabled when Touch Projection is active
                        // (projecting touches to a frozen display would be meaningless).
                        IconButton(
                            onClick = { ScreenCaptureManager.toggleFrozen() },
                            enabled = !isTouchProjectionActive,
                            modifier = Modifier
                                .size(CONTROL_BUTTON_SIZE)
                                .background(
                                    color = when {
                                        isTouchProjectionActive -> Color.White.copy(alpha = 0.12f)
                                        isFrozen -> accentColor
                                        else -> Color.White.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = if (isFrozen) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(
                                    if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze
                                ),
                                tint = Color.White.copy(alpha = if (isTouchProjectionActive) 0.38f else 1f),
                                modifier = Modifier.size(CONTROL_ICON_SIZE)
                            )
                        }

                        // Lock / Unlock (also deactivates touch projection when unlocking,
                        // since projection requires lock as a precondition).
                        IconButton(
                            onClick = { ScreenCaptureManager.toggleLocked() },
                            modifier = Modifier
                                .size(CONTROL_BUTTON_SIZE)
                                .background(
                                    color = if (isLocked) accentColor else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = stringResource(
                                    if (isLocked) R.string.cd_unlock_view else R.string.cd_lock_view
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(CONTROL_ICON_SIZE)
                            )
                        }

                        // Touch Projection on / off — disabled when stream is frozen
                        // (touches on a still image cannot be forwarded meaningfully).
                        IconButton(
                            onClick = { ScreenCaptureManager.toggleTouchProjection() },
                            enabled = !isFrozen,
                            modifier = Modifier
                                .size(CONTROL_BUTTON_SIZE)
                                .background(
                                    color = when {
                                        isFrozen -> Color.White.copy(alpha = 0.12f)
                                        isTouchProjectionActive -> accentColor
                                        else -> Color.White.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = stringResource(
                                    if (isTouchProjectionActive) R.string.cd_touch_projection_off
                                    else R.string.cd_touch_projection_on
                                ),
                                tint = Color.White.copy(alpha = if (isFrozen) 0.38f else 1f),
                                modifier = Modifier.size(CONTROL_ICON_SIZE)
                            )
                        }
                    }
                }
            }
        }

        // CarouselOverlay is a sibling of the gesture Box so its touch events are
        // not intercepted by detectTapGestures / detectTransformGestures.
        // It must live here (inside MirrorPresentation's ComposeView) because
        // MirrorPresentation is a separate window on top of the Activity window;
        // MainAppScreen's CarouselOverlay would be invisible while mirroring.
        CarouselOverlay(visible = showControls, onInteraction = { AppStateManager.triggerOverlay() })
    }
}

/**
 * Maps a raw touch position on the mirror surface back through the current zoom/pan
 * transform to obtain the normalised content coordinate [0, 1] that corresponds to
 * the touched point on the primary display.
 *
 * The SurfaceView is centered in the secondary display's FrameLayout, so its pivot
 * point for the scale/translate transform lies at the screen center (screenW/2, screenH/2),
 * NOT at the SurfaceView's own center in its local coordinate space (sw/2, sh/2).
 * These differ when the content is letterboxed (sw != screenW or sh != screenH).
 *
 * Visual transform (screen → SurfaceView local)::
 *   screenPos = screenCenter + (svPos - svCenter) * scale + offset
 *   svPos     = (screenPos  - screenCenter - offset) / scale + svCenter
 *
 * Returns `null` when the touch lands outside the visible content area (e.g. letterbox
 * bars), in which case the caller should not inject the touch.
 *
 * @param touchX   Raw X of the touch on the secondary display (pixels)
 * @param touchY   Raw Y of the touch on the secondary display (pixels)
 * @param screenW  Full width of the secondary display Compose surface (gestureBoxSize.width)
 * @param screenH  Full height of the secondary display Compose surface (gestureBoxSize.height)
 * @param sw       Width of the letterboxed content area = ScreenCaptureManager.surfaceWidth
 * @param sh       Height of the letterboxed content area = ScreenCaptureManager.surfaceHeight
 * @param scale    Current zoom scale (1.0 = no zoom)
 * @param offsetX  Current pan offset X (pixels)
 * @param offsetY  Current pan offset Y (pixels)
 * @return Pair(normalizedX, normalizedY) or null if out-of-bounds
 */
private fun projectCoordinates(
    touchX: Float,
    touchY: Float,
    screenW: Float,
    screenH: Float,
    sw: Float,
    sh: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Pair<Float, Float>? {
    if (sw <= 0f || sh <= 0f || scale <= 0f || screenW <= 0f || screenH <= 0f) return null
    // Screen-space center — this is where the SurfaceView is anchored (CENTER gravity).
    val screenCenterX = screenW / 2f
    val screenCenterY = screenH / 2f
    // SurfaceView-local pivot for the scale transform.
    val svCenterX = sw / 2f
    val svCenterY = sh / 2f
    // Invert: svPos = (screenPos - screenCenter - offset) / scale + svCenter
    val svX = (touchX - screenCenterX - offsetX) / scale + svCenterX
    val svY = (touchY - screenCenterY - offsetY) / scale + svCenterY
    val nx = svX / sw
    val ny = svY / sh
    if (nx !in 0f..1f || ny !in 0f..1f) return null
    return Pair(nx, ny)
}
