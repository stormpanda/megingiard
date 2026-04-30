package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Suppress("unused")
private const val TAG = "KeyRepeatController"

private const val KB_REPEAT_INITIAL_DELAY_MS = 500L
private const val KB_REPEAT_INTERVAL_MS = 30L
private const val KB_MODIFIER_HOLD_MS = 300L
private const val KB_TRACKPOINT_MOUSE_SENSITIVITY = 3f

/**
 * Orchestrates keyboard key dispatch, repeat, modifier hold, and trackpoint
 * mouse movement — all Compose-free business logic extracted from [KeyboardScreen].
 *
 * The UI calls `onKeyDown` / `onKeyUp` / `onKeySlide` with primitive types;
 * this controller drives [KeyInjector], [KeyboardState], and [MouseInjector].
 *
 * Create one instance per keyboard screen session.
 */
class KeyRepeatController(private val scope: CoroutineScope) {

    private val _pressedKeys = MutableStateFlow(emptySet<String>())
    /** Set of currently pressed key IDs (for visual highlighting in the UI). */
    val pressedKeys: StateFlow<Set<String>> = _pressedKeys.asStateFlow()

    private val _trackpointVisible = MutableStateFlow(false)
    /** Whether the trackpoint overlay should be shown. */
    val trackpointVisible: StateFlow<Boolean> = _trackpointVisible.asStateFlow()

    // Per-pointer tracking
    private val pointerKeyMap = mutableMapOf<Long, String>()
    private val trackpointPointers = mutableSetOf<Long>()

    // Repeat / hold jobs
    private var repeatJob: Job? = null
    private var modifierHoldJob: Job? = null
    private var heldKey: KeyDef? = null
    private var modifierBeingHeld: KeyDef? = null

    /**
     * Handle a pointer press on a key.
     *
     * @param pointerId  unique pointer ID
     * @param keyId      key ID (from hit-test) or null if no key was hit
     * @param layout     current keyboard layout rows
     * @param repeatEnabled  whether key repeat is enabled in settings
     * @return true if the key was handled and the pointer should be consumed
     */
    fun onKeyDown(
        pointerId: Long,
        keyId: String?,
        layout: List<List<KeyDef>>,
        repeatEnabled: Boolean,
    ): Boolean {
        val id = keyId ?: return false
        val keyDef = findKeyInLayout(layout, id) ?: return false

        when (keyDef.type) {
            KeyType.NORMAL -> {
                pointerKeyMap[pointerId] = id
                _pressedKeys.value = _pressedKeys.value + id
                heldKey = keyDef
                if (keyDef.linuxKeycode != 0) {
                    KeyboardState.activeModifierKeycodes(layout)
                        .forEach { KeyInjector.keyDown(it) }
                    KeyInjector.keyDown(keyDef.linuxKeycode)
                    if (!repeatEnabled) {
                        KeyInjector.keyUp(keyDef.linuxKeycode)
                        KeyboardState.activeModifierKeycodes(layout)
                            .forEach { KeyInjector.keyUp(it) }
                    }
                }
                startRepeat(keyDef, layout, repeatEnabled)
            }
            KeyType.MODIFIER -> {
                pointerKeyMap[pointerId] = id
                modifierBeingHeld = keyDef
                KeyboardState.onModifierTouchDown(id)
                startModifierHold(keyDef)
            }
            KeyType.TRACKPOINT -> {
                trackpointPointers += pointerId
                pointerKeyMap[pointerId] = id
                _trackpointVisible.value = true
            }
        }
        return true
    }

    /**
     * Handle a pointer move (slide) over the keyboard.
     *
     * @param pointerId  the pointer that moved
     * @param newKeyId   key ID at the new position (from hit test)
     * @param deltaX     position change X (device pixels)
     * @param deltaY     position change Y (device pixels)
     * @param layout     current keyboard layout
     * @param repeatEnabled  whether key repeat is enabled
     * @return true if the event was handled
     */
    fun onKeyMove(
        pointerId: Long,
        newKeyId: String?,
        deltaX: Float,
        deltaY: Float,
        layout: List<List<KeyDef>>,
        repeatEnabled: Boolean,
    ): Boolean {
        // Trackpoint pointer: drive cursor
        if (pointerId in trackpointPointers) {
            val dx = (deltaX * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
            val dy = (deltaY * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
            if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
            return true
        }

        val prevId = pointerKeyMap[pointerId] ?: return false
        val newId = newKeyId ?: return false
        if (prevId == newId) return false

        val prevDef = findKeyInLayout(layout, prevId)
        val newDef = findKeyInLayout(layout, newId) ?: return false

        // Release previous NORMAL key
        if (prevDef?.type == KeyType.NORMAL && prevDef.linuxKeycode != 0) {
            heldKey = null
            repeatJob?.cancel()
            if (repeatEnabled) {
                KeyInjector.keyUp(prevDef.linuxKeycode)
                KeyboardState.activeModifierKeycodes(layout)
                    .forEach { KeyInjector.keyUp(it) }
            }
            _pressedKeys.value = _pressedKeys.value - prevId
        }

        // Press new NORMAL key
        if (newDef.type == KeyType.NORMAL) {
            pointerKeyMap[pointerId] = newId
            _pressedKeys.value = _pressedKeys.value + newId
            heldKey = newDef
            if (newDef.linuxKeycode != 0) {
                KeyboardState.activeModifierKeycodes(layout)
                    .forEach { KeyInjector.keyDown(it) }
                KeyInjector.keyDown(newDef.linuxKeycode)
                if (!repeatEnabled) {
                    KeyInjector.keyUp(newDef.linuxKeycode)
                    KeyboardState.activeModifierKeycodes(layout)
                        .forEach { KeyInjector.keyUp(it) }
                }
            }
            startRepeat(newDef, layout, repeatEnabled)
            return true
        }
        return false
    }

    /**
     * Handle a pointer release.
     *
     * @param pointerId  the pointer that released
     * @param layout     current keyboard layout
     * @param repeatEnabled  whether key repeat is enabled
     * @return true if the event was handled
     */
    fun onKeyUp(
        pointerId: Long,
        layout: List<List<KeyDef>>,
        repeatEnabled: Boolean,
    ): Boolean {
        // Trackpoint pointer release
        if (pointerId in trackpointPointers) {
            trackpointPointers -= pointerId
            pointerKeyMap.remove(pointerId)
            if (trackpointPointers.isEmpty()) _trackpointVisible.value = false
            return true
        }

        val releasedId = pointerKeyMap.remove(pointerId) ?: return false
        val keyDef = findKeyInLayout(layout, releasedId) ?: return false

        when (keyDef.type) {
            KeyType.NORMAL -> {
                heldKey = null
                repeatJob?.cancel()
                if (keyDef.linuxKeycode != 0 && repeatEnabled) {
                    KeyInjector.keyUp(keyDef.linuxKeycode)
                    KeyboardState.releaseStickyModifiers(layout)
                        .forEach { KeyInjector.keyUp(it) }
                } else if (keyDef.linuxKeycode != 0) {
                    KeyboardState.releaseStickyModifiers(layout)
                        .forEach { KeyInjector.keyUp(it) }
                }
                _pressedKeys.value = _pressedKeys.value - releasedId
            }
            KeyType.MODIFIER -> {
                modifierBeingHeld = null
                modifierHoldJob?.cancel()
                KeyboardState.onModifierTouchUp(releasedId, keyDef.linuxKeycode)
                    .forEach { KeyInjector.keyUp(it) }
            }
            KeyType.TRACKPOINT -> { /* handled in trackpoint block above */ }
        }
        return true
    }

    /** Whether a given pointer is being tracked as a trackpoint finger. */
    fun isTrackpointPointer(pointerId: Long): Boolean = pointerId in trackpointPointers

    /** Clean up all state. Call when the keyboard screen leaves composition. */
    fun dispose() {
        repeatJob?.cancel()
        modifierHoldJob?.cancel()
        pointerKeyMap.clear()
        trackpointPointers.clear()
        _pressedKeys.value = emptySet()
        _trackpointVisible.value = false
        heldKey = null
        modifierBeingHeld = null
    }

    private fun startRepeat(keyDef: KeyDef, layout: List<List<KeyDef>>, enabled: Boolean) {
        repeatJob?.cancel()
        if (!enabled || keyDef.linuxKeycode == 0) return
        repeatJob = scope.launch {
            delay(KB_REPEAT_INITIAL_DELAY_MS)
            while (heldKey == keyDef) {
                val mods = KeyboardState.activeModifierKeycodes(layout)
                mods.forEach { KeyInjector.keyDown(it) }
                KeyInjector.keyDown(keyDef.linuxKeycode)
                KeyInjector.keyUp(keyDef.linuxKeycode)
                mods.forEach { KeyInjector.keyUp(it) }
                delay(KB_REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun startModifierHold(keyDef: KeyDef) {
        modifierHoldJob?.cancel()
        modifierHoldJob = scope.launch {
            delay(KB_MODIFIER_HOLD_MS)
            if (modifierBeingHeld == keyDef) {
                val keycode = KeyboardState.onModifierLongPress(keyDef.id, keyDef.linuxKeycode)
                if (keycode != null) KeyInjector.keyDown(keycode)
            }
        }
    }
}
