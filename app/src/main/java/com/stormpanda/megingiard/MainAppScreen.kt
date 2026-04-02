package com.stormpanda.megingiard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.media.MediaScreen
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.touchpad.TouchpadScreen
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.LocalAppColors

private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp

@Composable
fun MainAppScreen() {
    val currentMode by AppStateManager.currentMode.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()
    val colors = LocalAppColors.current

    val showControls by AppStateManager.overlayVisible.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { MAS_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MAS_SWIPE_THRESHOLD.toPx() }

    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler { showExitDialog = true }

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
                        modifier = Modifier.fillMaxSize().background(colors.appBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isValidScreen) {
                            if (!isCapturing && userDeclinedCapture) {
                                OutlinedButton(
                                    onClick = { AppStateManager.setUserDeclinedCapture(false) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = colors.buttonBody,
                                        contentColor = colors.buttonIconTint
                                    ),
                                    border = BorderStroke(2.dp, colors.navPillBorder),
                                    modifier = Modifier.padding(16.dp).height(72.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.mirror_start_button),
                                        modifier = Modifier.padding(end = 8.dp).size(36.dp),
                                        tint = colors.buttonIconTint
                                    )
                                    Text(stringResource(R.string.mirror_start_button), color = colors.buttonIconTint)
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.mirror_wrong_screen),
                                color = colors.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
                AppMode.MEDIA -> MediaScreen()
                AppMode.TOUCHPAD -> TouchpadScreen()
                AppMode.KEYBOARD -> KeyboardScreen()
                AppMode.MACROPAD -> MacroPadScreen()
            }
        }

        CarouselOverlay(visible = showControls, onInteraction = { AppStateManager.triggerOverlay() })
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_dialog_title), color = colors.onSurface) },
            text = { Text(stringResource(R.string.exit_dialog_message), color = colors.onSurface) },
            confirmButton = {
                TextButton(onClick = { (context as ComponentActivity).finishAndRemoveTask() }) {
                    Text(stringResource(R.string.exit_dialog_confirm), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.exit_dialog_cancel), color = colors.accent)
                }
            },
            containerColor = colors.surface,
        )
    }
}

