package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.ROLLING_CAROUSEL_ANIM_DURATION_MS

private const val TAG = "GameFocusInputPacer"
private const val NANOS_PER_MS = 1_000_000L
private const val ITEM_HOLD_DELAY_MULTIPLIER = 2L
private const val CATEGORY_SETTLE_MARGIN_MS = 10L
private const val LIBRARY_TOGGLE_SETTLE_MARGIN_MS = 50L

/**
 * Canonical animation durations and paired controller/touch pacing budgets for Game Focus.
 *
 * Keeping transition durations and input cooldowns together prevents UI animations and repeat
 * cadences from drifting out of sync when tuning feel.
 */
internal object GameFocusMotionTokens {
    const val CAROUSEL_STEP_ANIM_DURATION_MS = 120
    const val CATEGORY_ROLL_ANIM_DURATION_MS = ROLLING_CAROUSEL_ANIM_DURATION_MS
    const val LIBRARY_TRANSITION_DURATION_MS = 350
    const val PROMPT_ANIM_DURATION_MS = 200
    const val HIDE_ANIMATION_DURATION_MS = 300

    const val ITEM_REPEAT_INTERVAL_MS = 115L
    const val ITEM_INITIAL_HOLD_DELAY_MS = CAROUSEL_STEP_ANIM_DURATION_MS * ITEM_HOLD_DELAY_MULTIPLIER
    const val CATEGORY_REPEAT_INTERVAL_MS = CATEGORY_ROLL_ANIM_DURATION_MS + CATEGORY_SETTLE_MARGIN_MS
    const val CATEGORY_INITIAL_HOLD_DELAY_MS = 280L
    const val LIBRARY_TOGGLE_COOLDOWN_MS = LIBRARY_TRANSITION_DURATION_MS + LIBRARY_TOGGLE_SETTLE_MARGIN_MS
}

internal const val ITEM_REPEAT_INTERVAL_MS = GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS
internal const val ITEM_INITIAL_HOLD_DELAY_MS = GameFocusMotionTokens.ITEM_INITIAL_HOLD_DELAY_MS
internal const val CATEGORY_REPEAT_INTERVAL_MS = GameFocusMotionTokens.CATEGORY_REPEAT_INTERVAL_MS
internal const val CATEGORY_INITIAL_HOLD_DELAY_MS = GameFocusMotionTokens.CATEGORY_INITIAL_HOLD_DELAY_MS
internal const val LIBRARY_TOGGLE_COOLDOWN_MS = GameFocusMotionTokens.LIBRARY_TOGGLE_COOLDOWN_MS

/**
 * Independent pacing channels for Game Focus navigation and view transitions.
 *
 * Separate channels ensure switching axes or actions (e.g. Category Down -> Poster Right)
 * executes with zero artificial latency while rapid consecutive inputs or holds on the same
 * channel respect UI animation budgets.
 */
enum class GameFocusInputChannel(
    val minIntervalMs: Long,
    val initialHoldDelayMs: Long,
) {
    ITEM_HORIZONTAL(
        minIntervalMs = GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = GameFocusMotionTokens.ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    ITEM_VERTICAL(
        minIntervalMs = GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = GameFocusMotionTokens.ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    GALLERY_LETTER(
        minIntervalMs = GameFocusMotionTokens.ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = GameFocusMotionTokens.ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    CATEGORY_OR_TAB(
        minIntervalMs = GameFocusMotionTokens.CATEGORY_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = GameFocusMotionTokens.CATEGORY_INITIAL_HOLD_DELAY_MS,
    ),
    LIBRARY_TOGGLE(
        minIntervalMs = GameFocusMotionTokens.LIBRARY_TOGGLE_COOLDOWN_MS,
        initialHoldDelayMs = GameFocusMotionTokens.LIBRARY_TOGGLE_COOLDOWN_MS,
    ),
}

/**
 * Monotonic input pacer shared between controller handlers and touch switcher controls in Game Focus.
 */
class GameFocusInputPacer(
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) {
    private val hasCommitted = BooleanArray(GameFocusInputChannel.entries.size)
    private val lastCommittedAtMs = LongArray(GameFocusInputChannel.entries.size)

    /**
     * Returns the remaining cooldown in milliseconds for [channel], or `0L` if the channel is ready.
     */
    fun remainingCooldownMs(channel: GameFocusInputChannel): Long {
        val index = channel.ordinal
        if (!hasCommitted[index]) return 0L
        val elapsedMs = clockMs() - lastCommittedAtMs[index]
        if (elapsedMs < 0L || elapsedMs >= channel.minIntervalMs) {
            return 0L
        }
        return channel.minIntervalMs - elapsedMs
    }

    /**
     * Records a completed action timestamp on [channel] at the current monotonic clock reading.
     */
    fun commit(channel: GameFocusInputChannel) {
        val index = channel.ordinal
        hasCommitted[index] = true
        lastCommittedAtMs[index] = clockMs()
    }

    /**
     * Runs [action] if [channel] is outside its cooldown window, committing the cooldown only when
     * [action] reports that state actually changed (`true`).
     */
    fun tryRun(
        channel: GameFocusInputChannel,
        action: () -> Boolean,
    ): Boolean {
        val remainingMs = remainingCooldownMs(channel)
        if (remainingMs > 0L) {
            AppLog.d(TAG, "Throttled action on ${channel.name} (${remainingMs}ms remaining)")
            return false
        }
        val changed = action()
        if (changed) {
            commit(channel)
        }
        return changed
    }

    /**
     * Clears all recorded channel cooldowns.
     */
    fun reset() {
        AppLog.d(TAG, "Resetting all input pacer cooldowns")
        hasCommitted.fill(false)
        lastCommittedAtMs.fill(0L)
    }
}
