package com.stormpanda.megingiard.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ScreenshotPreviewOverlay"

// Layout dimensions & ratios
private const val SS_PREVIEW_WIDTH_FRACTION = 0.85f
private val SS_SHADOW_ELEVATION = 12.dp
private val SS_CORNER_RADIUS = 4.dp
private val SS_BORDER_WIDTH = 1.dp
private val SS_IMAGE_BORDER_WIDTH = 1.dp
private val SS_FRAME_PADDING = 12.dp

// Colors
private val SS_BG_COLOR = Color(0xFFFCFBF9) // Clean off-white Polaroid frame
private val SS_BORDER_COLOR = Color(0xFFE5E4E0)
private val SS_IMAGE_BORDER_COLOR = Color(0x22000000)

// Glare animation sweeps
private const val SS_GLARE_START_OFFSET = -0.5f
private const val SS_GLARE_END_OFFSET = 1.5f
private const val SS_GLARE_SWEEP_WIDTH_RATIO = 0.4f
private const val SS_GLARE_ALPHA_EDGE = 0.02f
private const val SS_GLARE_ALPHA_CENTER = 0.45f

// Timers / Durations (in milliseconds)
private const val SS_GLARE_ANIM_DURATION_MS = 1000
private const val SS_PREVIEW_STAY_DURATION_MS = 1800L
private const val SS_FADE_OUT_DURATION_MS = 400

@Composable
fun ScreenshotPreviewOverlay(modifier: Modifier = Modifier) {
    val previewBitmap by ScreenCaptureManager.screenshotPreview.collectAsState()
    var isPreviewVisible by remember { mutableStateOf(false) }
    val sweepOffset = remember { Animatable(SS_GLARE_START_OFFSET) }

    LaunchedEffect(previewBitmap) {
        val bitmap = previewBitmap
        if (bitmap != null) {
            AppLog.d(TAG, "Screenshot preview bitmap updated, triggering animation")
            isPreviewVisible = true
            sweepOffset.snapTo(SS_GLARE_START_OFFSET)
            launch {
                sweepOffset.animateTo(
                    targetValue = SS_GLARE_END_OFFSET,
                    animationSpec =
                        tween(
                            durationMillis = SS_GLARE_ANIM_DURATION_MS,
                            easing = LinearEasing,
                        ),
                )
            }
            delay(SS_PREVIEW_STAY_DURATION_MS)
            isPreviewVisible = false
            delay(SS_FADE_OUT_DURATION_MS.toLong())
            ScreenCaptureManager.clearScreenshotPreview()
        } else {
            isPreviewVisible = false
        }
    }

    val currentBitmap = previewBitmap
    if (currentBitmap != null) {
        AnimatedVisibility(
            visible = isPreviewVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = SS_FADE_OUT_DURATION_MS)),
            exit =
                fadeOut(animationSpec = tween(durationMillis = SS_FADE_OUT_DURATION_MS)) +
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = SS_FADE_OUT_DURATION_MS),
                    ),
            modifier = modifier,
        ) {
            val aspectRatio =
                remember(currentBitmap) {
                    currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(SS_PREVIEW_WIDTH_FRACTION)
                        .shadow(elevation = SS_SHADOW_ELEVATION, shape = RoundedCornerShape(SS_CORNER_RADIUS))
                        .background(SS_BG_COLOR)
                        .border(width = SS_BORDER_WIDTH, color = SS_BORDER_COLOR, shape = RoundedCornerShape(SS_CORNER_RADIUS))
                        .padding(SS_FRAME_PADDING)
                        .drawWithContent {
                            drawContent()
                            val progress = sweepOffset.value
                            if (progress in SS_GLARE_START_OFFSET..SS_GLARE_END_OFFSET) {
                                val width = size.width
                                val height = size.height
                                val startX = width * progress
                                val startY = 0f
                                val endX = startX + width * SS_GLARE_SWEEP_WIDTH_RATIO
                                val endY = height
                                val glossBrush =
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = SS_GLARE_ALPHA_EDGE),
                                                Color.White.copy(alpha = SS_GLARE_ALPHA_CENTER),
                                                Color.White.copy(alpha = SS_GLARE_ALPHA_EDGE),
                                                Color.Transparent,
                                            ),
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY),
                                    )
                                drawRect(brush = glossBrush)
                            }
                        },
            ) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .border(width = SS_IMAGE_BORDER_WIDTH, color = SS_IMAGE_BORDER_COLOR),
                )
            }
        }
    }
}
