package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val AMO_SWIPE_EDGE_ZONE = 40.dp
private val AMO_SWIPE_THRESHOLD = 25.dp
// At vignetteSize = 1.0 the gradient radius equals half the diagonal, reaching all four corners.
private const val AMO_VIGNETTE_RADIUS_FACTOR = 1.4142f // sqrt(2)

// ─────────────────────────────────────────────────────────────────────────────
// Ambient MacroPad Overlay — renders MacroPad buttons over the screen mirror
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AmbientMacroPadOverlay() {
    val context = LocalContext.current
    val profile by MacroPadState.activeProfile.collectAsState()
    val colors = LocalAppColors.current

    val blurRadius by SettingsManager.macropadAmbientBlur.collectAsState()
    val dimAlpha by SettingsManager.macropadAmbientDim.collectAsState()
    val vignetteEnabled by SettingsManager.macropadAmbientVignetteEnabled.collectAsState()
    val vignetteSize by SettingsManager.macropadAmbientVignetteSize.collectAsState()
    val vignetteOpacity by SettingsManager.macropadAmbientVignetteOpacity.collectAsState()
    val vignetteColorInt by SettingsManager.macropadAmbientVignetteColor.collectAsState()
    val isPeekActive by MacroPadState.isPeekActive.collectAsState()
    val ambientFrame by ScreenCaptureManager.ambientFrame.collectAsState()

    val showControls by AppStateManager.overlayVisible.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { AMO_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { AMO_SWIPE_THRESHOLD.toPx() }

    // Effective blur/dim/vignette: overridden to 0 when peeking
    val effectiveBlur = if (isPeekActive) 0f else blurRadius
    val effectiveDim = if (isPeekActive) 0f else dimAlpha
    val effectiveVignetteOpacity = if (isPeekActive) 0f else vignetteOpacity

    // Start injectors after the carousel overlay has closed (same pattern as MacroPadScreen)
    LaunchedEffect(Unit) {
        AppStateManager.overlayVisible.first { !it }
        withContext(Dispatchers.IO) {
            val ap = MacroPadState.activeProfile.value
            if (ap?.enableKeyboard != false) KeyInjector.start(context)
            if (ap?.enableGamepad != false) GamepadInjector.start(context)
            if (ap?.enableMouse != false) MouseInjector.start(context)
        }
    }

    // Stop all injectors and reset peek state when leaving
    DisposableEffect(Unit) {
        onDispose {
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
            MacroPadState.resetPeek()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Initial pass: detect swipe from the pill edge to trigger carousel overlay,
            // without consuming events so MacroPad button presses still work.
            .pointerInput(overlayAtBottom) {
                awaitPointerEventScope {
                    var swipeStartY = Float.NaN
                    var swipeTriggered = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> {
                                val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                val nearEdge = if (overlayAtBottom) {
                                    y >= size.height - edgeZonePx
                                } else {
                                    y <= edgeZonePx
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
                                swipeStartY = Float.NaN
                                swipeTriggered = false
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        // Layer 1: Blurred mirror background (only when blur > 0 and frame available)
        if (effectiveBlur > 0f) {
            val frame = ambientFrame
            if (frame != null && !frame.isRecycled) {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(effectiveBlur.dp),
                )
            }
        }
        // When blur == 0, SurfaceView is visible beneath this ComposeView (live stream)

        // Layer 2: Dim overlay
        if (effectiveDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveDim))
            )
        }

        // Layer 3: Vignette overlay (radial gradient darkening the edges)
        if (vignetteEnabled && effectiveVignetteOpacity > 0f) {
            val vColor = Color(vignetteColorInt)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val radius = (size.minDimension / 2f) * vignetteSize * AMO_VIGNETTE_RADIUS_FACTOR
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    vColor.copy(alpha = effectiveVignetteOpacity),
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius,
                            )
                        )
                    }
            )
        }

        // Layer 4: MacroPad buttons
        val p = profile
        if (p == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(MP_SCREEN_PADDING),
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
            Box(modifier = Modifier.fillMaxSize().padding(MP_SCREEN_PADDING)) {
                PadSurface(
                    profile = p,
                    accentColor = colors.accent,
                    isPeekActive = isPeekActive,
                    transparentBackground = true,
                )
            }
        }

        // Layer 5: Carousel overlay for mode switching
        CarouselOverlay(
            visible = showControls,
            onInteraction = { AppStateManager.triggerOverlay() },
        )
    }
}
