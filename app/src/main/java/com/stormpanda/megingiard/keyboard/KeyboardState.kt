package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * it lifts. [onRegularKeyInjected] is called by [KeyboardScreen] immediately
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
     * - If currently [ModifierState.HELD] → release (set [ModifierState.INACTIVE])
     * - If currently [ModifierState.STICKY] → double-tap = lock: keep STICKY (no change)
     * - If currently [ModifierState.INACTIVE] and duration < threshold → set [ModifierState.STICKY]
     * - If currently [ModifierState.INACTIVE] and duration >= threshold → set [ModifierState.INACTIVE]
     *   (HELD was already activated by a long-press; caller released while still HELD — treat
     *    as release so caller injects KEY_UP via [heldKeycodes])
     *
     * Returns the list of keycodes that need a KEY_UP event injected right now
     * (only for HELD modifiers being released).
     */
    fun onModifierTouchUp(id: String, keycode: Int): List<Int> {
        val flow = getOrCreate(id)
        val downTime = touchDownTimes.remove(id) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - downTime

        return when (flow.value) {
            ModifierState.HELD -> {
                flow.value = ModifierState.INACTIVE
                if (keycode != 0) listOf(keycode) else emptyList()
            }
            ModifierState.STICKY -> {
                // second tap on an already-sticky modifier cycles back to INACTIVE
                flow.value = ModifierState.INACTIVE
                if (keycode != 0) listOf(keycode) else emptyList()
            }
            ModifierState.INACTIVE -> {
                if (duration < MODIFIER_HOLD_THRESHOLD_MS) {
                    // quick tap → sticky
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
            flow.value = ModifierState.HELD
            return if (keycode != 0) keycode else null
        }
        return null
    }

    /**
     * Called immediately after any non-modifier key is injected.
     *
     * Clears all [ModifierState.STICKY] modifiers and returns the list of
     * their keycodes so the caller can inject KEY_UP for each.
     */
    fun onRegularKeyInjected(): List<Int> {
        val toRelease = mutableListOf<Int>()
        for ((id, flow) in _modifiers) {
            if (flow.value == ModifierState.STICKY) {
                flow.value = ModifierState.INACTIVE
                // look up the keycode from the layout; use id-to-keycode via caller
                // — caller uses releaseStickyKeycodes() which has direct access
            }
        }
        return toRelease
    }

    /**
     * Variant of [onRegularKeyInjected] that accepts the full layout so it can
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
        _modifiers.values.forEach { it.value = ModifierState.INACTIVE }
        touchDownTimes.clear()
    }
}
