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

private const val TAG = "QuickMenuBar"

// ── Dimensions ──────────────────────────────────────────────────────────────
private val QM_BAR_TOP_PADDING = 6.dp
private val QM_BAR_IDLE_WIDTH = 72.dp
private val QM_BAR_IDLE_HEIGHT = 4.dp
private val QM_BAR_SHADOW_ELEVATION = 3.dp

/** Vertical space this bar occupies at the screen edge. Screens can inset by this amount. */
val QUICK_MENU_BAR_INSET: Dp = QM_BAR_TOP_PADDING + QM_BAR_IDLE_HEIGHT + 3.dp

/**
 * Always-visible Quick Menu Bar anchored to the screen edge defined by [SettingsManager.overlayAtBottom].
 *
 * - In normal state: a slim rounded bar tab serves as a swipe affordance.
 *   The actual edge-zone swipe gesture is handled in [MainAppScreen][com.stormpanda.megingiard.MainAppScreen]
 *   via [SwipeGestureProcessor][com.stormpanda.megingiard.SwipeGestureProcessor], which calls
 *   [AppStateManager.handleEdgeSwipe] — opening the [QuickMenu] or closing modals as appropriate.
 * - When [AppStateManager.isAnyModalActive] is true: a "× close" label appears on the
 *   interior side of the bar, indicating that a swipe will close the active modal.
 * - When [AppStateManager.isQuickMenuOpen] is true: [QuickMenu] renders as a full-screen overlay.
 */
@Composable
fun QuickMenuBar(modifier: Modifier = Modifier) {
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    if (previewConfig != null || isViewportEditActive) return

    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isQuickMenuOpen by AppStateManager.isQuickMenuOpen.collectAsState()
    val colors = LocalAppColors.current

    Box(modifier = modifier.fillMaxSize()) {
        // Bar tab
        QuickMenuBarTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier = Modifier.align(
                if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
            )
        )

        // Quick Menu overlay — rendered as a sibling so it covers MacroPadScreen
        QuickMenu(
            visible = isQuickMenuOpen,
            onDismiss = { AppStateManager.closeQuickMenu() },
        )
    }
}

@Composable
private fun QuickMenuBarTab(overlayAtBottom: Boolean, colors: AppColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(
                if (overlayAtBottom) Modifier.padding(bottom = QM_BAR_TOP_PADDING)
                else Modifier.padding(top = QM_BAR_TOP_PADDING)
            )
            .shadow(
                elevation = QM_BAR_SHADOW_ELEVATION,
                shape = RoundedCornerShape(50),
                clip = false,
            )
            .size(width = QM_BAR_IDLE_WIDTH, height = QM_BAR_IDLE_HEIGHT)
            .background(colors.quickMenuBarIdleColor, RoundedCornerShape(50)),
    )
}
