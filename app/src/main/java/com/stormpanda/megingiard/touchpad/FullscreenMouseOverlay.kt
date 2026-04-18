package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FullscreenMouseOverlay"
private const val FMO_BG_ALPHA = 0.85f

/**
 * Full-screen transparent overlay that captures all touch as relative mouse input.
 *
 * Activated when [AppStateManager.isFullscreenMouseActive] is true. The overlay:
 * - Starts [MouseInjector] on composition, stops it on disposal.
 * - Uses [TouchpadGestureProcessor] in mouse mode with the sensitivity from
 *   [AppStateManager.fullscreenMouseSensitivity].
 * - Supports tap-to-click and two-finger-tap right-click via [SettingsManager].
 * - Closed by an edge-swipe detected in [MainAppScreen][com.stormpanda.megingiard.MainAppScreen]
 *   calling [AppStateManager.handleEdgeSwipe].
 */
@Composable
fun FullscreenMouseOverlay() {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()

    val sensitivity by AppStateManager.fullscreenMouseSensitivity.collectAsState()
    val tapToClick by SettingsManager.touchpadTapToClick.collectAsState()
    val twoFingerTap by SettingsManager.touchpadTwoFingerTap.collectAsState()

    val tapToClickState = rememberUpdatedState(tapToClick)
    val twoFingerTapState = rememberUpdatedState(twoFingerTap)

    LaunchedEffect(Unit) {
        AppLog.i(TAG, "start: starting MouseInjector")
        withContext(Dispatchers.IO) { MouseInjector.start(context) }
    }

    DisposableEffect(Unit) {
        onDispose {
            AppLog.i(TAG, "dispose: stopping MouseInjector")
            MouseInjector.stop()
        }
    }

    // Recreate processor when sensitivity changes so the new factor applies immediately.
    val processor = remember(sensitivity) {
        AppLog.d(TAG, "creating TouchpadGestureProcessor useMouse=true sensitivity=$sensitivity")
        TouchpadGestureProcessor(useMouse = true, scope = coroutineScope, sensitivity = sensitivity)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground.copy(alpha = FMO_BG_ALPHA))
            .pointerInput(processor) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val sw = size.width.toFloat()
                        val sh = size.height.toFloat()

                        for (change in event.changes) {
                            val id = change.id.value
                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (!change.previousPressed) {
                                        processor.onPress(
                                            id, change.position.x, change.position.y,
                                            sw, sh, overlayOpen = false
                                        )
                                        change.consume()
                                    }
                                }
                                PointerEventType.Move -> {
                                    val delta = change.positionChange()
                                    processor.onMove(
                                        id, change.position.x, change.position.y,
                                        delta.x, delta.y, sw, sh, overlayOpen = false
                                    )
                                    change.consume()
                                }
                                PointerEventType.Release -> {
                                    if (!change.pressed) {
                                        val allUp = event.changes.none { it.pressed }
                                        processor.onRelease(
                                            id, change.position.x, change.position.y,
                                            sw, sh,
                                            allPointersUp = allUp,
                                            tapToClick = tapToClickState.value,
                                            twoFingerTap = twoFingerTapState.value
                                        )
                                        change.consume()
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
    )
}
