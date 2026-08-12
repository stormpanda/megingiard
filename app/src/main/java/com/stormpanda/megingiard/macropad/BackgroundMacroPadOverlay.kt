package com.stormpanda.megingiard.macropad

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.SwipeGestureProgress
import com.stormpanda.megingiard.SwipeGestureType
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.TrackpointMode
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.QuickMenuBar
import com.stormpanda.megingiard.ui.QuickMenuBarLayout
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberQuickMenuGestureMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val AM_SCREEN_PADDING_DP = 0
private val AM_SCREEN_PADDING = AM_SCREEN_PADDING_DP.dp
private const val AM_PERCENT_DIVISOR = 100f

/** Mirrors MacroPadViewModel.INJECTOR_RESTART_DEBOUNCE_MS — absorbs rapid modal transitions. */
private const val AM_INJECTOR_RESTART_DEBOUNCE_MS = 150L

private data class AmbientInjectorGate(
    val stopKeyboard: Boolean,
    val stopMouse: Boolean,
    val stopGamepad: Boolean,
)

private const val TAG = "BackgroundMacroPadOverlay"

// ─────────────────────────────────────────────────────────────────────────────
// Ambient MacroPad Overlay — renders MacroPad buttons over the screen mirror
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BackgroundMacroPadOverlay(showQuickMenuBar: Boolean = true) {
    val context = LocalContext.current
    val profile by MacroPadState.activeProfile.collectAsState()
    val layout by MacroPadState.activeLayout.collectAsState()
    val colors = LocalAppColors.current

    val dimAlpha = layout?.ambientDim ?: 0f
    val isPeekActive by MacroPadState.isPeekActive.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val previewConfig by AmbientPreviewManager.config.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val (
        edgeZonePx,
        swipeThresholdPx,
        quickMenuBarZoneWidthPx,
        kbBarMinX,
        kbBarMaxX,
        tpBarWidthPx,
        tpBarEndPaddingPx,
        tpBarZoneWidthPx,
    ) = rememberQuickMenuGestureMetrics()

    val qmSwipe =
        remember(overlayAtBottom, edgeZonePx, swipeThresholdPx, quickMenuBarZoneWidthPx) {
            SwipeGestureProcessor(
                edgeZonePx = edgeZonePx,
                swipeThresholdPx = swipeThresholdPx,
                overlayAtBottom = overlayAtBottom,
                quickMenuBarZoneWidthPx = quickMenuBarZoneWidthPx,
                onSwipeProgress = { delta, isPast ->
                    AppStateManager.updateActiveSwipe(
                        SwipeGestureProgress(SwipeGestureType.MENU, delta, swipeThresholdPx, isPast),
                    )
                },
                onSwipeCancel = {
                    AppStateManager.updateActiveSwipe(null)
                },
                onHapticTick = {
                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                },
                onEdgeSwipe = {
                    AppStateManager.updateActiveSwipe(null)
                    AppStateManager.handleEdgeSwipe()
                },
            )
        }

    val kbSwipe =
        remember(overlayAtBottom, edgeZonePx, swipeThresholdPx, kbBarMinX, kbBarMaxX) {
            SwipeGestureProcessor(
                edgeZonePx = edgeZonePx,
                swipeThresholdPx = swipeThresholdPx,
                overlayAtBottom = overlayAtBottom,
                customZoneCheck = { x, _ -> x >= kbBarMinX && x <= kbBarMaxX },
                onSwipeProgress = { delta, isPast ->
                    AppStateManager.updateActiveSwipe(
                        SwipeGestureProgress(SwipeGestureType.KEYBOARD, delta, swipeThresholdPx, isPast),
                    )
                },
                onSwipeCancel = {
                    AppStateManager.updateActiveSwipe(null)
                },
                onHapticTick = {
                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                },
                onEdgeSwipe = {
                    AppStateManager.updateActiveSwipe(null)
                    if (AppStateManager.isAnyModalActive.value) {
                        AppStateManager.closeActiveModal()
                    } else if (AppStateManager.isQuickMenuOpen.value) {
                        AppStateManager.closeQuickMenu()
                    } else {
                        AppStateManager.setFullscreenKeyboardActive(true)
                    }
                },
            )
        }

    val tpSwipe =
        remember(overlayAtBottom, edgeZonePx, swipeThresholdPx, tpBarWidthPx, tpBarEndPaddingPx, tpBarZoneWidthPx) {
            SwipeGestureProcessor(
                edgeZonePx = edgeZonePx,
                swipeThresholdPx = swipeThresholdPx,
                overlayAtBottom = overlayAtBottom,
                customZoneCheck = { x, width ->
                    val tpBarCenter = width - tpBarEndPaddingPx - (tpBarWidthPx / 2f)
                    val tpBarMinX = tpBarCenter - (tpBarZoneWidthPx / 2f)
                    val tpBarMaxX = tpBarCenter + (tpBarZoneWidthPx / 2f)
                    x >= tpBarMinX && x <= tpBarMaxX
                },
                onSwipeProgress = { delta, isPast ->
                    AppStateManager.updateActiveSwipe(
                        SwipeGestureProgress(SwipeGestureType.TOUCHPAD, delta, swipeThresholdPx, isPast),
                    )
                },
                onSwipeCancel = {
                    AppStateManager.updateActiveSwipe(null)
                },
                onHapticTick = {
                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                },
                onEdgeSwipe = {
                    AppStateManager.updateActiveSwipe(null)
                    if (AppStateManager.isAnyModalActive.value) {
                        AppStateManager.closeActiveModal()
                    } else if (AppStateManager.isQuickMenuOpen.value) {
                        AppStateManager.closeQuickMenu()
                    } else {
                        AppStateManager.setFullscreenMouseActive(true)
                    }
                },
            )
        }

    // Effective dim: overridden to 0 when peeking
    val effectiveDim = if (isPeekActive) 0f else dimAlpha

    // Single watcher: stop injectors according to overlay state, restart when all closed.
    // Mirrors MacroPadViewModel.watchInjectorLifecycle() — must use the same combine()+
    // collectLatest+delay pattern so that rapid QuickMenu-close→Ambient-open transitions
    // do not cause a spurious injector restart.
    LaunchedEffect(Unit) {
        combine(
            AppStateManager.isQuickMenuOpen,
            AppStateManager.isEditorActive,
            AppStateManager.isBackgroundSettingsActive,
            AppStateManager.isFullscreenKeyboardActive,
            AppStateManager.isFullscreenMouseActive,
            AppStateManager.isViewportEditActive,
            MacroPadState.activeLayout,
        ) { array ->
            val quickMenu = array[0] as Boolean
            val editor = array[1] as Boolean
            val ambient = array[2] as Boolean
            val kb = array[3] as Boolean
            val mouse = array[4] as Boolean
            val vp = array[5] as Boolean
            val activeL = array[6] as? PadLayout

            val hasKeyboard = activeL?.buttons?.any { it.action is PadAction.KeyboardKey } == true
            val hasGamepad = activeL?.buttons?.any { it.action is PadAction.GamepadButton || it.action is PadAction.Macro } == true
            val hasMouse =
                activeL?.buttons?.any {
                    it.action is PadAction.MouseButton ||
                        it.action is PadAction.ScrollWheel ||
                        (
                            it.action is PadAction.TrackpointMove &&
                                (it.action as PadAction.TrackpointMove).mode == TrackpointMode.PHYSICAL_MOUSE
                        )
                } == true || activeL?.backgroundTouchpad?.enabled == true

            val blockingModal = editor || ambient || vp
            val startKeyboard = kb || (hasKeyboard && !blockingModal)
            val startMouse = mouse || (hasMouse && !blockingModal && !kb)
            val startGamepad = hasGamepad && !blockingModal && !quickMenu && !kb && !mouse

            AmbientInjectorGate(
                stopKeyboard = !startKeyboard,
                stopMouse = !startMouse,
                stopGamepad = !startGamepad,
            )
        }.distinctUntilChanged()
            .collectLatest { gate ->
                if (gate.stopKeyboard) {
                    AppLog.d(TAG, "stopping keyboard injector")
                    KeyInjector.stop()
                }
                if (gate.stopMouse) {
                    AppLog.d(TAG, "stopping mouse injector")
                    MouseInjector.stop()
                }
                if (gate.stopGamepad) {
                    AppLog.d(TAG, "stopping gamepad injector")
                    GamepadInjector.stop()
                }

                // Absorb rapid transitions (e.g. QuickMenu closes then Editor opens
                // in the same frame). collectLatest will cancel this branch
                // if any gate flips back to stop-mode within the delay window.
                delay(AM_INJECTOR_RESTART_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    val activeL = MacroPadState.activeLayout.value
                    val hasKeyboard = activeL?.buttons?.any { it.action is PadAction.KeyboardKey } == true
                    val hasGamepad = activeL?.buttons?.any { it.action is PadAction.GamepadButton || it.action is PadAction.Macro } == true
                    val hasMouse =
                        activeL?.buttons?.any {
                            it.action is PadAction.MouseButton ||
                                it.action is PadAction.ScrollWheel ||
                                (
                                    it.action is PadAction.TrackpointMove &&
                                        (it.action as PadAction.TrackpointMove).mode == TrackpointMode.PHYSICAL_MOUSE
                                )
                        } == true || activeL?.backgroundTouchpad?.enabled == true
                    val hasTouch =
                        activeL?.buttons?.any {
                            (
                                it.action is PadAction.TrackpointMove &&
                                    (it.action as PadAction.TrackpointMove).mode == TrackpointMode.VIRTUAL_TOUCH
                            ) ||
                                it.action is PadAction.Macro
                        } == true

                    val blockingModalActive =
                        AppStateManager.isEditorActive.value || AppStateManager.isBackgroundSettingsActive.value ||
                            AppStateManager.isViewportEditActive.value

                    if (!gate.stopKeyboard && hasKeyboard) {
                        KeyInjector.start(context)
                    }
                    if (!gate.stopGamepad && hasGamepad) {
                        GamepadInjector.start(context)
                    }
                    if (!gate.stopMouse && hasMouse) {
                        MouseInjector.start(context)
                    }

                    if (hasTouch) {
                        if (!blockingModalActive) {
                            TouchInjector.start(context, "BackgroundMacroPadOverlay")
                        } else {
                            TouchInjector.stop("BackgroundMacroPadOverlay")
                        }
                    } else {
                        TouchInjector.stop("BackgroundMacroPadOverlay")
                    }
                }
            }
    }

    // Stop all injectors and reset peek state when leaving
    DisposableEffect(Unit) {
        onDispose {
            AppLog.d(TAG, "BackgroundMacroPadOverlay disposed → all injectors stopped")
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
            TouchInjector.stop("BackgroundMacroPadOverlay")
            MacroPadState.resetPeek()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(
                    overlayAtBottom,
                    edgeZonePx,
                    swipeThresholdPx,
                    quickMenuBarZoneWidthPx,
                    kbBarMinX,
                    kbBarMaxX,
                    previewConfig == null,
                    isFullscreenKeyboardActive,
                    isFullscreenMouseActive,
                    showQuickMenuBar,
                ) {
                    if (previewConfig != null || !showQuickMenuBar) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val primaryChange = event.changes.firstOrNull()
                            val x = primaryChange?.position?.x ?: 0f
                            val y = primaryChange?.position?.y ?: 0f
                            when (event.type) {
                                PointerEventType.Press -> {
                                    qmSwipe.onPress(
                                        pointerY = y,
                                        containerHeight = size.height.toFloat(),
                                        pointerX = x,
                                        containerWidth = size.width.toFloat(),
                                    )
                                    kbSwipe.onPress(
                                        pointerY = y,
                                        containerHeight = size.height.toFloat(),
                                        pointerX = x,
                                        containerWidth = size.width.toFloat(),
                                    )
                                    tpSwipe.onPress(
                                        pointerY = y,
                                        containerHeight = size.height.toFloat(),
                                        pointerX = x,
                                        containerWidth = size.width.toFloat(),
                                    )
                                    // No Press consumption
                                }

                                PointerEventType.Move -> {
                                    qmSwipe.onMove(y)
                                    kbSwipe.onMove(y)
                                    tpSwipe.onMove(y)
                                    if (qmSwipe.isSwipeTriggered || kbSwipe.isSwipeTriggered || tpSwipe.isSwipeTriggered) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                PointerEventType.Release -> {
                                    val allPointersLifted = !event.changes.any { it.pressed }
                                    val qmTriggered = qmSwipe.isSwipeTriggered
                                    val kbTriggered = kbSwipe.isSwipeTriggered
                                    val tpTriggered = tpSwipe.isSwipeTriggered
                                    qmSwipe.onRelease(allPointersLifted)
                                    kbSwipe.onRelease(allPointersLifted)
                                    tpSwipe.onRelease(allPointersLifted)
                                    if (qmTriggered || kbTriggered || tpTriggered) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                },
    ) {
        // Layer 1: Dim overlay
        if (effectiveDim > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveDim)),
            )
        }

        // Layer 4: MacroPad buttons
        // During viewport edit: rendered at 50% alpha so the user can see button
        //   positions while adjusting the mirror crop.
        // Normal: fully opaque (or peek-adjusted via isPeekActive).
        val buttonAlpha =
            when {
                isViewportEditActive || previewConfig != null -> 0.5f
                else -> 1f
            }
        if (buttonAlpha > 0f) {
            val p = profile
            val l = layout
            if (p == null || l == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(AM_SCREEN_PADDING),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.macropad_no_profile),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(AM_SCREEN_PADDING)
                            .graphicsLayer { alpha = buttonAlpha },
                ) {
                    PadSurface(
                        profile = p,
                        layout = l,
                        accentColor = colors.accent,
                        isPeekActive = isPeekActive,
                        transparentBackground = true,
                    )
                }
            }
        }

        // ── Ambient preview bar (secondary screen) ──────────────────────────────────
        // The slider renders on the same screen as the live ambient effect so the
        // user can see and adjust the value while watching the result in real time.
        val pc = previewConfig
        val pl = layout
        if (pc != null && pl != null) {
            val previewValue =
                when (pc.type) {
                    AmbientPreviewType.DIM -> pl.ambientDim
                    AmbientPreviewType.EDGE_BLENDING -> pl.mirrorEdgeBlendWidth
                }
            val formatPreviewLabel: (Float) -> String = { v ->
                if (pc.type == AmbientPreviewType.DIM) {
                    "${(v * AM_PERCENT_DIVISOR).toInt()}%"
                } else {
                    when (v.roundToInt()) {
                        in 0..12 -> context.getString(R.string.mirror_edge_blend_strength_off)
                        in 13..37 -> context.getString(R.string.mirror_edge_blend_strength_light)
                        in 38..62 -> context.getString(R.string.mirror_edge_blend_strength_medium)
                        in 63..87 -> context.getString(R.string.mirror_edge_blend_strength_strong)
                        else -> context.getString(R.string.mirror_edge_blend_strength_max)
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .blockPointerEvents(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AsoPreviewBar(
                    label = pc.label,
                    value = previewValue,
                    valueRange = pc.valueRange,
                    formatLabel = formatPreviewLabel,
                    accentColor = colors.accent,
                    onValueChange = { v ->
                        val updated =
                            when (pc.type) {
                                AmbientPreviewType.DIM -> pl.copy(ambientDim = v)
                                AmbientPreviewType.EDGE_BLENDING -> pl.copy(mirrorEdgeBlendWidth = v)
                            }
                        MacroPadState.updateLayout(updated)
                    },
                    onCancel = {
                        AppLog.d(TAG, "ambient preview ${pc.type} cancelled")
                        val restored =
                            when (pc.type) {
                                AmbientPreviewType.DIM -> pl.copy(ambientDim = pc.originalValue)
                                AmbientPreviewType.EDGE_BLENDING -> pl.copy(mirrorEdgeBlendWidth = pc.originalValue)
                            }
                        MacroPadState.updateLayout(restored)
                        AmbientPreviewManager.setConfig(null)
                    },
                    onConfirm = {
                        AppLog.d(TAG, "ambient preview ${pc.type} confirmed")
                        AmbientPreviewManager.setConfig(null)
                    },
                )
            }
        }

        if (showQuickMenuBar && !isFullscreenKeyboardActive) QuickMenuBar()
    }
}
