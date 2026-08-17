package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.SwipeGestureType
import com.stormpanda.megingiard.macropad.AmbientPreviewManager
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
    val previewConfig by AmbientPreviewManager.config.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val isWizardActive by OnboardingWizardManager.isWizardActive.collectAsState()
    val isPrivdSetupWizardActive by AppStateManager.isPrivdSetupWizardActive.collectAsState()
    if (previewConfig != null || isViewportEditActive || isWizardActive || isPrivdSetupWizardActive) return

    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsState()
    val isQuickMenuOpen by AppStateManager.isQuickMenuOpen.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val colors = LocalAppColors.current

    val alpha = remember { Animatable(QM_BAR_ALPHA_VISIBLE) }
    LaunchedEffect(isQuickMenuOpen, isFullscreenKeyboardActive, isFullscreenMouseActive, overlayFadeOut) {
        AppLog.d(TAG, "QuickMenuBar: state changed isQuickMenuOpen=$isQuickMenuOpen")
        if (overlayFadeOut && !isQuickMenuOpen && !isFullscreenKeyboardActive && !isFullscreenMouseActive) {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
            delay(QM_BAR_FADE_OUT_DELAY_MS)
            alpha.animateTo(QM_BAR_ALPHA_FADED, animationSpec = tween(QM_BAR_FADE_OUT_DURATION_MS))
        } else {
            alpha.snapTo(QM_BAR_ALPHA_VISIBLE)
        }
    }

    val activeSwipe by AppStateManager.activeSwipe.collectAsState()
    val swipeAlpha by animateFloatAsState(
        targetValue = if (activeSwipe != null) 0f else 1f,
        label = "swipeAlpha",
    )

    val density = LocalDensity.current

    fun getTargetOffset(type: SwipeGestureType): Dp {
        val currentActive = activeSwipe
        return if (currentActive != null && currentActive.type == type) {
            val delta = with(density) { currentActive.deltaPx.toDp() }
            val threshold = with(density) { currentActive.thresholdPx.toDp() }
            val visualDelta = delta * GESTURE_VISUAL_MULTIPLIER
            val visualThreshold = threshold * GESTURE_VISUAL_MULTIPLIER
            val visual =
                if (visualDelta < visualThreshold) {
                    visualDelta
                } else {
                    visualThreshold + (visualDelta - visualThreshold) * GESTURE_RESISTANCE_FACTOR
                }
            visual.coerceAtMost(GESTURE_MAX_OFFSET)
        } else {
            0.dp
        }
    }

    val kbAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.KEYBOARD),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "kbAnimatedOffset",
    )
    val menuAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.MENU),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "menuAnimatedOffset",
    )
    val tpAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.TOUCHPAD),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tpAnimatedOffset",
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
                    ).graphicsLayer(alpha = alpha.value * swipeAlpha),
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
                        ).padding(start = QuickMenuBarLayout.TAB_PADDING)
                        .graphicsLayer(alpha = alpha.value * swipeAlpha),
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
                        ).padding(end = QuickMenuBarLayout.TAB_PADDING)
                        .graphicsLayer(alpha = alpha.value * swipeAlpha),
            )
        }

        // ─── Sliding gesture pills ───────────────────────────────────────────────
        // Keyboard Sliding Pill
        if (kbAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = activeSwipe?.let { it.type == SwipeGestureType.KEYBOARD && it.isPastThreshold } ?: false
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset = if (overlayAtBottom) initialOffscreenOffset - kbAnimatedOffsetDp else -initialOffscreenOffset + kbAnimatedOffsetDp

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                        .offset(y = yOffset)
                        .padding(start = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
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
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = if (isPastThreshold) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }

        // Menu Sliding Pill
        if (menuAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = activeSwipe?.let { it.type == SwipeGestureType.MENU && it.isPastThreshold } ?: false
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset =
                if (overlayAtBottom) {
                    initialOffscreenOffset - menuAnimatedOffsetDp
                } else {
                    -initialOffscreenOffset +
                        menuAnimatedOffsetDp
                }

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .offset(y = yOffset)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
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
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = null,
                    tint = if (isPastThreshold) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }

        // Touchpad Sliding Pill
        if (tpAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = activeSwipe?.let { it.type == SwipeGestureType.TOUCHPAD && it.isPastThreshold } ?: false
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset = if (overlayAtBottom) initialOffscreenOffset - tpAnimatedOffsetDp else -initialOffscreenOffset + tpAnimatedOffsetDp

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .offset(y = yOffset)
                        .padding(end = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
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
                    imageVector = Icons.Rounded.Mouse,
                    contentDescription = null,
                    tint = if (isPastThreshold) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }

        // Quick Menu overlay — rendered as a sibling so it covers MacroPadScreen
        QuickMenu(
            visible = isQuickMenuOpen,
            onDismiss = { AppStateManager.closeQuickMenu() },
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
