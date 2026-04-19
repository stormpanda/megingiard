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
import com.stormpanda.megingiard.macropad.MacroPadHitTestEngine
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MacroPadViewModel"

/**
 * ViewModel for [MacroPadScreen] — manages multi-injector lifecycle
 * and hit-test engine.
 */
class MacroPadViewModel(application: Application) : AndroidViewModel(application) {

    val activeProfile: StateFlow<PadProfile?> = MacroPadState.activeProfile
    val activeLayout: StateFlow<PadLayout?> = MacroPadState.activeLayout
    val isPillMenuOpen: StateFlow<Boolean> = AppStateManager.isPillMenuOpen

    fun createHitTestEngine(buttonUnitDpToPx: (Float) -> Float) =
        MacroPadHitTestEngine(buttonUnitDpToPx)

    fun startInjectors(context: Context) {
        viewModelScope.launch {
            AppStateManager.isPillMenuOpen.first { !it }
            withContext(Dispatchers.IO) {
                val ap = MacroPadState.activeProfile.value
                AppLog.i(TAG, "starting injectors for profile '${ap?.name}' (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse})")
                if (ap?.enableKeyboard == true) KeyInjector.start(context)
                if (ap?.enableGamepad == true) GamepadInjector.start(context)
                if (ap?.enableMouse == true) MouseInjector.start(context)
            }
        }
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
