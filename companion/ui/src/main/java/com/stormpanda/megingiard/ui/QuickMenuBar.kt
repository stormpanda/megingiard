package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.SwipeGestureProgress
import com.stormpanda.megingiard.SwipeGestureType
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
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
private val QM_BAR_INSET_EXTRA = 3.dp

/** Vertical space this bar occupies at the screen edge. Screens can inset by this amount. */
val QUICK_MENU_BAR_INSET: Dp = QM_BAR_TOP_PADDING + QM_BAR_IDLE_HEIGHT + QM_BAR_INSET_EXTRA

private const val GESTURE_VISUAL_MULTIPLIER = 1.8f
private const val GESTURE_RESISTANCE_FACTOR = 0.3f
private val GESTURE_MAX_OFFSET = 80.dp

private val PILL_ZERO_OFFSET = 0.dp
private val PILL_OFFSCREEN_PADDING = 10.dp
private val PILL_SHADOW_ELEVATION = 6.dp
private val PILL_BORDER_WIDTH = 1.dp
private val PILL_ICON_SIZE = 24.dp

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
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsStateWithLifecycle()
    val isWizardActive by OnboardingWizardManager.isWizardActive.collectAsStateWithLifecycle()
    val isPrivdSetupWizardActive by AppStateManager.isPrivdSetupWizardActive.collectAsStateWithLifecycle()
    if (isViewportEditActive || isWizardActive || isPrivdSetupWizardActive) return

    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsStateWithLifecycle()
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsStateWithLifecycle()
    val isQuickMenuOpen by AppStateManager.isQuickMenuOpen.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    val alpha = remember { Animatable(QM_BAR_ALPHA_VISIBLE) }
    LaunchedEffect(isQuickMenuOpen, overlayFadeOut) {
        AppLog.d(TAG, "QuickMenuBar: state changed isQuickMenuOpen=$isQuickMenuOpen")
        if (overlayFadeOut && !isQuickMenuOpen) {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
            delay(QM_BAR_FADE_OUT_DELAY_MS)
            alpha.animateTo(QM_BAR_ALPHA_FADED, animationSpec = tween(QM_BAR_FADE_OUT_DURATION_MS))
        } else {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
        }
    }

    val activeSwipeState = AppStateManager.activeSwipe.collectAsStateWithLifecycle()
    val isSwiping by remember { derivedStateOf { activeSwipeState.value != null } }
    val swipeAlphaState =
        animateFloatAsState(
            targetValue = if (isSwiping) QM_BAR_ALPHA_FADED else QM_BAR_ALPHA_VISIBLE,
            label = "swipeAlpha",
        )

    Box(modifier = modifier.fillMaxSize()) {
        // Bar tab (Center)
        QuickMenuBarTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(
                        if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
                    ).graphicsLayer { this.alpha = alpha.value * swipeAlphaState.value },
        )

        // Keyboard Bar tab (Left/Start)
        QuickMenuBarTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(
                        if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart,
                    ).padding(start = QuickMenuBarLayout.TAB_PADDING)
                    .graphicsLayer { this.alpha = alpha.value * swipeAlphaState.value },
        )

        // Touchpad Bar tab (Right/End)
        QuickMenuBarTab(
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(
                        if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd,
                    ).padding(end = QuickMenuBarLayout.TAB_PADDING)
                    .graphicsLayer { this.alpha = alpha.value * swipeAlphaState.value },
        )

        // ─── Sliding gesture pills ───────────────────────────────────────────────
        QuickMenuLiveSlidingPill(
            type = SwipeGestureType.KEYBOARD,
            icon = Icons.Rounded.Keyboard,
            activeSwipeState = activeSwipeState,
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                    .padding(start = QuickMenuBarLayout.SLIDING_PILL_PADDING),
        )

        QuickMenuLiveSlidingPill(
            type = SwipeGestureType.MENU,
            icon = Icons.Rounded.Menu,
            activeSwipeState = activeSwipeState,
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier = Modifier.align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter),
        )

        QuickMenuLiveSlidingPill(
            type = SwipeGestureType.TOUCHPAD,
            icon = Icons.Rounded.Mouse,
            activeSwipeState = activeSwipeState,
            overlayAtBottom = overlayAtBottom,
            colors = colors,
            modifier =
                Modifier
                    .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                    .padding(end = QuickMenuBarLayout.SLIDING_PILL_PADDING),
        )

        // Quick Menu overlay — rendered as a sibling so it covers MacroPadScreen
        QuickMenu(
            visible = isQuickMenuOpen,
            onDismiss = AppStateManager::closeQuickMenu,
        )
    }
}

@Composable
private fun QuickMenuLiveSlidingPill(
    type: SwipeGestureType,
    icon: ImageVector,
    activeSwipeState: State<SwipeGestureProgress?>,
    overlayAtBottom: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
) {
    val pillState by remember(type, activeSwipeState) {
        derivedStateOf {
            val current = activeSwipeState.value
            if (current != null && current.type == type && current.deltaPx > 0f) {
                true to current.isPastThreshold
            } else {
                false to false
            }
        }
    }
    val (isVisible, isPastThreshold) = pillState
    if (!isVisible) return

    val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
    Box(
        modifier =
            modifier
                .offset {
                    val current = activeSwipeState.value
                    val visualOffsetDp =
                        if (current != null && current.type == type) {
                            computeSwipeVisualOffsetDp(current.deltaPx, current.thresholdPx, this)
                        } else {
                            PILL_ZERO_OFFSET
                        }
                    val yOffset =
                        if (overlayAtBottom) {
                            initialOffscreenOffset - visualOffsetDp
                        } else {
                            -initialOffscreenOffset + visualOffsetDp
                        }
                    IntOffset(0, yOffset.roundToPx())
                }.shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                .background(color = if (isPastThreshold) colors.accent else colors.controlOverlay, shape = CircleShape)
                .border(
                    width = PILL_BORDER_WIDTH,
                    color = if (isPastThreshold) colors.accent else colors.controlOverlayBorder,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPastThreshold) colors.onAccent else colors.onSurface,
            modifier = Modifier.size(PILL_ICON_SIZE),
        )
    }
}

internal fun computeSwipeVisualOffsetDp(
    deltaPx: Float,
    thresholdPx: Float,
    density: Density,
): Dp =
    with(density) {
        val delta = deltaPx.toDp()
        val threshold = thresholdPx.toDp()
        val visualDelta = delta * GESTURE_VISUAL_MULTIPLIER
        val visualThreshold = threshold * GESTURE_VISUAL_MULTIPLIER
        val visual =
            if (visualDelta < visualThreshold) {
                visualDelta
            } else {
                visualThreshold + (visualDelta - visualThreshold) * GESTURE_RESISTANCE_FACTOR
            }
        visual.coerceAtMost(GESTURE_MAX_OFFSET)
    }

@Composable
internal fun QuickMenuSlidingPill(
    icon: ImageVector,
    animatedOffsetDp: Dp,
    isPastThreshold: Boolean,
    overlayAtBottom: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    alpha: Float = QM_BAR_ALPHA_VISIBLE,
) {
    if (animatedOffsetDp <= PILL_ZERO_OFFSET || alpha <= QM_BAR_ALPHA_FADED) return
    val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
    val yOffset = if (overlayAtBottom) initialOffscreenOffset - animatedOffsetDp else -initialOffscreenOffset + animatedOffsetDp

    Box(
        modifier =
            modifier
                .offset { IntOffset(0, yOffset.roundToPx()) }
                .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                .graphicsLayer { this.alpha = alpha }
                .background(color = if (isPastThreshold) colors.accent else colors.controlOverlay, shape = CircleShape)
                .border(
                    width = PILL_BORDER_WIDTH,
                    color = if (isPastThreshold) colors.accent else colors.controlOverlayBorder,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPastThreshold) colors.onAccent else colors.onSurface,
            modifier = Modifier.size(PILL_ICON_SIZE),
        )
    }
}

@Composable
internal fun QuickMenuBarTab(
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
                    shape = CircleShape,
                    clip = false,
                ).size(width = QM_BAR_IDLE_WIDTH, height = QM_BAR_IDLE_HEIGHT)
                .background(colors.quickMenuBarIdleColor, CircleShape),
    )
}
