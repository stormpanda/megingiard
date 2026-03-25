package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ToolSettingsPanel
import kotlinx.coroutines.delay

private val OVERLAY_BUTTON_SIZE = 72.dp
private val OVERLAY_ICON_SIZE = 36.dp
private val OVERLAY_PADDING = 16.dp
private val SETTINGS_BUTTON_SIZE = 48.dp
private val SETTINGS_ICON_SIZE = 24.dp

/**
 * Remembers a pair of (showControls, onInteraction) where calling onInteraction()
 * shows the controls and resets the auto-hide timer. The timeout is read from
 * [SettingsManager.overlayTimeoutMs].
 */
@Composable
fun rememberAutoHideState(): Pair<Boolean, () -> Unit> {
    val timeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()
    var showControls by rememberSaveable { mutableStateOf(false) }
    var interactionTime by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(showControls, interactionTime, timeoutMs) {
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
 * Carousel navigation overlay with auto-hide.
 * Gear button (TopEnd) opens [ToolSettingsPanel] which in turn can open [GlobalSettingsScreen].
 */
@Composable
fun CarouselOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val activeTools by SettingsManager.activeTools.collectAsState()
    var showToolPanel by remember { mutableStateOf(false) }
    var showGlobalSettings by remember { mutableStateOf(false) }

    // ToolSettingsPanel renders as a floating Dialog window – outside the Box hierarchy
    if (showToolPanel) {
        ToolSettingsPanel(
            onDismiss = { showToolPanel = false },
            onOpenGlobalSettings = {
                showToolPanel = false
                showGlobalSettings = true
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Auto-hide overlay: chevrons + gear button
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTools.size > 1) {
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

                IconButton(
                    onClick = { showToolPanel = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = OVERLAY_PADDING, end = OVERLAY_PADDING)
                        .size(SETTINGS_BUTTON_SIZE)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = Color.White,
                        modifier = Modifier.size(SETTINGS_ICON_SIZE)
                    )
                }
            }
        }

        // Global settings screen slides up over the entire window
        AnimatedVisibility(
            visible = showGlobalSettings,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            GlobalSettingsScreen(onBack = { showGlobalSettings = false })
        }
    }
}
