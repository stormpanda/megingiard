package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector

// MacroState and MacroExecutor are in the same package — no import needed.

private const val TAG = "MacroPadActionDispatch"

// ─────────────────────────────────────────────────────────────────────────────
// Injection helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun injectActionDown(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey -> {
            AppLog.d(TAG, "actionDown: KeyboardKey keycode=${action.keycode} modifiers=${action.modifiers}")
            action.modifiers.forEach { KeyInjector.keyDown(it) }
            KeyInjector.keyDown(action.keycode)
        }
        is PadAction.GamepadButton -> {
            AppLog.d(TAG, "actionDown: GamepadButton code=${action.btnCode} extras=${action.extraBtnCodes}")
            GamepadInjector.buttonDown(action.btnCode)
            action.extraBtnCodes.forEach { GamepadInjector.buttonDown(it) }
        }
        is PadAction.MouseButton     -> {
            AppLog.d(TAG, "actionDown: MouseButton ${action.button}")
            when (action.button) {
                MouseButton.LEFT   -> MouseInjector.leftDown()
                MouseButton.RIGHT  -> MouseInjector.rightDown()
                MouseButton.MIDDLE -> MouseInjector.middleDown()
                MouseButton.MOUSE4 -> MouseInjector.mouse4Down()
                MouseButton.MOUSE5 -> MouseInjector.mouse5Down()
            }
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        is PadAction.Macro           -> {
            val macro = MacroState.macros.value.firstOrNull { it.id == action.macroId }
            AppLog.d(TAG, "actionDown: Macro id=${action.macroId} found=${macro != null}")
            if (macro != null) MacroExecutor.execute(macro)
        }
        is PadAction.AmbientPeek     -> { AppLog.d(TAG, "actionDown: AmbientPeek"); MacroPadState.togglePeek() }
        is PadAction.MouseLeftClick  -> { AppLog.d(TAG, "actionDown: MouseLeftClick"); MouseInjector.leftDown() }
        is PadAction.MouseRightClick -> { AppLog.d(TAG, "actionDown: MouseRightClick"); MouseInjector.rightDown() }
    }
}

internal fun injectActionUp(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey -> {
            AppLog.d(TAG, "actionUp: KeyboardKey keycode=${action.keycode} modifiers=${action.modifiers}")
            KeyInjector.keyUp(action.keycode)
            action.modifiers.reversed().forEach { KeyInjector.keyUp(it) }
        }
        is PadAction.GamepadButton -> {
            AppLog.d(TAG, "actionUp: GamepadButton code=${action.btnCode} extras=${action.extraBtnCodes}")
            action.extraBtnCodes.reversed().forEach { GamepadInjector.buttonUp(it) }
            GamepadInjector.buttonUp(action.btnCode)
        }
        is PadAction.MouseButton     -> {
            AppLog.d(TAG, "actionUp: MouseButton ${action.button}")
            when (action.button) {
                MouseButton.LEFT   -> MouseInjector.leftUp()
                MouseButton.RIGHT  -> MouseInjector.rightUp()
                MouseButton.MIDDLE -> MouseInjector.middleUp()
                MouseButton.MOUSE4 -> MouseInjector.mouse4Up()
                MouseButton.MOUSE5 -> MouseInjector.mouse5Up()
            }
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        is PadAction.Macro           -> { /* fire-and-forget on down; up is no-op */ }
        is PadAction.AmbientPeek     -> { /* toggle on down; up is no-op */ }
        is PadAction.MouseLeftClick  -> { AppLog.d(TAG, "actionUp: MouseLeftClick"); MouseInjector.leftUp() }
        is PadAction.MouseRightClick -> { AppLog.d(TAG, "actionUp: MouseRightClick"); MouseInjector.rightUp() }
    }
}
