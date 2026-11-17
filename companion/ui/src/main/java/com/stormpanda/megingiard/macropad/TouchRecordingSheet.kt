package com.stormpanda.megingiard.macropad

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppPalettes
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.isBackKeyDown
import com.stormpanda.megingiard.ui.rememberBezelBrush

private const val TAG = "TouchRecordingSheet"

private const val TRS_RADAR_ASPECT_RATIO = 16f / 9f
private const val TRS_RADAR_CROSSHAIR_ALPHA = 0.25f
private const val TRS_RADAR_BG_ALPHA = 0.12f
private const val TRS_CONTAINER_MAX_WIDTH_DP = 560
private const val TRS_PULSE_SCALE_MIN = 0.8f
private const val TRS_PULSE_SCALE_MAX = 1.35f
private const val TRS_PULSE_DURATION_MS = 600
private const val TRS_TRAIL_HEAD_ALPHA = 0.85f

private val TRS_CONTAINER_RADIUS = 20.dp
private val TRS_BUTTON_RADIUS = 12.dp
private val TRS_PILL_RADIUS = 8.dp

private val TRS_CONTAINER_SHAPE = RoundedCornerShape(TRS_CONTAINER_RADIUS)
private val TRS_BUTTON_SHAPE = RoundedCornerShape(TRS_BUTTON_RADIUS)
private val TRS_PILL_SHAPE = RoundedCornerShape(TRS_PILL_RADIUS)

private val TRS_CONTAINER_PADDING = 12.dp
private val TRS_SCREEN_PADDING_H = 16.dp
private val TRS_SCREEN_PADDING_V = 8.dp
private val TRS_COLUMN_SPACING = 6.dp
private val TRS_BADGE_HEIGHT = 28.dp
private val TRS_RADAR_HEIGHT = 245.dp
private val TRS_BUTTON_HEIGHT = 38.dp
private val TRS_BUTTON_PADDING_H = 16.dp
private val TRS_DOT_SIZE = 10.dp
private val TRS_SPACING_XS = 4.dp
private val TRS_SPACING_S = 6.dp
private val TRS_SPACING_M = 8.dp
private val TRS_SPACING_L = 10.dp
private val TRS_SPACING_XL = 12.dp
private val TRS_ICON_SIZE = 16.dp
private val TRS_BORDER_WIDTH = 1.dp
private val TRS_ZERO_DP = 0.dp
private val TRS_CONTAINER_MAX_WIDTH = TRS_CONTAINER_MAX_WIDTH_DP.dp
private val TRS_TOUCH_INDICATOR_RADIUS = TRS_TOUCH_INDICATOR_RADIUS_DP.dp
private val TRS_TOUCH_PULSE_RADIUS = TRS_TOUCH_PULSE_RADIUS_DP.dp
private val TRS_TRAIL_STROKE_WIDTH = TRS_TRAIL_STROKE_WIDTH_DP.dp
private const val TRS_TOUCH_INDICATOR_RADIUS_DP = 10f
private const val TRS_TOUCH_PULSE_RADIUS_DP = 22f
private const val TRS_TRAIL_STROKE_WIDTH_DP = 3.5f
private const val TRS_MODE_BADGE_ALPHA = 0.15f
private const val TRS_PULSE_FILL_ALPHA = 0.35f
private const val TRS_INNER_DOT_FRACTION = 0.45f
private const val TRS_PERCENT_SCALE = 100f

private val TRS_POINTER_PALETTE = AppPalettes.touchPointerSlots

private fun pointerColor(
    slot: Int,
    accentColor: Color,
): Color =
    if (slot == 0) {
        accentColor
    } else {
        TRS_POINTER_PALETTE[(slot - 1) % TRS_POINTER_PALETTE.size]
    }

@Composable
internal fun LiveTouchRecordingSheet(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateState = TouchRecordingManager.state.collectAsStateWithLifecycle()
    TouchRecordingSheet(
        stateState = stateState,
        onStop = onStop,
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
internal fun TouchRecordingSheet(
    state: TouchRecordingState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TouchRecordingSheet(
        stateState = rememberUpdatedState(state),
        onStop = onStop,
        onCancel = onCancel,
        modifier = modifier,
    )
}

/**
 * Companion HUD rendered on Display 4 (Secondary Display) during a touch macro recording session.
 *
 * Displays live touch tracking state, a proportional 16:9 screen radar visualizing top-screen touch positions
 * and active gesture trails in real time, step counter, session timer, and Cancel / Stop action buttons.
 */
@Composable
internal fun TouchRecordingSheet(
    stateState: State<TouchRecordingState>,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()
    val sessionMeta by remember(stateState) {
        derivedStateOf {
            val rec = stateState.value as? TouchRecordingState.Recording
            (rec?.startElapsedRealtime ?: 0L) to (rec?.mode ?: TouchRecordingMode.TAP)
        }
    }
    val (startEpoch, mode) = sessionMeta

    BackHandler {
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "TouchRecordingSheet mounted on Display 4")
        onDispose { AppLog.i(TAG, "TouchRecordingSheet unmounted from Display 4") }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.isBackKeyDown()) {
                        onCancel()
                        true
                    } else if (keyEvent.type == KeyEventType.KeyDown &&
                        (
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A
                        )
                    ) {
                        if (mode == TouchRecordingMode.GESTURE) {
                            onStop()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Scrim blocking touch pass-through to background macro canvas
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .blockPointerEvents(),
        )

        // Floating HUD modal container
        Box(
            modifier =
                Modifier
                    .widthIn(max = TRS_CONTAINER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = TRS_SCREEN_PADDING_H, vertical = TRS_SCREEN_PADDING_V)
                    .clip(TRS_CONTAINER_SHAPE)
                    .background(colors.surface)
                    .border(
                        width = TRS_BORDER_WIDTH,
                        brush = bezelBrush,
                        shape = TRS_CONTAINER_SHAPE,
                    ).padding(TRS_CONTAINER_PADDING),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TRS_COLUMN_SPACING),
            ) {
                // ── Header Row (Clean Title with Breathing Room) ────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingRecordingDot(
                        color = colors.error,
                        modifier = Modifier.size(TRS_DOT_SIZE),
                    )
                    Spacer(Modifier.width(TRS_SPACING_M))
                    Text(
                        text = stringResource(R.string.touch_recording_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                }

                AppDivider()

                // ── Status Badges Row (Above Radar, Uniform Height & Margins) ─
                val modeLabel =
                    when (mode) {
                        TouchRecordingMode.TAP -> stringResource(R.string.touch_recording_mode_tap)
                        TouchRecordingMode.SINGLE_GESTURE -> stringResource(R.string.touch_recording_mode_single_gesture)
                        TouchRecordingMode.GESTURE -> stringResource(R.string.touch_recording_mode_gesture)
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TRS_SPACING_M, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mode Badge
                    Box(
                        modifier =
                            Modifier
                                .height(TRS_BADGE_HEIGHT)
                                .clip(TRS_PILL_SHAPE)
                                .background(colors.accent.copy(alpha = TRS_MODE_BADGE_ALPHA))
                                .border(width = TRS_BORDER_WIDTH, color = colors.accent, shape = TRS_PILL_SHAPE)
                                .padding(horizontal = TRS_SPACING_L),
                        contentAlignment = Alignment.Center,
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Live Timer Badge
                    Box(
                        modifier =
                            Modifier
                                .height(TRS_BADGE_HEIGHT)
                                .clip(TRS_PILL_SHAPE)
                                .background(colors.surfaceVariant)
                                .border(width = TRS_BORDER_WIDTH, color = colors.divider, shape = TRS_PILL_SHAPE)
                                .padding(horizontal = TRS_SPACING_L),
                        contentAlignment = Alignment.Center,
                    ) {
                        RecordingElapsedTimerText(
                            startEpoch = startEpoch,
                            color = colors.accent,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    // Live Step Count Badge
                    TouchRecordingStepBadge(
                        stateState = stateState,
                        mode = mode,
                    )
                }

                AppDivider()

                // ── Instruction Hint ────────────────────────────────────────
                val hintLabel =
                    when (mode) {
                        TouchRecordingMode.TAP -> stringResource(R.string.touch_recording_hint_tap)
                        TouchRecordingMode.SINGLE_GESTURE -> stringResource(R.string.touch_recording_hint_single_gesture)
                        TouchRecordingMode.GESTURE -> stringResource(R.string.touch_recording_hint_gesture)
                    }

                Text(
                    text = hintLabel,
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── 16:9 Screen Touch Radar ─────────────────────────────────
                TouchScreenRadar(
                    stateState = stateState,
                    modifier =
                        Modifier
                            .height(TRS_RADAR_HEIGHT)
                            .aspectRatio(TRS_RADAR_ASPECT_RATIO),
                )

                // ── Coords Readout ──────────────────────────────────────────
                TouchRecordingCoordsReadout(stateState = stateState)

                AppDivider()

                // ── Footer Actions ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = TRS_BUTTON_SHAPE,
                        modifier = Modifier.height(TRS_BUTTON_HEIGHT),
                        contentPadding = PaddingValues(horizontal = TRS_BUTTON_PADDING_H, vertical = TRS_ZERO_DP),
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
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (mode == TouchRecordingMode.GESTURE) {
                        Spacer(Modifier.width(TRS_SPACING_XL))
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                            shape = TRS_BUTTON_SHAPE,
                            modifier = Modifier.height(TRS_BUTTON_HEIGHT),
                            contentPadding = PaddingValues(horizontal = TRS_BUTTON_PADDING_H, vertical = TRS_ZERO_DP),
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
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchRecordingStepBadge(
    stateState: State<TouchRecordingState>,
    mode: TouchRecordingMode,
) {
    val colors = LocalAppColors.current
    val counts by remember(stateState, mode) {
        derivedStateOf {
            val recording = stateState.value as? TouchRecordingState.Recording
            when (mode) {
                TouchRecordingMode.TAP -> (if (recording?.liveNormX != null) 1 else 0) to 0
                TouchRecordingMode.SINGLE_GESTURE -> (recording?.activePointersCount ?: 0) to (recording?.activeTrailPoints?.size ?: 0)
                TouchRecordingMode.GESTURE -> (recording?.recordedGestureCount ?: 0) to (recording?.totalRecordedSampleCount ?: 0)
            }
        }
    }
    val (primaryCount, secondaryCount) = counts
    val badgeText =
        when (mode) {
            TouchRecordingMode.TAP -> {
                stringResource(R.string.touch_recording_actions_tap, primaryCount)
            }

            TouchRecordingMode.SINGLE_GESTURE -> {
                stringResource(
                    R.string.touch_recording_actions_single_gesture,
                    primaryCount,
                    secondaryCount,
                )
            }

            TouchRecordingMode.GESTURE -> {
                stringResource(R.string.touch_recording_actions_gestures, primaryCount, secondaryCount)
            }
        }
    Box(
        modifier =
            Modifier
                .height(TRS_BADGE_HEIGHT)
                .clip(TRS_PILL_SHAPE)
                .background(colors.surfaceVariant)
                .border(width = TRS_BORDER_WIDTH, color = colors.divider, shape = TRS_PILL_SHAPE)
                .padding(horizontal = TRS_SPACING_L),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeText,
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TouchRecordingCoordsReadout(stateState: State<TouchRecordingState>) {
    val colors = LocalAppColors.current
    val recording = stateState.value as? TouchRecordingState.Recording
    val activePointers = recording?.activePointers ?: emptyList()
    val fallbackX = recording?.liveNormX
    val fallbackY = recording?.liveNormY
    val coordsText =
        when {
            activePointers.size > 1 -> {
                activePointers.joinToString(separator = "  •  ") { pointer ->
                    val pName = "P${pointer.slot + 1}"
                    val px = pointer.normX * TRS_PERCENT_SCALE
                    val py = pointer.normY * TRS_PERCENT_SCALE
                    "$pName: %1$.1f%%, %2$.1f%%".format(px, py)
                }
            }

            activePointers.size == 1 -> {
                val p = activePointers.first()
                val pctX = p.normX * TRS_PERCENT_SCALE
                val pctY = p.normY * TRS_PERCENT_SCALE
                stringResource(R.string.touch_recording_radar_coords, pctX, pctY)
            }

            fallbackX != null && fallbackY != null -> {
                val pctX = fallbackX * TRS_PERCENT_SCALE
                val pctY = fallbackY * TRS_PERCENT_SCALE
                stringResource(R.string.touch_recording_radar_coords, pctX, pctY)
            }

            else -> {
                stringResource(R.string.touch_recording_radar_waiting)
            }
        }

    val isTracking = activePointers.isNotEmpty() || fallbackX != null
    Text(
        text = coordsText,
        color = if (isTracking) colors.accent else colors.onSurfaceSecondary,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TouchScreenRadar(
    stateState: State<TouchRecordingState>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    val touchIndicatorRadiusPx = with(density) { TRS_TOUCH_INDICATOR_RADIUS.toPx() }
    val touchPulseRadiusPx = with(density) { TRS_TOUCH_PULSE_RADIUS.toPx() }
    val trailStrokeWidthPx = with(density) { TRS_TRAIL_STROKE_WIDTH.toPx() }

    val accentColor = colors.accent
    val dividerColor = colors.divider
    val surfaceVariant = colors.surfaceVariant

    val hasActiveTouch by remember(stateState) {
        derivedStateOf {
            val rec = stateState.value as? TouchRecordingState.Recording
            rec?.activePointers?.isNotEmpty() == true || rec?.isTouchDown == true
        }
    }
    val pulseTransition =
        if (hasActiveTouch) {
            rememberInfiniteTransition(label = "radarTouchPulse")
        } else {
            null
        }
    val livePulseScaleState =
        pulseTransition?.animateFloat(
            initialValue = TRS_PULSE_SCALE_MIN,
            targetValue = TRS_PULSE_SCALE_MAX,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = TRS_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "touchRadarLiveScale",
        )

    val trailPath = remember { Path() }

    Box(
        modifier =
            modifier
                .clip(TRS_PILL_SHAPE)
                .background(surfaceVariant.copy(alpha = TRS_RADAR_BG_ALPHA))
                .border(TRS_BORDER_WIDTH, dividerColor, TRS_PILL_SHAPE),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val recording = stateState.value as? TouchRecordingState.Recording
            val width = size.width
            val height = size.height
            val livePulseScale = livePulseScaleState?.value ?: 1.0f

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

            val activePointers = recording?.activePointers ?: emptyList()
            if (activePointers.isNotEmpty()) {
                for (pointer in activePointers) {
                    val color = pointerColor(pointer.slot, accentColor)
                    drawNormalizedTrail(trailPath, pointer.trail, width, height, color, trailStrokeWidthPx)
                    drawPointerDot(
                        center = Offset(pointer.normX * width, pointer.normY * height),
                        color = color,
                        pulseRadius = touchPulseRadiusPx * livePulseScale,
                        indicatorRadius = touchIndicatorRadiusPx,
                    )
                }
            } else {
                drawNormalizedTrail(trailPath, recording?.activeTrailPoints ?: emptyList(), width, height, accentColor, trailStrokeWidthPx)
                val liveX = recording?.liveNormX
                val liveY = recording?.liveNormY
                if (liveX != null && liveY != null) {
                    drawPointerDot(
                        center = Offset(liveX * width, liveY * height),
                        color = accentColor,
                        pulseRadius = if (recording.isTouchDown) touchPulseRadiusPx * livePulseScale else null,
                        indicatorRadius = touchIndicatorRadiusPx,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawNormalizedTrail(
    trailPath: Path,
    trail: List<Pair<Float, Float>>,
    width: Float,
    height: Float,
    color: Color,
    strokeWidthPx: Float,
) {
    trailPath.rewind()
    if (trail.size < 2) return
    trail.forEachIndexed { index, (nx, ny) ->
        val px = nx * width
        val py = ny * height
        if (index == 0) trailPath.moveTo(px, py) else trailPath.lineTo(px, py)
    }
    drawPath(path = trailPath, color = color.copy(alpha = TRS_TRAIL_HEAD_ALPHA), style = Stroke(width = strokeWidthPx))
}

private fun DrawScope.drawPointerDot(
    center: Offset,
    color: Color,
    pulseRadius: Float?,
    indicatorRadius: Float,
) {
    if (pulseRadius != null) {
        drawCircle(color = color.copy(alpha = TRS_PULSE_FILL_ALPHA), radius = pulseRadius, center = center)
    }
    drawCircle(color = color, radius = indicatorRadius, center = center)
    drawCircle(color = Color.White, radius = indicatorRadius * TRS_INNER_DOT_FRACTION, center = center)
}
