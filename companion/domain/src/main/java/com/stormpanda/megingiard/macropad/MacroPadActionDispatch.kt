package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager

// MacroPadState and MacroExecutor are in the same package — no import needed.

private const val TAG = "MacroPadActionDispatch"
internal const val MP_TOUCH_TAP_SLOT = 8
private const val MP_TOUCH_TAP_SLOT_ALT_1 = 9
private const val MP_TOUCH_TAP_SLOT_ALT_2 = 7
private const val MP_TOUCH_TAP_SLOT_ALT_3 = 6
private const val MP_TOUCH_TAP_SLOT_ALT_4 = 5
internal val MP_TOUCH_TAP_SLOT_POOL =
    intArrayOf(
        MP_TOUCH_TAP_SLOT,
        MP_TOUCH_TAP_SLOT_ALT_1,
        MP_TOUCH_TAP_SLOT_ALT_2,
        MP_TOUCH_TAP_SLOT_ALT_3,
        MP_TOUCH_TAP_SLOT_ALT_4,
    )

// ─────────────────────────────────────────────────────────────────────────────
// Injection helpers
// ─────────────────────────────────────────────────────────────────────────────

fun injectActionDown(
    action: PadAction,
    touchSlot: Int = MP_TOUCH_TAP_SLOT,
) {
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
                } else {
                    MacroExecutor.execute(macro)
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

        is PadAction.SwitchToLayout -> {
            AppLog.d(TAG, "actionDown: SwitchToLayout layoutId=${action.layoutId} holdToShift=${action.holdToShift}")
            if (action.holdToShift) {
                MacroPadState.beginHoldToShiftLayout(action.layoutId)
            } else {
                MacroPadState.switchToLayout(action.layoutId)
            }
        }

        is PadAction.SwitchToProfile -> {
            AppLog.d(TAG, "actionDown: SwitchToProfile profileId=${action.profileId}")
            if (MacroPadState.switchToProfile(action.profileId)) {
                AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
            }
        }

        is PadAction.ProfileSwitcher -> {
            AppLog.d(TAG, "actionDown: ProfileSwitcher")
            AppStateManager.openQuickMenu()
        }

        is PadAction.SwitchToHub -> {
            AppLog.d(TAG, "actionDown: SwitchToHub")
            AppStateManager.setCompanionViewMode(CompanionViewMode.DASHBOARD)
        }

        is PadAction.FullScreenMouse -> {
            AppLog.d(TAG, "actionDown: FullScreenMouse sens=${action.sensitivity}")
            AppStateManager.setFullscreenMouseActive(true, action.sensitivity)
        }

        is PadAction.FullScreenKeyboard -> {
            AppLog.d(TAG, "actionDown: FullScreenKeyboard")
            AppStateManager.setFullscreenKeyboardActive(true)
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

        is PadAction.Screenshot -> {
            AppLog.d(TAG, "actionDown: Screenshot target=${action.target}")
            ScreenCaptureManager.requestScreenshot(action.target)
        }

        is PadAction.StopAllMacros -> {
            AppLog.d(TAG, "actionDown: StopAllMacros")
            MacroExecutor.stopAll()
        }

        is PadAction.TouchTap -> {
            val clampedX = action.normX.coerceIn(0f, 1f)
            val clampedY = action.normY.coerceIn(0f, 1f)
            AppLog.d(TAG, "actionDown: TouchTap slot=$touchSlot normX=$clampedX normY=$clampedY")
            TouchInjector.injectTouch(touchSlot, TouchAction.DOWN, clampedX, clampedY)
        }

        is PadAction.MediaControl -> {
            AppLog.d(TAG, "actionDown: MediaControl action=${action.mediaAction} keycode=${action.mediaAction.keycode}")
            KeyInjector.keyDown(action.mediaAction.keycode)
        }
    }
}

fun injectActionUp(
    action: PadAction,
    touchSlot: Int = MP_TOUCH_TAP_SLOT,
) {
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

        is PadAction.SwitchToLayout -> {
            if (action.holdToShift) {
                AppLog.d(TAG, "actionUp: SwitchToLayout holdToShift layoutId=${action.layoutId}")
                MacroPadState.endHoldToShiftLayout(action.layoutId)
            }
        }

        is PadAction.TouchTap -> {
            val clampedX = action.normX.coerceIn(0f, 1f)
            val clampedY = action.normY.coerceIn(0f, 1f)
            AppLog.d(TAG, "actionUp: TouchTap slot=$touchSlot normX=$clampedX normY=$clampedY")
            TouchInjector.injectTouch(touchSlot, TouchAction.UP, clampedX, clampedY)
        }

        is PadAction.MediaControl -> {
            AppLog.d(TAG, "actionUp: MediaControl action=${action.mediaAction} keycode=${action.mediaAction.keycode}")
            KeyInjector.keyUp(action.mediaAction.keycode)
        }

        else -> { /* All other action types fire or toggle on down; up is no-op */ }
    }
}
