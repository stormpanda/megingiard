package com.stormpanda.megingiard.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.InjectorLifecycleManager
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KeyDef
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.KeyboardGestureProcessor
import com.stormpanda.megingiard.keyboard.KeyboardMode
import com.stormpanda.megingiard.keyboard.KeyboardState
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.keyboard.ModifierState
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.settings.KeyboardSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "KeyboardViewModel"

/**
 * ViewModel for [KeyboardScreen] — manages injector lifecycle, key repeat,
 * and keyboard state.
 */
class KeyboardViewModel : ViewModel() {
    val kbLayout: StateFlow<KbLayout> = KeyboardSettings.kbLayout
    val kbRepeatEnabled: StateFlow<Boolean> = KeyboardSettings.kbRepeatEnabled
    val kbTouchpadEnabled: StateFlow<Boolean> = KeyboardSettings.kbTouchpadEnabled
    val kbAutoOpenOnFocus: StateFlow<Boolean> = KeyboardSettings.kbAutoOpenOnFocus
    val isQuickMenuOpen: StateFlow<Boolean> = AppStateManager.isQuickMenuOpen

    private val _keyboardMode = MutableStateFlow(KeyboardMode.LETTERS)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode.asStateFlow()

    private val _isMacroRowVisible = MutableStateFlow(false)
    val isMacroRowVisible: StateFlow<Boolean> = _isMacroRowVisible.asStateFlow()

    fun setKeyboardMode(mode: KeyboardMode) {
        _keyboardMode.value = mode
    }

    fun toggleMacroRow() {
        _isMacroRowVisible.value = !_isMacroRowVisible.value
    }

    fun setKbTouchpadEnabled(value: Boolean) = KeyboardSettings.setKbTouchpadEnabled(value)

    fun setKbAutoOpenOnFocus(value: Boolean) = KeyboardSettings.setKbAutoOpenOnFocus(value)

    private fun sendCtrlCombo(keyCode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
            KeyInjector.keyDown(keyCode)
            KeyInjector.keyUp(keyCode)
            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
        }
    }

    fun selectAll() = sendCtrlCombo(LinuxKeycodes.KEY_A)

    fun cut() = sendCtrlCombo(LinuxKeycodes.KEY_X)

    fun copy() = sendCtrlCombo(LinuxKeycodes.KEY_C)

    fun paste() = sendCtrlCombo(LinuxKeycodes.KEY_V)

    val gestureProcessor =
        KeyboardGestureProcessor(
            scope = viewModelScope,
            kbRepeatEnabled = { kbRepeatEnabled.value },
            isShiftActive = {
                KeyboardState.stateFor("lshift").value != ModifierState.INACTIVE ||
                    KeyboardState.stateFor("rshift").value != ModifierState.INACTIVE
            },
            isCapsActive = {
                KeyboardState.stateFor("caps").value != ModifierState.INACTIVE
            },
            isAltGrActive = {
                KeyboardState.stateFor("altgr").value != ModifierState.INACTIVE ||
                    KeyboardState.stateFor("ralt").value != ModifierState.INACTIVE
            },
            initialDensity = 1f,
            onInjectPopupSelection = ::injectPopupSelection,
        )

    fun closeQuickMenu() = AppStateManager.closeQuickMenu()

    fun cycleKbLayout() {
        KeyboardSettings.setKbLayout(KbLayout.entries.nextItem(kbLayout.value))
    }

    fun setKbLayout(value: KbLayout) = KeyboardSettings.setKbLayout(value)

    fun startInjectors(context: Context) {
        AppLog.i(TAG, "startInjectors called -> watching InjectorLifecycleManager")
        KeyboardState.reset()
        InjectorLifecycleManager.watch(context)
    }

    fun stopAndReset() {
        AppLog.i(TAG, "stopAndReset called -> resetting KeyboardState & disposing gestureProcessor")
        gestureProcessor.dispose()
        KeyboardState.reset()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → KeyboardState reset & disposing gestureProcessor")
        _keyboardMode.value = KeyboardMode.LETTERS
        gestureProcessor.dispose()
        KeyboardState.reset()
    }

    fun injectPopupSelection(
        popupKeyDef: KeyDef,
        charToInject: String,
    ) {
        if (popupKeyDef.shiftLabel != null && popupKeyDef.shiftLabel == charToInject) {
            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTSHIFT)
            KeyInjector.keyDown(popupKeyDef.linuxKeycode)
            KeyInjector.keyUp(popupKeyDef.linuxKeycode)
            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTSHIFT)
            return
        }
        val mapping = charToInject.singleOrNull()?.let(LinuxKeycodes::charToKeyMapping) ?: return
        if (mapping.shift) KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTSHIFT)
        KeyInjector.keyDown(mapping.keycode)
        KeyInjector.keyUp(mapping.keycode)
        if (mapping.shift) KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTSHIFT)
    }
}
