package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.ROLLING_CAROUSEL_ANIM_DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GameFocusPacerTest"
private const val NEGATIVE_START_CLOCK_MS = -500_000L
private const val PARTIAL_ITEM_ELAPSED_MS = 40L
private const val EXPECTED_ITEM_REMAINING_MS = 75L
private const val PARTIAL_CATEGORY_ELAPSED_MS = 100L
private const val EXPECTED_CATEGORY_REMAINING_MS = 110L
private const val PARTIAL_LIBRARY_ELAPSED_MS = 150L
private const val EXPECTED_LIBRARY_REMAINING_MS = 250L
private const val EXPECTED_HOLD_MULTIPLIER = 2L

class GameFocusInputPacerTest {
    @Test
    fun motionTokensAlignWithChannelIntervalsAndTransitionBudgets() {
        AppLog.d(TAG, "Verifying GameFocusMotionTokens invariants against UI animation durations")
        assertEquals(ROLLING_CAROUSEL_ANIM_DURATION_MS, GameFocusMotionTokens.CATEGORY_ROLL_ANIM_DURATION_MS)
        assertEquals(
            GameFocusMotionTokens.CAROUSEL_STEP_ANIM_DURATION_MS * EXPECTED_HOLD_MULTIPLIER,
            GameFocusMotionTokens.ITEM_INITIAL_HOLD_DELAY_MS,
        )
        assertTrue(GameFocusMotionTokens.CATEGORY_REPEAT_INTERVAL_MS > GameFocusMotionTokens.CATEGORY_ROLL_ANIM_DURATION_MS)
        assertTrue(GameFocusMotionTokens.CATEGORY_INITIAL_HOLD_DELAY_MS > GameFocusMotionTokens.CATEGORY_REPEAT_INTERVAL_MS)
        assertTrue(GameFocusMotionTokens.LIBRARY_TOGGLE_COOLDOWN_MS > GameFocusMotionTokens.LIBRARY_TRANSITION_DURATION_MS)

        assertEquals(GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS, GameFocusInputChannel.ITEM_HORIZONTAL.minIntervalMs)
        assertEquals(GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS, GameFocusInputChannel.ITEM_VERTICAL.minIntervalMs)
        assertEquals(GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS, GameFocusInputChannel.GALLERY_LETTER.minIntervalMs)
        assertEquals(GameFocusMotionTokens.CATEGORY_REPEAT_INTERVAL_MS, GameFocusInputChannel.CATEGORY_OR_TAB.minIntervalMs)
        assertEquals(GameFocusMotionTokens.LIBRARY_TOGGLE_COOLDOWN_MS, GameFocusInputChannel.LIBRARY_TOGGLE.minIntervalMs)
    }

    @Test
    fun initialRunSucceedsImmediatelyIncludingAtZeroAndNegativeClock() {
        AppLog.d(TAG, "Testing initial execution across zero and negative monotonic clock origins")
        var nowMs = 0L
        val zeroOriginPacer = GameFocusInputPacer { nowMs }
        assertEquals(0L, zeroOriginPacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))
        assertTrue(zeroOriginPacer.tryRun(GameFocusInputChannel.ITEM_HORIZONTAL) { true })
        assertEquals(ITEM_REPEAT_INTERVAL_MS, zeroOriginPacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))

        nowMs = NEGATIVE_START_CLOCK_MS
        val negativeOriginPacer = GameFocusInputPacer { nowMs }
        assertEquals(0L, negativeOriginPacer.remainingCooldownMs(GameFocusInputChannel.CATEGORY_OR_TAB))
        assertTrue(negativeOriginPacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })
        assertEquals(CATEGORY_REPEAT_INTERVAL_MS, negativeOriginPacer.remainingCooldownMs(GameFocusInputChannel.CATEGORY_OR_TAB))
    }

    @Test
    fun itemAndLetterChannelsEnforce115msCooldown() {
        var nowMs = 0L
        val pacer = GameFocusInputPacer { nowMs }

        pacer.commit(GameFocusInputChannel.ITEM_HORIZONTAL)
        nowMs += PARTIAL_ITEM_ELAPSED_MS
        assertEquals(EXPECTED_ITEM_REMAINING_MS, pacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))
        assertFalse(pacer.tryRun(GameFocusInputChannel.ITEM_HORIZONTAL) { true })

        nowMs = ITEM_REPEAT_INTERVAL_MS - 1L
        assertEquals(1L, pacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))
        assertFalse(pacer.tryRun(GameFocusInputChannel.ITEM_HORIZONTAL) { true })

        nowMs = ITEM_REPEAT_INTERVAL_MS
        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))
        assertTrue(pacer.tryRun(GameFocusInputChannel.ITEM_HORIZONTAL) { true })

        assertTrue(pacer.tryRun(GameFocusInputChannel.GALLERY_LETTER) { true })
        assertEquals(ITEM_REPEAT_INTERVAL_MS, pacer.remainingCooldownMs(GameFocusInputChannel.GALLERY_LETTER))
        assertEquals(ITEM_INITIAL_HOLD_DELAY_MS, GameFocusInputChannel.GALLERY_LETTER.initialHoldDelayMs)
    }

    @Test
    fun categoryAndLibraryToggleChannelsEnforce210msAnd400msCooldowns() {
        var nowMs = 0L
        val pacer = GameFocusInputPacer { nowMs }

        assertTrue(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })
        assertEquals(CATEGORY_INITIAL_HOLD_DELAY_MS, GameFocusInputChannel.CATEGORY_OR_TAB.initialHoldDelayMs)
        nowMs += PARTIAL_CATEGORY_ELAPSED_MS
        assertEquals(EXPECTED_CATEGORY_REMAINING_MS, pacer.remainingCooldownMs(GameFocusInputChannel.CATEGORY_OR_TAB))
        assertFalse(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })

        nowMs = CATEGORY_REPEAT_INTERVAL_MS
        assertTrue(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })

        nowMs = 0L
        val togglePacer = GameFocusInputPacer { nowMs }
        assertTrue(togglePacer.tryRun(GameFocusInputChannel.LIBRARY_TOGGLE) { true })
        nowMs += PARTIAL_LIBRARY_ELAPSED_MS
        assertEquals(EXPECTED_LIBRARY_REMAINING_MS, togglePacer.remainingCooldownMs(GameFocusInputChannel.LIBRARY_TOGGLE))
        assertFalse(togglePacer.tryRun(GameFocusInputChannel.LIBRARY_TOGGLE) { true })

        nowMs = LIBRARY_TOGGLE_COOLDOWN_MS
        assertEquals(0L, togglePacer.remainingCooldownMs(GameFocusInputChannel.LIBRARY_TOGGLE))
        assertTrue(togglePacer.tryRun(GameFocusInputChannel.LIBRARY_TOGGLE) { true })
    }

    @Test
    fun crossChannelInputsRemainIndependentWithZeroDelay() {
        var nowMs = 0L
        val pacer = GameFocusInputPacer { nowMs }

        assertTrue(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })
        assertFalse(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })

        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.ITEM_HORIZONTAL))
        assertTrue(pacer.tryRun(GameFocusInputChannel.ITEM_HORIZONTAL) { true })

        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.ITEM_VERTICAL))
        assertTrue(pacer.tryRun(GameFocusInputChannel.ITEM_VERTICAL) { true })

        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.GALLERY_LETTER))
        assertTrue(pacer.tryRun(GameFocusInputChannel.GALLERY_LETTER) { true })

        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.LIBRARY_TOGGLE))
        assertTrue(pacer.tryRun(GameFocusInputChannel.LIBRARY_TOGGLE) { true })
    }

    @Test
    fun tryRunDoesNotCommitCooldownWhenActionIsNoOp() {
        var nowMs = 0L
        val pacer = GameFocusInputPacer { nowMs }

        assertFalse(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { false })
        assertEquals(0L, pacer.remainingCooldownMs(GameFocusInputChannel.CATEGORY_OR_TAB))

        assertTrue(pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) { true })
        assertEquals(CATEGORY_REPEAT_INTERVAL_MS, pacer.remainingCooldownMs(GameFocusInputChannel.CATEGORY_OR_TAB))

        var invokedWhileCoolingDown = false
        assertFalse(
            pacer.tryRun(GameFocusInputChannel.CATEGORY_OR_TAB) {
                invokedWhileCoolingDown = true
                true
            },
        )
        assertFalse(invokedWhileCoolingDown)
    }

    @Test
    fun resetClearsAllActiveChannelCooldowns() {
        var nowMs = 0L
        val pacer = GameFocusInputPacer { nowMs }

        GameFocusInputChannel.entries.forEach { channel ->
            pacer.commit(channel)
            assertTrue(pacer.remainingCooldownMs(channel) > 0L)
        }

        pacer.reset()

        GameFocusInputChannel.entries.forEach { channel ->
            assertEquals(0L, pacer.remainingCooldownMs(channel))
            assertTrue(pacer.tryRun(channel) { true })
        }
    }
}
