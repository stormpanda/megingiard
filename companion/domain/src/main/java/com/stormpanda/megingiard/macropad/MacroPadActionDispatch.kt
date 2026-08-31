package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.privd.PrivdClient

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

        is PadAction.MouseButton -> {
            AppLog.d(TAG, "actionDown: MouseButton ${action.button}")
            MouseInjector.buttonDown(action.button)
        }

        is PadAction.ScrollWheel -> { /* handled via drag events */ }

        is PadAction.TrackpointMove -> { /* handled via drag events */ }

        is PadAction.Macro -> {
            val macro =
                MacroPadState.activeProfile.value
                    ?.macros
                    ?.firstOrNull { it.id == action.macroId }
            val running = MacroExecutor.isRunning(action.macroId)
            AppLog.d(TAG, "actionDown: Macro id=${action.macroId} found=${macro != null} running=$running")
            if (macro != null) {
                if (running) {
                    MacroExecutor.stop(action.macroId)
                } else if (PrivdClient.isConnected) {
                    MacroExecutor.execute(macro)
                } else {
                    AppLog.w(TAG, "Cannot execute macro '${macro.name}': Privileged Mode is not connected")
                }
            }
        }

        is PadAction.BackgroundPeek -> {
            AppLog.d(TAG, "actionDown: BackgroundPeek")
            MacroPadState.togglePeek()
        }

        is PadAction.LayoutNext -> {
            AppLog.d(TAG, "actionDown: LayoutNext")
            MacroPadState.nextLayout()
        }

        is PadAction.LayoutPrevious -> {
            AppLog.d(TAG, "actionDown: LayoutPrevious")
            MacroPadState.previousLayout()
        }

        is PadAction.ProfileSwitcher -> {
            AppLog.d(TAG, "actionDown: ProfileSwitcher")
            AppStateManager.openQuickMenu()
        }

        is PadAction.FullScreenMouse -> {
            AppLog.d(TAG, "actionDown: FullScreenMouse sens=${action.sensitivity}")
            AppStateManager.setFullscreenMouseActive(true, action.sensitivity)
        }

        is PadAction.FullScreenKeyboard -> {
            AppLog.d(TAG, "actionDown: FullScreenKeyboard layout=${action.layout}")
            AppStateManager.setFullscreenKeyboardActive(true, action.layout)
        }

        is PadAction.MirrorPlayStop -> {
            AppLog.d(TAG, "actionDown: MirrorPlayStop capturing=${ScreenCaptureManager.isCapturing.value}")
            if (ScreenCaptureManager.isCapturing.value) {
                AppStateManager.requestMirrorStop()
            } else {
                AppStateManager.requestMirrorStart()
            }
        }

        is PadAction.MirrorFreeze -> {
            AppLog.d(TAG, "actionDown: MirrorFreeze")
            ScreenCaptureManager.toggleFrozen()
        }

        is PadAction.MirrorViewportEdit -> {
            AppLog.d(TAG, "actionDown: MirrorViewportEdit")
            AppStateManager.setViewportEditActive(true)
        }

        is PadAction.MirrorTouchProjection -> {
            AppLog.d(TAG, "actionDown: MirrorTouchProjection")
            ScreenCaptureManager.toggleTouchProjection()
        }

        is PadAction.AppLauncher -> {
            AppLog.d(TAG, "actionDown: AppLauncher pkg=${action.packageName}")
            if (action.packageName.isNotBlank()) {
                AppStateManager.requestAppLaunch(action.packageName)
            }
        }
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

        is PadAction.MouseButton -> {
            AppLog.d(TAG, "actionUp: MouseButton ${action.button}")
            MouseInjector.buttonUp(action.button)
        }

        else -> { /* All other action types fire or toggle on down; up is no-op */ }
    }
}
