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
import androidx.compose.ui.input.pointer.pointerInput
import com.stormpanda.megingiard.media.MediaScreen
import com.stormpanda.megingiard.mirror.MirrorScreen
import kotlin.math.abs

enum class AppMode { MIRROR, MEDIA }

@Composable
fun MainAppScreen() {
    var currentMode by remember { mutableStateOf(AppMode.MIRROR) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
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
                            if (abs(currentX - startX) > 200f) {
                                currentMode = if (currentMode == AppMode.MIRROR) AppMode.MEDIA else AppMode.MIRROR
                                event.changes.forEach { it.consume() }
                                break 
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
            when (mode) {
                AppMode.MIRROR -> MirrorScreen()
                AppMode.MEDIA -> MediaScreen()
            }
        }
    }
}
