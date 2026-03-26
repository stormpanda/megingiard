package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ToolSettingsPanel
import kotlinx.coroutines.delay

// ── Top mode handle ────────────────────────────────────────────────────────
private val CO_PILL_TOP_PADDING = 6.dp
private val CO_PILL_WIDTH = 72.dp
private val CO_PILL_HEIGHT = 4.dp
private const val CO_PILL_ALPHA_IDLE = 0.4f
private const val CO_PILL_ALPHA_ACTIVE = 0.75f
private val CO_HANDLE_H_PADDING = 12.dp
private val CO_HANDLE_ROW_V_PADDING = 6.dp
private val CO_HANDLE_CORNER = 12.dp
private val CO_HANDLE_BG = Color.Black.copy(alpha = 0.55f)

// ── Dot indicators ─────────────────────────────────────────────────────────
private val CO_DOT_SIZE_IDLE = 14.dp
private val CO_DOT_SIZE_EXPANDED = 22.dp
private val CO_DOT_GAP_IDLE = 10.dp
private val CO_DOT_GAP_EXPANDED = 20.dp
private val CO_DOT_TOUCH_PADDING_V = 12.dp
private val CO_DOT_TRACK_CORNER = 24.dp
private val CO_DOT_TRACK_BG = Color.White.copy(alpha = 0.12f)
private const val CO_HYSTERESIS = 0.28f  // fraction past boundary required to switch

// ── Settings button ────────────────────────────────────────────────────────
private val CO_SETTINGS_BUTTON_SIZE = 40.dp
private val CO_SETTINGS_ICON_SIZE = 20.dp

private fun AppMode.nameResId(): Int = when (this) {
    AppMode.MIRROR -> R.string.tool_name_mirror
    AppMode.MEDIA -> R.string.tool_name_media
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
}

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
 * Navigation overlay.
 *
 * Always shows a subtle pull-tab pill at the top edge. When [visible], the pill
 * brightens and a compact mode-indicator bar slides down below it: mode name,
 * an interactive [DraggableDotIndicator], and a settings gear.
 *
 * **Mode switching via dots:** place a finger on the dot cluster → dots grow and
 * a drag-track appears → slide finger to the target dot → [AppStateManager.setMode]
 * is called live. Releasing the finger confirms the selection and shrinks the dots.
 */
@Composable
fun CarouselOverlay(
    visible: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTools by SettingsManager.activeTools.collectAsState()
    val currentMode by AppStateManager.currentMode.collectAsState()
    var showToolPanel by remember { mutableStateOf(false) }
    var showGlobalSettings by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top mode handle ─────────────────────────────────────────────────
        TopModeHandle(
            visible = visible,
            activeTools = activeTools,
            currentMode = currentMode,
            onSettingsClick = { showToolPanel = true },
            onInteraction = onInteraction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .wrapContentHeight()
        )

        // Tool settings panel – rendered in-tree (no Dialog window) so it works
        // both in the main Activity and inside MirrorPresentation.
        AnimatedVisibility(
            visible = showToolPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ToolSettingsPanel(
                onDismiss = { showToolPanel = false },
                onOpenGlobalSettings = {
                    showToolPanel = false
                    showGlobalSettings = true
                }
            )
        }

        // Global settings full-screen overlay ────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// Top mode handle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopModeHandle(
    visible: Boolean,
    activeTools: List<AppMode>,
    currentMode: AppMode,
    onSettingsClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillAlpha by animateFloatAsState(
        targetValue = if (visible) CO_PILL_ALPHA_ACTIVE else CO_PILL_ALPHA_IDLE,
        animationSpec = tween(durationMillis = 300),
        label = "pill_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Pull-tab pill – always visible, brightens when controls are shown
        Spacer(Modifier.height(CO_PILL_TOP_PADDING))
        Box(
            Modifier
                .size(width = CO_PILL_WIDTH, height = CO_PILL_HEIGHT)
                .background(Color.White.copy(alpha = pillAlpha), RoundedCornerShape(50))
        )

        // Expanded info bar – slides down when visible = true
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            // Box layout: title pinned to start, dots always centered, gear pinned to end
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CO_HANDLE_H_PADDING, vertical = CO_HANDLE_ROW_V_PADDING)
                    .background(CO_HANDLE_BG, RoundedCornerShape(CO_HANDLE_CORNER))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                // Current mode name – start
                Text(
                    text = stringResource(currentMode.nameResId()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // Dot indicators – always centered
                if (activeTools.size > 1) {
                    DraggableDotIndicator(
                        tools = activeTools,
                        currentMode = currentMode,
                        onInteraction = onInteraction,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Settings gear – end
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(CO_SETTINGS_BUTTON_SIZE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = Color.White,
                        modifier = Modifier.size(CO_SETTINGS_ICON_SIZE)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Draggable dot indicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A row of dots where the user can:
 *  1. Place a finger to expand the dots and reveal a drag-track background.
 *  2. Slide left / right — the dot under the finger highlights and
 *     [AppStateManager.setMode] is called live.
 *  3. Lift the finger to confirm and collapse.
 */
@Composable
private fun DraggableDotIndicator(
    tools: List<AppMode>,
    currentMode: AppMode,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val dotSize by animateDpAsState(
        targetValue = if (isExpanded) CO_DOT_SIZE_EXPANDED else CO_DOT_SIZE_IDLE,
        animationSpec = spring(),
        label = "dot_size"
    )
    val dotGap by animateDpAsState(
        targetValue = if (isExpanded) CO_DOT_GAP_EXPANDED else CO_DOT_GAP_IDLE,
        animationSpec = spring(),
        label = "dot_gap"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(dotGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                if (isExpanded) CO_DOT_TRACK_BG else Color.Transparent,
                RoundedCornerShape(CO_DOT_TRACK_CORNER)
            )
            .padding(horizontal = dotSize, vertical = CO_DOT_TOUCH_PADDING_V)
            .pointerInput(tools) {
                awaitEachGesture {
                    // Detect initial press: pressed=true, previousPressed=false
                    var down = awaitPointerEvent()
                    var downChange = down.changes.firstOrNull { it.pressed && !it.previousPressed }
                    while (downChange == null) {
                        down = awaitPointerEvent()
                        downChange = down.changes.firstOrNull { it.pressed && !it.previousPressed }
                    }
                    downChange.consume()
                    isExpanded = true
                    onInteraction()

                    // Set mode for the dot under the initial press
                    var committedIdx = xToIndex(downChange.position.x, tools.size, size.width)
                    AppStateManager.setMode(tools[committedIdx])

                    // Track finger movement until lifted – with hysteresis to avoid jitter
                    val pointerId = downChange.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        val newIdx = xToIndexHysteresis(
                            change.position.x, tools.size, size.width, committedIdx
                        )
                        if (newIdx != committedIdx) {
                            committedIdx = newIdx
                            AppStateManager.setMode(tools[committedIdx])
                        }
                        change.consume()
                    }

                    isExpanded = false
                }
            }
    ) {
        tools.forEach { tool ->
            Box(
                Modifier
                    .size(dotSize)
                    .background(
                        Color.White.copy(alpha = if (tool == currentMode) 1f else 0.35f),
                        CircleShape
                    )
            )
        }
    }
}

private fun xToIndex(x: Float, count: Int, totalWidthPx: Int): Int =
    ((x / totalWidthPx.toFloat()) * count).toInt().coerceIn(0, count - 1)

/**
 * Like [xToIndex] but requires the finger to overshoot the boundary by [CO_HYSTERESIS]
 * (as a fraction of one cell) before committing to the adjacent index.
 * Prevents erratic toggling when the finger rests near a boundary.
 */
private fun xToIndexHysteresis(x: Float, count: Int, totalWidthPx: Int, current: Int): Int {
    val floatPos = (x / totalWidthPx.toFloat()) * count
    return when {
        floatPos < current - (0.5f - CO_HYSTERESIS) -> (current - 1).coerceAtLeast(0)
        floatPos > current + (0.5f + CO_HYSTERESIS) -> (current + 1).coerceAtMost(count - 1)
        else -> current
    }
}
