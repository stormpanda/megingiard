package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager

// MacroPadState and MacroExecutor are in the same package — no import needed.

private const val TAG = "MacroPadActionDispatch"

// ─────────────────────────────────────────────────────────────────────────────
// Injection helpers
// ─────────────────────────────────────────────────────────────────────────────

fun injectActionDown(action: PadAction) {
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
            val macro = MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == action.macroId }
            val running = MacroExecutor.isRunning(action.macroId)
            AppLog.d(TAG, "actionDown: Macro id=${action.macroId} found=${macro != null} running=$running")
            if (macro != null) {
                if (running) MacroExecutor.stop(action.macroId) else MacroExecutor.execute(macro)
            }
        }
        is PadAction.AmbientPeek          -> { AppLog.d(TAG, "actionDown: AmbientPeek"); MacroPadState.togglePeek() }
        is PadAction.LayoutNext             -> { AppLog.d(TAG, "actionDown: LayoutNext"); MacroPadState.nextLayout() }
        is PadAction.LayoutPrevious         -> { AppLog.d(TAG, "actionDown: LayoutPrevious"); MacroPadState.previousLayout() }
        is PadAction.ProfileSwitcher        -> { AppLog.d(TAG, "actionDown: ProfileSwitcher"); AppStateManager.openPillMenu() }
        is PadAction.FullScreenMouse        -> { AppLog.d(TAG, "actionDown: FullScreenMouse sens=${action.sensitivity}"); AppStateManager.setFullscreenMouseActive(true, action.sensitivity) }
        is PadAction.FullScreenKeyboard     -> { AppLog.d(TAG, "actionDown: FullScreenKeyboard layout=${action.layout}"); AppStateManager.setFullscreenKeyboardActive(true, action.layout) }
        is PadAction.MirrorPlayStop         -> {
            AppLog.d(TAG, "actionDown: MirrorPlayStop capturing=${ScreenCaptureManager.isCapturing.value}")
            if (ScreenCaptureManager.isCapturing.value) {
                AppStateManager.requestMirrorStop()
            } else {
                AppStateManager.requestMirrorStart()
            }
        }
        is PadAction.MirrorFreeze           -> { AppLog.d(TAG, "actionDown: MirrorFreeze"); ScreenCaptureManager.toggleFrozen() }
        is PadAction.MirrorViewportEdit     -> { AppLog.d(TAG, "actionDown: MirrorViewportEdit"); AppStateManager.setViewportEditActive(true) }
        is PadAction.MirrorTouchProjection  -> { AppLog.d(TAG, "actionDown: MirrorTouchProjection"); ScreenCaptureManager.toggleTouchProjection() }
    }
}

fun injectActionUp(action: PadAction) {
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
        is PadAction.Macro                 -> { /* toggle on down; up is no-op */ }
        is PadAction.AmbientPeek            -> { /* toggle on down; up is no-op */ }
        is PadAction.LayoutNext             -> { /* fires on down; up is no-op */ }
        is PadAction.LayoutPrevious         -> { /* fires on down; up is no-op */ }
        is PadAction.ProfileSwitcher        -> { /* fires on down; up is no-op */ }
        is PadAction.FullScreenMouse        -> { /* fires on down; up is no-op */ }
        is PadAction.FullScreenKeyboard     -> { /* fires on down; up is no-op */ }
        is PadAction.MirrorPlayStop         -> { /* fires on down; up is no-op */ }
        is PadAction.MirrorFreeze           -> { /* fires on down; up is no-op */ }
        is PadAction.MirrorViewportEdit     -> { /* fires on down; up is no-op */ }
        is PadAction.MirrorTouchProjection  -> { /* fires on down; up is no-op */ }
    }
}
