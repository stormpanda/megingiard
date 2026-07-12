package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroPadHitTestEngine
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MacroPadViewModel"

/** Debounce window for injector restart to absorb rapid modal open→close→open sequences. */
private const val INJECTOR_RESTART_DEBOUNCE_MS = 150L

private data class InjectorGate(
    val stopKeyboard: Boolean,
    val stopMouse: Boolean,
    val stopGamepad: Boolean,
)

/**
 * ViewModel for [MacroPadScreen] — manages multi-injector lifecycle
 * and hit-test engine.
 *
 * Injector lifecycle rule:
 *   - Quick Menu open: stop mouse/gamepad injectors only.
 *   - Blocking modal open (Editor/Ambient Settings) or system prompt in flight:
 *     stop all injectors including keyboard.
 *   - Restart as soon as all guards are clear.
 *
 * [watchInjectorLifecycle] is the single authoritative restart path.
 * [MacroPadEditor] and [BackgroundSettingsOverlay] only stop injectors on entry;
 * they do NOT restart on exit — this watcher handles that.
 */
class MacroPadViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val activeProfile: StateFlow<PadProfile?> = MacroPadState.activeProfile
    val activeLayout: StateFlow<PadLayout?> = MacroPadState.activeLayout
    val isQuickMenuOpen: StateFlow<Boolean> = AppStateManager.isQuickMenuOpen

    fun createHitTestEngine(
        buttonUnitDpToPx: (Float) -> Float,
        onHapticFeedback: ((String, HapticStrength, Int, Int, Float) -> Unit)? = null,
    ) = MacroPadHitTestEngine(buttonUnitDpToPx, onHapticFeedback)

    /**
     * Starts a long-lived watcher that reacts to menu/modal/prompt flags.
     * Called once from [MacroPadScreen]'s LaunchedEffect(Unit).
     *
     * Quick Menu open → stop mouse/gamepad immediately.
     * Blocking modal open or prompt in flight → stop all injectors immediately.
     * When all guards are clear → restart injectors for the active profile.
     */
    fun watchInjectorLifecycle(context: Context) {
        viewModelScope.launch {
            combine(
                AppStateManager.isQuickMenuOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
                AppStateManager.promptInFlight,
                AppStateManager.isFullscreenKeyboardActive,
                AppStateManager.isFullscreenMouseActive,
                AppStateManager.isViewportEditActive,
            ) { array ->
                val quickMenu = array[0]
                val editor = array[1]
                val ambient = array[2]
                val prompt = array[3]
                val kb = array[4]
                val mouse = array[5]
                val vp = array[6]

                val blockingModal = editor || ambient || prompt || vp
                InjectorGate(
                    stopKeyboard = blockingModal && !kb,
                    stopMouse = blockingModal && !kb && !mouse,
                    stopGamepad = blockingModal || quickMenu || kb || mouse,
                )
            }.distinctUntilChanged()
                .collectLatest { gate ->
                    if (gate.stopKeyboard) {
                        AppLog.d(TAG, "stopping keyboard injector")
                        KeyInjector.stop()
                    }
                    if (gate.stopMouse) {
                        AppLog.d(TAG, "stopping mouse injector")
                        MouseInjector.stop()
                    }
                    if (gate.stopGamepad) {
                        AppLog.d(TAG, "stopping gamepad injector")
                        GamepadInjector.stop()
                    }

                    // Absorb rapid transitions (e.g. QuickMenu closes then Editor opens
                    // in the same frame). collectLatest will cancel this branch
                    // if any gate flips back to stop-mode within the delay window.
                    delay(INJECTOR_RESTART_DEBOUNCE_MS)
                    withContext(Dispatchers.IO) {
                        val ap = MacroPadState.activeProfile.value
                        val blockingModalActive =
                            editorStateFlowValue() || ambientStateFlowValue() || promptStateFlowValue() || vpStateFlowValue()

                        if (!gate.stopKeyboard && ap?.enableKeyboard == true) {
                            KeyInjector.start(context)
                        }
                        if (!gate.stopGamepad && ap?.enableGamepad == true) {
                            GamepadInjector.start(context)
                        }
                        if (!gate.stopMouse && ap?.enableMouse == true) {
                            MouseInjector.start(context)
                        }

                        if (ap?.enableTouch == true) {
                            if (!blockingModalActive) {
                                TouchInjector.start(context, "MacroPadViewModel")
                            } else {
                                TouchInjector.stop("MacroPadViewModel")
                            }
                        }
                    }
                }
        }
    }

    private fun editorStateFlowValue() = AppStateManager.isEditorActive.value

    private fun ambientStateFlowValue() = AppStateManager.isBackgroundSettingsActive.value

    private fun promptStateFlowValue() = AppStateManager.promptInFlight.value

    private fun vpStateFlowValue() = AppStateManager.isViewportEditActive.value

    fun stopInjectors() {
        AppLog.i(TAG, "stopInjectors called")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        TouchInjector.stop("MacroPadViewModel")
        MacroPadState.resetPeek()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → all injectors stopped")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        TouchInjector.stop("MacroPadViewModel")
        MacroPadState.resetPeek()
    }
}
