package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.InjectorLifecycleWatcher
import com.stormpanda.megingiard.macropad.MacroPadHitTestEngine
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "MacroPadViewModel"

/**
 * ViewModel for [MacroPadScreen] — manages multi-injector lifecycle
 * and hit-test engine.
 *
 * Injector lifecycle rule:
 *   - Stop immediately whenever any blocking modal opens (Pill Menu, Editor, Ambient Settings).
 *   - Restart as soon as ALL three are closed simultaneously.
 *
 * [watchInjectorLifecycle] delegates to [InjectorLifecycleWatcher] — the canonical
 * restart path. [MacroPadEditor] and [AmbientSettingsOverlay] only stop injectors
 * on entry; they do NOT restart on exit — the watcher handles that.
 */
class MacroPadViewModel(application: Application) : AndroidViewModel(application) {

    val activeProfile: StateFlow<PadProfile?> = MacroPadState.activeProfile
    val activeLayout: StateFlow<PadLayout?> = MacroPadState.activeLayout
    val isPillMenuOpen: StateFlow<Boolean> = AppStateManager.isPillMenuOpen

    fun createHitTestEngine(buttonUnitDpToPx: (Float) -> Float) =
        MacroPadHitTestEngine(buttonUnitDpToPx)

    /**
     * Starts a long-lived watcher that reacts to all three blocking-modal flags.
     * Called once from [MacroPadScreen]'s LaunchedEffect(Unit).
     *
     * When any modal is open  → stop all injectors immediately.
     * When all modals closed  → restart injectors for the active profile.
     */
    fun watchInjectorLifecycle(context: Context) {
        InjectorLifecycleWatcher(viewModelScope, context).start()
    }

    fun stopInjectors() {
        AppLog.i(TAG, "stopInjectors called")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        MacroPadState.resetPeek()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → all injectors stopped")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        MacroPadState.resetPeek()
    }
}
