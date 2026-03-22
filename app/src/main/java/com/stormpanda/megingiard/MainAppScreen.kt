package com.stormpanda.megingiard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.stormpanda.megingiard.media.MediaScreen
import kotlinx.coroutines.delay

@Composable
fun MainAppScreen() {
    val currentMode by AppStateManager.currentMode.collectAsState()
    var showControls by remember { mutableStateOf(false) }
    var interactionTime by remember { mutableStateOf(0L) }
    
    val isCapturing by com.stormpanda.megingiard.mirror.ScreenCaptureManager.isCapturing.collectAsState()
    val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()

    LaunchedEffect(showControls, interactionTime) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                            showControls = true
                            interactionTime = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
            when (mode) {
                AppMode.MIRROR -> {
                    val isValidScreen by AppStateManager.isOnValidScreen.collectAsState()
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        if (isValidScreen) {
                            if (!isCapturing && userDeclinedCapture) {
                                androidx.compose.material3.Button(
                                    onClick = { AppStateManager.userDeclinedCapture.value = false },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(16.dp).height(72.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start", modifier = Modifier.padding(end = 8.dp).size(36.dp), tint = Color.White)
                                    androidx.compose.material3.Text("Start mirroring", color = Color.White)
                                }
                            }
                        } else {
                            androidx.compose.material3.Text(
                                text = "Mirroring is only supported when Megingiard is running on the bottom screen.",
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
                AppMode.MEDIA -> MediaScreen()
            }
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
            }
        }
    }
}
