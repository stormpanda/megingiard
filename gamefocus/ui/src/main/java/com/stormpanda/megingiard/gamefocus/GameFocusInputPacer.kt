package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog

private const val TAG = "GameFocusInputPacer"

internal const val ITEM_REPEAT_INTERVAL_MS = 115L
internal const val ITEM_INITIAL_HOLD_DELAY_MS = 240L
internal const val CATEGORY_REPEAT_INTERVAL_MS = 210L
internal const val CATEGORY_INITIAL_HOLD_DELAY_MS = 280L
internal const val LIBRARY_TOGGLE_COOLDOWN_MS = 400L

private const val NANOS_PER_MS = 1_000_000L

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
        minIntervalMs = ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    ITEM_VERTICAL(
        minIntervalMs = ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    GALLERY_LETTER(
        minIntervalMs = ITEM_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = ITEM_INITIAL_HOLD_DELAY_MS,
    ),
    CATEGORY_OR_TAB(
        minIntervalMs = CATEGORY_REPEAT_INTERVAL_MS,
        initialHoldDelayMs = CATEGORY_INITIAL_HOLD_DELAY_MS,
    ),
    LIBRARY_TOGGLE(
        minIntervalMs = LIBRARY_TOGGLE_COOLDOWN_MS,
        initialHoldDelayMs = LIBRARY_TOGGLE_COOLDOWN_MS,
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
