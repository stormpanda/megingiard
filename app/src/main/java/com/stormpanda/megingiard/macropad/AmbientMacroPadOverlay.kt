package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val AM_SCREEN_PADDING_DP = 8
private val AM_SCREEN_PADDING = AM_SCREEN_PADDING_DP.dp
private val AM_SWIPE_EDGE_ZONE = 40.dp
private val AM_SWIPE_THRESHOLD = 25.dp
// Minimum gap between gradient color stops to prevent duplicate-stop artifacts.
private const val VIGNETTE_MIN_STOP_GAP = 0.001f

private const val TAG = "AmbientMacroPadOverlay"

// ─────────────────────────────────────────────────────────────────────────────
// Ambient MacroPad Overlay — renders MacroPad buttons over the screen mirror
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AmbientMacroPadOverlay() {
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
    // When touch projection or freeze is active, hide pad content entirely.
    // Viewport edit is handled separately: vignette stays, buttons go semi-transparent.
    val hideContent = isTouchProjectionActive || isFrozen
    val applyTheme by SettingsManager.macropadAmbientApplyTheme.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { AM_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { AM_SWIPE_THRESHOLD.toPx() }
    val swipeProcessor = remember(overlayAtBottom, edgeZonePx, swipeThresholdPx) {
        SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom)
    }

    // Effective dim/vignette: overridden to 0 when peeking
    val effectiveDim = if (isPeekActive) 0f else dimAlpha
    val effectiveVignetteOpacity = if (isPeekActive) 0f else vignetteOpacity

    // Start injectors immediately
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ap = MacroPadState.activeProfile.value
            AppLog.d(TAG, "starting injectors for profile '${ap?.name}' (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse})")
            if (ap?.enableKeyboard != false) KeyInjector.start(context)
            if (ap?.enableGamepad != false) GamepadInjector.start(context)
            if (ap?.enableMouse != false) MouseInjector.start(context)
        }
    }

    // Stop injectors while the Pill Menu is open so the OS soft IME can appear
    // in text-input dialogs (New Profile / New Layout).  Restart when it closes.
    val isPillMenuOpen by AppStateManager.isPillMenuOpen.collectAsState()
    LaunchedEffect(isPillMenuOpen) {
        if (isPillMenuOpen) {
            AppLog.d(TAG, "PillMenu opened → stopping injectors")
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
        } else {
            // Menu closed → restart only the devices enabled by the active profile
            withContext(Dispatchers.IO) {
                val ap = MacroPadState.activeProfile.value
                AppLog.d(TAG, "PillMenu closed → restarting injectors")
                if (ap?.enableKeyboard != false) KeyInjector.start(context)
                if (ap?.enableGamepad != false) GamepadInjector.start(context)
                if (ap?.enableMouse != false) MouseInjector.start(context)
            }
        }
    }

    // Stop all injectors and reset peek state when leaving
    DisposableEffect(Unit) {
        onDispose {
            AppLog.d(TAG, "AmbientMacroPadOverlay disposed → all injectors stopped")
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
            MacroPadState.resetPeek()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom, edgeZonePx, swipeThresholdPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val primaryChange = event.changes.firstOrNull()
                        when (event.type) {
                            PointerEventType.Press -> {
                                swipeProcessor.onPress(
                                    primaryChange?.position?.y ?: 0f,
                                    size.height.toFloat(),
                                )
                            }
                            PointerEventType.Move -> {
                                swipeProcessor.onMove(primaryChange?.position?.y ?: 0f)
                            }
                            PointerEventType.Release -> {
                                val allPointersLifted = !event.changes.any { it.pressed }
                                swipeProcessor.onRelease(allPointersLifted)
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
            hideContent        -> 0f
            isViewportEditActive -> 0.5f
            else               -> 1f
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
                        fontSize = 14.sp,
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
                        neutralStyle = !applyTheme,
                    )
                }
            }
        }

        IdlePill()
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
