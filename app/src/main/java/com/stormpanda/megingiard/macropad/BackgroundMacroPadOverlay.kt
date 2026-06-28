package com.stormpanda.megingiard.macropad

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
import com.stormpanda.megingiard.AmbientPreviewType
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.ButtonColorStyle
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.QuickMenuBar
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val AM_SCREEN_PADDING_DP = 0
private val AM_SCREEN_PADDING = AM_SCREEN_PADDING_DP.dp
private val AM_SWIPE_EDGE_ZONE = 40.dp
private val AM_SWIPE_THRESHOLD = 25.dp
private val AM_SWIPE_QM_BAR_ZONE_WIDTH = 120.dp
private const val AM_PERCENT_DIVISOR = 100f


/** Mirrors MacroPadViewModel.INJECTOR_RESTART_DEBOUNCE_MS — absorbs rapid modal transitions. */
private const val AM_INJECTOR_RESTART_DEBOUNCE_MS = 150L

private data class AmbientInjectorGate(
    val stopKeyboard: Boolean,
    val stopMouseAndGamepad: Boolean,
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
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { AM_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { AM_SWIPE_THRESHOLD.toPx() }
    val quickMenuBarZoneWidthPx = with(density) { AM_SWIPE_QM_BAR_ZONE_WIDTH.toPx() }
    val swipeProcessor = remember(overlayAtBottom, edgeZonePx, swipeThresholdPx, quickMenuBarZoneWidthPx) {
        SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom, quickMenuBarZoneWidthPx)
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
        ) { array ->
            val quickMenu = array[0]
            val editor = array[1]
            val ambient = array[2]
            val kb = array[3]
            val mouse = array[4]
            val vp = array[5]
            val stopAll = editor || ambient || kb || mouse || vp
            AmbientInjectorGate(
                stopKeyboard = stopAll,
                stopMouseAndGamepad = stopAll || quickMenu,
            )
        }.distinctUntilChanged()
        .collectLatest { gate ->
            when {
                gate.stopKeyboard -> {
                    AppLog.d(TAG, "blocking modal open → stopping keyboard/gamepad/mouse injectors")
                    KeyInjector.stop()
                    GamepadInjector.stop()
                    MouseInjector.stop()
                }
                gate.stopMouseAndGamepad -> {
                    AppLog.d(TAG, "quick menu open → stopping gamepad/mouse injectors")
                    GamepadInjector.stop()
                    MouseInjector.stop()
                }
                else -> {
                    delay(AM_INJECTOR_RESTART_DEBOUNCE_MS)
                    withContext(Dispatchers.IO) {
                        val ap = MacroPadState.activeProfile.value
                        AppLog.i(TAG, "all guards clear → starting injectors for profile '${ap?.name}' (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse} ts=${ap?.enableTouch})")
                        if (ap?.enableKeyboard == true) KeyInjector.start(context)
                        if (ap?.enableGamepad == true) GamepadInjector.start(context)
                        if (ap?.enableMouse == true) MouseInjector.start(context)
                        if (ap?.enableTouch == true) TouchInjector.start(context, "BackgroundMacroPadOverlay")
                    }
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
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom, edgeZonePx, swipeThresholdPx, quickMenuBarZoneWidthPx, previewConfig == null) {
                if (previewConfig != null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val primaryChange = event.changes.firstOrNull()
                        val x = primaryChange?.position?.x ?: 0f
                        val y = primaryChange?.position?.y ?: 0f
                        when (event.type) {
                            PointerEventType.Press -> {
                                swipeProcessor.onPress(
                                    pointerY = y,
                                    containerHeight = size.height.toFloat(),
                                    pointerX = x,
                                    containerWidth = size.width.toFloat(),
                                )
                                if (swipeProcessor.isNearEdge) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Move -> {
                                swipeProcessor.onMove(y)
                                if (swipeProcessor.isNearEdge) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Release -> {
                                val allPointersLifted = !event.changes.any { it.pressed }
                                swipeProcessor.onRelease(allPointersLifted)
                                if (swipeProcessor.isNearEdge) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        // Layer 1: Dim overlay
        if (effectiveDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveDim))
            )
        }



        // Layer 4: MacroPad buttons
        // During viewport edit: rendered at 50% alpha so the user can see button
        //   positions while adjusting the mirror crop.
        // Normal: fully opaque (or peek-adjusted via isPeekActive).
        val buttonAlpha = when {
            isViewportEditActive || previewConfig != null -> 0.5f
            else                                          -> 1f
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
                    modifier = Modifier
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
                        neutralStyle = l.buttonColorMirror == ButtonColorStyle.NEUTRAL,
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
            val previewValue = when (pc.type) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .blockPointerEvents(),
                contentAlignment = Alignment.BottomCenter
            ) {
                AsoPreviewBar(
                    label = pc.label,
                    value = previewValue,
                    valueRange = pc.valueRange,
                    formatLabel = formatPreviewLabel,
                    accentColor = colors.accent,
                    onValueChange = { v ->
                        val updated = when (pc.type) {
                            AmbientPreviewType.DIM -> pl.copy(ambientDim = v)
                            AmbientPreviewType.EDGE_BLENDING -> pl.copy(mirrorEdgeBlendWidth = v)
                        }
                        MacroPadState.updateLayout(updated)
                    },
                    onCancel = {
                        AppLog.d(TAG, "ambient preview ${pc.type} cancelled")
                        val restored = when (pc.type) {
                            AmbientPreviewType.DIM -> pl.copy(ambientDim = pc.originalValue)
                            AmbientPreviewType.EDGE_BLENDING -> pl.copy(mirrorEdgeBlendWidth = pc.originalValue)
                        }
                        MacroPadState.updateLayout(restored)
                        AppStateManager.setAmbientPreviewConfig(null)
                    },
                    onConfirm = {
                        AppLog.d(TAG, "ambient preview ${pc.type} confirmed")
                        AppStateManager.setAmbientPreviewConfig(null)
                    },
                )
            }
        }

        if (showQuickMenuBar) QuickMenuBar()
    }
}


