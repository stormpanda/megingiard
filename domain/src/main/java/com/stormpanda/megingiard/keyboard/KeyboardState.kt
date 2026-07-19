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
    fun stateFor(id: String): StateFlow<ModifierState> = getOrCreate(id).asStateFlow()

    private fun getOrCreate(id: String): MutableStateFlow<ModifierState> =
        _modifiers.getOrPut(id) { MutableStateFlow(ModifierState.INACTIVE) }

    // -----------------------------------------------------------------------
    // Touch-down tracking — records the timestamp to distinguish tap vs hold
    // -----------------------------------------------------------------------

    private val touchDownTimes: MutableMap<String, Long> = mutableMapOf()

    /** Called when a modifier key touch begins. Records the current time. */
    fun onModifierTouchDown(
        id: String,
        keycode: Int = 0,
        isFullLayout: Boolean = false,
    ): Int? {
        touchDownTimes[id] = System.currentTimeMillis()
        if (isFullLayout) {
            val flow = getOrCreate(id)
            if (flow.value == ModifierState.INACTIVE) {
                AppLog.d(TAG, "modifier '$id' INACTIVE → HELD (immediate full layout touch down)")
                flow.value = ModifierState.HELD
                return if (keycode != 0) keycode else null
            }
        }
        return null
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
    fun onModifierTouchUp(
        id: String,
        keycode: Int,
        isFullLayout: Boolean = false,
    ): List<Int> {
        val flow = getOrCreate(id)
        val downTime = touchDownTimes.remove(id) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - downTime

        val capsFlow = getOrCreate("caps")
        val wasCapsActive = capsFlow.value != ModifierState.INACTIVE

        return when (flow.value) {
            ModifierState.HELD -> {
                if (isFullLayout && duration < MODIFIER_HOLD_THRESHOLD_MS) {
                    AppLog.d(TAG, "modifier '$id' HELD → STICKY (short touch on full layout)")
                    flow.value = ModifierState.STICKY
                    if (keycode != 0) listOf(keycode) else emptyList()
                } else {
                    AppLog.d(TAG, "modifier '$id' HELD → INACTIVE (keycode=$keycode)")
                    flow.value = ModifierState.INACTIVE
                    if (keycode != 0) listOf(keycode) else emptyList()
                }
            }

            ModifierState.STICKY -> {
                // second tap on an already-sticky modifier cycles back to INACTIVE
                AppLog.d(TAG, "modifier '$id' STICKY → INACTIVE (second tap)")
                flow.value = ModifierState.INACTIVE
                if (!isFullLayout && (id == "lshift" || id == "rshift")) {
                    if (wasCapsActive) {
                        capsFlow.value = ModifierState.INACTIVE
                    }
                }
                if (keycode != 0) listOf(keycode) else emptyList()
            }

            ModifierState.INACTIVE -> {
                if (!isFullLayout && (id == "lshift" || id == "rshift")) {
                    if (wasCapsActive) {
                        capsFlow.value = ModifierState.INACTIVE
                        return if (keycode != 0) listOf(keycode) else emptyList()
                    }
                }
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
    fun onModifierLongPress(
        id: String,
        keycode: Int,
        isFullLayout: Boolean = false,
    ): Int? {
        val flow = getOrCreate(id)
        if (flow.value == ModifierState.INACTIVE) {
            AppLog.d(TAG, "modifier '$id' INACTIVE → HELD (long-press)")
            flow.value = ModifierState.HELD
            if (!isFullLayout && (id == "lshift" || id == "rshift")) {
                getOrCreate("caps").value = ModifierState.HELD
            }
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
                    if (key.id == "caps") continue
                    val flow = _modifiers[key.id] ?: continue
                    if (flow.value == ModifierState.STICKY) {
                        flow.value = ModifierState.INACTIVE
                        if (key.linuxKeycode != 0) keycodes += key.linuxKeycode
                    }
                }
            }
        }
        val toolbarMods =
            listOf(
                Triple("ctrl", LinuxKeycodes.KEY_LEFTCTRL, "ctrl"),
                Triple("alt", LinuxKeycodes.KEY_LEFTALT, "alt"),
                Triple("altgr", LinuxKeycodes.KEY_RIGHTALT, "ralt"),
            )
        for ((id, code, modKey) in toolbarMods) {
            val flow = _modifiers[id] ?: _modifiers[modKey] ?: continue
            if (flow.value == ModifierState.STICKY) {
                flow.value = ModifierState.INACTIVE
                keycodes += code
            }
        }
        if (keycodes.isNotEmpty()) AppLog.d(TAG, "releaseStickyModifiers: $keycodes")
        return keycodes
    }

    fun activeModifierKeycodes(layout: List<List<KeyDef>>): List<Int> {
        val keycodes = mutableListOf<Int>()
        for (row in layout) {
            for (key in row) {
                if (key.type == KeyType.MODIFIER) {
                    if (key.id == "caps") continue
                    val state = _modifiers[key.id]?.value ?: ModifierState.INACTIVE
                    if (state != ModifierState.INACTIVE && key.linuxKeycode != 0) {
                        keycodes += key.linuxKeycode
                    }
                }
            }
        }
        val capsState = _modifiers["caps"]?.value ?: ModifierState.INACTIVE
        if (capsState != ModifierState.INACTIVE) {
            if (LinuxKeycodes.KEY_LEFTSHIFT !in keycodes) {
                keycodes += LinuxKeycodes.KEY_LEFTSHIFT
            }
        }
        val toolbarMods =
            listOf(
                "ctrl" to LinuxKeycodes.KEY_LEFTCTRL,
                "alt" to LinuxKeycodes.KEY_LEFTALT,
                "altgr" to LinuxKeycodes.KEY_RIGHTALT,
                "ralt" to LinuxKeycodes.KEY_RIGHTALT,
            )
        for ((id, code) in toolbarMods) {
            val state = _modifiers[id]?.value ?: ModifierState.INACTIVE
            if (state != ModifierState.INACTIVE && code !in keycodes) {
                keycodes += code
            }
        }
        return keycodes
    }

    /** Returns the keycodes of all active modifiers, filtering out Shift for non-letter keys if not held. */
    fun activeModifierKeycodesFor(
        key: KeyDef,
        layout: List<List<KeyDef>>,
    ): List<Int> {
        val keycodes = activeModifierKeycodes(layout)
        val isLetter = key.label.length == 1 && key.label[0].isLetter()
        val isFullLayout = layout.size == 6
        if (!isFullLayout && !isLetter) {
            val lshiftHeld = _modifiers["lshift"]?.value == ModifierState.HELD
            val rshiftHeld = _modifiers["rshift"]?.value == ModifierState.HELD
            if (!lshiftHeld && !rshiftHeld) {
                return keycodes.filter { it != LinuxKeycodes.KEY_LEFTSHIFT }
            }
        }
        return keycodes
    }

    /** Resets all modifier states to [ModifierState.INACTIVE]. Called on screen exit. */
    fun reset() {
        AppLog.d(TAG, "reset modifier states")
        val activeCodes = mutableListOf<Int>()
        _modifiers.forEach { (id, flow) ->
            if (flow.value != ModifierState.INACTIVE) {
                val code =
                    when (id) {
                        "lshift" -> LinuxKeycodes.KEY_LEFTSHIFT
                        "rshift" -> LinuxKeycodes.KEY_RIGHTSHIFT
                        "caps" -> LinuxKeycodes.KEY_LEFTSHIFT
                        "ctrl" -> LinuxKeycodes.KEY_LEFTCTRL
                        "alt" -> LinuxKeycodes.KEY_LEFTALT
                        "altgr", "ralt" -> LinuxKeycodes.KEY_RIGHTALT
                        "meta" -> LinuxKeycodes.KEY_LEFTMETA
                        else -> 0
                    }
                if (code != 0 && code !in activeCodes) {
                    activeCodes.add(code)
                }
            }
        }
        _modifiers.values.forEach { it.value = ModifierState.INACTIVE }
        touchDownTimes.clear()
        if (activeCodes.isNotEmpty()) {
            AppLog.i(TAG, "reset: releasing active modifiers in OS: $activeCodes")
            activeCodes.forEach { KeyInjector.keyUp(it) }
        }
    }
}
