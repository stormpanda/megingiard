package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlinx.coroutines.delay

private const val TAG = "TouchRecordingSheet"

private const val TRS_PULSE_ANIM_MS = 900
private const val TRS_TIMER_TICK_MS = 50L
private const val TRS_RADAR_ASPECT_RATIO = 16f / 9f
private const val TRS_RADAR_CROSSHAIR_ALPHA = 0.25f
private const val TRS_RADAR_BG_ALPHA = 0.12f
private const val TRS_CONTAINER_WIDTH_FRACTION = 0.92f
private val TRS_CONTAINER_RADIUS = 20.dp
private val TRS_BUTTON_RADIUS = 12.dp
private val TRS_PILL_RADIUS = 8.dp
private val TRS_CONTAINER_PADDING = 20.dp
private val TRS_COLUMN_SPACING = 14.dp
private val TRS_DOT_SIZE = 12.dp
private val TRS_SPACING_XS = 4.dp
private val TRS_SPACING_S = 6.dp
private val TRS_SPACING_M = 8.dp
private val TRS_SPACING_L = 10.dp
private val TRS_SPACING_XL = 12.dp
private val TRS_ICON_SIZE = 16.dp
private const val TRS_TOUCH_INDICATOR_RADIUS_DP = 8f
private const val TRS_TOUCH_PULSE_RADIUS_DP = 18f
private const val TRS_TRAIL_STROKE_WIDTH_DP = 3f

private fun formatElapsedTime(elapsedMs: Long): String {
    val totalSec = elapsedMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val tenth = (elapsedMs % 1000) / 100
    return "%02d:%02d.%01d".format(min, sec, tenth)
}

/**
 * Companion HUD rendered on Display 4 (Secondary Display) during a touch macro recording session.
 *
 * Displays live touch telemetry, a 16:9 screen radar visualizing top-screen touch positions and
 * active gesture trails in real time, step counter, session timer, and Cancel / Stop action buttons.
 */
@Composable
internal fun TouchRecordingSheet(
    state: TouchRecordingState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()
    val recording = state as? TouchRecordingState.Recording

    BackHandler {
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "TouchRecordingSheet mounted on Display 4")
        onDispose { AppLog.i(TAG, "TouchRecordingSheet unmounted from Display 4") }
    }

    var currentElapsedMs by remember { mutableLongStateOf(0L) }
    val startEpoch = recording?.startElapsedRealtime ?: 0L

    LaunchedEffect(startEpoch) {
        if (startEpoch > 0L) {
            while (true) {
                currentElapsedMs = (SystemClock.elapsedRealtime() - startEpoch).coerceAtLeast(0L)
                delay(TRS_TIMER_TICK_MS)
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "touchRecordingPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = TRS_PULSE_ANIM_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "touchRecordingDotPulse",
    )

    val mode = recording?.mode ?: TouchRecordingMode.TAP

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                onCancel()
                                true
                            }

                            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_A -> {
                                if (mode == TouchRecordingMode.GESTURE) {
                                    onStop()
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> {
                                false
                            }
                        }
                    } else {
                        false
                    }
                }.blockPointerEvents(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(TRS_CONTAINER_WIDTH_FRACTION)
                    .clip(RoundedCornerShape(TRS_CONTAINER_RADIUS))
                    .background(colors.surface)
                    .border(
                        width = 1.dp,
                        brush = bezelBrush,
                        shape = RoundedCornerShape(TRS_CONTAINER_RADIUS),
                    ).padding(TRS_CONTAINER_PADDING),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TRS_COLUMN_SPACING),
            ) {
                // ── Header Row ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(TRS_DOT_SIZE)
                                .clip(CircleShape)
                                .background(colors.error.copy(alpha = pulseAlpha)),
                    )
                    Spacer(Modifier.width(TRS_SPACING_M))
                    Text(
                        text = stringResource(R.string.touch_recording_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    // Live Timer badge
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(TRS_PILL_RADIUS))
                                .background(colors.surfaceVariant)
                                .border(width = 1.dp, color = colors.divider, shape = RoundedCornerShape(TRS_PILL_RADIUS))
                                .padding(horizontal = TRS_SPACING_L, vertical = TRS_SPACING_XS),
                    ) {
                        Text(
                            text = formatElapsedTime(currentElapsedMs),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(TRS_SPACING_M))

                    // Live Step Count badge
                    val badgeText =
                        if (mode == TouchRecordingMode.TAP) {
                            stringResource(R.string.touch_recording_actions_tap, if (recording?.liveNormX != null) 1 else 0)
                        } else {
                            val count = recording?.recordedGestureCount ?: 0
                            val sampleCount = recording?.totalRecordedSampleCount ?: 0
                            stringResource(R.string.touch_recording_actions_gestures, count, sampleCount)
                        }

                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(TRS_PILL_RADIUS))
                                .background(colors.surfaceVariant)
                                .border(width = 1.dp, color = colors.divider, shape = RoundedCornerShape(TRS_PILL_RADIUS))
                                .padding(horizontal = TRS_SPACING_L, vertical = TRS_SPACING_XS),
                    ) {
                        Text(
                            text = badgeText,
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                AppDivider()

                // ── Mode & Instruction Banner ───────────────────────────────
                val modeLabel =
                    if (mode == TouchRecordingMode.TAP) {
                        stringResource(R.string.touch_recording_mode_tap)
                    } else {
                        stringResource(R.string.touch_recording_mode_gesture)
                    }
                val hintLabel =
                    if (mode == TouchRecordingMode.TAP) {
                        stringResource(R.string.touch_recording_hint_tap)
                    } else {
                        stringResource(R.string.touch_recording_hint_gesture)
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TRS_SPACING_M),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(TRS_PILL_RADIUS))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .border(width = 1.dp, color = colors.accent, shape = RoundedCornerShape(TRS_PILL_RADIUS))
                                .padding(horizontal = TRS_SPACING_L, vertical = TRS_SPACING_XS),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TRS_SPACING_XS),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(TRS_ICON_SIZE),
                            )
                            Text(
                                text = modeLabel,
                                color = colors.accent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = hintLabel,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── 16:9 Screen Touch Radar ─────────────────────────────────
                TouchScreenRadar(
                    recording = recording,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(TRS_RADAR_ASPECT_RATIO),
                )

                // ── Coords Readout ──────────────────────────────────────────
                val liveX = recording?.liveNormX
                val liveY = recording?.liveNormY
                val coordsText =
                    if (liveX != null && liveY != null) {
                        val pctX = liveX * 100f
                        val pctY = liveY * 100f
                        stringResource(R.string.touch_recording_radar_coords, pctX, pctY)
                    } else {
                        stringResource(R.string.touch_recording_radar_waiting)
                    }

                Text(
                    text = coordsText,
                    color = if (liveX != null) colors.accent else colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                AppDivider()

                // ── Footer Actions ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(TRS_BUTTON_RADIUS),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(TRS_ICON_SIZE),
                        )
                        Spacer(Modifier.width(TRS_SPACING_S))
                        Text(
                            text = stringResource(R.string.privd_recording_physical_cancel),
                            color = colors.onSurfaceSecondary,
                        )
                    }

                    if (mode == TouchRecordingMode.GESTURE) {
                        Spacer(Modifier.width(TRS_SPACING_XL))
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                            shape = RoundedCornerShape(TRS_BUTTON_RADIUS),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(TRS_ICON_SIZE),
                            )
                            Spacer(Modifier.width(TRS_SPACING_S))
                            Text(
                                text = stringResource(R.string.privd_recording_physical_stop),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchScreenRadar(
    recording: TouchRecordingState.Recording?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    val touchIndicatorRadiusPx = with(density) { TRS_TOUCH_INDICATOR_RADIUS_DP.dp.toPx() }
    val touchPulseRadiusPx = with(density) { TRS_TOUCH_PULSE_RADIUS_DP.dp.toPx() }
    val trailStrokeWidthPx = with(density) { TRS_TRAIL_STROKE_WIDTH_DP.dp.toPx() }

    val accentColor = colors.accent
    val dividerColor = colors.divider
    val surfaceVariant = colors.surfaceVariant
    val actionColor = colors.actionColorSystem

    val pulseTransition = rememberInfiniteTransition(label = "radarTouchPulse")
    val livePulseScale by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "touchRadarLiveScale",
    )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(TRS_PILL_RADIUS))
                .background(surfaceVariant.copy(alpha = TRS_RADAR_BG_ALPHA))
                .border(1.dp, dividerColor, RoundedCornerShape(TRS_PILL_RADIUS)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Center crosshair grid
            val midX = width / 2f
            val midY = height / 2f
            drawLine(
                color = dividerColor.copy(alpha = TRS_RADAR_CROSSHAIR_ALPHA),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1f,
            )
            drawLine(
                color = dividerColor.copy(alpha = TRS_RADAR_CROSSHAIR_ALPHA),
                start = Offset(midX, 0f),
                end = Offset(midX, height),
                strokeWidth = 1f,
            )

            // Active trail points (drawn connected)
            val trail = recording?.activeTrailPoints ?: emptyList()
            if (trail.size >= 2) {
                val path = Path()
                trail.forEachIndexed { index, (nx, ny) ->
                    val px = nx * width
                    val py = ny * height
                    if (index == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }
                drawPath(
                    path = path,
                    color = actionColor,
                    style = Stroke(width = trailStrokeWidthPx),
                )
            }

            // Live touch pointer indicator
            val liveX = recording?.liveNormX
            val liveY = recording?.liveNormY
            if (liveX != null && liveY != null) {
                val touchPx = liveX * width
                val touchPy = liveY * height

                if (recording.isTouchDown) {
                    drawCircle(
                        color = actionColor.copy(alpha = 0.35f),
                        radius = touchPulseRadiusPx * livePulseScale,
                        center = Offset(touchPx, touchPy),
                    )
                }

                drawCircle(
                    color = accentColor,
                    radius = touchIndicatorRadiusPx,
                    center = Offset(touchPx, touchPy),
                )
                drawCircle(
                    color = Color.White,
                    radius = touchIndicatorRadiusPx * 0.45f,
                    center = Offset(touchPx, touchPy),
                )
            }
        }
    }
}
