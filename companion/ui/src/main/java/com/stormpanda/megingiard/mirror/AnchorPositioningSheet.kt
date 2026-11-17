package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Rect
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private const val TAG = "AnchorPositioningSheet"

private const val SHEET_MAX_WIDTH_FRACTION = 0.88f
private val SHEET_CORNER_RADIUS = 16.dp
private val SHEET_PADDING = 20.dp
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
private val HEADER_ICON_SIZE = 20.dp
private val INSTRUCTION_BOX_MIN_HEIGHT = 56.dp
private val SPACING_S = 8.dp
private val SPACING_M = 12.dp
private val SPACING_L = 16.dp
private const val SCRIM_ALPHA = 0.55f
private const val INSTRUCTION_BG_ALPHA = 0.5f
private val BORDER_WIDTH = 1.dp
private const val SAMPLE_INTERVAL_MS = 100L
private const val DEFAULT_SCREEN_WIDTH = 1920
private const val DEFAULT_SCREEN_HEIGHT = 1080

/**
 * Secondary display companion overlay rendered on Display 4 while the user is positioning
 * the visual reference anchor on Display 0.
 *
 * Provides a live, magnified hardware crop of whatever is inside the active anchor bounding box
 * over a checkerboard background, dimensions badge, guidance instructions, and a Done button.
 */
@Composable
internal fun AnchorPositioningSheet(
    layoutId: String,
    onDone: () -> Unit,
) {
    AppLog.d(TAG, "AnchorPositioningSheet composed for layoutId=$layoutId")
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val profiles by MacroPadState.profiles.collectAsStateWithLifecycle()
    val layout =
        activeProfile?.layouts?.find { it.id == layoutId }
            ?: profiles.flatMap { it.layouts }.find { it.id == layoutId }
            ?: return

    val currentAnchorState = rememberUpdatedState(layout.visualAnchor)
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var reusableCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        if (ScreenCaptureManager.isFrozen.value) {
            ScreenCaptureManager.setFrozen(false)
        }
        onDispose {
            AppLog.d(TAG, "AnchorPositioningSheet disposed, cleaning up preview bitmaps")
            previewBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            previewBitmap = null
            reusableCropBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            reusableCropBitmap = null
        }
    }

    LaunchedEffect(layoutId) {
        while (isActive) {
            val anchor = currentAnchorState.value
            val left = (anchor.srcX * DEFAULT_SCREEN_WIDTH).roundToInt().coerceIn(0, DEFAULT_SCREEN_WIDTH - 1)
            val top = (anchor.srcY * DEFAULT_SCREEN_HEIGHT).roundToInt().coerceIn(0, DEFAULT_SCREEN_HEIGHT - 1)
            val right = ((anchor.srcX + anchor.srcWidth) * DEFAULT_SCREEN_WIDTH).roundToInt().coerceIn(left + 1, DEFAULT_SCREEN_WIDTH)
            val bottom = ((anchor.srcY + anchor.srcHeight) * DEFAULT_SCREEN_HEIGHT).roundToInt().coerceIn(top + 1, DEFAULT_SCREEN_HEIGHT)
            val rect = Rect(left, top, right, bottom)

            if (rect.width() > 0 && rect.height() > 0) {
                val crop = MirrorFrameSampler.captureCrop(rect, reusableCropBitmap)
                if (crop != null && !crop.isRecycled) {
                    reusableCropBitmap = crop
                    try {
                        val displayCopy = crop.copy(Bitmap.Config.ARGB_8888, false)
                        val old = previewBitmap
                        previewBitmap = displayCopy
                        if (old != null && old != displayCopy && !old.isRecycled) {
                            old.recycle()
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to copy crop bitmap for display", e)
                    }
                }
            }
            delay(SAMPLE_INTERVAL_MS)
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
                // ── Header row: Icon, Title, Dimension Badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterCenterFocus,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(HEADER_ICON_SIZE),
                    )
                    Spacer(Modifier.width(SPACING_M))
                    Text(
                        text = stringResource(R.string.mirror_anchor_positioning_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    val pxW = (layout.visualAnchor.srcWidth * DEFAULT_SCREEN_WIDTH).roundToInt()
                    val pxH = (layout.visualAnchor.srcHeight * DEFAULT_SCREEN_HEIGHT).roundToInt()
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
                            text = "$pxW × $pxH px",
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // ── Live Magnified Preview Box with Checkerboard Transparency Background ──
                val checkerBrush = rememberCheckerboardBrush(colors.surfaceVariant, colors.surface, CHECKER_SIZE)
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
                                drawRect(brush = checkerBrush)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    val currentPreview = previewBitmap
                    if (currentPreview != null && !currentPreview.isRecycled) {
                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = stringResource(R.string.mirror_calibration_preview_desc),
                            modifier = Modifier.fillMaxSize().padding(SPACING_S),
                            contentScale = ContentScale.Fit,
                        )
                    }
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
                        text = stringResource(R.string.mirror_anchor_positioning_instruction),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Single Action Button: Done ──
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
                        text = stringResource(R.string.mirror_anchor_selector_done),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberCheckerboardBrush(
    color1: Color,
    color2: Color,
    cellSize: Dp,
): ShaderBrush {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx().roundToInt().coerceAtLeast(1) }
    return remember(color1, color2, cellPx) {
        val tileSize = cellPx * 2
        val argb1 = color1.toArgb()
        val argb2 = color2.toArgb()
        val pixels = IntArray(tileSize * tileSize)
        for (y in 0 until tileSize) {
            val rowOffset = y * tileSize
            val topHalf = y < cellPx
            for (x in 0 until tileSize) {
                val leftHalf = x < cellPx
                pixels[rowOffset + x] = if (topHalf == leftHalf) argb1 else argb2
            }
        }
        val bitmap =
            Bitmap
                .createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888)
                .apply { prepareToDraw() }
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}
