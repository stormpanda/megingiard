package com.stormpanda.megingiard.mirror

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun MirrorScreen(modifier: Modifier = Modifier) {
    var showControls by remember { mutableStateOf(false) }
    var interactionTime by remember { mutableStateOf(0L) }
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }

    LaunchedEffect(showControls, interactionTime) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(animScale.value, animOffsetX.value, animOffsetY.value) {
        ScreenCaptureManager.scale.value = animScale.value
        ScreenCaptureManager.offsetX.value = animOffsetX.value
        ScreenCaptureManager.offsetY.value = animOffsetY.value
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        coroutineScope.launch { animScale.animateTo(1f) }
                        coroutineScope.launch { animOffsetX.animateTo(0f) }
                        coroutineScope.launch { animOffsetY.animateTo(0f) }
                        interactionTime = System.currentTimeMillis()
                    },
                    onTap = { 
                        showControls = !showControls 
                        interactionTime = System.currentTimeMillis()
                    }
                )
            }
            .pointerInput(Unit) {
                while (true) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        interactionTime = System.currentTimeMillis()
                        coroutineScope.launch {
                            val newScale = (animScale.value * zoom).coerceIn(1f, 5f)
                            animScale.snapTo(newScale)

                            // Constraint boundaries for gallery-style panning
                            val maxX = (surfaceWidth * (newScale - 1f)) / 2f
                            val maxY = (surfaceHeight * (newScale - 1f)) / 2f

                            val newX = (animOffsetX.value + pan.x).coerceIn(-maxX, maxX)
                            val newY = (animOffsetY.value + pan.y).coerceIn(-maxY, maxY)

                            animOffsetX.snapTo(newX)
                            animOffsetY.snapTo(newY)
                        }
                    }
                    // Snap back if scale drops below a comfortable threshold
                    if (animScale.value < 1.15f) {
                        coroutineScope.launch { animScale.animateTo(1f) }
                        coroutineScope.launch { animOffsetX.animateTo(0f) }
                        coroutineScope.launch { animOffsetY.animateTo(0f) }
                    }
                }
            }
    ) {
        val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
        val scale by ScreenCaptureManager.scale.collectAsState()
        val offsetX by ScreenCaptureManager.offsetX.collectAsState()
        val offsetY by ScreenCaptureManager.offsetY.collectAsState()

        if (isFrozen && frozenBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = frozenBitmap!!.asImageBitmap(),
                contentDescription = "Frozen Stream Image",
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
                text = "Warte auf Bildschirmfreigabe...",
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
                IconButton(
                    onClick = { AppStateManager.prevMode() },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                ) {
                    Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Vorheriges Tool", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = { AppStateManager.nextMode() },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                ) {
                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Nächstes Tool", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            context.stopService(android.content.Intent(context, com.stormpanda.megingiard.mirror.ScreenCaptureService::class.java))
                            ScreenCaptureManager.isCapturing.value = false
                            ScreenCaptureManager.isFrozen.value = false
                            ScreenCaptureManager.frozenBitmap.value = null
                            com.stormpanda.megingiard.AppStateManager.userDeclinedCapture.value = true
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Red.copy(alpha = 0.6f), RoundedCornerShape(50))
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop Mirroring", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = { ScreenCaptureManager.isFrozen.value = !ScreenCaptureManager.isFrozen.value },
                        modifier = Modifier
                            .size(72.dp)
                            .background(if (isFrozen) Color.Red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = if (isFrozen) Icons.Filled.PlayArrow else Icons.Filled.Pause, 
                            contentDescription = "Frozen", 
                            tint = Color.White, 
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}
