package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ToolSettingsPanel

// ── Top mode handle ────────────────────────────────────────────────────────
private val CO_PILL_TOP_PADDING = 6.dp
// Idle pull-tab pill (always visible)
private val CO_PILL_IDLE_WIDTH = 72.dp
private val CO_PILL_IDLE_HEIGHT = 4.dp
private const val CO_PILL_IDLE_ALPHA = 0.4f
private val CO_HANDLE_H_PADDING = 12.dp
private val CO_HANDLE_ROW_V_PADDING = 6.dp
private val CO_HANDLE_CORNER = 12.dp
private val CO_HANDLE_BG = Color.Black.copy(alpha = 0.55f)

// ── Dot indicators ───────────────────────────────────────────────────────────
private val CO_DOT_SIZE = 30.dp
private val CO_FINGER_CIRCLE_SIZE = 44.dp
private val CO_PILL_ACTIVE_HEIGHT = CO_FINGER_CIRCLE_SIZE
// Uniform spacing: same on all four sides AND between dots = vertical clearance
private val CO_DOT_SPACING = (CO_PILL_ACTIVE_HEIGHT - CO_DOT_SIZE) / 2
private val CO_FINGER_CIRCLE_COLOR = Color.White.copy(alpha = 0.45f)
private const val CO_HYSTERESIS = 0.28f  // fraction past boundary required to switch

/** Pill width: equal spacing on all sides and between dots. */
private fun pillActiveWidth(toolCount: Int): Dp =
    CO_DOT_SPACING * (toolCount + 1) + CO_DOT_SIZE * toolCount

// ── Settings button ────────────────────────────────────────────────────────
private val CO_SETTINGS_BUTTON_SIZE = 40.dp
private val CO_SETTINGS_ICON_SIZE = 20.dp

private fun AppMode.nameResId(): Int = when (this) {
    AppMode.MIRROR -> R.string.tool_name_mirror
    AppMode.MEDIA -> R.string.tool_name_media
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
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
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    var showToolPanel by remember { mutableStateOf(false) }
    var showGlobalSettings by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Mode handle ─────────────────────────────────────────────────────
        TopModeHandle(
            visible = visible,
            activeTools = activeTools,
            currentMode = currentMode,
            overlayAtBottom = overlayAtBottom,
            onSettingsClick = { showToolPanel = true },
            onInteraction = onInteraction,
            modifier = Modifier
                .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
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
    overlayAtBottom: Boolean,
    onSettingsClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor by SettingsManager.accentColor.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var fingerXFraction by remember { mutableStateOf(0f) }

    val overlayEdgeAlignment = if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter
    val overlayExpandFrom = if (overlayAtBottom) Alignment.Bottom else Alignment.Top
    Box(
        contentAlignment = overlayEdgeAlignment,
        modifier = modifier
    ) {
        // Always-visible idle pull-tab pill (pure affordance, no interaction)
        Box(
            Modifier
                .then(
                    if (overlayAtBottom) Modifier.padding(bottom = CO_PILL_TOP_PADDING)
                    else Modifier.padding(top = CO_PILL_TOP_PADDING)
                )
                .size(width = CO_PILL_IDLE_WIDTH, height = CO_PILL_IDLE_HEIGHT)
                .background(Color.White.copy(alpha = CO_PILL_IDLE_ALPHA), RoundedCornerShape(50))
        )

        // Full control row: title | active pill | gear — all vertically centred
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = overlayExpandFrom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = overlayExpandFrom) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CO_HANDLE_H_PADDING, vertical = CO_HANDLE_ROW_V_PADDING)
                    .background(CO_HANDLE_BG, RoundedCornerShape(CO_HANDLE_CORNER))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Tool name – pinned to start
                Text(
                    text = stringResource(currentMode.nameResId()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // Active pill – centred in the row (accent colour, only when >1 tool)
                if (activeTools.size > 1) {
                    val pillWidth = pillActiveWidth(activeTools.size)
                    Box(
                    modifier = Modifier
                        .size(width = pillWidth, height = CO_PILL_ACTIVE_HEIGHT)
                        .background(accentColor, RoundedCornerShape(50))
                        .pointerInput(activeTools) {
                            awaitEachGesture {
                                var down = awaitPointerEvent()
                                var downChange = down.changes.firstOrNull { it.pressed && !it.previousPressed }
                                while (downChange == null) {
                                    down = awaitPointerEvent()
                                    downChange = down.changes.firstOrNull { it.pressed && !it.previousPressed }
                                }
                                downChange.consume()
                                isExpanded = true
                                fingerXFraction = (downChange.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onInteraction()

                                var committedIdx = xToIndex(downChange.position.x, activeTools.size, size.width)
                                AppStateManager.setMode(activeTools[committedIdx])

                                val pointerId = downChange.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break
                                    fingerXFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val newIdx = xToIndexHysteresis(
                                        change.position.x, activeTools.size, size.width, committedIdx
                                    )
                                    if (newIdx != committedIdx) {
                                        committedIdx = newIdx
                                        AppStateManager.setMode(activeTools[committedIdx])
                                    }
                                    change.consume()
                                }

                                isExpanded = false
                            }
                        }
                ) {
                    // Finger-position circle (mid Z)
                    if (isExpanded) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (pillWidth - CO_FINGER_CIRCLE_SIZE) * fingerXFraction)
                                .size(CO_FINGER_CIRCLE_SIZE)
                                .background(CO_FINGER_CIRCLE_COLOR, CircleShape)
                        )
                    }
                    // Dot indicators (top Z)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(CO_DOT_SPACING),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeTools.forEach { tool ->
                            Box(
                                Modifier
                                    .size(CO_DOT_SIZE)
                                    .background(
                                        Color.White.copy(alpha = if (tool == currentMode) 1f else 0.35f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
                }

                // Settings gear – pinned to end
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
