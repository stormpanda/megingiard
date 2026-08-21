package com.stormpanda.megingiard.mirror

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.macropad.TouchRecordingMode
import com.stormpanda.megingiard.macropad.TouchSample
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.delay

private const val TAG = "TouchRecordingOverlay"
private const val TRO_FEEDBACK_TAP_DURATION_MS = 50L
private const val TRO_CONTROLS_BACKGROUND_ALPHA = 0.90f
private val TRO_CONTROLS_HORIZONTAL_PADDING = 16.dp
private val TRO_CONTROLS_BUTTON_SPACING = 12.dp
private val TRO_STOP_ICON_SIZE = 18.dp
private val TRO_STOP_ICON_TEXT_SPACING = 6.dp

private class GestureRecordingSession(
    val sessionStartEpochMs: Long,
) {
    val samples = mutableListOf<TouchSample>()
    val activePointerIds = mutableSetOf<Long>()
    var recordingStarted = false
    var segmentStartEpochMs = 0L
    var segmentStartOffsetMs = 0L

    fun startSegment(nowMs: Long) {
        recordingStarted = true
        segmentStartEpochMs = nowMs
        segmentStartOffsetMs = nowMs - sessionStartEpochMs
    }

    fun clearSegment() {
        samples.clear()
        activePointerIds.clear()
        recordingStarted = false
        segmentStartEpochMs = 0L
        segmentStartOffsetMs = 0L
    }
}

@Composable
fun TouchRecordingOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mode by TouchRecordingManager.recordingMode.collectAsState()
    val srcWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val srcHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()

    BackHandler {
        TouchRecordingManager.cancelRecording()
        AppStateManager.resumeSuspended()
    }

    DisposableEffect(Unit) {
        TouchInjector.start(context, "TouchRecordingOverlay")
        AppLog.d(TAG, "TouchInjector started by TouchRecordingOverlay")
        onDispose {
            TouchInjector.stop("TouchRecordingOverlay")
            AppLog.d(TAG, "TouchInjector stopped by TouchRecordingOverlay")
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val targetWidth = constraints.maxWidth
        val targetHeight = constraints.maxHeight

        val sWidth = if (srcWidth > 0) srcWidth else 1920
        val sHeight = if (srcHeight > 0) srcHeight else 1080
        val srcRatio = sWidth.toFloat() / sHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

        var finalWidth = targetWidth
        var finalHeight = targetHeight
        if (srcRatio > targetRatio) {
            finalHeight = (targetWidth / srcRatio).toInt()
        } else {
            finalWidth = (targetHeight * srcRatio).toInt()
        }

        if (mode == TouchRecordingMode.GESTURE) {
            GestureRecordingOverlay(
                contentWidth = finalWidth,
                contentHeight = finalHeight,
                bottomBarHeightPx = (targetHeight - finalHeight) / 2,
            )
        } else {
            TapCaptureOverlay(
                contentWidth = finalWidth,
                contentHeight = finalHeight,
            )
        }
    }
}

@Composable
private fun TapCaptureOverlay(
    contentWidth: Int,
    contentHeight: Int,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(contentWidth, contentHeight) {
                    var captured: Pair<Float, Float>? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                val change = event.changes.firstOrNull() ?: continue
                                if (size.width <= 0 || size.height <= 0) continue
                                val result =
                                    projectCoordinates(
                                        touchX = change.position.x,
                                        touchY = change.position.y,
                                        screenW = size.width.toFloat(),
                                        screenH = size.height.toFloat(),
                                        sw = contentWidth.toFloat(),
                                        sh = contentHeight.toFloat(),
                                        scale = 1f,
                                        offsetX = 0f,
                                        offsetY = 0f,
                                    ) ?: continue
                                val (normX, normY) = result
                                AppLog.i(TAG, "tap captured normX=$normX normY=$normY")
                                change.consume()
                                captured = Pair(normX, normY)
                                break
                            }
                        }
                    }
                    val (normX, normY) = captured ?: return@pointerInput
                    TouchInjector.injectTouch(TouchAction.DOWN, normX, normY)
                    delay(TRO_FEEDBACK_TAP_DURATION_MS)
                    TouchInjector.injectTouch(TouchAction.UP, normX, normY)
                    TouchRecordingManager.onTapRecorded(normX, normY)
                    AppStateManager.resumeSuspended()
                },
    )
}

@Composable
private fun GestureRecordingOverlay(
    contentWidth: Int,
    contentHeight: Int,
    bottomBarHeightPx: Int,
) {
    val session = remember { GestureRecordingSession(SystemClock.elapsedRealtime()) }

    fun flushCurrentSegment() {
        if (!session.recordingStarted || session.samples.isEmpty()) return
        AppLog.i(TAG, "gesture segment finished with ${session.samples.size} samples")
        TouchRecordingManager.recordGestureCompleted(
            samples = session.samples.toList(),
            startOffsetMs = session.segmentStartOffsetMs,
        )
        session.clearSegment()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GestureCaptureOverlay(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            session = session,
            onSegmentStart = { startEpochMs -> session.startSegment(startEpochMs) },
            onSegmentEnd = { flushCurrentSegment() },
        )
        TouchRecordingControls(
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomBarHeightPx = bottomBarHeightPx,
            onCancel = {
                TouchRecordingManager.cancelRecording()
                AppStateManager.resumeSuspended()
            },
            onStop = {
                flushCurrentSegment()
                TouchRecordingManager.finishRecording()
                AppStateManager.resumeSuspended()
            },
        )
    }
}

@Composable
private fun TouchRecordingControls(
    modifier: Modifier,
    bottomBarHeightPx: Int,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalAppColors.current
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.coerceAtLeast(0).toDp() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(bottomBarHeight)
                .background(colors.surface.copy(alpha = TRO_CONTROLS_BACKGROUND_ALPHA))
                .padding(horizontal = TRO_CONTROLS_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.privd_recording_physical_cancel),
                color = colors.onSurfaceSecondary,
            )
        }
        Spacer(Modifier.width(TRO_CONTROLS_BUTTON_SPACING))
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = null,
                modifier = Modifier.size(TRO_STOP_ICON_SIZE),
            )
            Spacer(Modifier.width(TRO_STOP_ICON_TEXT_SPACING))
            Text(stringResource(R.string.privd_recording_physical_stop))
        }
    }
}

@Composable
private fun GestureCaptureOverlay(
    contentWidth: Int,
    contentHeight: Int,
    session: GestureRecordingSession,
    onSegmentStart: (startEpochMs: Long) -> Unit,
    onSegmentEnd: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(contentWidth, contentHeight) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val now = SystemClock.elapsedRealtime()

                            val changes = event.changes
                            if (changes.isEmpty()) continue

                            val allPointersReleased = changes.none { it.pressed }

                            for (change in changes) {
                                val pointerId = change.id.value
                                val position = change.position
                                val isPressed = change.pressed
                                val isAlreadyTracked = session.activePointerIds.contains(pointerId)

                                val result =
                                    projectCoordinates(
                                        touchX = position.x,
                                        touchY = position.y,
                                        screenW = size.width.toFloat(),
                                        screenH = size.height.toFloat(),
                                        sw = contentWidth.toFloat(),
                                        sh = contentHeight.toFloat(),
                                        scale = 1f,
                                        offsetX = 0f,
                                        offsetY = 0f,
                                    )

                                if (result == null && !isAlreadyTracked) {
                                    continue
                                }

                                val (normX, normY) =
                                    if (result != null) {
                                        result
                                    } else {
                                        val screenCenterX = size.width.toFloat() / 2f
                                        val screenCenterY = size.height.toFloat() / 2f
                                        val svCenterX = contentWidth.toFloat() / 2f
                                        val svCenterY = contentHeight.toFloat() / 2f
                                        val svX = (position.x - screenCenterX) + svCenterX
                                        val svY = (position.y - screenCenterY) + svCenterY
                                        val nx = (svX / contentWidth.toFloat()).coerceIn(0f, 1f)
                                        val ny = (svY / contentHeight.toFloat()).coerceIn(0f, 1f)
                                        Pair(nx, ny)
                                    }

                                if (!session.recordingStarted) {
                                    onSegmentStart(now)
                                    AppLog.i(TAG, "gesture recording started")
                                }

                                val offsetMs = now - session.segmentStartEpochMs

                                val action =
                                    when {
                                        isPressed && !isAlreadyTracked -> {
                                            session.activePointerIds.add(pointerId)
                                            TouchAction.DOWN
                                        }

                                        isPressed && isAlreadyTracked -> {
                                            TouchAction.MOVE
                                        }

                                        !isPressed && isAlreadyTracked -> {
                                            session.activePointerIds.remove(pointerId)
                                            TouchAction.UP
                                        }

                                        else -> {
                                            continue
                                        }
                                    }

                                session.samples.add(
                                    TouchSample(
                                        offsetMs = offsetMs,
                                        pointerId = pointerId.toInt(),
                                        action = action,
                                        normX = normX,
                                        normY = normY,
                                    ),
                                )

                                TouchInjector.injectTouch(pointerId.toInt(), action, normX, normY)
                                change.consume()
                            }

                            if (session.recordingStarted && (session.activePointerIds.isEmpty() || allPointersReleased)) {
                                onSegmentEnd()
                            }
                        }
                    }
                },
    )
}
