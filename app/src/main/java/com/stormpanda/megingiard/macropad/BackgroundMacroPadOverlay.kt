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
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.ButtonColorStyle
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.MirrorSettings
import java.util.Locale
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val AM_SCREEN_PADDING_DP = 0
private val AM_SCREEN_PADDING = AM_SCREEN_PADDING_DP.dp
private val AM_SWIPE_EDGE_ZONE = 40.dp
private val AM_SWIPE_THRESHOLD = 25.dp
private val AM_SWIPE_PILL_ZONE_WIDTH = 120.dp
private const val AM_PERCENT_DIVISOR = 100f
// Minimum gap between gradient color stops to prevent duplicate-stop artifacts.
private const val VIGNETTE_MIN_STOP_GAP = 0.001f

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
internal fun BackgroundMacroPadOverlay(showIdlePill: Boolean = true) {
    val context = LocalContext.current
    val profile by MacroPadState.activeProfile.collectAsState()
    val layout by MacroPadState.activeLayout.collectAsState()
    val colors = LocalAppColors.current

    val dimAlpha = layout?.ambientDim ?: 0f
    val vignetteEnabled = layout?.ambientVignetteEnabled ?: false
    val vignetteVisibleArea = layout?.ambientVignetteVisibleArea ?: 0.7f
    val vignetteTransition = layout?.ambientVignetteTransition ?: 0.5f
    val vignetteOpacity = layout?.ambientVignetteOpacity ?: 0.6f
    val vignetteColorInt = layout?.ambientVignetteColor ?: 0xFF000000.toInt()
    val vignetteShape = layout?.ambientVignetteShape ?: VignetteShape.RADIAL
    val isPeekActive by MacroPadState.isPeekActive.collectAsState()
    val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    // When touch projection or freeze is active, hide pad content entirely.
    // Viewport edit is handled separately: vignette stays, buttons go semi-transparent.
    val hideContent = isTouchProjectionActive || isFrozen
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { AM_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { AM_SWIPE_THRESHOLD.toPx() }
    val pillZoneWidthPx = with(density) { AM_SWIPE_PILL_ZONE_WIDTH.toPx() }
    val swipeProcessor = remember(overlayAtBottom, edgeZonePx, swipeThresholdPx, pillZoneWidthPx) {
        SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom, pillZoneWidthPx)
    }

    // Effective dim/vignette: overridden to 0 when peeking
    val effectiveDim = if (isPeekActive) 0f else dimAlpha
    val effectiveVignetteOpacity = if (isPeekActive) 0f else vignetteOpacity

    // Single watcher: stop injectors according to overlay state, restart when all closed.
    // Mirrors MacroPadViewModel.watchInjectorLifecycle() — must use the same combine()+
    // collectLatest+delay pattern so that rapid PillMenu-close→Ambient-open transitions
    // do not cause a spurious injector restart.
    LaunchedEffect(Unit) {
        combine(
            AppStateManager.isPillMenuOpen,
            AppStateManager.isEditorActive,
            AppStateManager.isBackgroundSettingsActive,
            AppStateManager.isFullscreenKeyboardActive,
            AppStateManager.isFullscreenMouseActive,
            AppStateManager.isViewportEditActive,
        ) { array ->
            val pillMenu = array[0]
            val editor = array[1]
            val ambient = array[2]
            val kb = array[3]
            val mouse = array[4]
            val vp = array[5]
            val stopAll = editor || ambient || kb || mouse || vp
            AmbientInjectorGate(
                stopKeyboard = stopAll,
                stopMouseAndGamepad = stopAll || pillMenu,
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
                    AppLog.d(TAG, "pill menu open → stopping gamepad/mouse injectors")
                    GamepadInjector.stop()
                    MouseInjector.stop()
                }
                else -> {
                    delay(AM_INJECTOR_RESTART_DEBOUNCE_MS)
                    withContext(Dispatchers.IO) {
                        val ap = MacroPadState.activeProfile.value
                        AppLog.i(TAG, "all guards clear → starting injectors for profile '${ap?.name}' (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse})")
                        if (ap?.enableKeyboard == true) KeyInjector.start(context)
                        if (ap?.enableGamepad == true) GamepadInjector.start(context)
                        if (ap?.enableMouse == true) MouseInjector.start(context)
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
            MacroPadState.resetPeek()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom, edgeZonePx, swipeThresholdPx, pillZoneWidthPx) {
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
        if (!hideContent && effectiveDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveDim))
            )
        }

        // Layer 3: Vignette overlay (shape-specific gradient darkening the edges)
        // Shown in normal mode AND during viewport edit (intentionally retained so the
        // user can judge where the vignette sits relative to the chosen viewport).
        if (!hideContent && vignetteEnabled && effectiveVignetteOpacity > 0f) {
            val vColor = Color(vignetteColorInt)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        when (vignetteShape) {
                            VignetteShape.RADIAL     -> drawRadialVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.LETTERBOX  -> drawLetterboxVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.PILLARBOX  -> drawPillarboxVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.TOP        -> drawTopVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.BOTTOM     -> drawBottomVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.LEFT       -> drawLeftVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                            VignetteShape.RIGHT      -> drawRightVignette(vColor, vignetteVisibleArea, vignetteTransition, effectiveVignetteOpacity)
                        }
                    }
            )
        }

        // Layer 4: MacroPad buttons
        // During touch projection / freeze: fully hidden.
        // During viewport edit: rendered at 50% alpha so the user can see button
        //   positions while adjusting the mirror crop.
        // Normal: fully opaque (or peek-adjusted via isPeekActive).
        val buttonAlpha = when {
            hideContent                                   -> 0f
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
            val labelSoft = stringResource(R.string.settings_macropad_vignette_transition_soft)
            val labelHard = stringResource(R.string.settings_macropad_vignette_transition_hard)
            val previewValue = when (pc.type) {
                AmbientPreviewType.DIM                 -> pl.ambientDim
                AmbientPreviewType.VIGNETTE_AREA       -> pl.ambientVignetteVisibleArea
                AmbientPreviewType.VIGNETTE_TRANSITION -> pl.ambientVignetteTransition
                AmbientPreviewType.VIGNETTE_OPACITY    -> pl.ambientVignetteOpacity
                AmbientPreviewType.FOLLOW_ACCELERATION -> MirrorSettings.followAcceleration.collectAsState().value
                AmbientPreviewType.FOLLOW_ZOOM         -> MirrorSettings.followZoom.collectAsState().value
            }
            val formatPreviewLabel: (Float) -> String = when (pc.type) {
                AmbientPreviewType.VIGNETTE_TRANSITION -> { v ->
                    when {
                        v <= 0f -> labelSoft
                        v >= 1f -> labelHard
                        else    -> "${(v * AM_PERCENT_DIVISOR).toInt()}%"
                    }
                }
                AmbientPreviewType.FOLLOW_ACCELERATION -> { v -> String.format(Locale.ROOT, "%.3f", v) }
                AmbientPreviewType.FOLLOW_ZOOM         -> { v -> String.format(Locale.ROOT, "%.1fx", v) }
                else -> { v -> "${(v * AM_PERCENT_DIVISOR).toInt()}%" }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AsoPreviewBar(
                    label = pc.label,
                    value = previewValue,
                    valueRange = pc.valueRange,
                    formatLabel = formatPreviewLabel,
                    accentColor = colors.accent,
                    onValueChange = { v ->
                        when (pc.type) {
                            AmbientPreviewType.FOLLOW_ACCELERATION -> {
                                MirrorSettings.setFollowAcceleration(v)
                            }
                            AmbientPreviewType.FOLLOW_ZOOM -> {
                                MirrorSettings.setFollowZoom(v)
                            }
                            else -> {
                                val updated = when (pc.type) {
                                    AmbientPreviewType.DIM                 -> pl.copy(ambientDim = v)
                                    AmbientPreviewType.VIGNETTE_AREA       -> pl.copy(ambientVignetteVisibleArea = v)
                                    AmbientPreviewType.VIGNETTE_TRANSITION -> pl.copy(ambientVignetteTransition = v)
                                    AmbientPreviewType.VIGNETTE_OPACITY    -> pl.copy(ambientVignetteOpacity = v)
                                    else -> pl
                                }
                                MacroPadState.updateLayout(updated)
                            }
                        }
                    },
                    onCancel = {
                        AppLog.d(TAG, "ambient preview ${pc.type} cancelled")
                        when (pc.type) {
                            AmbientPreviewType.FOLLOW_ACCELERATION -> {
                                MirrorSettings.setFollowAcceleration(pc.originalValue)
                            }
                            AmbientPreviewType.FOLLOW_ZOOM -> {
                                MirrorSettings.setFollowZoom(pc.originalValue)
                            }
                            else -> {
                                val restored = when (pc.type) {
                                    AmbientPreviewType.DIM                 -> pl.copy(ambientDim = pc.originalValue)
                                    AmbientPreviewType.VIGNETTE_AREA       -> pl.copy(ambientVignetteVisibleArea = pc.originalValue)
                                    AmbientPreviewType.VIGNETTE_TRANSITION -> pl.copy(ambientVignetteTransition = pc.originalValue)
                                    AmbientPreviewType.VIGNETTE_OPACITY    -> pl.copy(ambientVignetteOpacity = pc.originalValue)
                                    else -> pl
                                }
                                MacroPadState.updateLayout(restored)
                            }
                        }
                        AppStateManager.setAmbientPreviewConfig(null)
                    },
                    onConfirm = {
                        AppLog.d(TAG, "ambient preview ${pc.type} confirmed")
                        AppStateManager.setAmbientPreviewConfig(null)
                    },
                )
            }
        }

        if (showIdlePill) IdlePill()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vignette DrawScope helpers
//
// visibleArea: 0 = full effect, 1 = effect off-screen (nothing covered)
// transition:  0 = soft (full gradient), 1 = hard (instant cut)
// opacity:     alpha applied to the vignetteColor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Radial vignette: transparent circle in the center, colored edges.
 * At visibleArea = 1.0, inner radius equals the half-diagonal (corners just reached).
 */
private fun DrawScope.drawRadialVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    if (visibleArea >= 1f) return
    val halfDiag = sqrt(size.width * size.width + size.height * size.height) / 2f
    val innerFrac = visibleArea                                        // transparent zone [0..halfDiag)
    // Quadratic Ease-Out für weicheren Übergang
    fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)
    val easedTransition = easeOutQuad(1f - transition)
    val outerFrac = minOf(1f, maxOf(innerFrac + VIGNETTE_MIN_STOP_GAP,
        innerFrac + (1f - innerFrac) * easedTransition))            // gradient end fraction
    val vColor = vignetteColor.copy(alpha = opacity)
    val stops = buildList {
        add(0f to Color.Transparent)
        if (innerFrac > 0f) add(innerFrac to Color.Transparent)
        add(outerFrac to vColor)
        if (outerFrac < 1f) add(1f to vColor)
    }.toTypedArray()
    drawRect(
        brush = Brush.radialGradient(
            colorStops = stops,
            center = Offset(size.width / 2f, size.height / 2f),
            radius = halfDiag,
        )
    )
}

/**
 * Letterbox vignette: dark bands at top and bottom, transparent center strip.
 * visibleArea controls the height of the transparent center strip (1 = full height, 0 = none).
 */
private fun DrawScope.drawLetterboxVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    val innerFrac = (1f - visibleArea) / 2f   // fraction of height that is dark band (each side)
    if (innerFrac <= 0f) return
    // When visibleArea=0 both gradient stops land at 0.5f (same position) → Brush crash.
    // Full-coverage case: just fill solid.
    if (innerFrac >= 0.5f) { drawRect(color = vignetteColor.copy(alpha = opacity)); return }
    // Quadratic Ease-Out für weicheren Übergang
    fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)
    val easedTransition = easeOutQuad(1f - transition)
    val transitionFrac = innerFrac * easedTransition
    val gradStart = maxOf(0f, minOf(innerFrac - VIGNETTE_MIN_STOP_GAP, innerFrac - transitionFrac))
    val vColor = vignetteColor.copy(alpha = opacity)
    val stops = buildList {
        add(0f to vColor)
        if (gradStart > 0f) add(gradStart to vColor)
        add(innerFrac to Color.Transparent)
        add((1f - innerFrac) to Color.Transparent)
        if (gradStart > 0f) add((1f - gradStart) to vColor)
        add(1f to vColor)
    }.toTypedArray()
    drawRect(brush = Brush.verticalGradient(colorStops = stops))
}

/**
 * Pillarbox vignette: dark bands at left and right, transparent center strip.
 * visibleArea controls the width of the transparent center strip (1 = full width, 0 = none).
 */
private fun DrawScope.drawPillarboxVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    val innerFrac = (1f - visibleArea) / 2f   // fraction of width that is dark band (each side)
    if (innerFrac <= 0f) return
    // When visibleArea=0 both gradient stops land at 0.5f (same position) → Brush crash.
    // Full-coverage case: just fill solid.
    if (innerFrac >= 0.5f) { drawRect(color = vignetteColor.copy(alpha = opacity)); return }
    // Quadratic Ease-Out für weicheren Übergang
    fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)
    val easedTransition = easeOutQuad(1f - transition)
    val transitionFrac = innerFrac * easedTransition
    val gradStart = maxOf(0f, minOf(innerFrac - VIGNETTE_MIN_STOP_GAP, innerFrac - transitionFrac))
    val vColor = vignetteColor.copy(alpha = opacity)
    val stops = buildList {
        add(0f to vColor)
        if (gradStart > 0f) add(gradStart to vColor)
        add(innerFrac to Color.Transparent)
        add((1f - innerFrac) to Color.Transparent)
        if (gradStart > 0f) add((1f - gradStart) to vColor)
        add(1f to vColor)
    }.toTypedArray()
    drawRect(brush = Brush.horizontalGradient(colorStops = stops))
}

/**
 * Directional vignette: darkens from one edge inward, leaving the rest transparent.
 * visibleArea controls the fraction of the screen that remains transparent.
 */
private fun DrawScope.drawEdgeVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
    vertical: Boolean,
    fromStart: Boolean,
) {
    val coveredFrac = 1f - visibleArea
    if (coveredFrac <= 0f) return
    if (coveredFrac >= 1f) {
        drawRect(color = vignetteColor.copy(alpha = opacity))
        return
    }
    fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)
    val easedTransition = easeOutQuad(1f - transition)
    val transitionFrac = coveredFrac * easedTransition
    val vColor = vignetteColor.copy(alpha = opacity)
    val stops = if (fromStart) {
        val transparentStart = maxOf(
            VIGNETTE_MIN_STOP_GAP,
            minOf(coveredFrac, coveredFrac - transitionFrac),
        )
        buildList {
            add(0f to vColor)
            if (transparentStart > 0f) add(transparentStart to vColor)
            add(coveredFrac to Color.Transparent)
            add(1f to Color.Transparent)
        }.toTypedArray()
    } else {
        val coloredStart = minOf(
            1f - VIGNETTE_MIN_STOP_GAP,
            maxOf(1f - coveredFrac, 1f - coveredFrac + transitionFrac),
        )
        buildList {
            add(0f to Color.Transparent)
            add((1f - coveredFrac) to Color.Transparent)
            if (coloredStart < 1f) add(coloredStart to vColor)
            add(1f to vColor)
        }.toTypedArray()
    }
    val brush = if (vertical) {
        Brush.verticalGradient(colorStops = stops)
    } else {
        Brush.horizontalGradient(colorStops = stops)
    }
    drawRect(brush = brush)
}

private fun DrawScope.drawTopVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    drawEdgeVignette(vignetteColor, visibleArea, transition, opacity, vertical = true, fromStart = true)
}

private fun DrawScope.drawBottomVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    drawEdgeVignette(vignetteColor, visibleArea, transition, opacity, vertical = true, fromStart = false)
}

private fun DrawScope.drawLeftVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    drawEdgeVignette(vignetteColor, visibleArea, transition, opacity, vertical = false, fromStart = true)
}

private fun DrawScope.drawRightVignette(
    vignetteColor: Color,
    visibleArea: Float,
    transition: Float,
    opacity: Float,
) {
    drawEdgeVignette(vignetteColor, visibleArea, transition, opacity, vertical = false, fromStart = false)
}
