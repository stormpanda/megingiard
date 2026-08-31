package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.InjectorLifecycleManager
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.keyboard.KeyDef
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.KeyRepeatController
import com.stormpanda.megingiard.keyboard.KeyboardGestureProcessor
import com.stormpanda.megingiard.keyboard.KeyboardMode
import com.stormpanda.megingiard.keyboard.KeyboardState
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.keyboard.ModifierState
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.SettingsManager
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
class KeyboardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val kbLayout: StateFlow<KbLayout> = KeyboardSettings.kbLayout
    val kbRepeatEnabled: StateFlow<Boolean> = KeyboardSettings.kbRepeatEnabled
    val kbTrackpointEnabled: StateFlow<Boolean> = KeyboardSettings.kbTrackpointEnabled
    val kbFullscreen: StateFlow<Boolean> = KeyboardSettings.kbFullscreen
    val kbMouseBtnPos: StateFlow<KbMouseBtnPos> = KeyboardSettings.kbMouseBtnPos
    val kbTouchpadEnabled: StateFlow<Boolean> = KeyboardSettings.kbTouchpadEnabled
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val isQuickMenuOpen: StateFlow<Boolean> = AppStateManager.isQuickMenuOpen

    private val _keyboardMode = MutableStateFlow(KeyboardMode.LETTERS)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode.asStateFlow()

    fun setKeyboardMode(mode: KeyboardMode) {
        _keyboardMode.value = mode
    }

    fun setKbTouchpadEnabled(value: Boolean) {
        KeyboardSettings.setKbTouchpadEnabled(value)
    }

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

    val controller = KeyRepeatController(viewModelScope)
    val gestureProcessor =
        KeyboardGestureProcessor(
            controller = controller,
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
            onInjectPopupSelection = { popupKeyDef, charToInject ->
                injectPopupSelection(popupKeyDef, charToInject)
            },
        )

    fun closeQuickMenu() = AppStateManager.closeQuickMenu()

    fun cycleKbLayout() {
        val current = kbLayout.value
        val layouts = KbLayout.entries
        val nextIndex = (layouts.indexOf(current) + 1) % layouts.size
        KeyboardSettings.setKbLayout(layouts[nextIndex])
    }

    fun setKbLayout(value: KbLayout) {
        KeyboardSettings.setKbLayout(value)
    }

    fun startInjectors(context: Context) {
        AppLog.i(TAG, "startInjectors called -> watching InjectorLifecycleManager")
        KeyboardState.reset()
        InjectorLifecycleManager.watch(context)
    }

    fun stopAndReset() {
        AppLog.i(TAG, "stopAndReset called -> resetting KeyboardState & disposing controller")
        controller.dispose()
        KeyboardState.reset()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.i(TAG, "onCleared → KeyboardState reset & disposing controller")
        _keyboardMode.value = KeyboardMode.LETTERS
        controller.dispose()
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
        } else {
            injectPopupChar(charToInject, kbLayout.value)
        }
    }

    private fun injectPopupChar(
        char: String,
        layout: KbLayout,
    ) {
        val lower = char.lowercase()
        val isUpper = char.length == 1 && char[0].isUpperCase()

        fun sendKey(
            keycode: Int,
            autoModifiers: List<Int> = emptyList(),
        ) {
            val mods =
                buildList {
                    if (isUpper) {
                        add(LinuxKeycodes.KEY_LEFTSHIFT)
                    }
                    addAll(autoModifiers)
                }

            mods.forEach { KeyInjector.keyDown(it) }
            KeyInjector.keyDown(keycode)
            KeyInjector.keyUp(keycode)
            mods.forEach { KeyInjector.keyUp(it) }
        }

        val normalized =
            when (lower) {
                "é", "è", "ê", "ë", "ē", "ė" -> "e"
                "à", "á", "â", "ã", "å", "æ", "ā" -> "a"
                "ò", "ó", "ô", "õ", "œ", "ø", "ō" -> "o"
                "ù", "ú", "û", "ū" -> "u"
                "ì", "í", "î", "ï", "ī" -> "i"
                "ñ", "ń" -> "n"
                "ç", "ć", "č" -> "c"
                "ÿ" -> "y"
                "ž" -> "z"
                "ś", "š" -> "s"
                else -> lower
            }

        if (normalized == "ä") {
            if (layout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_APOSTROPHE)
            } else {
                sendKey(LinuxKeycodes.KEY_A, listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
            return
        }
        if (normalized == "ö") {
            if (layout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_SEMICOLON)
            } else {
                sendKey(LinuxKeycodes.KEY_O, listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
            return
        }
        if (normalized == "ü") {
            if (layout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_LEFTBRACE)
            } else {
                sendKey(LinuxKeycodes.KEY_U, listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
            return
        }
        if (normalized == "ß") {
            if (layout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_MINUS)
            } else {
                sendKey(LinuxKeycodes.KEY_S, listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
            return
        }

        val lookup = POPUP_CHAR_MAP[normalized]
        if (lookup != null) {
            sendKey(lookup.first, lookup.second)
        }
    }
}

private val POPUP_CHAR_MAP: Map<String, Pair<Int, List<Int>>> =
    mapOf(
        "1" to (LinuxKeycodes.KEY_1 to emptyList()),
        "2" to (LinuxKeycodes.KEY_2 to emptyList()),
        "3" to (LinuxKeycodes.KEY_3 to emptyList()),
        "4" to (LinuxKeycodes.KEY_4 to emptyList()),
        "5" to (LinuxKeycodes.KEY_5 to emptyList()),
        "6" to (LinuxKeycodes.KEY_6 to emptyList()),
        "7" to (LinuxKeycodes.KEY_7 to emptyList()),
        "8" to (LinuxKeycodes.KEY_8 to emptyList()),
        "9" to (LinuxKeycodes.KEY_9 to emptyList()),
        "0" to (LinuxKeycodes.KEY_0 to emptyList()),
        "@" to (LinuxKeycodes.KEY_2 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "#" to (LinuxKeycodes.KEY_3 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "_" to (LinuxKeycodes.KEY_MINUS to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "&" to (LinuxKeycodes.KEY_7 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "-" to (LinuxKeycodes.KEY_MINUS to emptyList()),
        "+" to (LinuxKeycodes.KEY_EQUAL to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "(" to (LinuxKeycodes.KEY_9 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ")" to (LinuxKeycodes.KEY_0 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "/" to (LinuxKeycodes.KEY_SLASH to emptyList()),
        "*" to (LinuxKeycodes.KEY_8 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "\"" to (LinuxKeycodes.KEY_APOSTROPHE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "'" to (LinuxKeycodes.KEY_APOSTROPHE to emptyList()),
        ":" to (LinuxKeycodes.KEY_SEMICOLON to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ";" to (LinuxKeycodes.KEY_SEMICOLON to emptyList()),
        "!" to (LinuxKeycodes.KEY_1 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "?" to (LinuxKeycodes.KEY_SLASH to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "[" to (LinuxKeycodes.KEY_LEFTBRACE to emptyList()),
        "]" to (LinuxKeycodes.KEY_RIGHTBRACE to emptyList()),
        "{" to (LinuxKeycodes.KEY_LEFTBRACE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "}" to (LinuxKeycodes.KEY_RIGHTBRACE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "<" to (LinuxKeycodes.KEY_COMMA to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ">" to (LinuxKeycodes.KEY_DOT to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "\\" to (LinuxKeycodes.KEY_BACKSLASH to emptyList()),
        "$" to (LinuxKeycodes.KEY_4 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "%" to (LinuxKeycodes.KEY_5 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "a" to (LinuxKeycodes.KEY_A to emptyList()),
        "b" to (LinuxKeycodes.KEY_B to emptyList()),
        "c" to (LinuxKeycodes.KEY_C to emptyList()),
        "d" to (LinuxKeycodes.KEY_D to emptyList()),
        "e" to (LinuxKeycodes.KEY_E to emptyList()),
        "f" to (LinuxKeycodes.KEY_F to emptyList()),
        "g" to (LinuxKeycodes.KEY_G to emptyList()),
        "h" to (LinuxKeycodes.KEY_H to emptyList()),
        "i" to (LinuxKeycodes.KEY_I to emptyList()),
        "j" to (LinuxKeycodes.KEY_J to emptyList()),
        "k" to (LinuxKeycodes.KEY_K to emptyList()),
        "l" to (LinuxKeycodes.KEY_L to emptyList()),
        "m" to (LinuxKeycodes.KEY_M to emptyList()),
        "n" to (LinuxKeycodes.KEY_N to emptyList()),
        "o" to (LinuxKeycodes.KEY_O to emptyList()),
        "p" to (LinuxKeycodes.KEY_P to emptyList()),
        "q" to (LinuxKeycodes.KEY_Q to emptyList()),
        "r" to (LinuxKeycodes.KEY_R to emptyList()),
        "s" to (LinuxKeycodes.KEY_S to emptyList()),
        "t" to (LinuxKeycodes.KEY_T to emptyList()),
        "u" to (LinuxKeycodes.KEY_U to emptyList()),
        "v" to (LinuxKeycodes.KEY_V to emptyList()),
        "w" to (LinuxKeycodes.KEY_W to emptyList()),
        "x" to (LinuxKeycodes.KEY_X to emptyList()),
        "y" to (LinuxKeycodes.KEY_Y to emptyList()),
        "z" to (LinuxKeycodes.KEY_Z to emptyList()),
    )
