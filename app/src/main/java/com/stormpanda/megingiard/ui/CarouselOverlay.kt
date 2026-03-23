package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay

private val OVERLAY_BUTTON_SIZE = 72.dp
private val OVERLAY_ICON_SIZE = 36.dp
private val OVERLAY_PADDING = 16.dp
private val OVERLAY_TIMEOUT_MS = 3_000L

/**
 * Remembers a pair of (showControls, onInteraction) where calling onInteraction()
 * shows the controls and resets the auto-hide timer to [timeoutMs] milliseconds.
 */
@Composable
fun rememberAutoHideState(timeoutMs: Long = OVERLAY_TIMEOUT_MS): Pair<Boolean, () -> Unit> {
    var showControls by rememberSaveable { mutableStateOf(false) }
    var interactionTime by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(showControls, interactionTime) {
        if (showControls) {
            delay(timeoutMs)
            showControls = false
        }
    }

    val onInteraction: () -> Unit = {
        showControls = true
        interactionTime = System.currentTimeMillis()
    }

    return showControls to onInteraction
}

/**
 * Translucent left/right carousel navigation overlay that fades in/out.
 * Calls [AppStateManager.prevMode] / [AppStateManager.nextMode] on tap.
 */
@Composable
fun CarouselOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = { AppStateManager.prevMode() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = OVERLAY_PADDING)
                    .size(OVERLAY_BUTTON_SIZE)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_previous_tool),
                    tint = Color.White,
                    modifier = Modifier.size(OVERLAY_ICON_SIZE)
                )
            }

            IconButton(
                onClick = { AppStateManager.nextMode() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = OVERLAY_PADDING)
                    .size(OVERLAY_BUTTON_SIZE)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_next_tool),
                    tint = Color.White,
                    modifier = Modifier.size(OVERLAY_ICON_SIZE)
                )
            }
        }
    }
}
