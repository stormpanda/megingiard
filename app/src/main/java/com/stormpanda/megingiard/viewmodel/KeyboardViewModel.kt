package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.KeyRepeatController
import com.stormpanda.megingiard.keyboard.KeyboardMode
import com.stormpanda.megingiard.keyboard.KeyboardState
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.TrackpointMode
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KeyboardViewModel"

/**
 * ViewModel for [KeyboardScreen] — manages injector lifecycle, key repeat,
 * and keyboard state.
 */
class KeyboardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val kbLayout: StateFlow<KbLayout> = KeyboardSettings.kbLayout
    val kbRepeatEnabled: StateFlow<Boolean> = KeyboardSettings.kbRepeatEnabled
    val kbTrackpointEnabled: StateFlow<Boolean> = KeyboardSettings.kbTrackpointEnabled
    val kbFullscreen: StateFlow<Boolean> = KeyboardSettings.kbFullscreen
    val kbMouseBtnPos: StateFlow<KbMouseBtnPos> = KeyboardSettings.kbMouseBtnPos
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val isQuickMenuOpen: StateFlow<Boolean> = AppStateManager.isQuickMenuOpen

    private val _keyboardMode = MutableStateFlow(KeyboardMode.LETTERS)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode.asStateFlow()

    fun setKeyboardMode(mode: KeyboardMode) {
        _keyboardMode.value = mode
    }

    val controller = KeyRepeatController(viewModelScope)

    fun closeQuickMenu() = AppStateManager.closeQuickMenu()

    fun cycleKbLayout() {
        val current = kbLayout.value
        val layouts = KbLayout.entries
        val nextIndex = (layouts.indexOf(current) + 1) % layouts.size
        KeyboardSettings.setKbLayout(layouts[nextIndex])
    }

    fun startInjectors(context: Context) {
        viewModelScope.launch {
            KeyboardState.reset()
            AppStateManager.isQuickMenuOpen.first { !it }
            AppLog.i(TAG, "quick menu closed, starting KeyInjector + MouseInjector")
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
        val activeL = MacroPadState.activeLayout.value
        val hasKeyboard = activeL?.buttons?.any { it.action is PadAction.KeyboardKey || it.action is PadAction.Macro } == true
        val hasMouse =
            activeL?.buttons?.any {
                it.action is PadAction.MouseButton ||
                    it.action is PadAction.ScrollWheel ||
                    (it.action is PadAction.TrackpointMove && (it.action as PadAction.TrackpointMove).mode == TrackpointMode.PHYSICAL_MOUSE) ||
                    it.action is PadAction.Macro
            } == true
        if (!hasKeyboard) {
            KeyInjector.stop()
        }
        if (!hasMouse) {
            MouseInjector.stop()
        }
        KeyboardState.reset()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → KeyInjector + MouseInjector stopped, KeyboardState reset")
        _keyboardMode.value = KeyboardMode.LETTERS
        controller.dispose()
        KeyInjector.stop()
        MouseInjector.stop()
        KeyboardState.reset()
    }
}
