package com.stormpanda.megingiard.mirror

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MR_SWIPE_EDGE_ZONE = 40.dp
private val MR_SWIPE_THRESHOLD = 25.dp
private val MR_TOUCH_INDICATOR_SIZE = 24.dp
private const val MR_TOUCH_INDICATOR_ALPHA = 0.5f
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 5f
private const val SNAP_BACK_THRESHOLD = 1.15f
private const val MR_VIEWPORT_SAVE_DEBOUNCE_MS = 300L

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
    val colors = LocalAppColors.current
    val pinchWhileProjecting by SettingsManager.pinchWhileProjecting.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Initialise from the current ScreenCaptureManager values so the first snapshotFlow
    // emission is already correct and does not overwrite the restored viewport with defaults.
    val animScale = remember { Animatable(ScreenCaptureManager.scale.value) }
    val animOffsetX = remember { Animatable(ScreenCaptureManager.offsetX.value) }
    val animOffsetY = remember { Animatable(ScreenCaptureManager.offsetY.value) }

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

    // When capturing starts (or MirrorScreen re-enters composition while already capturing),
    // sync the Animatable values from ScreenCaptureManager.  Session state was already
    // restored by ScreenCaptureService before setCapturing(true) was called, so no DataStore
    // I/O is needed here — just a synchronous read from the in-memory StateFlow values.
    LaunchedEffect(isCapturing) {
        if (isCapturing) {
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
    val rememberViewport by SettingsManager.rememberViewport.collectAsState()
    val rememberLock by SettingsManager.rememberLock.collectAsState()
    val rememberProjection by SettingsManager.rememberProjection.collectAsState()
    val currentMode by AppStateManager.currentMode.collectAsState()
    LaunchedEffect(rememberViewport, currentMode) {
        snapshotFlow { Triple(animScale.value, animOffsetX.value, animOffsetY.value) }
            .onEach { snapshot ->
                ScreenCaptureManager.setScale(snapshot.first)
                ScreenCaptureManager.setOffsetX(snapshot.second)
                ScreenCaptureManager.setOffsetY(snapshot.third)
            }
            .debounce(MR_VIEWPORT_SAVE_DEBOUNCE_MS)
            .collectLatest {
                // Save only in Mirror mode and when "Remember viewport" is enabled
                if (currentMode == AppMode.MIRROR && rememberViewport) {
                    SettingsManager.saveMirrorSessionState()
                }
            }
    }

    // Save lock and touch-projection state immediately on change, so a mode-switch
    // without explicit Stop doesn't lose the current values.
    LaunchedEffect(rememberLock, rememberProjection, currentMode) {
        combine(
            ScreenCaptureManager.isLocked,
            ScreenCaptureManager.isTouchProjectionActive,
        ) { locked, projection -> Pair(locked, projection) }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest {
                if (currentMode == AppMode.MIRROR && (rememberLock || rememberProjection)) {
                    SettingsManager.saveMirrorSessionState()
                }
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
                // Pass 3 — Pinch-zoom and pan.
                //
                // Three modes controlled by isLocked and pinchWhileProjecting:
                //
                // A) Not locked → full detectTransformGestures (existing behaviour).
                //
                // B) Locked + Touch Projection + pinchWhileProjecting enabled →
                //    multi-finger-only transform: two-finger pinch/pan still works,
                //    but single-finger events pass through to Block 4 for injection.
                //
                // C) Locked (for any other reason) → no transform at all.
                //
                // Keys include pinchWhileProjecting and isTouchProjectionActive so the
                // block restarts whenever the option or projection state changes.
                .pointerInput(isLocked, pinchWhileProjecting, isTouchProjectionActive, surfaceWidth, surfaceHeight) {
                    val multiFingerMode = isLocked && isTouchProjectionActive && pinchWhileProjecting
                    when {
                        multiFingerMode -> {
                            // Multi-finger-only transform: consume 2+-finger events, let
                            // single-finger events fall through to Block 4 for injection.
                            awaitPointerEventScope {
                                var multiActive = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        // Two or more fingers: perform zoom/pan.
                                        multiActive = true
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        coroutineScope.launch {
                                            val newScale = (animScale.value * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                                            animScale.snapTo(newScale)
                                            val maxX = (surfaceWidth * (newScale - 1f)) / 2f
                                            val maxY = (surfaceHeight * (newScale - 1f)) / 2f
                                            animOffsetX.snapTo((animOffsetX.value + pan.x).coerceIn(-maxX, maxX))
                                            animOffsetY.snapTo((animOffsetY.value + pan.y).coerceIn(-maxY, maxY))
                                        }
                                        // Consume all changes so Block 4 does not inject.
                                        event.changes.forEach { it.consume() }
                                    } else if (multiActive) {
                                        // Fingers reducing from 2 → 1 → 0: suppress the
                                        // lingering single-finger events to avoid a stray
                                        // DOWN injection after a pinch gesture ends.
                                        event.changes.forEach { it.consume() }
                                        if (pressedCount == 0) {
                                            multiActive = false
                                            // Snap back to 1× when a pinch-out drops below
                                            // the comfort threshold, same as normal mode.
                                            if (animScale.value < SNAP_BACK_THRESHOLD) {
                                                coroutineScope.launch { animScale.animateTo(ZOOM_MIN) }
                                                coroutineScope.launch { animOffsetX.animateTo(0f) }
                                                coroutineScope.launch { animOffsetY.animateTo(0f) }
                                            }
                                        }
                                    }
                                    // Single finger + !multiActive → don't consume:
                                    // Block 4 handles injection normally.
                                }
                            }
                        }
                        !isLocked -> {
                            // Standard unlocked mode: full detectTransformGestures.
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
                        else -> {
                            // Locked without pinch-while-projecting: no transform.
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
                        // Track the exact pointer ID that started the gesture so MOVE/UP events
                        // always use the correct finger position even when multiple pointers are
                        // present (e.g. pinch-while-projecting second finger arrives).
                        var activePointerId = -1L
                        // Last successfully injected normalised position — used as fallback for
                        // the UP event when a forced cancel happens mid-gesture.
                        var lastInjectedNx = 0f
                        var lastInjectedNy = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (gestureBoxSize == IntSize.Zero) continue
                            val scW = gestureBoxSize.width.toFloat()
                            val scH = gestureBoxSize.height.toFloat()

                            when (event.type) {
                                PointerEventType.Press -> {
                                    // Resolve the newly-pressed pointer (a Press event may
                                    // carry multiple changes; find the one just pressed).
                                    val newPointer = event.changes
                                        .firstOrNull { it.pressed && !it.previousPressed }
                                        ?: event.changes.firstOrNull() ?: continue

                                    // If a second finger lands while we have an active injection
                                    // gesture (pinchWhileProjecting mode: Block 3 will take over),
                                    // gracefully cancel the in-flight touch before Block 3 consumes.
                                    if (gestureStarted && event.changes.size > 1) {
                                        touchIndicatorPos = null
                                        TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
                                        gestureStarted = false
                                        activePointerId = -1L
                                        continue
                                    }

                                    gestureStarted = false
                                    val touchY = newPointer.position.y
                                    val nearEdge = if (overlayAtBottom) {
                                        touchY >= gestureBoxSize.height - edgeZonePx
                                    } else {
                                        touchY <= edgeZonePx
                                    }
                                    gestureInEdgeZone = nearEdge
                                    if (nearEdge) continue
                                    // If a button or other child consumed this press, don't forward.
                                    if (newPointer.isConsumed) continue

                                    val touchX = newPointer.position.x
                                    val projected = projectCoordinates(
                                        touchX, touchY, scW, scH,
                                        ScreenCaptureManager.surfaceWidth.value,
                                        ScreenCaptureManager.surfaceHeight.value,
                                        ScreenCaptureManager.scale.value,
                                        ScreenCaptureManager.offsetX.value,
                                        ScreenCaptureManager.offsetY.value
                                    ) ?: continue
                                    touchIndicatorPos = newPointer.position
                                    lastInjectedNx = projected.first
                                    lastInjectedNy = projected.second
                                    TouchInjector.injectTouch(TouchAction.DOWN, lastInjectedNx, lastInjectedNy)
                                    newPointer.consume()
                                    activePointerId = newPointer.id.value
                                    gestureStarted = true
                                }
                                PointerEventType.Move -> {
                                    if (gestureInEdgeZone || !gestureStarted) continue
                                    // Find the tracked pointer by ID for stable position even
                                    // when multiple fingers are on screen.
                                    val activePointer = event.changes
                                        .firstOrNull { it.id.value == activePointerId }
                                        ?: continue
                                    // If Block 3 (multi-finger transform) consumed this event,
                                    // it means a second finger joined and the gesture was taken
                                    // over — send UP using the last known good position.
                                    if (activePointer.isConsumed) {
                                        touchIndicatorPos = null
                                        TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
                                        gestureStarted = false
                                        activePointerId = -1L
                                        continue
                                    }

                                    val touchX = activePointer.position.x
                                    val touchY = activePointer.position.y
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
                                        activePointer.consume()
                                        gestureStarted = false
                                        activePointerId = -1L
                                        continue
                                    }
                                    lastInjectedNx = coords.first
                                    lastInjectedNy = coords.second
                                    touchIndicatorPos = activePointer.position
                                    TouchInjector.injectTouch(TouchAction.MOVE, lastInjectedNx, lastInjectedNy)
                                    activePointer.consume()
                                }
                                PointerEventType.Release -> {
                                    // Find the tracked pointer by ID; fall back to first if
                                    // the pointer is already gone from the changes list.
                                    val activePointer = event.changes
                                        .firstOrNull { it.id.value == activePointerId }
                                        ?: event.changes.firstOrNull()
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
                                        if (activePointer != null) {
                                            val touchX = activePointer.position.x
                                            val touchY = activePointer.position.y
                                            val coords = projectCoordinates(
                                                touchX, touchY, scW, scH, sw, sh, sc, ox, oy
                                            )
                                            val nx = coords?.first
                                                ?: ((touchX - scW / 2f - ox) / sc + sw / 2f).coerceIn(0f, sw) / sw
                                            val ny = coords?.second
                                                ?: ((touchY - scH / 2f - oy) / sc + sh / 2f).coerceIn(0f, sh) / sh
                                            TouchInjector.injectTouch(TouchAction.UP, nx, ny)
                                            activePointer.consume()
                                        } else {
                                            // Pointer already left the list — use last known position.
                                            TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
                                        }
                                    }
                                    gestureInEdgeZone = false
                                    gestureStarted = false
                                    activePointerId = -1L
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.appBackground)
                )
                Text(
                    text = stringResource(R.string.mirror_waiting_permission),
                    color = colors.onSurface,
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
                        .background(colors.fingerCircle, CircleShape)
                )
            }

            MirrorControlPanel(
                visible = showControls || showButtons,
                isCapturing = isCapturing,
                isFrozen = isFrozen,
                isLocked = isLocked,
                isTouchProjectionActive = isTouchProjectionActive,
            )
        }

        // CarouselOverlay is a sibling of the gesture Box so its touch events are
        // not intercepted by detectTapGestures / detectTransformGestures.
        // It must live here (inside MirrorPresentation's ComposeView) because
        // MirrorPresentation is a separate window on top of the Activity window;
        // MainAppScreen's CarouselOverlay would be invisible while mirroring.
        CarouselOverlay(visible = showControls, onInteraction = { AppStateManager.triggerOverlay() })
    }
}
