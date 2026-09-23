package com.stormpanda.megingiard.mirror

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.PulsingRecordingDot
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlin.math.ceil

private const val TAG = "AutoTuneCalibrationSheet"

private const val SHEET_MAX_WIDTH_FRACTION = 0.88f
private val SHEET_CORNER_RADIUS = 16.dp
private val SHEET_PADDING = 20.dp
private val PULSE_DOT_SIZE = 12.dp
private val PREVIEW_HEIGHT = 140.dp
private val PREVIEW_CORNER_RADIUS = 12.dp
private val CHECKER_SIZE = 8.dp
private val PILL_CORNER_RADIUS = 999.dp
private val PILL_HORIZONTAL_PADDING = 12.dp
private val PILL_VERTICAL_PADDING = 4.dp
private val BUTTON_HEIGHT = 44.dp
private val BUTTON_CORNER_RADIUS = 10.dp
private val BUTTON_ICON_SIZE = 18.dp
private val HINT_ICON_SIZE = 26.dp
private val INSTRUCTION_BOX_MIN_HEIGHT = 56.dp
private val SPACING_S = 8.dp
private val SPACING_M = 12.dp
private val SPACING_L = 16.dp
private const val SCRIM_ALPHA = 0.55f
private const val INSTRUCTION_BG_ALPHA = 0.5f
private const val BADGE_BG_ALPHA = 0.75f
private const val DISABLED_CONTENT_ALPHA = 0.5f
private const val PAUSED_SCRIM_ALPHA = 0.50f
private val BORDER_WIDTH = 1.dp
private val LOADING_STROKE_WIDTH = 2.dp
private val LOADING_INDICATOR_SIZE = 18.dp

/**
 * Companion display overlay rendered on Display 4 during active auto-tune calibration.
 *
 * Appears while the primary modal on Display 0 is suspended, giving the user complete
 * freedom to move and rotate the camera in-game without touch or input interference.
 * Displays a live preview of the reference element over a checkerboard background:
 * moving scenery turns transparent in real time while stationary elements remain opaque.
 * Provides user-driven [onFinish] and [onCancel] controls.
 */
@Composable
internal fun AutoTuneCalibrationSheet(
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()

    val calibrationType by VisualAutoTuneCoordinator.calibrationType.collectAsStateWithLifecycle()
    val canFinish by VisualAutoTuneCoordinator.canFinish.collectAsStateWithLifecycle()
    val isPaused by VisualAutoTuneCoordinator.isPaused.collectAsStateWithLifecycle()

    val title =
        when (calibrationType) {
            CalibrationType.LAYOUT_ANCHOR -> stringResource(R.string.mirror_anchor_calibration_title)
            else -> stringResource(R.string.mirror_calibration_title)
        }

    val instruction =
        if (isPaused) {
            stringResource(R.string.mirror_calibration_paused_instruction)
        } else {
            when (calibrationType) {
                CalibrationType.LAYOUT_ANCHOR -> stringResource(R.string.mirror_anchor_calibration_instruction)
                else -> stringResource(R.string.mirror_calibration_instruction_preview)
            }
        }

    BackHandler {
        AppLog.i(TAG, "BackHandler triggered during auto-tune calibration")
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "AutoTuneCalibrationSheet visible on secondary display")
        onDispose {
            AppLog.i(TAG, "AutoTuneCalibrationSheet disposed")
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .blockPointerEvents(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(SHEET_MAX_WIDTH_FRACTION)
                    .clip(RoundedCornerShape(SHEET_CORNER_RADIUS))
                    .background(colors.surface)
                    .border(
                        width = BORDER_WIDTH,
                        brush = bezelBrush,
                        shape = RoundedCornerShape(SHEET_CORNER_RADIUS),
                    ).padding(SHEET_PADDING),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SPACING_L),
            ) {
                // ── Header row: Pulse Dot, Title, Sample Counter Badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPaused) {
                        Box(
                            modifier =
                                Modifier
                                    .size(PULSE_DOT_SIZE)
                                    .clip(CircleShape)
                                    .background(colors.onSurfaceSecondary),
                        )
                    } else {
                        PulsingRecordingDot(
                            color = colors.accent,
                            modifier = Modifier.size(PULSE_DOT_SIZE),
                        )
                    }
                    Spacer(Modifier.width(SPACING_M))
                    Text(
                        text = title,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    SampleCounterBadge()
                }

                // ── Live Preview Box with Checkerboard Transparency Background ──
                CalibrationPreviewBox(bezelBrush = bezelBrush, isPaused = isPaused)

                // ── Instruction Prompt Box ──
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = INSTRUCTION_BOX_MIN_HEIGHT)
                            .clip(RoundedCornerShape(BUTTON_CORNER_RADIUS))
                            .background(colors.surfaceVariant.copy(alpha = INSTRUCTION_BG_ALPHA))
                            .padding(SPACING_M),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_M),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(HINT_ICON_SIZE),
                    )
                    Text(
                        text = instruction,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Action Buttons: Cancel, Pause/Resume, and Finish ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SPACING_M),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(BUTTON_HEIGHT),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(BUTTON_ICON_SIZE),
                            tint = colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.width(SPACING_S))
                        Text(
                            text = stringResource(R.string.mirror_calibration_cancel),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    OutlinedButton(
                        onClick = { VisualAutoTuneCoordinator.togglePause() },
                        modifier = Modifier.weight(1f).height(BUTTON_HEIGHT),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(BUTTON_ICON_SIZE),
                            tint = if (isPaused) colors.accent else colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.width(SPACING_S))
                        Text(
                            text =
                                if (isPaused) {
                                    stringResource(R.string.mirror_calibration_resume)
                                } else {
                                    stringResource(R.string.mirror_calibration_pause)
                                },
                            color = if (isPaused) colors.accent else colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f).height(BUTTON_HEIGHT),
                        enabled = canFinish,
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                                disabledContainerColor = colors.surfaceVariant,
                                disabledContentColor = colors.onSurfaceSecondary.copy(alpha = DISABLED_CONTENT_ALPHA),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(BUTTON_ICON_SIZE),
                        )
                        Spacer(Modifier.width(SPACING_S))
                        Text(
                            text = stringResource(R.string.mirror_calibration_finish),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCounterBadge() {
    val colors = LocalAppColors.current
    val sampleCount by VisualAutoTuneCoordinator.sampleCount.collectAsStateWithLifecycle()
    val canFinish by VisualAutoTuneCoordinator.canFinish.collectAsStateWithLifecycle()

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(PILL_CORNER_RADIUS))
                .background(colors.surfaceVariant)
                .border(
                    width = BORDER_WIDTH,
                    color = colors.divider,
                    shape = RoundedCornerShape(PILL_CORNER_RADIUS),
                ).padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = PILL_VERTICAL_PADDING),
    ) {
        Text(
            text =
                if (sampleCount < MIN_CALIBRATION_FRAMES) {
                    stringResource(R.string.mirror_calibration_sampling)
                } else {
                    stringResource(R.string.mirror_calibration_frames_count, sampleCount)
                },
            color = if (canFinish) colors.accent else colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CalibrationPreviewBox(
    bezelBrush: Brush,
    isPaused: Boolean,
) {
    val colors = LocalAppColors.current
    val previewBitmap by VisualAutoTuneCoordinator.previewBitmap.collectAsStateWithLifecycle()
    val canFinish by VisualAutoTuneCoordinator.canFinish.collectAsStateWithLifecycle()
    val dynamicPercent by VisualAutoTuneCoordinator.dynamicPercent.collectAsStateWithLifecycle()

    val checkerColor1 = colors.surfaceVariant
    val checkerColor2 = colors.surface
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(PREVIEW_CORNER_RADIUS))
                .border(
                    width = BORDER_WIDTH,
                    brush = bezelBrush,
                    shape = RoundedCornerShape(PREVIEW_CORNER_RADIUS),
                ).drawBehind {
                    val checkPx = CHECKER_SIZE.toPx()
                    val cols = ceil(size.width / checkPx).toInt()
                    val rows = ceil(size.height / checkPx).toInt()
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val color = if ((r + c) % 2 == 0) checkerColor1 else checkerColor2
                            drawRect(
                                color = color,
                                topLeft = Offset(c * checkPx, r * checkPx),
                                size = Size(checkPx, checkPx),
                            )
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val currentPreview = previewBitmap
        if (currentPreview != null) {
            Image(
                bitmap = currentPreview.asImageBitmap(),
                contentDescription = stringResource(R.string.mirror_calibration_preview_desc),
                modifier = Modifier.fillMaxSize().padding(SPACING_S),
                contentScale = ContentScale.Fit,
            )

            // Paused state overlay badge
            if (isPaused) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = PAUSED_SCRIM_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SPACING_S),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(PILL_CORNER_RADIUS))
                                .background(colors.surface)
                                .border(
                                    width = BORDER_WIDTH,
                                    brush = bezelBrush,
                                    shape = RoundedCornerShape(PILL_CORNER_RADIUS),
                                ).padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = PILL_VERTICAL_PADDING),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(BUTTON_ICON_SIZE),
                            tint = colors.accent,
                        )
                        Text(
                            text = stringResource(R.string.mirror_calibration_paused_badge),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Dynamic transparency percentage pill
            if (canFinish && dynamicPercent > 0) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(SPACING_S)
                            .clip(RoundedCornerShape(PILL_CORNER_RADIUS))
                            .background(Color.Black.copy(alpha = BADGE_BG_ALPHA))
                            .padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = PILL_VERTICAL_PADDING),
                ) {
                    Text(
                        text = stringResource(R.string.mirror_calibration_dynamic_pct, dynamicPercent),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            if (isPaused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_S),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(BUTTON_ICON_SIZE),
                    )
                    Text(
                        text = stringResource(R.string.mirror_calibration_paused_badge),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_S),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(LOADING_INDICATOR_SIZE),
                        color = colors.accent,
                        strokeWidth = LOADING_STROKE_WIDTH,
                    )
                    Text(
                        text = stringResource(R.string.mirror_calibration_sampling),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
