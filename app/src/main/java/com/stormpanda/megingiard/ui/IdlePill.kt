package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "IdlePill"

// ── Dimensions ──────────────────────────────────────────────────────────────
private val IP_PILL_TOP_PADDING = 6.dp
private val IP_PILL_IDLE_WIDTH = 72.dp
private val IP_PILL_IDLE_HEIGHT = 4.dp
private val IP_PILL_SHADOW_ELEVATION = 3.dp
private val IP_CLOSE_LABEL_TEXT_SIZE = 11.sp
private val IP_CLOSE_LABEL_PADDING = 5.dp

/** Vertical space this pill occupies at the screen edge. Screens can inset by this amount. */
val PILL_INSET: Dp = IP_PILL_TOP_PADDING + IP_PILL_IDLE_HEIGHT + 3.dp

/**
 * Always-visible Idle Pill anchored to the screen edge defined by [SettingsManager.overlayAtBottom].
 *
 * - In normal state: a slim rounded pill tab serves as a swipe affordance.
 *   The actual edge-zone swipe gesture is handled in [MainAppScreen][com.stormpanda.megingiard.MainAppScreen]
 *   via [SwipeGestureProcessor][com.stormpanda.megingiard.SwipeGestureProcessor], which calls
 *   [AppStateManager.handleEdgeSwipe] — opening the [PillMenu] or closing modals as appropriate.
 * - When [AppStateManager.isAnyModalActive] is true: a "× close" label appears on the
 *   interior side of the pill, indicating that a swipe will close the active modal.
 * - When [AppStateManager.isPillMenuOpen] is true: [PillMenu] renders as a full-screen overlay.
 */
@Composable
fun IdlePill(modifier: Modifier = Modifier) {
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isAnyModalActive by AppStateManager.isAnyModalActive.collectAsState()
    val isPillMenuOpen by AppStateManager.isPillMenuOpen.collectAsState()
    val colors = LocalAppColors.current

    Box(modifier = modifier.fillMaxSize()) {
        // Pill tab + conditional "× close" label
        Column(
            modifier = Modifier.align(
                if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!overlayAtBottom) {
                // Pill at top → label below (toward screen interior)
                PillTab(overlayAtBottom = false, colors = colors)
                AnimatedVisibility(
                    visible = isAnyModalActive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit  = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Text(
                        text = stringResource(R.string.pill_x_close),
                        color = colors.onSurface.copy(alpha = 0.7f),
                        fontSize = IP_CLOSE_LABEL_TEXT_SIZE,
                        modifier = Modifier.padding(bottom = IP_CLOSE_LABEL_PADDING),
                    )
                }
            } else {
                // Pill at bottom → label above (toward screen interior)
                AnimatedVisibility(
                    visible = isAnyModalActive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit  = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    Text(
                        text = stringResource(R.string.pill_x_close),
                        color = colors.onSurface.copy(alpha = 0.7f),
                        fontSize = IP_CLOSE_LABEL_TEXT_SIZE,
                        modifier = Modifier.padding(top = IP_CLOSE_LABEL_PADDING),
                    )
                }
                PillTab(overlayAtBottom = true, colors = colors)
            }
        }

        // Pill Menu overlay — rendered as a sibling so it covers MacroPadScreen
        PillMenu(
            visible = isPillMenuOpen,
            onDismiss = { AppStateManager.closePillMenu() },
        )
    }
}

@Composable
private fun PillTab(overlayAtBottom: Boolean, colors: AppColors) {
    Box(
        modifier = (if (overlayAtBottom) Modifier.padding(bottom = IP_PILL_TOP_PADDING)
                    else Modifier.padding(top = IP_PILL_TOP_PADDING))
            .shadow(
                elevation = IP_PILL_SHADOW_ELEVATION,
                shape = RoundedCornerShape(50),
                clip = false,
            )
            .size(width = IP_PILL_IDLE_WIDTH, height = IP_PILL_IDLE_HEIGHT)
            .background(colors.pillIdleColor, RoundedCornerShape(50)),
    )
}
