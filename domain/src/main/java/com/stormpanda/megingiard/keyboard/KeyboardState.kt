package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KeyboardState"

/**
 * Three-state lifecycle for a modifier key:
 * - [INACTIVE]  — not active
 * - [STICKY]    — activated by a quick tap; will be released after the next
 *                 non-modifier key is injected, then returns to [INACTIVE]
 * - [HELD]      — activated by a long-press (>= [MODIFIER_HOLD_THRESHOLD_MS]);
 *                 stays active while the physical finger is down, released on
 *                 finger lift
 */
enum class ModifierState { INACTIVE, STICKY, HELD }

private const val MODIFIER_HOLD_THRESHOLD_MS = 300L

/**
 * Tracks the [ModifierState] for every known modifier key.
 *
 * Call [onModifierTouchDown] when the finger lands, [onModifierTouchUp] when
 * it lifts. [releaseStickyModifiers] is called by [KeyboardScreen] immediately
 * after sending any non-modifier key — it clears all STICKY modifiers and
 * returns the list of modifier keycodes that need a KEY_UP event.
 */
object KeyboardState {

    // -----------------------------------------------------------------------
    // State flows — one per modifier key id
    // -----------------------------------------------------------------------

    private val _modifiers: MutableMap<String, MutableStateFlow<ModifierState>> = mutableMapOf()

    /** Returns the [StateFlow] for the given modifier key [id], creating it lazily. */
    fun stateFor(id: String): StateFlow<ModifierState> =
        getOrCreate(id).asStateFlow()

    private fun getOrCreate(id: String): MutableStateFlow<ModifierState> =
        _modifiers.getOrPut(id) { MutableStateFlow(ModifierState.INACTIVE) }

    // -----------------------------------------------------------------------
    // Touch-down tracking — records the timestamp to distinguish tap vs hold
    // -----------------------------------------------------------------------

    private val touchDownTimes: MutableMap<String, Long> = mutableMapOf()

    /** Called when a modifier key touch begins. Records the current time. */
    fun onModifierTouchDown(id: String) {
        touchDownTimes[id] = System.currentTimeMillis()
    }

    /**
     * Called when a modifier key finger lifts.
     *
     * Decision logic:
     * - If currently [ModifierState.HELD] → set [ModifierState.INACTIVE], return keycode to inject KEY_UP
     * - If currently [ModifierState.STICKY] → second tap cycles back to [ModifierState.INACTIVE],
     *   return keycode to inject KEY_UP
     * - If currently [ModifierState.INACTIVE] and duration < threshold → set [ModifierState.STICKY],
     *   return empty list (key will be held until [releaseStickyModifiers] is called)
     * - If currently [ModifierState.INACTIVE] and duration >= threshold → already handled by
     *   [onModifierLongPress]; return empty list
     *
     * Returns the list of keycodes that need a KEY_UP event injected right now.
     */
    fun onModifierTouchUp(id: String, keycode: Int): List<Int> {
        val flow = getOrCreate(id)
        val downTime = touchDownTimes.remove(id) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - downTime

        return when (flow.value) {
            ModifierState.HELD -> {
                AppLog.d(TAG, "modifier '$id' HELD → INACTIVE (keycode=$keycode)")
                flow.value = ModifierState.INACTIVE
                if (keycode != 0) listOf(keycode) else emptyList()
            }
            ModifierState.STICKY -> {
                // second tap on an already-sticky modifier cycles back to INACTIVE
                AppLog.d(TAG, "modifier '$id' STICKY → INACTIVE (second tap)")
                flow.value = ModifierState.INACTIVE
                if (keycode != 0) listOf(keycode) else emptyList()
            }
            ModifierState.INACTIVE -> {
                if (duration < MODIFIER_HOLD_THRESHOLD_MS) {
                    // quick tap → sticky
                    AppLog.d(TAG, "modifier '$id' INACTIVE → STICKY (${duration}ms < ${MODIFIER_HOLD_THRESHOLD_MS}ms)")
                    flow.value = ModifierState.STICKY
                }
                // else: short hold case is handled by onModifierLongPress
                emptyList()
            }
        }
    }

    /**
     * Called by the keyboard screen after [MODIFIER_HOLD_THRESHOLD_MS] has elapsed
     * with the finger still on the modifier key. Sets the modifier to [ModifierState.HELD]
     * and returns the keycode to inject as KEY_DOWN immediately.
     */
    fun onModifierLongPress(id: String, keycode: Int): Int? {
        val flow = getOrCreate(id)
        if (flow.value == ModifierState.INACTIVE) {
            AppLog.d(TAG, "modifier '$id' INACTIVE → HELD (long-press)")
            flow.value = ModifierState.HELD
            return if (keycode != 0) keycode else null
        }
        return null
    }

    /**
     * Variant that accepts the full layout so it can
     * look up keycodes from key ids automatically.
     *
     * Returns the list of keycodes that need KEY_UP injection.
     */
    fun releaseStickyModifiers(layout: List<List<KeyDef>>): List<Int> {
        val keycodes = mutableListOf<Int>()
        for (row in layout) {
            for (key in row) {
                if (key.type == KeyType.MODIFIER) {
                    val flow = _modifiers[key.id] ?: continue
                    if (flow.value == ModifierState.STICKY) {
                        flow.value = ModifierState.INACTIVE
                        if (key.linuxKeycode != 0) keycodes += key.linuxKeycode
                    }
                }
            }
        }
        if (keycodes.isNotEmpty()) AppLog.d(TAG, "releaseStickyModifiers: $keycodes")
        return keycodes
    }

    /** Returns the keycodes of all currently STICKY or HELD modifiers (for KEY_DOWN on press). */
    fun activeModifierKeycodes(layout: List<List<KeyDef>>): List<Int> {
        val keycodes = mutableListOf<Int>()
        for (row in layout) {
            for (key in row) {
                if (key.type == KeyType.MODIFIER) {
                    val state = _modifiers[key.id]?.value ?: ModifierState.INACTIVE
                    if (state != ModifierState.INACTIVE && key.linuxKeycode != 0) {
                        keycodes += key.linuxKeycode
                    }
                }
            }
        }
        return keycodes
    }

    /** Resets all modifier states to [ModifierState.INACTIVE]. Called on screen exit. */
    fun reset() {
        AppLog.d(TAG, "reset modifier states")
        _modifiers.values.forEach { it.value = ModifierState.INACTIVE }
        touchDownTimes.clear()
    }
}
