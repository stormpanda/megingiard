package com.stormpanda.megingiard.macropad

import android.os.SystemClock
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "PhysGamepadRecordSheet"

private const val PR_TIMER_TICK_MS = 50L
private const val PR_STICK_RADAR_SIZE_DP = 96
private const val PR_STICK_THUMB_RADIUS_DP = 7f
private const val PR_RADAR_CROSSHAIR_ALPHA = 0.25f
private const val PR_RADAR_BG_ALPHA = 0.12f
private const val PR_CONTAINER_RADIUS_DP = 20
private const val PR_BUTTON_RADIUS_DP = 12
private const val PR_PILL_RADIUS_DP = 8

private val PR_CONTAINER_SHAPE = RoundedCornerShape(PR_CONTAINER_RADIUS_DP.dp)
private val PR_BUTTON_SHAPE = RoundedCornerShape(PR_BUTTON_RADIUS_DP.dp)
private val PR_PILL_SHAPE = RoundedCornerShape(PR_PILL_RADIUS_DP.dp)
private val PR_BADGE_SHAPE = RoundedCornerShape(6.dp)

private val PR_PULSE_DOT_SIZE = 12.dp
private val PR_SPACING_XS = 4.dp
private val PR_SPACING_S = 8.dp
private val PR_SPACING_M = 10.dp
private val PR_SPACING_L = 14.dp
private val PR_CONTAINER_PADDING = 20.dp
private val PR_BORDER_WIDTH = 1.dp

private fun dpadArrowLabel(
    dirX: Int,
    dirY: Int,
): String =
    when {
        dirX > 0 && dirY < 0 -> "↗"
        dirX > 0 && dirY == 0 -> "→"
        dirX > 0 && dirY > 0 -> "↘"
        dirX == 0 && dirY < 0 -> "↑"
        dirX == 0 && dirY > 0 -> "↓"
        dirX < 0 && dirY < 0 -> "↖"
        dirX < 0 && dirY == 0 -> "←"
        dirX < 0 && dirY > 0 -> "↙"
        else -> "·"
    }

/**
 * Full-screen companion HUD shown on the secondary bottom display while a physical
 * gamepad recording session is active.
 *
 * It provides live controller telemetry (analog stick deflection radar, D-pad compass,
 * active button pills, duration timer, step counter) and touch action buttons to finish
 * or discard the recording and resume the top-screen editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhysicalGamepadRecordingSheet(
    state: GamepadRecordingState,
    swapFaceButtons: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val bezelBrush = rememberBezelBrush()
    val recording = state as? GamepadRecordingState.Recording

    BackHandler {
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "PhysicalGamepadRecordingSheet visible")
        onDispose { AppLog.i(TAG, "PhysicalGamepadRecordingSheet disposed") }
    }

    var currentElapsedMs by remember { mutableLongStateOf(0L) }
    val startEpoch = recording?.startElapsedRealtime ?: 0L

    LaunchedEffect(startEpoch) {
        if (startEpoch > 0L) {
            while (true) {
                currentElapsedMs = (SystemClock.elapsedRealtime() - startEpoch).coerceAtLeast(0L)
                delay(PR_TIMER_TICK_MS)
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .blockPointerEvents(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .clip(PR_CONTAINER_SHAPE)
                    .background(colors.surface)
                    .border(
                        width = 1.dp,
                        brush = bezelBrush,
                        shape = PR_CONTAINER_SHAPE,
                    ).padding(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PR_SPACING_L),
            ) {
                // ── Header row with Pulse Dot, Title, Timer, and Step Count ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingRecordingDot(
                        color = colors.error,
                        modifier = Modifier.size(PR_PULSE_DOT_SIZE),
                    )
                    Spacer(Modifier.width(PR_SPACING_S))
                    Text(
                        text = stringResource(R.string.privd_recording_physical_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    // Live Timer badge
                    Box(
                        modifier =
                            Modifier
                                .clip(PR_PILL_SHAPE)
                                .background(colors.surfaceVariant)
                                .border(width = PR_BORDER_WIDTH, color = colors.divider, shape = PR_PILL_SHAPE)
                                .padding(horizontal = PR_SPACING_M, vertical = PR_SPACING_XS),
                    ) {
                        Text(
                            text = formatElapsedTime(currentElapsedMs),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Live Step Count badge
                    val stepCount = recording?.stepCount ?: 0
                    Box(
                        modifier =
                            Modifier
                                .clip(PR_PILL_SHAPE)
                                .background(colors.surfaceVariant)
                                .border(width = 1.dp, color = colors.divider, shape = PR_PILL_SHAPE)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.privd_recording_physical_actions_count, stepCount),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                AppDivider()

                // ── Telemetry View (Dual Radars + D-Pad + Active Buttons) ─────
                if (recording != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Stick Radar
                        StickRadar(
                            label = stringResource(R.string.privd_recording_physical_stick_l_label),
                            x = recording.leftStickX,
                            y = recording.leftStickY,
                        )

                        // Center: D-Pad Direction + Active Buttons cluster
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // D-Pad direction
                            val dpadArrow = dpadArrowLabel(recording.dpadDirectionX, recording.dpadDirectionY)
                            val isDpadActive = recording.dpadDirectionX != 0 || recording.dpadDirectionY != 0
                            Box(
                                modifier =
                                    Modifier
                                        .clip(PR_PILL_SHAPE)
                                        .background(
                                            if (isDpadActive) colors.actionColorSystem.copy(alpha = 0.2f) else colors.surfaceVariant,
                                        ).border(
                                            width = 1.dp,
                                            color = if (isDpadActive) colors.actionColorSystem else colors.divider,
                                            shape = PR_PILL_SHAPE,
                                        ).padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.privd_recording_physical_dpad_title, dpadArrow),
                                    color = if (isDpadActive) colors.actionColorSystem else colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            // Active Pressed Buttons
                            if (recording.pressedButtons.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    recording.pressedButtons.forEach { code ->
                                        val label = gamepadCodeDisplayLabel(code, swapFaceButtons, context)
                                        if (label.isNotBlank()) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .padding(horizontal = 2.dp)
                                                        .clip(PR_BADGE_SHAPE)
                                                        .background(colors.accent.copy(alpha = 0.25f))
                                                        .border(width = 1.dp, color = colors.accent, shape = PR_BADGE_SHAPE)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = colors.onSurface,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.privd_recording_physical_no_buttons),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // Right Stick Radar
                        StickRadar(
                            label = stringResource(R.string.privd_recording_physical_stick_r_label),
                            x = recording.rightStickX,
                            y = recording.rightStickY,
                        )
                    }

                    AppDivider()
                }

                // ── Informational hint ────────────────────────────────────────
                Text(
                    text = stringResource(R.string.privd_recording_physical_hint),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                // ── Action Buttons (Cancel / Stop & Save) ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = PR_BUTTON_SHAPE,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.privd_recording_physical_cancel),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                        shape = PR_BUTTON_SHAPE,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.privd_recording_physical_stop),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickRadar(
    label: String,
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val accentColor = colors.actionColorGamepad
    val borderColor = colors.divider
    val textColor = colors.onSurface
    val secondaryTextColor = colors.onSurfaceSecondary
    val mag = sqrt(x * x + y * y).coerceIn(0f, 1f)
    val magPercent = (mag * 100).roundToInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Box(
            modifier = Modifier.size(PR_STICK_RADAR_SIZE_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Outer radar circle
                drawCircle(
                    color = borderColor.copy(alpha = PR_RADAR_BG_ALPHA),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = borderColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Crosshairs
                drawLine(
                    color = borderColor.copy(alpha = PR_RADAR_CROSSHAIR_ALPHA),
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = borderColor.copy(alpha = PR_RADAR_CROSSHAIR_ALPHA),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = 1.dp.toPx(),
                )

                // Center deadzone circle
                drawCircle(
                    color = borderColor.copy(alpha = 0.35f),
                    radius = radius * 0.2f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // Thumb Position
                val thumbOffset =
                    Offset(
                        x = center.x + (x.coerceIn(-1f, 1f) * (radius - PR_STICK_THUMB_RADIUS_DP.dp.toPx())),
                        y = center.y + (y.coerceIn(-1f, 1f) * (radius - PR_STICK_THUMB_RADIUS_DP.dp.toPx())),
                    )

                if (mag > 0.05f) {
                    // Vector line from center
                    drawLine(
                        color = accentColor.copy(alpha = 0.6f),
                        start = center,
                        end = thumbOffset,
                        strokeWidth = 2.dp.toPx(),
                    )
                }

                drawCircle(
                    color = if (mag > 0.05f) accentColor else borderColor,
                    radius = PR_STICK_THUMB_RADIUS_DP.dp.toPx(),
                    center = thumbOffset,
                )
            }
        }

        val statusText =
            if (magPercent > 5) {
                "${"%.2f".format(x)}, ${"%.2f".format(y)} ($magPercent%)"
            } else {
                stringResource(R.string.privd_recording_physical_stick_neutral)
            }

        Text(
            text = statusText,
            color = if (magPercent > 5) accentColor else secondaryTextColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (magPercent > 5) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
