package com.stormpanda.megingiard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout configurations and touch boundaries for the Quick Menu Bar.
 *
 * Consolidating these parameters in a single location guarantees that the visual tab
 * alignments in [QuickMenuBar][com.stormpanda.megingiard.ui.QuickMenuBar] and the edge-swipe gesture zones in the host screens
 * ([MainAppScreen][com.stormpanda.megingiard.MainAppScreen], [BackgroundMacroPadOverlay][com.stormpanda.megingiard.macropad.BackgroundMacroPadOverlay], [MirrorPresentation][com.stormpanda.megingiard.mirror.MirrorPresentation])
 * always remain perfectly in sync.
 */
object QuickMenuBarLayout {
    /** Minimum swipe distance required to trigger the action. */
    val SWIPE_THRESHOLD: Dp = 50.dp

    /** Vertical touch height from the screen edge that registers the gesture. */
    val SWIPE_EDGE_ZONE: Dp = 40.dp

    /** Horizontal touch width for the center Quick Menu zone. */
    val SWIPE_QM_BAR_ZONE_WIDTH: Dp = 120.dp

    /** Width of the Keyboard and Touchpad slim affordance tabs. */
    val TAB_WIDTH: Dp = 72.dp

    /** Horizontal padding from the screen edge for the Keyboard/Touchpad tabs. */
    val TAB_PADDING: Dp = 24.dp

    /** Horizontal touch width for the Keyboard and Touchpad swipe zones. */
    val TAB_ZONE_WIDTH: Dp = 120.dp

    /** Diameter of the sliding visual pill. */
    val SLIDING_PILL_SIZE: Dp = 48.dp

    /**
     * Center alignment offset padding for the Keyboard/Touchpad sliding pills.
     * Computed as: (TAB_PADDING + TAB_WIDTH / 2) - SLIDING_PILL_SIZE / 2
     */
    val SLIDING_PILL_PADDING: Dp = 36.dp
}
