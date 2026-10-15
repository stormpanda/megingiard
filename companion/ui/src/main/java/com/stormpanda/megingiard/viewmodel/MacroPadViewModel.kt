package com.stormpanda.megingiard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.InjectorLifecycleManager
import com.stormpanda.megingiard.macropad.HapticStrength
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
 *   - Quick Menu open, blocking modal open (Editor/Ambient Settings), or system prompt in flight:
 *     stop all background macro injectors.
 *   - Restart as soon as all guards are clear.
 *
 * [watchInjectorLifecycle] is the single authoritative restart path.
 * [MacroPadEditor] only stops injectors on entry;
 * it does NOT restart on exit — this watcher handles that.
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
     * Starts watching injector lifecycles via [InjectorLifecycleManager].
     */
    fun watchInjectorLifecycle() {
        InjectorLifecycleManager.watch()
    }

    fun stopInjectors() {
        AppLog.d(TAG, "stopInjectors")
        MacroPadState.resetPeek()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared")
        stopInjectors()
    }
}
