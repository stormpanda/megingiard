package com.stormpanda.megingiard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.media.MediaScreen

import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import androidx.compose.runtime.collectAsState

@Composable
fun MainAppScreen() {
    val currentMode by AppStateManager.currentMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                                    AppStateManager.currentMode.value = if (currentMode == AppMode.MIRROR) AppMode.MEDIA else AppMode.MIRROR
                                    event.changes.forEach { it.consume() }
                                    break 
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
            when (mode) {
                AppMode.MIRROR -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                AppMode.MEDIA -> MediaScreen()
            }
        }
    }
}
