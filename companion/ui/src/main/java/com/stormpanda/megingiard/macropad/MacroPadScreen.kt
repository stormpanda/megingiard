package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.stormpanda.megingiard.mirror.EmbeddedMirrorView
import com.stormpanda.megingiard.mirror.MasterSurfaceRegistry
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.TouchProjectionController
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberQuickMenuGestureMetrics
import com.stormpanda.megingiard.viewmodel.MacroPadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 0.dp

// Shared with PadCanvas so the editor canvas is pixel-identical to use mode.
internal val MP_SCREEN_PADDING = 0.dp

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
    val context = LocalContext.current
    val profile by viewModel.activeProfile.collectAsState()
    val layout by viewModel.activeLayout.collectAsState()
    val isEditorActive by AppStateManager.isEditorActive.collectAsState()
    val isEditingPositions by MacroPadState.isEditingButtonPositions.collectAsState()
    val gridMode by MacroPadState.gridMode.collectAsState()
    val colors = LocalAppColors.current
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
        } else if (isEditorActive) {
            PadCanvas(
                profile = p,
                layout = l,
                accentColor = colors.accent,
                gridMode = gridMode,
                isLocked = !isEditingPositions,
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
                    disabledFeedback = reason
                    disabledFeedbackTrigger += 1
                    AppLog.d(TAG, "show disabled action feedback: $reason")
                },
            )
        }

        val reason = disabledFeedback
        if (reason != null) {
            val feedbackText =
                when (reason) {
                    DisabledReason.KEYBOARD -> stringResource(R.string.macropad_device_disabled_keyboard)
                    DisabledReason.GAMEPAD -> stringResource(R.string.macropad_device_disabled_gamepad)
                    DisabledReason.MOUSE -> stringResource(R.string.macropad_device_disabled_mouse)
                    DisabledReason.TOUCH -> stringResource(R.string.macropad_device_disabled_touch)
                }
            Text(
                text = feedbackText,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
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
    onDisabledActionFeedback: (DisabledReason) -> Unit = {},
) {
    val viewModel: MacroPadViewModel = viewModel()
    val density = LocalDensity.current
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val canvasSizeState = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
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
            val dim = layout.backgroundImageDim
            if (dim > 0f) {
                val scale = 1f - dim
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        ),
                    ),
                )
            } else {
                null
            }
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
                    .clip(RoundedCornerShape(MP_CORNER_RADIUS))
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
                                            }
                                            return@forEach
                                        }

                                        if (change.isConsumed) return@forEach

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed) {
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
                        val userScale = layout.bgImageScale
                        val ox = layout.bgImageOffsetX
                        val oy = layout.bgImageOffsetY

                        val scaleBase = maxOf(cw / iw, ch / ih)
                        val ws = iw * scaleBase
                        val hs = ih * scaleBase

                        val maxTx = ((ws * userScale - cw) / 2f).coerceAtLeast(0f)
                        val maxTy = ((hs * userScale - ch) / 2f).coerceAtLeast(0f)
                        val clampedX = (ox * cw).coerceIn(-maxTx, maxTx)
                        val clampedY = (oy * ch).coerceIn(-maxTy, maxTy)

                        drawImage(
                            image = bgBitmap!!,
                            dstOffset =
                                IntOffset(
                                    ((cw - ws * userScale) / 2f + clampedX).toInt(),
                                    ((ch - hs * userScale) / 2f + clampedY).toInt(),
                                ),
                            dstSize =
                                IntSize(
                                    (ws * userScale).toInt(),
                                    (hs * userScale).toInt(),
                                ),
                            colorFilter = bgImageDimFilter,
                        )
                    }
                }
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
