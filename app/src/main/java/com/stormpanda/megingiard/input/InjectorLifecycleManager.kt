package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.UiMode
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.TrackpointMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "InjectorLifecycleManager"
private const val DEBOUNCE_MS = 150L

internal data class InjectorStates(
    val startKeyboard: Boolean,
    val startMouse: Boolean,
    val startGamepad: Boolean,
    val startTouch: Boolean,
)

/**
 * Centralized single source of truth for input injector lifecycles across the application.
 *
 * Implements the 2-sentence rule for input injectors:
 * - ON: When the relevant control UI is active (e.g. [UiMode.FULLSCREEN_KEYBOARD] for Keyboard,
 *   [UiMode.FULLSCREEN_MOUSE] for Mouse), OR when an active MacroPad layout contains buttons for that
 *   injector type and no blocking modal/editor is active.
 * - OFF: When no controls are needed, OR when any editor screen, settings modal, quick menu,
 *   or prompt is active (preventing hardware device conflicts with Android's software IME).
 */
object InjectorLifecycleManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcherJob: Job? = null

    @Synchronized
    fun watch(context: Context) {
        if (watcherJob?.isActive == true) return
        val appContext = context.applicationContext

        watcherJob =
            scope.launch {
                combine(
                    AppStateManager.uiMode,
                    AppStateManager.promptInFlight,
                    MacroPadState.activeLayout,
                ) { uiMode, promptInFlight, activeLayout ->
                    calculateInjectorStates(uiMode, promptInFlight, activeLayout)
                }.distinctUntilChanged()
                    .collectLatest { states ->
                        if (!states.startKeyboard) {
                            AppLog.d(TAG, "stopping KeyInjector")
                            KeyInjector.stop()
                        }
                        if (!states.startMouse) {
                            AppLog.d(TAG, "stopping MouseInjector")
                            MouseInjector.stop()
                        }
                        if (!states.startGamepad) {
                            AppLog.d(TAG, "stopping GamepadInjector")
                            GamepadInjector.stop()
                        }
                        if (!states.startTouch) {
                            TouchInjector.stop("InjectorLifecycleManager")
                        }

                        delay(DEBOUNCE_MS)
                        withContext(Dispatchers.IO) {
                            if (states.startKeyboard) {
                                KeyInjector.start(appContext)
                            }
                            if (states.startMouse) {
                                MouseInjector.start(appContext)
                            }
                            if (states.startGamepad) {
                                GamepadInjector.start(appContext)
                            }
                            if (states.startTouch) {
                                TouchInjector.start(appContext, "InjectorLifecycleManager")
                            }
                        }
                    }
            }
    }

    internal fun calculateInjectorStates(
        uiMode: UiMode,
        promptInFlight: Boolean,
        activeLayout: PadLayout?,
    ): InjectorStates {
        val isEditorModalOrMenu =
            uiMode == UiMode.LAYOUT_EDITOR ||
                uiMode == UiMode.BACKGROUND_SETTINGS ||
                uiMode == UiMode.KEYBOARD_SETTINGS ||
                uiMode == UiMode.TOUCHPAD_SETTINGS ||
                uiMode == UiMode.GLOBAL_SETTINGS ||
                uiMode == UiMode.VIEWPORT_EDIT ||
                uiMode == UiMode.QUICK_MENU ||
                promptInFlight

        val isFullscreenKb = uiMode == UiMode.FULLSCREEN_KEYBOARD
        val isFullscreenMouse = uiMode == UiMode.FULLSCREEN_MOUSE

        val hasKeyboardMacros =
            activeLayout?.buttons?.any { it.action is PadAction.KeyboardKey } == true
        val hasGamepadMacros =
            activeLayout?.buttons?.any { it.action is PadAction.GamepadButton || it.action is PadAction.Macro } == true
        val hasMouseMacros =
            activeLayout?.buttons?.any {
                it.action is PadAction.MouseButton ||
                    it.action is PadAction.ScrollWheel ||
                    (
                        it.action is PadAction.TrackpointMove &&
                            (it.action as PadAction.TrackpointMove).mode == TrackpointMode.PHYSICAL_MOUSE
                    )
            } == true || activeLayout?.backgroundTouchpad?.enabled == true
        val hasTouchMacros =
            activeLayout?.buttons?.any {
                (
                    it.action is PadAction.TrackpointMove &&
                        (it.action as PadAction.TrackpointMove).mode == TrackpointMode.VIRTUAL_TOUCH
                ) || it.action is PadAction.Macro
            } == true

        val startKeyboard = isFullscreenKb || (hasKeyboardMacros && !isEditorModalOrMenu)
        val startMouse = isFullscreenMouse || (hasMouseMacros && !isEditorModalOrMenu && !isFullscreenKb)
        val startGamepad = hasGamepadMacros && !isEditorModalOrMenu && !isFullscreenKb && !isFullscreenMouse
        val startTouch = hasTouchMacros && !isEditorModalOrMenu

        return InjectorStates(
            startKeyboard = startKeyboard,
            startMouse = startMouse,
            startGamepad = startGamepad,
            startTouch = startTouch,
        )
    }

    @Synchronized
    fun stopAll() {
        AppLog.i(TAG, "stopAll called")
        watcherJob?.cancel()
        watcherJob = null
        KeyInjector.stop()
        MouseInjector.stop()
        GamepadInjector.stop()
        TouchInjector.stop("InjectorLifecycleManager")
    }
}
