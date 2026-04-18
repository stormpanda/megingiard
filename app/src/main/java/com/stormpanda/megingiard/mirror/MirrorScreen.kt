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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.MirrorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MR_SWIPE_EDGE_ZONE = 40.dp
private val MR_TOUCH_INDICATOR_SIZE = 24.dp
private const val MR_TOUCH_INDICATOR_ALPHA = 0.5f
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 5f
private const val SNAP_BACK_THRESHOLD = 1.15f

private const val TAG = "MirrorScreen"

@Composable
fun MirrorScreen(modifier: Modifier = Modifier, viewModel: MirrorViewModel = viewModel()) {
    val context = LocalContext.current
    val isCapturing by viewModel.isCapturing.collectAsState()
    val surfaceWidth by viewModel.surfaceWidth.collectAsState()
    val surfaceHeight by viewModel.surfaceHeight.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()
    val frozenBitmap by viewModel.frozenBitmap.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isTouchProjectionActive by viewModel.isTouchProjectionActive.collectAsState()
    val colors = LocalAppColors.current
    val pinchWhileProjecting by viewModel.pinchWhileProjecting.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Initialise from the current viewport controller values so the first snapshotFlow
    // emission is already correct and does not overwrite the restored viewport with defaults.
    val animScale = remember { Animatable(viewModel.scale.value) }
    val animOffsetX = remember { Animatable(viewModel.offsetX.value) }
    val animOffsetY = remember { Animatable(viewModel.offsetY.value) }

    val showControls by viewModel.overlayVisible.collectAsState()
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val edgeZonePx = with(density) { MR_SWIPE_EDGE_ZONE.toPx() }

    // Local visibility for the control button row — shown on any touch (or edge-swipe
    // when touch projection is active), independently of the carousel overlay timer.
    var showButtons by remember { mutableStateOf(false) }
    var buttonTriggerCount by remember { mutableIntStateOf(0) }
    val isTouchingState by viewModel.isTouching.collectAsState()
    val overlayTimeoutMs by viewModel.overlayTimeoutMs.collectAsState()

    // Measured pixel size of the gesture Box, used for touch projection
    // coordinate mapping and edge-zone calculations inside pointerInput blocks.
    var gestureBoxSize by remember { mutableStateOf(IntSize.Zero) }

    // Touch-projection controller — created once per overlay-at-bottom / edge-zone change.
    val projectionController = remember(edgeZonePx, overlayAtBottom) {
        viewModel.createTouchProjectionController(edgeZonePx, overlayAtBottom)
    }
    val touchIndicatorPos by projectionController.indicatorPos.collectAsState()

    // Start / stop the native touch injector when projection is toggled.
    LaunchedEffect(isTouchProjectionActive) {
        if (isTouchProjectionActive) {
            viewModel.startTouchInjector(context)
        } else {
            viewModel.stopTouchInjector()
        }
    }

    // Ensure the injector is stopped whenever MirrorScreen leaves the composition.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTouchInjector()
        }
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
    // sync the Animatable values from the viewport controller.  Session state was already
    // restored by ScreenCaptureService before setCapturing(true) was called.
    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            viewModel.restoreFromManager()
            val s = viewModel.scale.value
            val ox = viewModel.offsetX.value
            val oy = viewModel.offsetY.value
            if (s != animScale.value) animScale.snapTo(s)
            if (ox != animOffsetX.value) animOffsetX.snapTo(ox)
            if (oy != animOffsetY.value) animOffsetY.snapTo(oy)
        }
    }

    // Sync animated transform values to the viewport controller.
    // Debounced persistence and lock/projection save are handled by
    // MirrorViewportController.startPersistence() launched in MirrorViewModel.init.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(animScale.value, animOffsetX.value, animOffsetY.value) }
            .collect { (s, ox, oy) ->
                viewModel.setViewportValues(s, ox, oy)
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> gestureBoxSize = coords.size }
                // Pass 1 — Initial pass: tracks isTouching, shows control buttons (with
                // touch-projection-aware logic), and detects edge-zone swipe for overlay.
                .pointerInput(overlayAtBottom, isTouchProjectionActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press -> {
                                    viewModel.setTouching(true)
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
                                }
                                PointerEventType.Release -> {
                                    if (!event.changes.any { it.pressed }) {
                                        viewModel.setTouching(false)
                                        viewModel.setPillExpanded(false)
                                    }
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
                // Pass 4 — Touch projection via TouchProjectionController.
                // Intercepts Main-pass events and forwards them to the primary display
                // via the native touch injector.  Edge-zone touches are skipped.
                .pointerInput(isTouchProjectionActive, overlayAtBottom) {
                    if (!isTouchProjectionActive) return@pointerInput
                    projectionController.reset()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (gestureBoxSize == IntSize.Zero) continue
                            val scW = gestureBoxSize.width.toFloat()
                            val scH = gestureBoxSize.height.toFloat()

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val newPointer = event.changes
                                        .firstOrNull { it.pressed && !it.previousPressed }
                                        ?: event.changes.firstOrNull() ?: continue
                                    val consumed = projectionController.onPress(
                                        pointerId = newPointer.id.value,
                                        x = newPointer.position.x,
                                        y = newPointer.position.y,
                                        boxW = scW,
                                        boxH = scH,
                                        isConsumed = newPointer.isConsumed,
                                        pointerCount = event.changes.size
                                    )
                                    if (consumed) newPointer.consume()
                                }
                                PointerEventType.Move -> {
                                    val activePointer = event.changes.firstOrNull() ?: continue
                                    val consumed = projectionController.onMove(
                                        pointerId = activePointer.id.value,
                                        x = activePointer.position.x,
                                        y = activePointer.position.y,
                                        boxW = scW,
                                        boxH = scH,
                                        isConsumed = activePointer.isConsumed
                                    )
                                    if (consumed) activePointer.consume()
                                }
                                PointerEventType.Release -> {
                                    val activePointer = event.changes.firstOrNull()
                                    projectionController.onRelease(
                                        pointerId = activePointer?.id?.value ?: -1L,
                                        x = activePointer?.position?.x,
                                        y = activePointer?.position?.y,
                                        boxW = scW,
                                        boxH = scH,
                                    )
                                    activePointer?.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            val scale by viewModel.scale.collectAsState()
            val offsetX by viewModel.offsetX.collectAsState()
            val offsetY by viewModel.offsetY.collectAsState()

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
                                x = (indicatorPos.first - indicatorSizePx / 2f).roundToInt(),
                                y = (indicatorPos.second - indicatorSizePx / 2f).roundToInt()
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

    }
}
