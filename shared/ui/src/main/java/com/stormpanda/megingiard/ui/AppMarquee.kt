package com.stormpanda.megingiard.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

private const val DEFAULT_MARQUEE_INITIAL_DELAY_MS = 500

/**
 * Reusable modifier extension applying marquee scrolling to single-line text elements
 * that exceed their layout bounds without forcing an offscreen GPU compositing layer.
 *
 * @param enabled Whether the marquee effect is active (defaults to true).
 * @param initialDelayMillis Delay in ms before scrolling begins (defaults to 500ms).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appMarquee(
    enabled: Boolean = true,
    initialDelayMillis: Int = DEFAULT_MARQUEE_INITIAL_DELAY_MS,
): Modifier =
    if (enabled) {
        this.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = initialDelayMillis,
        )
    } else {
        this
    }
