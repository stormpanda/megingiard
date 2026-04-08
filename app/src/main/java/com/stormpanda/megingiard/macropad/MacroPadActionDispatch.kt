package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector

// MacroState and MacroExecutor are in the same package — no import needed.

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
        is PadAction.Macro           -> {
            val macro = MacroState.macros.value.firstOrNull { it.id == action.macroId }
            if (macro != null) MacroExecutor.execute(macro)
        }
        is PadAction.AmbientPeek     -> MacroPadState.togglePeek()
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
        is PadAction.Macro           -> { /* fire-and-forget on down; up is no-op */ }
        is PadAction.AmbientPeek     -> { /* toggle on down; up is no-op */ }
        is PadAction.MouseLeftClick  -> MouseInjector.leftUp()
        is PadAction.MouseRightClick -> MouseInjector.rightUp()
    }
}
