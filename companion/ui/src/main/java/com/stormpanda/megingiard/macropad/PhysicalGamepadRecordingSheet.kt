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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
internal fun RecordingElapsedTimerText(
    startEpoch: Long,
    color: Color,
    style: TextStyle,
) {
    var currentElapsedMs by remember(startEpoch) { mutableLongStateOf(0L) }
    LaunchedEffect(startEpoch) {
        if (startEpoch > 0L) {
            while (true) {
                currentElapsedMs = (SystemClock.elapsedRealtime() - startEpoch).coerceAtLeast(0L)
                delay(PR_TIMER_TICK_MS)
            }
        }
    }
    Text(
        text = formatElapsedTime(currentElapsedMs),
        color = color,
        style = style,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
}

private const val PR_STICK_RADAR_SIZE_DP = 96
private const val PR_STICK_THUMB_RADIUS_DP = 7f
private const val PR_RADAR_CROSSHAIR_ALPHA = 0.25f
private const val PR_RADAR_BG_ALPHA = 0.12f
private const val PR_ACTIVE_PILL_BG_ALPHA = 0.2f
private const val PR_TRIGGER_DISPLAY_THRESHOLD = 0.02f
private const val PR_PERCENT_SCALE = 100f
private const val PR_CONTAINER_RADIUS_DP = 20
private const val PR_BUTTON_RADIUS_DP = 12
private const val PR_PILL_RADIUS_DP = 8
private const val PR_BADGE_RADIUS_DP = 6

private val PR_CONTAINER_SHAPE = RoundedCornerShape(PR_CONTAINER_RADIUS_DP.dp)
private val PR_BUTTON_SHAPE = RoundedCornerShape(PR_BUTTON_RADIUS_DP.dp)
private val PR_PILL_SHAPE = RoundedCornerShape(PR_PILL_RADIUS_DP.dp)
private val PR_BADGE_SHAPE = RoundedCornerShape(PR_BADGE_RADIUS_DP.dp)

private val PR_PULSE_DOT_SIZE = 12.dp
private val PR_BADGE_PAD_H = 8.dp
private val PR_BADGE_PAD_V = 2.dp
private val PR_BADGE_MARGIN_H = 2.dp
private val PR_SPACING_XS = 4.dp
private val PR_SPACING_SM = 6.dp
private val PR_SPACING_S = 8.dp
private val PR_SPACING_M = 10.dp
private val PR_SPACING_MD = 12.dp
private val PR_SPACING_L = 14.dp
private val PR_CONTAINER_PADDING = 20.dp
private val PR_BORDER_WIDTH = 1.dp
private val PR_RADAR_OUTER_STROKE = 1.5.dp
private val PR_RADAR_VECTOR_STROKE = 2.dp
private val PR_ACTION_BTN_HEIGHT = 46.dp
private val PR_CLOSE_ICON_SIZE = 18.dp
private val PR_STOP_ICON_SIZE = 20.dp
private val PR_STICK_RADAR_SIZE = PR_STICK_RADAR_SIZE_DP.dp
private val PR_STICK_THUMB_RADIUS = PR_STICK_THUMB_RADIUS_DP.dp
private const val PR_CONTAINER_WIDTH_FRACTION = 0.92f
private const val PR_DEADZONE_RING_ALPHA = 0.35f
private const val PR_DEADZONE_RING_FRACTION = 0.2f
private const val PR_VECTOR_ALPHA = 0.6f
private const val PR_DEFLECTION_THRESHOLD = 0.05f
private const val PR_DEFLECTION_PERCENT_THRESHOLD = 5

@Composable
internal fun LivePhysicalGamepadRecordingSheet(
    swapFaceButtons: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val stateState = PhysicalGamepadRecordingManager.state.collectAsStateWithLifecycle()
    PhysicalGamepadRecordingSheet(
        stateState = stateState,
        swapFaceButtons = swapFaceButtons,
        onStop = onStop,
        onCancel = onCancel,
    )
}

@Composable
internal fun PhysicalGamepadRecordingSheet(
    state: GamepadRecordingState,
    swapFaceButtons: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    PhysicalGamepadRecordingSheet(
        stateState = rememberUpdatedState(state),
        swapFaceButtons = swapFaceButtons,
        onStop = onStop,
        onCancel = onCancel,
    )
}

/**
 * Full-screen companion HUD shown on the secondary bottom display while a physical
 * gamepad recording session is active.
 *
 * It provides live controller telemetry (analog stick deflection radar, D-pad compass,
 * active button pills, duration timer, step counter) and touch action buttons to finish
 * or discard the recording and resume the top-screen editor.
 */
@Composable
internal fun PhysicalGamepadRecordingSheet(
    stateState: State<GamepadRecordingState>,
    swapFaceButtons: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()
    val startEpoch by remember(stateState) {
        derivedStateOf { (stateState.value as? GamepadRecordingState.Recording)?.startElapsedRealtime ?: 0L }
    }
    val hasRecording by remember(stateState) {
        derivedStateOf { stateState.value is GamepadRecordingState.Recording }
    }

    BackHandler {
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "PhysicalGamepadRecordingSheet visible")
        onDispose { AppLog.i(TAG, "PhysicalGamepadRecordingSheet disposed") }
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
                    .fillMaxWidth(PR_CONTAINER_WIDTH_FRACTION)
                    .clip(PR_CONTAINER_SHAPE)
                    .background(colors.surface)
                    .border(
                        width = PR_BORDER_WIDTH,
                        brush = bezelBrush,
                        shape = PR_CONTAINER_SHAPE,
                    ).padding(PR_CONTAINER_PADDING),
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
                        RecordingElapsedTimerText(
                            startEpoch = startEpoch,
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Spacer(Modifier.width(PR_SPACING_S))

                    // Live Step Count badge
                    PhysicalRecordingStepBadge(stateState = stateState)
                }

                AppDivider()

                // ── Telemetry View (Dual Radars + D-Pad + Active Buttons) ─────
                if (hasRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StickRadar(
                            label = stringResource(R.string.privd_recording_physical_stick_l_label),
                            isRightStick = false,
                            stateState = stateState,
                        )

                        PhysicalRecordingCenterCluster(
                            stateState = stateState,
                            swapFaceButtons = swapFaceButtons,
                            modifier = Modifier.weight(1f).padding(horizontal = PR_SPACING_MD),
                        )

                        StickRadar(
                            label = stringResource(R.string.privd_recording_physical_stick_r_label),
                            isRightStick = true,
                            stateState = stateState,
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
                    horizontalArrangement = Arrangement.spacedBy(PR_SPACING_MD),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(PR_ACTION_BTN_HEIGHT),
                        shape = PR_BUTTON_SHAPE,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(PR_CLOSE_ICON_SIZE),
                            tint = colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.width(PR_SPACING_SM))
                        Text(
                            text = stringResource(R.string.privd_recording_physical_cancel),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(PR_ACTION_BTN_HEIGHT),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                        shape = PR_BUTTON_SHAPE,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(PR_STOP_ICON_SIZE),
                        )
                        Spacer(Modifier.width(PR_SPACING_SM))
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
private fun PhysicalRecordingStepBadge(stateState: State<GamepadRecordingState>) {
    val colors = LocalAppColors.current
    val stepCount by remember(stateState) {
        derivedStateOf { (stateState.value as? GamepadRecordingState.Recording)?.stepCount ?: 0 }
    }
    Box(
        modifier =
            Modifier
                .clip(PR_PILL_SHAPE)
                .background(colors.surfaceVariant)
                .border(width = PR_BORDER_WIDTH, color = colors.divider, shape = PR_PILL_SHAPE)
                .padding(horizontal = PR_SPACING_M, vertical = PR_SPACING_XS),
    ) {
        Text(
            text = stringResource(R.string.privd_recording_physical_actions_count, stepCount),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhysicalRecordingCenterCluster(
    stateState: State<GamepadRecordingState>,
    swapFaceButtons: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val dpadXY by remember(stateState) {
        derivedStateOf {
            val rec = stateState.value as? GamepadRecordingState.Recording
            (rec?.dpadDirectionX ?: 0) to (rec?.dpadDirectionY ?: 0)
        }
    }
    val triggerPctPair by remember(stateState) {
        derivedStateOf {
            val rec = stateState.value as? GamepadRecordingState.Recording
            val lt = rec?.leftTrigger ?: 0f
            val rt = rec?.rightTrigger ?: 0f
            val ltPct = if (lt > PR_TRIGGER_DISPLAY_THRESHOLD) (lt * PR_PERCENT_SCALE).roundToInt() else -1
            val rtPct = if (rt > PR_TRIGGER_DISPLAY_THRESHOLD) (rt * PR_PERCENT_SCALE).roundToInt() else -1
            ltPct to rtPct
        }
    }
    val pressedButtons by remember(stateState) {
        derivedStateOf {
            (stateState.value as? GamepadRecordingState.Recording)?.pressedButtons.orEmpty()
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PR_SPACING_S),
    ) {
        val (dpadX, dpadY) = dpadXY
        val dpadArrow = dirArrow(dpadX, dpadY)
        val isDpadActive = dpadX != 0 || dpadY != 0
        Box(
            modifier =
                Modifier
                    .clip(PR_PILL_SHAPE)
                    .background(
                        if (isDpadActive) colors.actionColorSystem.copy(alpha = PR_ACTIVE_PILL_BG_ALPHA) else colors.surfaceVariant,
                    ).border(
                        width = PR_BORDER_WIDTH,
                        color = if (isDpadActive) colors.actionColorSystem else colors.divider,
                        shape = PR_PILL_SHAPE,
                    ).padding(horizontal = PR_SPACING_M, vertical = PR_SPACING_XS),
        ) {
            Text(
                text = stringResource(R.string.privd_recording_physical_dpad_title, dpadArrow),
                color = if (isDpadActive) colors.actionColorSystem else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        val (ltPct, rtPct) = triggerPctPair
        if (ltPct >= 0 || rtPct >= 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PR_SPACING_XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ltPct >= 0) {
                    Box(
                        modifier =
                            Modifier
                                .clip(PR_BADGE_SHAPE)
                                .background(colors.actionColorGamepad.copy(alpha = PR_ACTIVE_PILL_BG_ALPHA))
                                .border(
                                    width = PR_BORDER_WIDTH,
                                    color = colors.actionColorGamepad,
                                    shape = PR_BADGE_SHAPE,
                                ).padding(horizontal = PR_BADGE_PAD_H, vertical = PR_BADGE_PAD_V),
                    ) {
                        Text(
                            text = stringResource(R.string.privd_recording_physical_trigger_l_badge, ltPct),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (rtPct >= 0) {
                    Box(
                        modifier =
                            Modifier
                                .clip(PR_BADGE_SHAPE)
                                .background(colors.actionColorGamepad.copy(alpha = PR_ACTIVE_PILL_BG_ALPHA))
                                .border(
                                    width = PR_BORDER_WIDTH,
                                    color = colors.actionColorGamepad,
                                    shape = PR_BADGE_SHAPE,
                                ).padding(horizontal = PR_BADGE_PAD_H, vertical = PR_BADGE_PAD_V),
                    ) {
                        Text(
                            text = stringResource(R.string.privd_recording_physical_trigger_r_badge, rtPct),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (pressedButtons.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(PR_SPACING_XS),
            ) {
                pressedButtons.forEach { code ->
                    val label = gamepadCodeDisplayLabel(code, swapFaceButtons, context)
                    if (label.isNotBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = PR_BADGE_MARGIN_H)
                                    .clip(PR_BADGE_SHAPE)
                                    .background(colors.accent.copy(alpha = PR_RADAR_CROSSHAIR_ALPHA))
                                    .border(width = PR_BORDER_WIDTH, color = colors.accent, shape = PR_BADGE_SHAPE)
                                    .padding(horizontal = PR_BADGE_PAD_H, vertical = PR_BADGE_PAD_V),
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
}

@Composable
private fun StickRadar(
    label: String,
    isRightStick: Boolean,
    stateState: State<GamepadRecordingState>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val accentColor = colors.actionColorGamepad
    val borderColor = colors.divider
    val textColor = colors.onSurface

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PR_SPACING_XS),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Box(
            modifier = Modifier.size(PR_STICK_RADAR_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rec = stateState.value as? GamepadRecordingState.Recording
                val x = if (isRightStick) (rec?.rightStickX ?: 0f) else (rec?.leftStickX ?: 0f)
                val y = if (isRightStick) (rec?.rightStickY ?: 0f) else (rec?.leftStickY ?: 0f)
                val mag = sqrt(x * x + y * y).coerceIn(0f, 1f)
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = borderColor.copy(alpha = PR_RADAR_BG_ALPHA),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = borderColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = PR_RADAR_OUTER_STROKE.toPx()),
                )

                drawLine(
                    color = borderColor.copy(alpha = PR_RADAR_CROSSHAIR_ALPHA),
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = PR_BORDER_WIDTH.toPx(),
                )
                drawLine(
                    color = borderColor.copy(alpha = PR_RADAR_CROSSHAIR_ALPHA),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = PR_BORDER_WIDTH.toPx(),
                )

                drawCircle(
                    color = borderColor.copy(alpha = PR_DEADZONE_RING_ALPHA),
                    radius = radius * PR_DEADZONE_RING_FRACTION,
                    center = center,
                    style = Stroke(width = PR_BORDER_WIDTH.toPx()),
                )

                val thumbRadiusPx = PR_STICK_THUMB_RADIUS.toPx()
                val thumbOffset =
                    Offset(
                        x = center.x + (x.coerceIn(-1f, 1f) * (radius - thumbRadiusPx)),
                        y = center.y + (y.coerceIn(-1f, 1f) * (radius - thumbRadiusPx)),
                    )

                if (mag > PR_DEFLECTION_THRESHOLD) {
                    drawLine(
                        color = accentColor.copy(alpha = PR_VECTOR_ALPHA),
                        start = center,
                        end = thumbOffset,
                        strokeWidth = PR_RADAR_VECTOR_STROKE.toPx(),
                    )
                }

                drawCircle(
                    color = if (mag > PR_DEFLECTION_THRESHOLD) accentColor else borderColor,
                    radius = thumbRadiusPx,
                    center = thumbOffset,
                )
            }
        }

        StickRadarStatusText(
            isRightStick = isRightStick,
            stateState = stateState,
        )
    }
}

@Composable
private fun StickRadarStatusText(
    isRightStick: Boolean,
    stateState: State<GamepadRecordingState>,
) {
    val colors = LocalAppColors.current
    val stickXY by remember(isRightStick, stateState) {
        derivedStateOf {
            val rec = stateState.value as? GamepadRecordingState.Recording
            if (isRightStick) {
                (rec?.rightStickX ?: 0f) to (rec?.rightStickY ?: 0f)
            } else {
                (rec?.leftStickX ?: 0f) to (rec?.leftStickY ?: 0f)
            }
        }
    }
    val (x, y) = stickXY
    val mag = sqrt(x * x + y * y).coerceIn(0f, 1f)
    val magPercent = (mag * PR_PERCENT_SCALE).roundToInt()
    val isDeflected = magPercent > PR_DEFLECTION_PERCENT_THRESHOLD
    val statusText =
        if (isDeflected) {
            "${"%.2f".format(x)}, ${"%.2f".format(y)} ($magPercent%)"
        } else {
            stringResource(R.string.privd_recording_physical_stick_neutral)
        }
    Text(
        text = statusText,
        color = if (isDeflected) colors.actionColorGamepad else colors.onSurfaceSecondary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (isDeflected) FontWeight.Bold else FontWeight.Normal,
    )
}
