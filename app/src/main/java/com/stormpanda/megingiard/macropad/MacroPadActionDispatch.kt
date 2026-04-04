package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector

// ─────────────────────────────────────────────────────────────────────────────
// Injection helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun injectActionDown(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey     -> KeyInjector.keyDown(action.keycode)
        is PadAction.GamepadButton   -> GamepadInjector.buttonDown(action.btnCode)
        is PadAction.MouseButton     -> when (action.button) {
            MouseButton.LEFT   -> MouseInjector.leftDown()
            MouseButton.RIGHT  -> MouseInjector.rightDown()
            MouseButton.MIDDLE -> MouseInjector.middleDown()
            MouseButton.MOUSE4 -> MouseInjector.mouse4Down()
            MouseButton.MOUSE5 -> MouseInjector.mouse5Down()
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        is PadAction.Macro           -> { /* handled by MacroPadScreen recording/playback logic */ }
        is PadAction.MouseLeftClick  -> MouseInjector.leftDown()
        is PadAction.MouseRightClick -> MouseInjector.rightDown()
    }
}

internal fun injectActionUp(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey     -> KeyInjector.keyUp(action.keycode)
        is PadAction.GamepadButton   -> GamepadInjector.buttonUp(action.btnCode)
        is PadAction.MouseButton     -> when (action.button) {
            MouseButton.LEFT   -> MouseInjector.leftUp()
            MouseButton.RIGHT  -> MouseInjector.rightUp()
            MouseButton.MIDDLE -> MouseInjector.middleUp()
            MouseButton.MOUSE4 -> MouseInjector.mouse4Up()
            MouseButton.MOUSE5 -> MouseInjector.mouse5Up()
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        is PadAction.Macro           -> { /* handled by MacroPadScreen recording/playback logic */ }
        is PadAction.MouseLeftClick  -> MouseInjector.leftUp()
        is PadAction.MouseRightClick -> MouseInjector.rightUp()
    }
}

/**
 * Replays a single [MacroInputEvent] through the appropriate injector.
 * Call from the macro playback coroutine on any thread — injectors are thread-safe.
 */
internal fun injectMacroEvent(event: MacroInputEvent) {
    when (event) {
        is MacroInputEvent.GamepadButtonDown -> GamepadInjector.buttonDown(event.code)
        is MacroInputEvent.GamepadButtonUp   -> GamepadInjector.buttonUp(event.code)
        is MacroInputEvent.GamepadAxis       -> GamepadInjector.axis(event.axis, event.value)
        is MacroInputEvent.GamepadHat        -> GamepadInjector.hat(event.axis, event.value)
    }
}

/**
 * Auto-trims a raw list of timestamped events: the first event becomes t=0
 * (eliminating leading dead time) and returns only up to the last recorded event
 * (trailing dead time is never stored since recording stops on user tap).
 */
internal fun autoTrim(raw: List<Pair<Long, MacroInputEvent>>): List<MacroEvent> {
    if (raw.isEmpty()) return emptyList()
    val firstMs = raw.first().first
    return raw.map { (ts, input) -> MacroEvent(relativeTimeMs = ts - firstMs, input = input) }
}

