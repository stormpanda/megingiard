package com.stormpanda.megingiard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KeyDef
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.KeyboardGestureProcessor
import com.stormpanda.megingiard.keyboard.KeyboardMode
import com.stormpanda.megingiard.keyboard.KeyboardState
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.keyboard.ModifierState
import com.stormpanda.megingiard.settings.KeyboardSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KeyboardViewModel"

/**
 * ViewModel for [KeyboardScreen] — manages key repeat, gestures,
 * and keyboard state.
 */
class KeyboardViewModel : ViewModel() {
    val kbLayout: StateFlow<KbLayout> = KeyboardSettings.kbLayout
    val kbTouchpadEnabled: StateFlow<Boolean> = KeyboardSettings.kbTouchpadEnabled
    val kbAutoOpenOnFocus: StateFlow<Boolean> = KeyboardSettings.kbAutoOpenOnFocus
    val kbAutoHideOnFocusLoss: StateFlow<Boolean> = KeyboardSettings.kbAutoHideOnFocusLoss
    val isQuickMenuOpen: StateFlow<Boolean> = AppStateManager.isQuickMenuOpen

    private val _keyboardMode = MutableStateFlow(KeyboardMode.LETTERS)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode.asStateFlow()

    private val _isMacroRowVisible = MutableStateFlow(false)
    val isMacroRowVisible: StateFlow<Boolean> = _isMacroRowVisible.asStateFlow()

    fun setKeyboardMode(mode: KeyboardMode) {
        _keyboardMode.value = mode
        KeyboardState.reset()
    }

    fun toggleMacroRow() {
        _isMacroRowVisible.value = !_isMacroRowVisible.value
    }

    fun setKbTouchpadEnabled(value: Boolean) = KeyboardSettings.setKbTouchpadEnabled(value)

    fun setKbAutoOpenOnFocus(value: Boolean) = KeyboardSettings.setKbAutoOpenOnFocus(value)

    fun setKbAutoHideOnFocusLoss(value: Boolean) = KeyboardSettings.setKbAutoHideOnFocusLoss(value)

    private fun sendCtrlCombo(keyCode: Int) {
        KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
        KeyInjector.keyDown(keyCode)
        KeyInjector.keyUp(keyCode)
        KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
    }

    fun selectAll() = sendCtrlCombo(LinuxKeycodes.KEY_A)

    fun cut() = sendCtrlCombo(LinuxKeycodes.KEY_X)

    fun copy() = sendCtrlCombo(LinuxKeycodes.KEY_C)

    fun paste() = sendCtrlCombo(LinuxKeycodes.KEY_V)

    val gestureProcessor =
        KeyboardGestureProcessor(
            scope = viewModelScope,
            isShiftActive = {
                KeyboardState.stateFor("lshift").value != ModifierState.INACTIVE ||
                    KeyboardState.stateFor("rshift").value != ModifierState.INACTIVE
            },
            isCapsActive = {
                KeyboardState.stateFor("caps").value != ModifierState.INACTIVE
            },
            isAltGrActive = {
                KeyboardState.stateFor("altgr").value != ModifierState.INACTIVE
            },
            initialDensity = 1f,
            onInjectPopupSelection = ::injectPopupSelection,
            onModeChange = ::setKeyboardMode,
            activeMode = { _keyboardMode.value },
        )

    fun closeQuickMenu() = AppStateManager.closeQuickMenu()

    fun setKbLayout(value: KbLayout) {
        KeyboardSettings.setKbLayout(value)
        KeyboardState.reset()
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
