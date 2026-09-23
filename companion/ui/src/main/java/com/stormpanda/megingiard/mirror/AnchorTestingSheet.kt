package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
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
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TAG = "AnchorTestingSheet"

private const val SHEET_MAX_WIDTH_FRACTION = 0.88f
private val SHEET_CORNER_RADIUS = 16.dp
private val SHEET_PADDING = 20.dp
private val PREVIEW_HEIGHT = 140.dp
private val PREVIEW_CORNER_RADIUS = 12.dp
private val CHECKER_SIZE = 8.dp
private val PILL_CORNER_RADIUS = 999.dp
private val PILL_HORIZONTAL_PADDING = 12.dp
private val PILL_VERTICAL_PADDING = 4.dp
private val LABEL_CORNER_RADIUS = 6.dp
private val LABEL_HORIZONTAL_PADDING = 8.dp
private val LABEL_VERTICAL_PADDING = 3.dp
private val BUTTON_HEIGHT = 44.dp
private val BUTTON_CORNER_RADIUS = 10.dp
private val BUTTON_ICON_SIZE = 18.dp
private val HINT_ICON_SIZE = 26.dp
private val HEADER_ICON_SIZE = 20.dp
private val STATUS_DOT_SIZE = 8.dp
private val INSTRUCTION_BOX_MIN_HEIGHT = 56.dp
private val SPACING_XS = 4.dp
private val SPACING_S = 8.dp
private val SPACING_M = 12.dp
private val SPACING_L = 16.dp
private const val SCRIM_ALPHA = 0.55f
private const val INSTRUCTION_BG_ALPHA = 0.5f
private const val LABEL_BG_ALPHA = 0.80f
private val BORDER_WIDTH = 1.dp
private val ACTIVE_BORDER_WIDTH = 2.dp

/**
 * Secondary display diagnostic overlay rendered on Display 4 during active anchor testing.
 *
 * Leaves Display 0 completely unobstructed with zero overlays for 120Hz gameplay.
 * Displays side-by-side previews of the target reference anchor signature alongside the live
 * video stream crop, real-time match percentage, and ACTIVE/INACTIVE presence indicator.
 */
@Composable
internal fun AnchorTestingSheet(onDone: () -> Unit) {
    AppLog.d(TAG, "AnchorTestingSheet composed on secondary display")
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()

    val matchRatio by AnchorTestCoordinator.currentMatchRatio.collectAsStateWithLifecycle()
    val isAnchorActive by AnchorTestCoordinator.isAnchorActive.collectAsStateWithLifecycle()
    val referenceBitmap by AnchorTestCoordinator.referenceBitmap.collectAsStateWithLifecycle()
    val liveCropBitmap by AnchorTestCoordinator.liveCropBitmap.collectAsStateWithLifecycle()

    BackHandler {
        AppLog.i(TAG, "BackHandler triggered during anchor testing")
        onDone()
    }

    DisposableEffect(Unit) {
        AppLog.i(TAG, "AnchorTestingSheet visible on secondary display")
        onDispose {
            AppLog.i(TAG, "AnchorTestingSheet disposed")
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
                // ── Header row: Icon, Title, Match %, Status Pill ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Anchor,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(HEADER_ICON_SIZE),
                    )
                    Spacer(Modifier.width(SPACING_M))
                    Text(
                        text = stringResource(R.string.mirror_anchor_test_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    // Match % pill
                    val matchPct = (matchRatio * 100f).roundToInt().coerceIn(0, 100)
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
                            text = stringResource(R.string.mirror_anchor_test_match_pct, matchPct),
                            color = if (isAnchorActive) colors.accent else colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(SPACING_S))

                    // ACTIVE / INACTIVE presence badge
                    val badgeBg = if (isAnchorActive) colors.accent.copy(alpha = 0.20f) else colors.surfaceVariant
                    val badgeBorder = if (isAnchorActive) colors.accent else colors.divider
                    val dotColor = if (isAnchorActive) colors.accent else colors.onSurfaceSecondary
                    val statusText =
                        if (isAnchorActive) {
                            stringResource(R.string.mirror_anchor_test_active)
                        } else {
                            stringResource(R.string.mirror_anchor_test_inactive)
                        }

                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(PILL_CORNER_RADIUS))
                                .background(badgeBg)
                                .border(
                                    width = BORDER_WIDTH,
                                    color = badgeBorder,
                                    shape = RoundedCornerShape(PILL_CORNER_RADIUS),
                                ).padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = PILL_VERTICAL_PADDING),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SPACING_XS),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(STATUS_DOT_SIZE)
                                    .clip(CircleShape)
                                    .background(dotColor),
                        )
                        Text(
                            text = statusText,
                            color = if (isAnchorActive) colors.accent else colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // ── Dual Preview Row: Target Signature vs. Live Screen Crop ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SPACING_M),
                ) {
                    // Left: Target Reference Signature
                    AnchorPreviewCard(
                        title = stringResource(R.string.mirror_anchor_test_target_label),
                        bitmap = referenceBitmap,
                        bezelBrush = bezelBrush,
                        isHighlightBorder = false,
                        modifier = Modifier.weight(1f),
                    )

                    // Right: Current Live Screen Feed
                    AnchorPreviewCard(
                        title = stringResource(R.string.mirror_anchor_test_current_label),
                        bitmap = liveCropBitmap,
                        bezelBrush = bezelBrush,
                        isHighlightBorder = isAnchorActive,
                        modifier = Modifier.weight(1f),
                    )
                }

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
                        text = stringResource(R.string.mirror_anchor_test_instruction),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Action Button: Done ──
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(BUTTON_ICON_SIZE),
                    )
                    Spacer(Modifier.width(SPACING_S))
                    Text(
                        text = stringResource(R.string.mirror_anchor_test_done),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorPreviewCard(
    title: String,
    bitmap: Bitmap?,
    bezelBrush: Brush,
    isHighlightBorder: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val checkerColor1 = colors.surfaceVariant
    val checkerColor2 = colors.surface
    val borderModifier =
        if (isHighlightBorder) {
            Modifier.border(
                width = ACTIVE_BORDER_WIDTH,
                color = colors.accent,
                shape = RoundedCornerShape(PREVIEW_CORNER_RADIUS),
            )
        } else {
            Modifier.border(
                width = BORDER_WIDTH,
                brush = bezelBrush,
                shape = RoundedCornerShape(PREVIEW_CORNER_RADIUS),
            )
        }

    Box(
        modifier =
            modifier
                .height(PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(PREVIEW_CORNER_RADIUS))
                .then(borderModifier)
                .drawBehind {
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
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize().padding(SPACING_S),
                contentScale = ContentScale.Fit,
            )
        }

        // Top-left label badge
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(SPACING_S)
                    .clip(RoundedCornerShape(LABEL_CORNER_RADIUS))
                    .background(colors.surface.copy(alpha = LABEL_BG_ALPHA))
                    .padding(horizontal = LABEL_HORIZONTAL_PADDING, vertical = LABEL_VERTICAL_PADDING),
        ) {
            Text(
                text = title,
                color = if (isHighlightBorder) colors.accent else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
