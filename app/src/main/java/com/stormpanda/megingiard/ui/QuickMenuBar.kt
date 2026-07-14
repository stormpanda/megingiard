package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.delay

private const val TAG = "QuickMenuBar"

private const val QM_BAR_FADE_OUT_DELAY_MS = 3000L
private const val QM_BAR_FADE_OUT_DURATION_MS = 1000
private const val QM_BAR_ALPHA_VISIBLE = 1f
private const val QM_BAR_ALPHA_FADED = 0f

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
 * - When [AppStateManager.isQuickMenuOpen] is true: [QuickMenu] renders as a full-screen overlay.
 */
@Composable
fun QuickMenuBar(modifier: Modifier = Modifier) {
    val previewConfig by AppStateManager.ambientPreviewConfig.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    if (previewConfig != null || isViewportEditActive) return

    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsState()
    val isQuickMenuOpen by AppStateManager.isQuickMenuOpen.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val colors = LocalAppColors.current

    val alpha = remember { Animatable(QM_BAR_ALPHA_VISIBLE) }
    LaunchedEffect(isQuickMenuOpen, isFullscreenKeyboardActive, isFullscreenMouseActive, overlayFadeOut) {
        if (overlayFadeOut && !isQuickMenuOpen && !isFullscreenKeyboardActive && !isFullscreenMouseActive) {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
            delay(QM_BAR_FADE_OUT_DELAY_MS)
            alpha.animateTo(QM_BAR_ALPHA_FADED, animationSpec = tween(QM_BAR_FADE_OUT_DURATION_MS))
        } else {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Bar tab (Center)
        QuickMenuBarTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(
                        if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
                    ).graphicsLayer(alpha = alpha.value),
        )

        // Keyboard Bar tab (Left/Start)
        if (!isFullscreenKeyboardActive) {
            QuickMenuBarTab(
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                modifier =
                    Modifier
                        .align(
                            if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart,
                        ).padding(start = 24.dp)
                        .graphicsLayer(alpha = alpha.value),
            )
        }

        // Touchpad Bar tab (Right/End)
        if (!isFullscreenMouseActive) {
            QuickMenuBarTab(
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                modifier =
                    Modifier
                        .align(
                            if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd,
                        ).padding(end = 24.dp)
                        .graphicsLayer(alpha = alpha.value),
            )
        }

        // Quick Menu overlay — rendered as a sibling so it covers MacroPadScreen
        QuickMenu(
            visible = isQuickMenuOpen,
            onDismiss = { AppStateManager.closeQuickMenu() },
        )
    }
}

@Composable
private fun QuickMenuBarTab(
    overlayAtBottom: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .then(
                    if (overlayAtBottom) {
                        Modifier.padding(bottom = QM_BAR_TOP_PADDING)
                    } else {
                        Modifier.padding(top = QM_BAR_TOP_PADDING)
                    },
                ).shadow(
                    elevation = QM_BAR_SHADOW_ELEVATION,
                    shape = RoundedCornerShape(50),
                    clip = false,
                ).size(width = QM_BAR_IDLE_WIDTH, height = QM_BAR_IDLE_HEIGHT)
                .background(colors.quickMenuBarIdleColor, RoundedCornerShape(50)),
    )
}
