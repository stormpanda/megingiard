package com.stormpanda.megingiard.mirror

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.PulsingRecordingDot
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberBezelBrush

private const val TAG = "HudAutoTuneCalibrationSheet"

private const val SHEET_MAX_WIDTH_FRACTION = 0.88f
private val SHEET_CORNER_RADIUS = 16.dp
private val SHEET_PADDING = 20.dp
private val PULSE_DOT_SIZE = 12.dp
private val PROGRESS_HEIGHT = 6.dp
private val PROGRESS_CORNER_RADIUS = 3.dp
private val PILL_CORNER_RADIUS = 999.dp
private val PILL_HORIZONTAL_PADDING = 12.dp
private val PILL_VERTICAL_PADDING = 4.dp
private val CANCEL_BUTTON_HEIGHT = 44.dp
private val CANCEL_BUTTON_CORNER_RADIUS = 10.dp
private val CANCEL_BUTTON_ICON_SIZE = 18.dp
private val HINT_ICON_SIZE = 26.dp
private const val CANCEL_BUTTON_WIDTH_FRACTION = 0.55f
private val SPACING_S = 8.dp
private val SPACING_M = 12.dp
private val SPACING_L = 16.dp
private const val SCRIM_ALPHA = 0.55f
private const val INSTRUCTION_BG_ALPHA = 0.5f
private val BORDER_WIDTH = 1.dp

/**
 * Companion display HUD rendered on Display 4 during active HUD auto-tune calibration.
 *
 * Appears while the primary modal on Display 0 is suspended, giving the user complete
 * freedom to move and rotate the camera in-game without touch or input interference.
 * Provides a live countdown, progress bar, actionable prompt, and cancel option.
 */
@Composable
internal fun HudAutoTuneCalibrationSheet(onCancel: () -> Unit) {
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()

    val progress by HudAutoTuneCoordinator.progress.collectAsStateWithLifecycle()
    val remainingSeconds by HudAutoTuneCoordinator.remainingSeconds.collectAsStateWithLifecycle()

    BackHandler {
        AppLog.i(TAG, "BackHandler triggered during HUD auto-tune calibration")
        onCancel()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "HudAutoTuneCalibrationSheet visible on secondary display")
        onDispose {
            AppLog.i(TAG, "HudAutoTuneCalibrationSheet disposed")
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
                // ── Header row: Pulse Dot, Title, Remaining Timer Badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingRecordingDot(
                        color = colors.accent,
                        modifier = Modifier.size(PULSE_DOT_SIZE),
                    )
                    Spacer(Modifier.width(SPACING_M))
                    Text(
                        text = stringResource(R.string.mirror_hud_calibration_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    // Countdown Badge
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
                            text = stringResource(R.string.mirror_hud_calibration_time_remaining, remainingSeconds),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // ── Progress Bar ──
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(PROGRESS_HEIGHT)
                            .clip(RoundedCornerShape(PROGRESS_CORNER_RADIUS)),
                    color = colors.accent,
                    trackColor = colors.surfaceVariant,
                )

                // ── Instruction Prompt Box ──
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CANCEL_BUTTON_CORNER_RADIUS))
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
                        text = stringResource(R.string.mirror_hud_calibration_instruction),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Action Button: Cancel ──
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(CANCEL_BUTTON_WIDTH_FRACTION).height(CANCEL_BUTTON_HEIGHT),
                    shape = RoundedCornerShape(CANCEL_BUTTON_CORNER_RADIUS),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(CANCEL_BUTTON_ICON_SIZE),
                        tint = colors.onSurfaceSecondary,
                    )
                    Spacer(Modifier.width(SPACING_S))
                    Text(
                        text = stringResource(R.string.mirror_hud_calibration_cancel),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
