package com.stormpanda.megingiard.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

private const val DEFAULT_MARQUEE_INITIAL_DELAY_MS = 500

/**
 * Reusable modifier extension applying smooth edge-faded marquee scrolling
 * to single-line text elements that exceed their layout bounds.
 *
 * @param enabled Whether the marquee effect is active (defaults to true).
 * @param initialDelayMillis Delay in ms before scrolling begins (defaults to 500ms).
 */
fun Modifier.appMarquee(
    enabled: Boolean = true,
    initialDelayMillis: Int = DEFAULT_MARQUEE_INITIAL_DELAY_MS,
): Modifier =
    if (enabled) {
        this
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = initialDelayMillis,
            )
    } else {
        this
    }
