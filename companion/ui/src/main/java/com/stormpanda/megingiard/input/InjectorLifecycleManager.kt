package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionSurfaceMode
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
 * - ON: When the relevant control UI is active (e.g. [CompanionSurfaceMode.KEYBOARD] for Keyboard,
 *   [CompanionSurfaceMode.TOUCHPAD] for Mouse), OR when an active MacroPad layout contains buttons for that
 *   injector type and no blocking modal/editor is active.
 * - OFF: When no controls are needed, OR when any editor screen, settings modal, quick menu,
 *   privileged setup wizard, or prompt is active (preventing hardware device conflicts with Android's software IME).
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
                    AppStateManager.companionSurfaceMode,
                    AppStateManager.activePrimaryModal,
                    AppStateManager.isQuickMenuOpen,
                    AppStateManager.promptInFlight,
                    AppStateManager.isPrivdSetupWizardActive,
                    MacroPadState.activeLayout,
                ) { flows ->
                    @Suppress("UNCHECKED_CAST")
                    val surfaceMode = flows[0] as CompanionSurfaceMode
                    val activePrimaryModal = flows[1]
                    val isQuickMenuOpen = flows[2] as Boolean
                    val promptInFlight = flows[3] as Boolean
                    val isPrivdSetupWizardActive = flows[4] as Boolean
                    val activeLayout = flows[5] as? PadLayout
                    calculateInjectorStates(
                        surfaceMode = surfaceMode,
                        isModalOpen = activePrimaryModal != null,
                        isQuickMenuOpen = isQuickMenuOpen,
                        promptInFlight = promptInFlight,
                        isPrivdSetupWizardActive = isPrivdSetupWizardActive,
                        activeLayout = activeLayout,
                    )
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
        surfaceMode: CompanionSurfaceMode,
        isModalOpen: Boolean,
        isQuickMenuOpen: Boolean,
        promptInFlight: Boolean,
        isPrivdSetupWizardActive: Boolean = false,
        activeLayout: PadLayout?,
    ): InjectorStates {
        val isFullscreenKb = surfaceMode == CompanionSurfaceMode.KEYBOARD
        val isFullscreenMouse = surfaceMode == CompanionSurfaceMode.TOUCHPAD

        val isBlockingMacroUse =
            isModalOpen ||
                isQuickMenuOpen ||
                promptInFlight ||
                isPrivdSetupWizardActive ||
                surfaceMode == CompanionSurfaceMode.VIEWPORT_EDIT

        val actions = activeLayout?.buttons?.map { it.action }.orEmpty()
        val hasKeyboardMacros = actions.any { it is PadAction.KeyboardKey }
        val hasGamepadMacros = actions.any { it is PadAction.GamepadButton || it is PadAction.Macro }
        val hasMouseMacros =
            actions.any {
                it is PadAction.MouseButton ||
                    it is PadAction.ScrollWheel ||
                    (it is PadAction.TrackpointMove && it.mode == TrackpointMode.PHYSICAL_MOUSE)
            } || activeLayout?.backgroundTouchpad?.enabled == true
        val hasTouchMacros =
            actions.any {
                (it is PadAction.TrackpointMove && it.mode == TrackpointMode.VIRTUAL_TOUCH) ||
                    it is PadAction.Macro
            }

        val startKeyboard = isFullscreenKb || (hasKeyboardMacros && !isBlockingMacroUse)
        val startMouse = isFullscreenMouse || (hasMouseMacros && !isBlockingMacroUse && !isFullscreenKb)
        val startGamepad = hasGamepadMacros && !isBlockingMacroUse && !isFullscreenKb && !isFullscreenMouse
        val startTouch = hasTouchMacros && !isBlockingMacroUse

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
