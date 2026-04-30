package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.KeyRepeatController
import com.stormpanda.megingiard.keyboard.KeyboardState
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
private const val TAG = "KeyboardViewModel"

/**
 * ViewModel for [KeyboardScreen] — manages injector lifecycle, key repeat,
 * and keyboard state.
 */
class KeyboardViewModel(application: Application) : AndroidViewModel(application) {

    val kbLayout: StateFlow<KbLayout> = SettingsManager.kbLayout
    val kbRepeatEnabled: StateFlow<Boolean> = SettingsManager.kbRepeatEnabled
    val kbTrackpointEnabled: StateFlow<Boolean> = SettingsManager.kbTrackpointEnabled
    val kbFullscreen: StateFlow<Boolean> = SettingsManager.kbFullscreen
    val kbMouseBtnPos: StateFlow<KbMouseBtnPos> = SettingsManager.kbMouseBtnPos
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val showFullscreenExitHints: StateFlow<Boolean> = SettingsManager.showFullscreenExitHints
    val isPillMenuOpen: StateFlow<Boolean> = AppStateManager.isPillMenuOpen

    val controller = KeyRepeatController(viewModelScope)

    fun closePillMenu() = AppStateManager.closePillMenu()

    fun startInjectors(context: Context) {
        viewModelScope.launch {
            KeyboardState.reset()
            AppStateManager.isPillMenuOpen.first { !it }
            AppLog.i(TAG, "pill menu closed, starting KeyInjector + MouseInjector")
            withContext(Dispatchers.IO) {
                KeyInjector.start(context)
                MouseInjector.start(context)
            }
            AppLog.i(TAG, "KeyInjector + MouseInjector started")
        }
    }

    fun stopAndReset() {
        AppLog.i(TAG, "stopAndReset called")
        controller.dispose()
        KeyInjector.stop()
        MouseInjector.stop()
        KeyboardState.reset()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → KeyInjector + MouseInjector stopped, KeyboardState reset")
        controller.dispose()
        KeyInjector.stop()
        MouseInjector.stop()
        KeyboardState.reset()
    }
}
