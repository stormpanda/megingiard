package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.BitmapUtils
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.mirror.EmbeddedMirrorView
import com.stormpanda.megingiard.mirror.MasterSurfaceRegistry
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.TouchProjectionController
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.DialogToastPill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.dimColorFilter
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.ui.rememberQuickMenuGestureMetrics
import com.stormpanda.megingiard.viewmodel.MacroPadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 0.dp
private val MP_SCREEN_SHAPE = RoundedCornerShape(MP_CORNER_RADIUS)

// Shared with PadCanvas so the editor canvas is pixel-identical to use mode.
internal val MP_SCREEN_PADDING = 0.dp

private const val MP_DISABLED_FEEDBACK_RATE_LIMIT_MS = 650L

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

private fun DisabledReason.feedbackTextResId(): Int =
    when (this) {
        DisabledReason.KEYBOARD -> R.string.macropad_device_disabled_keyboard
        DisabledReason.GAMEPAD -> R.string.macropad_device_disabled_gamepad
        DisabledReason.MOUSE -> R.string.macropad_device_disabled_mouse
        DisabledReason.TOUCH -> R.string.macropad_device_disabled_touch
        DisabledReason.MACRO_PRIVD -> R.string.macropad_device_disabled_macro_privd
    }

private fun DisabledReason.feedbackIcon(): ImageVector =
    when (this) {
        DisabledReason.KEYBOARD -> Icons.Rounded.Keyboard
        DisabledReason.GAMEPAD -> Icons.Rounded.SportsEsports
        DisabledReason.MOUSE -> Icons.Rounded.Mouse
        DisabledReason.TOUCH -> Icons.Rounded.TouchApp
        DisabledReason.MACRO_PRIVD -> Icons.Rounded.Warning
    }

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val viewModel: MacroPadViewModel = viewModel()
    val context = LocalContext.current
    val profile by viewModel.activeProfile.collectAsState()
    val layout by viewModel.activeLayout.collectAsState()
    val isEditorActive by AppStateManager.isEditorActive.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val isEditingPositions by MacroPadState.isEditingButtonPositions.collectAsState()
    val isCroppingBackground by MacroPadState.isCroppingBackground.collectAsState()
    val gridMode by MacroPadState.gridMode.collectAsState()
    val colors = LocalAppColors.current
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

    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val cutouts by ScreenCaptureManager.cutouts.collectAsState()
    val hasCutouts = cutouts.isNotEmpty()
    val showEmbeddedMirror = isCapturing && hasCutouts

    Box(
        modifier = modifier.fillMaxSize().background(colors.appBackground).padding(MP_SCREEN_PADDING),
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
                profile = p,
                layout = l,
                accentColor = colors.accent,
                gridMode = gridMode,
                isLocked = !isEditingPositions,
                isCropping = isCroppingBackground,
                transparentBackground = showEmbeddedMirror,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PadSurface(
                profile = p,
                layout = l,
                accentColor = colors.accent,
                transparentBackground = showEmbeddedMirror,
                onDisabledActionFeedback = { reason ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFeedbackAtMs < MP_DISABLED_FEEDBACK_RATE_LIMIT_MS) return@PadSurface
                    lastFeedbackAtMs = now
                    DialogToastManager.show(
                        message = context.getString(reason.feedbackTextResId()),
                        icon = reason.feedbackIcon(),
                    )
                    AppLog.d(TAG, "show disabled action feedback: $reason")
                },
            )
        }

        val activeToast by DialogToastManager.currentToast.collectAsState()
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
    onDisabledActionFeedback: (DisabledReason) -> Unit = {},
) {
    val viewModel: MacroPadViewModel = viewModel()
    val density = LocalDensity.current
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }
    val hapticLastMsByButton = remember { mutableMapOf<String, Long>() }

    var bgBitmap by remember(layout.backgroundImagePath, layout.backgroundImageVersion) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(layout.backgroundImagePath, layout.backgroundImageVersion) {
        val path = layout.backgroundImagePath
        if (path != null) {
            try {
                val decoded = MacroPadMediaRepository.loadScaledBitmap(context, path)
                bgBitmap = decoded?.asImageBitmap()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to decode background image $path", e)
                bgBitmap = null
            }
        } else {
            bgBitmap = null
        }
    }

    val bgImageDimFilter =
        remember(layout.backgroundImageDim) {
            dimColorFilter(layout.backgroundImageDim)
        }

    // Create hit-test engine with density-aware dp→px converter and haptic callback
    val engine =
        remember(profile, layout) {
            viewModel.createHitTestEngine(
                buttonUnitDpToPx = { dpValue -> with(density) { dpValue.dp.toPx() } },
                onHapticFeedback = { buttonId, strength, customDurationMs, customAmplitude, magnitude ->
                    if (strength == HapticStrength.OFF) return@createHitTestEngine
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
    val pressedIds by engine.pressedIds.collectAsState()
    // Track running macro IDs to drive the pulse animation
    val runningMacroIds by MacroExecutor.runningMacroIds.collectAsState()

    val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
    val isFollowActive by ScreenCaptureManager.isFollowActive.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
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

    LaunchedEffect(isTouchProjectionActive) {
        if (isTouchProjectionActive) {
            TouchInjector.start(context, "TouchProjection")
        } else {
            TouchInjector.stop("TouchProjection")
        }
    }

    LaunchedEffect(isFollowActive, isCapturing) {
        if (isFollowActive && isCapturing) {
            TouchScreenObserver.onTouchNormalized = { nx, ny ->
                ScreenCaptureManager.onTouchReceived(nx, ny)
            }
            TouchScreenObserver.start("MacroPadScreen_FollowMode")
        } else {
            TouchScreenObserver.stop("MacroPadScreen_FollowMode")
            TouchScreenObserver.onTouchNormalized = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            TouchInjector.stop("TouchProjection")
            TouchScreenObserver.stop("MacroPadScreen_FollowMode")
        }
    }

    val bgTouchpadActive = layout.backgroundTouchpad.enabled && !isTouchProjectionActive
    val coroutineScope = rememberCoroutineScope()
    val bgTouchpadProcessor =
        remember(layout, bgTouchpadActive) {
            TouchpadGestureProcessor(
                useMouse = { true },
                scope = coroutineScope,
                sensitivity = { layout.backgroundTouchpad.sensitivity },
                twoFingerScrollEnabled = { layout.backgroundTouchpad.twoFingerScroll },
                naturalScrollEnabled = { layout.backgroundTouchpad.naturalScroll },
                scrollSpeed = { layout.backgroundTouchpad.scrollSpeed },
                tapToClick = { layout.backgroundTouchpad.tapToClick },
                twoFingerTap = { layout.backgroundTouchpad.twoFingerTap },
                threeFingerTap = { layout.backgroundTouchpad.threeFingerTap },
                tapDrag = { layout.backgroundTouchpad.tapDrag },
                onHapticFeedback = {
                    if (layout.backgroundTouchpad.hapticsEnabled) {
                        triggerHaptic(vibrator, HapticStrength.MEDIUM, 0, 0)
                    }
                },
            )
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
                        profile,
                        layout,
                        canvasSizeState.value,
                        bgTouchpadActive,
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

                                    // Block input while quick menu overlay is open
                                    if (viewModel.isQuickMenuOpen.value && event.type != PointerEventType.Release) {
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }

                                    event.changes.forEach { change ->
                                        val id = change.id.value

                                        // Always release pointers when fingers are lifted or touch is cancelled,
                                        // even if another component has consumed the event.
                                        if (!change.pressed && change.previousPressed) {
                                            if (engine.isPointerTracked(id)) {
                                                engine.onRelease(id, layout.buttons, profile)
                                                change.consume()
                                            } else if (isTouchProjectionActive) {
                                                projectionController.onRelease(
                                                    pointerId = id,
                                                    x = change.position.x,
                                                    y = change.position.y,
                                                    boxW = w,
                                                    boxH = h,
                                                )
                                            } else if (bgTouchpadActive) {
                                                bgTouchpadProcessor.onRelease(id, change.position.x, change.position.y, w, h)
                                                change.consume()
                                            } else if (layout.isEmpty()) {
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
                                                    if (layout.isEmpty()) {
                                                        pointerStartPos = change.position
                                                    }
                                                    val isHit =
                                                        engine.hitTest(
                                                            change.position.x,
                                                            change.position.y,
                                                            w,
                                                            h,
                                                            layout.buttons,
                                                            isPeekActive,
                                                        )
                                                    if (isHit) {
                                                        val disabledBtn =
                                                            engine.onPress(
                                                                id,
                                                                change.position.x,
                                                                change.position.y,
                                                                w,
                                                                h,
                                                                layout.buttons,
                                                                profile,
                                                                isPeekActive,
                                                            )
                                                        if (disabledBtn != null) {
                                                            val reason =
                                                                MacroPadHitTestEngine.deviceDisabledReason(
                                                                    disabledBtn.action,
                                                                    profile,
                                                                )
                                                            if (reason != null) {
                                                                onDisabledActionFeedback(reason)
                                                            }
                                                        }
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
                                                    } else if (bgTouchpadActive) {
                                                        bgTouchpadProcessor.onPress(
                                                            id,
                                                            change.position.x,
                                                            change.position.y,
                                                            w,
                                                            h,
                                                            overlayOpen = viewModel.isQuickMenuOpen.value,
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
                                                        layout.buttons,
                                                        profile,
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
                                                } else if (bgTouchpadActive) {
                                                    val delta = change.positionChange()
                                                    bgTouchpadProcessor.onMove(
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
                            engine.releaseAll(layout.buttons)
                            if (isTouchProjectionActive) {
                                projectionController.reset()
                            }
                            if (bgTouchpadActive) {
                                bgTouchpadProcessor.onCancel()
                            }
                        }
                    },
        ) {
            if (bgBitmap != null && !transparentBackground) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cw = size.width
                    val ch = size.height
                    val iw = bgBitmap!!.width.toFloat()
                    val ih = bgBitmap!!.height.toFloat()
                    if (cw > 0f && ch > 0f && iw > 0f && ih > 0f) {
                        val (dstOffset, dstSize) =
                            when (layout.bgScaleMode) {
                                BackgroundScaleMode.STRETCH -> {
                                    IntOffset.Zero to IntSize(cw.toInt(), ch.toInt())
                                }

                                BackgroundScaleMode.FIT, BackgroundScaleMode.FILL -> {
                                    val userScale = layout.bgImageScale
                                    val scaleBase =
                                        if (layout.bgScaleMode == BackgroundScaleMode.FIT) {
                                            ViewportMath.calculateAspectFitScale(cw, ch, iw, ih)
                                        } else {
                                            ViewportMath.calculateAspectFillScale(cw, ch, iw, ih)
                                        }
                                    val ws = iw * scaleBase
                                    val hs = ih * scaleBase
                                    val maxTx = ((ws * userScale - cw) / 2f).coerceAtLeast(0f)
                                    val maxTy = ((hs * userScale - ch) / 2f).coerceAtLeast(0f)
                                    val clampedX = (layout.bgImageOffsetX * cw).coerceIn(-maxTx, maxTx)
                                    val clampedY = (layout.bgImageOffsetY * ch).coerceIn(-maxTy, maxTy)
                                    IntOffset(
                                        ((cw - ws * userScale) / 2f + clampedX).toInt(),
                                        ((ch - hs * userScale) / 2f + clampedY).toInt(),
                                    ) to IntSize((ws * userScale).toInt(), (hs * userScale).toInt())
                                }
                            }
                        drawImage(
                            image = bgBitmap!!,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            colorFilter = bgImageDimFilter,
                        )
                    }
                }
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
                val isDeviceDisabled = MacroPadHitTestEngine.isDeviceDisabled(btn.action, profile)
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
                    isDeviceDisabled = isDeviceDisabled,
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
