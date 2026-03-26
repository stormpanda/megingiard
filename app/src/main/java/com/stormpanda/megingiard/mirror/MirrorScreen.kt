package com.stormpanda.megingiard.mirror

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.rememberAutoHideState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val CONTROL_BUTTON_SIZE = 72.dp
private val CONTROL_ICON_SIZE = 36.dp
private val CONTROL_PADDING = 16.dp
private val CONTROL_BUTTON_GAP = 16.dp
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 5f
private const val SNAP_BACK_THRESHOLD = 1.15f

@Composable
fun MirrorScreen(modifier: Modifier = Modifier) {
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val animScale = remember { Animatable(ZOOM_MIN) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }

    val (showControls, onInteraction) = rememberAutoHideState()

    // Sync animated values to ScreenCaptureManager state flows via snapshotFlow,
    // avoiding excessive LaunchedEffect restarts on every animation frame.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(animScale.value, animOffsetX.value, animOffsetY.value) }
            .collectLatest { snapshot ->
                ScreenCaptureManager.setScale(snapshot.first)
                ScreenCaptureManager.setOffsetX(snapshot.second)
                ScreenCaptureManager.setOffsetY(snapshot.third)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        coroutineScope.launch { animScale.animateTo(ZOOM_MIN) }
                        coroutineScope.launch { animOffsetX.animateTo(0f) }
                        coroutineScope.launch { animOffsetY.animateTo(0f) }
                        onInteraction()
                    },
                    onTap = { onInteraction() }
                )
            }
            .pointerInput(Unit) {
                while (true) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onInteraction()
                        coroutineScope.launch {
                            val newScale = (animScale.value * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            animScale.snapTo(newScale)

                            // Gallery-style hard-edge bounding
                            val maxX = (surfaceWidth * (newScale - 1f)) / 2f
                            val maxY = (surfaceHeight * (newScale - 1f)) / 2f
                            animOffsetX.snapTo((animOffsetX.value + pan.x).coerceIn(-maxX, maxX))
                            animOffsetY.snapTo((animOffsetY.value + pan.y).coerceIn(-maxY, maxY))
                        }
                    }
                    // Snap back when a pinch-out drops below the comfortable threshold
                    if (animScale.value < SNAP_BACK_THRESHOLD) {
                        coroutineScope.launch { animScale.animateTo(ZOOM_MIN) }
                        coroutineScope.launch { animOffsetX.animateTo(0f) }
                        coroutineScope.launch { animOffsetY.animateTo(0f) }
                    }
                }
            }
    ) {
        val scale by ScreenCaptureManager.scale.collectAsState()
        val offsetX by ScreenCaptureManager.offsetX.collectAsState()
        val offsetY by ScreenCaptureManager.offsetY.collectAsState()

        if (isFrozen && frozenBitmap != null) {
            Image(
                bitmap = frozenBitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_frozen_image),
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
            )
        }

        if (!isCapturing) {
            Text(
                text = stringResource(R.string.mirror_waiting_permission),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(CONTROL_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(CONTROL_BUTTON_GAP)
                ) {
                    IconButton(
                        onClick = {
                            context.stopService(Intent(context, ScreenCaptureService::class.java))
                            ScreenCaptureManager.setCapturing(false)
                            ScreenCaptureManager.setFrozen(false)
                            ScreenCaptureManager.setFrozenBitmap(null)
                            AppStateManager.setUserDeclinedCapture(true)
                        },
                        modifier = Modifier
                            .size(CONTROL_BUTTON_SIZE)
                            .background(accentColor, RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.cd_stop_mirroring),
                            tint = Color.White,
                            modifier = Modifier.size(CONTROL_ICON_SIZE)
                        )
                    }

                    IconButton(
                        onClick = { ScreenCaptureManager.toggleFrozen() },
                        modifier = Modifier
                            .size(CONTROL_BUTTON_SIZE)
                            .background(
                                color = if (isFrozen) accentColor else Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            imageVector = if (isFrozen) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = stringResource(if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze),
                            tint = Color.White,
                            modifier = Modifier.size(CONTROL_ICON_SIZE)
                        )
                    }
                }
            }
        }

        CarouselOverlay(visible = showControls)
    }
}
