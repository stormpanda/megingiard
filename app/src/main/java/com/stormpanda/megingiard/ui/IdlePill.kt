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
import androidx.compose.material3.MaterialTheme
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
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "IdlePill"

// ── Dimensions ──────────────────────────────────────────────────────────────
private val IP_PILL_TOP_PADDING = 6.dp
private val IP_PILL_IDLE_WIDTH = 72.dp
private val IP_PILL_IDLE_HEIGHT = 4.dp
private val IP_PILL_SHADOW_ELEVATION = 3.dp

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
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    if (previewConfig != null || isViewportEditActive) return

    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isPillMenuOpen by AppStateManager.isPillMenuOpen.collectAsState()
    val colors = LocalAppColors.current

    Box(modifier = modifier.fillMaxSize()) {
        // Pill tab + conditional "× close" label
        PillTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier = Modifier.align(
                if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
            )
        )

        // Pill Menu overlay — rendered as a sibling so it covers MacroPadScreen
        PillMenu(
            visible = isPillMenuOpen,
            onDismiss = { AppStateManager.closePillMenu() },
        )
    }
}

@Composable
private fun PillTab(overlayAtBottom: Boolean, colors: AppColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(
                if (overlayAtBottom) Modifier.padding(bottom = IP_PILL_TOP_PADDING)
                else Modifier.padding(top = IP_PILL_TOP_PADDING)
            )
            .shadow(
                elevation = IP_PILL_SHADOW_ELEVATION,
                shape = RoundedCornerShape(50),
                clip = false,
            )
            .size(width = IP_PILL_IDLE_WIDTH, height = IP_PILL_IDLE_HEIGHT)
            .background(colors.pillIdleColor, RoundedCornerShape(50)),
    )
}
