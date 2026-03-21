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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.foundation.gestures.awaitFirstDown
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import kotlinx.coroutines.delay

@Composable
fun MirrorScreen(modifier: Modifier = Modifier) {
    var showControls by remember { mutableStateOf(false) }
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        ScreenCaptureManager.scale.value = 1f
                        ScreenCaptureManager.offsetX.value = 0f
                        ScreenCaptureManager.offsetY.value = 0f
                    },
                    onTap = { showControls = !showControls }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    ScreenCaptureManager.scale.value = (ScreenCaptureManager.scale.value * zoom).coerceIn(1f, 5f)
                    ScreenCaptureManager.offsetX.value += pan.x * ScreenCaptureManager.scale.value
                    ScreenCaptureManager.offsetY.value += pan.y * ScreenCaptureManager.scale.value
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isTwoFingerSwipe = false
                        var startX = 0f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size == 2 && !isTwoFingerSwipe) {
                                isTwoFingerSwipe = true
                                startX = event.changes[0].position.x
                            }
                            if (isTwoFingerSwipe) {
                                val currentX = event.changes[0].position.x
                                if (java.lang.Math.abs(currentX - startX) > 200f) {
                                    AppStateManager.currentMode.value = AppMode.MEDIA
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            IconButton(
                onClick = { /* TODO: freeze frame functionality to be implemented in Service */ },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Einfrieren",
                    tint = Color.White
                )
            }
        }
    }
}
