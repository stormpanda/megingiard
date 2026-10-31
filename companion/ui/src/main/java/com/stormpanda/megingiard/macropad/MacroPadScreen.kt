package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import android.os.Vibrator
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.EmbeddedMirrorView
import com.stormpanda.megingiard.mirror.InteractiveCutoutController
import com.stormpanda.megingiard.mirror.MasterSurfaceRegistry
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.TouchProjectionController
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadGestureConfig
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.DialogToastPill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.ui.rememberQuickMenuGestureMetrics

// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 0.dp
private val MP_SCREEN_SHAPE = RoundedCornerShape(MP_CORNER_RADIUS)

// Shared with PadCanvas so the editor canvas is pixel-identical to use mode.
internal val MP_SCREEN_PADDING = 0.dp

// Dynamic haptic interval bounds: faster movement → shorter interval
private const val MP_HAPTIC_MIN_INTERVAL_MS = 50L
private const val MP_HAPTIC_MAX_INTERVAL_MS = 333L
private const val MP_HAPTIC_BASE_SPEED = 2000f

private val MP_EMPTY_PILL_CORNER_RADIUS = 24.dp
private val MP_EMPTY_PILL_SHAPE = RoundedCornerShape(MP_EMPTY_PILL_CORNER_RADIUS)
private val MP_EMPTY_CANVAS_PADDING = 16.dp
private val MP_EMPTY_BORDER_STROKE_DP = 1.5.dp
private val MP_EMPTY_BORDER_CORNER_RADIUS_DP = 16.dp
private val MP_EMPTY_PILL_HORIZONTAL_PADDING = 24.dp
private val MP_EMPTY_PILL_VERTICAL_PADDING = 14.dp
private val MP_EMPTY_PILL_SPACING = 14.dp
private val MP_EMPTY_PILL_TITLE_HINT_SPACING = 2.dp
private val MP_EMPTY_BORDER_BEZEL_WIDTH = 1.dp
private const val MP_EMPTY_BORDER_ALPHA = 0.14f
private const val MP_EMPTY_PILL_BG_ALPHA = 0.88f
private val MP_EMPTY_ICON_SIZE_DP = 24.dp
private const val MP_EMPTY_DASH_ON = 12f
private const val MP_EMPTY_DASH_OFF = 8f
private const val MP_EMPTY_MAX_TAP_DISPLACEMENT_PX = 24f

private const val TAG = "MacroPadScreen"

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val profile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val layout by MacroPadState.activeLayout.collectAsStateWithLifecycle()
    val isEditorActive by AppStateManager.isEditorActive.collectAsStateWithLifecycle()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsStateWithLifecycle()
    val isEditingPositions by MacroPadState.isEditingButtonPositions.collectAsStateWithLifecycle()
    val isCroppingBackground by MacroPadState.isCroppingBackground.collectAsStateWithLifecycle()
    val isCroppingMask by MacroPadState.isCroppingMask.collectAsStateWithLifecycle()
    val gridMode by MacroPadState.gridMode.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    // Reset peek state when leaving MACROPAD mode
    DisposableEffect(Unit) {
        AppLog.i(TAG, "MacroPadScreen visible")
        onDispose {
            AppLog.i(TAG, "MacroPadScreen disposed")
            MacroPadState.resetPeek()
        }
    }

    val isCapturing by ScreenCaptureManager.isCapturing.collectAsStateWithLifecycle()
    val showEmbeddedMirror = isCapturing

    // Plain canvas background of MacroPad is strictly theme-invariant and always pitch black (Color.Black).
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(MP_SCREEN_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        if (showEmbeddedMirror) {
            EmbeddedMirrorView(
                modifier = Modifier.fillMaxSize(),
                surfaceOwner = MasterSurfaceRegistry.OWNER_MACROPAD,
                surfacePriority = MasterSurfaceRegistry.PRIORITY_MACROPAD,
            )
        }

        val p = profile
        val l = layout
        if (p == null || l == null) {
            // No profile yet
            Text(
                text = stringResource(R.string.macropad_no_profile),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        } else if (isEditorActive || isViewportEditActive) {
            PadCanvas(
                layout = l,
                accentColor = colors.accent,
                gridMode = gridMode,
                isLocked = !isEditingPositions,
                isCroppingBackground = isCroppingBackground,
                isCroppingMask = isCroppingMask,
                transparentBackground = showEmbeddedMirror,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PadSurface(
                layout = l,
                accentColor = colors.accent,
                transparentBackground = showEmbeddedMirror,
            )
        }

        LayoutTransitionSnapshotOverlay()

        val activeToast by DialogToastManager.currentToast.collectAsStateWithLifecycle()
        if (!isEditorActive && !isViewportEditActive) {
            DialogToastPill(
                toast = activeToast,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

@Composable
private fun LayoutTransitionSnapshotOverlay() {
    val transitionSnapshot by LayoutTransitionManager.transitionSnapshot.collectAsStateWithLifecycle()
    val transitionAlpha by LayoutTransitionManager.transitionAlpha.collectAsStateWithLifecycle()
    val activeSnapshot = transitionSnapshot
    if (activeSnapshot != null && !activeSnapshot.isRecycled && transitionAlpha > 0f) {
        Image(
            bitmap = activeSnapshot.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(MP_SCREEN_SHAPE)
                    .graphicsLayer {
                        alpha = transitionAlpha
                    },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadSurface(
    layout: PadLayout,
    accentColor: Color,
    isPeekActive: Boolean = false,
    transparentBackground: Boolean = false,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }
    val hapticLastMsByButton = remember { mutableMapOf<String, Long>() }

    val layerBitmaps = rememberLayoutLayerBitmaps(context, layout)

    // Create hit-test engine with density-aware dp→px converter and haptic callback
    val engine =
        remember(density, vibrator) {
            MacroPadHitTestEngine(
                buttonUnitDpToPx = { dpValue -> with(density) { dpValue.dp.toPx() } },
                onHapticFeedback = { buttonId, strength, customDurationMs, customAmplitude, magnitude ->
                    if (strength == HapticStrength.OFF) return@MacroPadHitTestEngine
                    val now = SystemClock.elapsedRealtime()
                    // magnitude == 0f → discrete event (button press or scroll batch), fire immediately.
                    // magnitude  > 0f → continuous trackpoint motion, interval shrinks with speed.
                    val intervalMs =
                        if (magnitude <= 0f) {
                            0L
                        } else {
                            (MP_HAPTIC_BASE_SPEED / magnitude)
                                .toLong()
                                .coerceIn(MP_HAPTIC_MIN_INTERVAL_MS, MP_HAPTIC_MAX_INTERVAL_MS)
                        }
                    val last = hapticLastMsByButton[buttonId] ?: 0L
                    if (now - last >= intervalMs) {
                        hapticLastMsByButton[buttonId] = now
                        triggerHaptic(vibrator, strength, customDurationMs, customAmplitude)
                    }
                },
            )
        }

    // Track which button IDs are currently pressed (from engine)
    val pressedIds by engine.pressedIds.collectAsStateWithLifecycle()
    // Track running macro IDs to drive the pulse animation
    val runningMacroIds by MacroExecutor.runningMacroIds.collectAsStateWithLifecycle()

    val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsStateWithLifecycle()
    val isFollowActive by ScreenCaptureManager.isFollowActive.collectAsStateWithLifecycle()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsStateWithLifecycle()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsStateWithLifecycle()
    val (
        edgeZonePx,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
    ) = rememberQuickMenuGestureMetrics()

    val projectionController =
        remember(edgeZonePx, overlayAtBottom) {
            TouchProjectionController(edgeZonePx, overlayAtBottom)
        }

    val hasFollowMode = isFollowActive && isCapturing
    DisposableEffect(hasFollowMode) {
        if (hasFollowMode) {
            TouchScreenObserver.start(
                clientToken = "MacroPadScreen_FollowMode",
                onTouchNormalized = { nx, ny ->
                    ScreenCaptureManager.onTouchReceived(nx, ny)
                },
            )
        }
        onDispose {
            TouchScreenObserver.stop("MacroPadScreen_FollowMode")
        }
    }

    DisposableEffect(vibrator) {
        InteractiveCutoutController.onHapticFeedback = {
            triggerHaptic(vibrator, HapticStrength.LIGHT, 0, 0)
        }
        onDispose {
            InteractiveCutoutController.onHapticFeedback = null
        }
    }

    val bgTouchpadActive = layout.backgroundTouchpad.enabled && !isTouchProjectionActive
    val coroutineScope = rememberCoroutineScope()
    val bgTouchpadProcessor =
        remember(layout, bgTouchpadActive) {
            TouchpadGestureProcessor(
                scope = coroutineScope,
                config = {
                    TouchpadGestureConfig(
                        useMouse = true,
                        sensitivity = layout.backgroundTouchpad.sensitivity,
                        twoFingerScrollEnabled = layout.backgroundTouchpad.twoFingerScroll,
                        naturalScrollEnabled = layout.backgroundTouchpad.naturalScroll,
                        scrollSpeed = layout.backgroundTouchpad.scrollSpeed,
                        tapToClick = layout.backgroundTouchpad.tapToClick,
                        twoFingerTap = layout.backgroundTouchpad.twoFingerTap,
                        threeFingerTap = layout.backgroundTouchpad.threeFingerTap,
                        tapDrag = layout.backgroundTouchpad.tapDrag,
                    )
                },
                onHapticFeedback = {
                    if (layout.backgroundTouchpad.hapticsEnabled) {
                        triggerHaptic(vibrator, HapticStrength.MEDIUM, 0, 0)
                    }
                },
            )
        }

    val currentLayout by rememberUpdatedState(layout)
    val currentBgTouchpadActive by rememberUpdatedState(bgTouchpadActive)
    val currentBgTouchpadProcessor by rememberUpdatedState(bgTouchpadProcessor)
    val currentIsPeekActive by rememberUpdatedState(isPeekActive)

    DisposableEffect(engine) {
        onDispose {
            engine.releaseAll(currentLayout.buttons)
        }
    }

    DisposableEffect(bgTouchpadProcessor) {
        onDispose {
            AppLog.d(TAG, "Disposing bgTouchpadProcessor for layout=${currentLayout.id}")
            bgTouchpadProcessor.onCancel()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(MP_SCREEN_SHAPE)
                    .background(if (transparentBackground) Color.Transparent else Color.Black)
                    .onSizeChanged { canvasSizeState.value = it }
                    .pointerInput(
                        canvasSizeState.value,
                        isTouchProjectionActive,
                        overlayAtBottom,
                        edgeZonePx,
                    ) {
                        try {
                            awaitPointerEventScope {
                                var pointerStartPos: Offset? = null
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val canvasSize = canvasSizeState.value
                                    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                    val activeLayout = currentLayout
                                    val activeBgTouchpad = currentBgTouchpadActive
                                    val activeBgProcessor = currentBgTouchpadProcessor

                                    // Block input while quick menu overlay is open
                                    if (AppStateManager.isQuickMenuOpen.value && event.type != PointerEventType.Release) {
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }

                                    event.changes.forEach { change ->
                                        val id = change.id.value

                                        // Always release pointers when fingers are lifted or touch is cancelled,
                                        // even if another component has consumed the event.
                                        if (!change.pressed && change.previousPressed) {
                                            if (engine.isPointerTracked(id)) {
                                                engine.onRelease(id, activeLayout.buttons)
                                                change.consume()
                                            } else if (InteractiveCutoutController.isPointerTracked(id)) {
                                                InteractiveCutoutController.onRelease(id, activeLayout.mirrorCutouts)
                                                change.consume()
                                            } else if (isTouchProjectionActive) {
                                                projectionController.onRelease(
                                                    pointerId = id,
                                                    x = change.position.x,
                                                    y = change.position.y,
                                                    boxW = w,
                                                    boxH = h,
                                                )
                                            } else if (activeBgTouchpad) {
                                                activeBgProcessor.onRelease(id, change.position.x, change.position.y, w, h)
                                                change.consume()
                                            } else if (activeLayout.isEmpty()) {
                                                val start = pointerStartPos
                                                if (start != null) {
                                                    val dx = change.position.x - start.x
                                                    val dy = change.position.y - start.y
                                                    val distSq = dx * dx + dy * dy
                                                    val nearEdge =
                                                        if (overlayAtBottom) {
                                                            start.y >= h - edgeZonePx || change.position.y >= h - edgeZonePx
                                                        } else {
                                                            start.y <= edgeZonePx || change.position.y <= edgeZonePx
                                                        }
                                                    if (!nearEdge &&
                                                        distSq <= MP_EMPTY_MAX_TAP_DISPLACEMENT_PX * MP_EMPTY_MAX_TAP_DISPLACEMENT_PX
                                                    ) {
                                                        AppStateManager.setEditorActive(true)
                                                        change.consume()
                                                    }
                                                }
                                                pointerStartPos = null
                                            }
                                            return@forEach
                                        }

                                        if (change.isConsumed) return@forEach

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed) {
                                                    if (activeLayout.isEmpty()) {
                                                        pointerStartPos = change.position
                                                    }
                                                    if (
                                                        engine.onPress(
                                                            id,
                                                            change.position.x,
                                                            change.position.y,
                                                            w,
                                                            h,
                                                            activeLayout.buttons,
                                                            currentIsPeekActive,
                                                        )
                                                    ) {
                                                        change.consume()
                                                    } else if (isTouchProjectionActive) {
                                                        val nearEdge =
                                                            if (overlayAtBottom) {
                                                                change.position.y >= h - edgeZonePx
                                                            } else {
                                                                change.position.y <= edgeZonePx
                                                            }
                                                        if (!nearEdge) {
                                                            projectionController.onPress(
                                                                pointerId = id,
                                                                x = change.position.x,
                                                                y = change.position.y,
                                                                boxW = w,
                                                                boxH = h,
                                                                isConsumed = change.isConsumed,
                                                                pointerCount = event.changes.size,
                                                            )
                                                        }
                                                    } else if (InteractiveCutoutController.onPress(
                                                            pointerId = id,
                                                            xPx = change.position.x,
                                                            yPx = change.position.y,
                                                            boxW = w,
                                                            boxH = h,
                                                            cutouts = activeLayout.mirrorCutouts,
                                                        )
                                                    ) {
                                                        change.consume()
                                                    } else if (activeBgTouchpad) {
                                                        activeBgProcessor.onPress(
                                                            id,
                                                            change.position.x,
                                                            change.position.y,
                                                            w,
                                                            h,
                                                        )
                                                        change.consume()
                                                    }
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                if (engine.isPointerTracked(id)) {
                                                    val delta = change.positionChange()
                                                    engine.onMove(
                                                        id,
                                                        change.position.x,
                                                        change.position.y,
                                                        delta.x,
                                                        delta.y,
                                                        activeLayout.buttons,
                                                    )
                                                    change.consume()
                                                } else if (InteractiveCutoutController.isPointerTracked(id)) {
                                                    InteractiveCutoutController.onMove(
                                                        pointerId = id,
                                                        xPx = change.position.x,
                                                        yPx = change.position.y,
                                                        boxW = w,
                                                        boxH = h,
                                                        cutouts = activeLayout.mirrorCutouts,
                                                    )
                                                    change.consume()
                                                } else if (isTouchProjectionActive) {
                                                    projectionController.onMove(
                                                        pointerId = id,
                                                        x = change.position.x,
                                                        y = change.position.y,
                                                        boxW = w,
                                                        boxH = h,
                                                        isConsumed = change.isConsumed,
                                                    )
                                                } else if (activeBgTouchpad) {
                                                    val delta = change.positionChange()
                                                    activeBgProcessor.onMove(
                                                        id,
                                                        change.position.x,
                                                        change.position.y,
                                                        delta.x,
                                                        delta.y,
                                                        w,
                                                        h,
                                                    )
                                                    change.consume()
                                                }
                                            }

                                            else -> {
                                                Unit
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            engine.releaseAll(currentLayout.buttons)
                            InteractiveCutoutController.cancelAllAnimations()
                            if (isTouchProjectionActive) {
                                projectionController.reset()
                            }
                            if (currentBgTouchpadActive) {
                                currentBgTouchpadProcessor.onCancel()
                            }
                        }
                    },
        ) {
            if (!transparentBackground) {
                LayoutImageLayer(
                    layout = layout,
                    bitmaps = layerBitmaps,
                )
            }

            if (layout.isEmpty() && !transparentBackground) {
                EmptyLayoutPlaceholder(
                    accentColor = accentColor,
                    onOpenEditor = { AppStateManager.setEditorActive(true) },
                )
            }

            // Render buttons (filtered by peek state)
            val visibleButtons =
                if (isPeekActive) {
                    layout.buttons.filter { it.action is PadAction.BackgroundPeek }
                } else {
                    layout.buttons
                }
            visibleButtons.forEach { btn ->
                val isPressed = btn.id in pressedIds
                val isRunning =
                    btn.action is PadAction.Macro &&
                        (btn.action as PadAction.Macro).macroId in runningMacroIds
                PadButton(
                    btn = btn,
                    layout = layout,
                    isPressed = isPressed,
                    canvasSize = canvasSizeState.value,
                    accentColor = accentColor,
                    isRunning = isRunning,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Layout Minimalist Ambient Placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLayoutPlaceholder(
    accentColor: Color,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Dashed ambient canvas border
        val borderColor = colors.onSurface.copy(alpha = MP_EMPTY_BORDER_ALPHA)
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(MP_EMPTY_CANVAS_PADDING),
        ) {
            val strokeWidth = MP_EMPTY_BORDER_STROKE_DP.toPx()
            val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(MP_EMPTY_DASH_ON, MP_EMPTY_DASH_OFF), 0f)
            val cornerRadius = MP_EMPTY_BORDER_CORNER_RADIUS_DP.toPx()
            drawRoundRect(
                color = borderColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style =
                    Stroke(
                        width = strokeWidth,
                        pathEffect = dashPathEffect,
                    ),
            )
        }

        // Central Minimalist Ambient Pill
        Box(
            modifier =
                Modifier
                    .clip(MP_EMPTY_PILL_SHAPE)
                    .background(colors.surface.copy(alpha = MP_EMPTY_PILL_BG_ALPHA))
                    .border(
                        width = MP_EMPTY_BORDER_BEZEL_WIDTH,
                        brush = bezelBrush,
                        shape = MP_EMPTY_PILL_SHAPE,
                    ).clickable(onClick = onOpenEditor)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                            )
                        ) {
                            onOpenEditor()
                            true
                        } else {
                            false
                        }
                    }.padding(
                        horizontal = MP_EMPTY_PILL_HORIZONTAL_PADDING,
                        vertical = MP_EMPTY_PILL_VERTICAL_PADDING,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MP_EMPTY_PILL_SPACING),
            ) {
                MaterialSymbol(
                    name = "dashboard_customize",
                    size = MP_EMPTY_ICON_SIZE_DP,
                    tint = accentColor,
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.macropad_empty_layout_pill_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(MP_EMPTY_PILL_TITLE_HINT_SPACING))
                    Text(
                        text = stringResource(R.string.macropad_empty_layout_pill_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
