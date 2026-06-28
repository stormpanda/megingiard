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
    val stopMouseAndGamepad: Boolean,
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
class MacroPadViewModel(application: Application) : AndroidViewModel(application) {

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
                val stopAll = editor || ambient || prompt || kb || mouse || vp
                InjectorGate(
                    stopKeyboard = stopAll,
                    stopMouseAndGamepad = stopAll || quickMenu,
                )
            }.distinctUntilChanged()
            .collectLatest { gate ->
                when {
                    gate.stopKeyboard -> {
                        AppLog.i(TAG, "blocking modal/prompt open \u2192 stopping keyboard/gamepad/mouse injectors")
                        KeyInjector.stop()
                        GamepadInjector.stop()
                        MouseInjector.stop()
                    }
                    gate.stopMouseAndGamepad -> {
                        AppLog.i(TAG, "quick menu open \u2192 stopping gamepad/mouse injectors")
                        GamepadInjector.stop()
                        MouseInjector.stop()
                    }
                    else -> {
                        // Absorb rapid transitions (e.g. QuickMenu closes then Editor opens
                        // in the same frame).  collectLatest will cancel this branch
                        // if any gate flips back to stop-mode within the delay window,
                        // preventing
                        // a spurious injector restart from racing ahead of the modal open.
                        delay(INJECTOR_RESTART_DEBOUNCE_MS)
                        withContext(Dispatchers.IO) {
                            val ap = MacroPadState.activeProfile.value
                            AppLog.i(TAG, "all guards clear \u2192 starting injectors for profile '${ap?.name}' (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse} ts=${ap?.enableTouch})")
                            if (ap?.enableKeyboard == true) KeyInjector.start(context)
                            if (ap?.enableGamepad == true) GamepadInjector.start(context)
                            if (ap?.enableMouse == true) MouseInjector.start(context)
                            if (ap?.enableTouch == true) TouchInjector.start(context, "MacroPadViewModel")
                        }
                    }
                }
            }
        }
    }

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
