package com.stormpanda.megingiard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.media.MediaScreen
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadScreen
import com.stormpanda.megingiard.ui.CarouselOverlay

private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp

@Composable
fun MainAppScreen() {
    val currentMode by AppStateManager.currentMode.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()

    val showControls by AppStateManager.overlayVisible.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { MAS_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MAS_SWIPE_THRESHOLD.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom) {
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
                                // Only clear touching when all pointers are up
                                // (multi-touch: one finger may release while another is still down)
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
    ) {
        Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
            when (mode) {
                AppMode.MIRROR -> {
                    val isValidScreen by AppStateManager.isOnValidScreen.collectAsState()
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isValidScreen) {
                            if (!isCapturing && userDeclinedCapture) {
                                Button(
                                    onClick = { AppStateManager.setUserDeclinedCapture(false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor
                                    ),
                                    modifier = Modifier.padding(16.dp).height(72.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.mirror_start_button),
                                        modifier = Modifier.padding(end = 8.dp).size(36.dp),
                                        tint = Color.White
                                    )
                                    Text(stringResource(R.string.mirror_start_button), color = Color.White)
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.mirror_wrong_screen),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
                AppMode.MEDIA -> MediaScreen()
                AppMode.TOUCHPAD -> TouchpadScreen(onInteraction = { AppStateManager.triggerOverlay() })
            }
        }

        CarouselOverlay(visible = showControls, onInteraction = { AppStateManager.triggerOverlay() })
    }
}

