package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.KeyboardSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AutoKeyboardFocusCoordinator"

/**
 * Coordinates automatic presentation and dismissal of Megingiard's virtual keyboard
 * on the secondary display based on text field focus events occurring on the primary display.
 *
 * Driven by accessibility events forwarded from MegingiardAccessibilityService.
 * Ensures that:
 * 1. Focusing an editable text field automatically opens the keyboard.
 * 2. Focus moving away or window navigation closes the keyboard.
 * 3. Manual user dismissal (e.g. via collapse chevron or swipe gesture) sets hysteresis,
 *    preventing the keyboard from aggressively re-opening until focus shifts to another field.
 */
object AutoKeyboardFocusCoordinator {
    private val _isKeyboardAutoOpened = MutableStateFlow(false)
    val isKeyboardAutoOpened: StateFlow<Boolean> = _isKeyboardAutoOpened.asStateFlow()

    private var lastFocusedFieldId: String? = null
    private var userDismissedFieldId: String? = null

    /**
     * Called when an editable view on the primary display is focused or clicked.
     *
     * @param fieldId Unique identifier for the focused field (e.g. windowId:nodeId).
     * @param isClicked Whether the event was explicitly triggered by user tap/click.
     */
    fun onTextFieldFocused(
        fieldId: String,
        isClicked: Boolean,
        autoOpenEnabled: Boolean = KeyboardSettings.kbAutoOpenOnFocus.value,
    ) {
        if (!autoOpenEnabled) {
            return
        }

        // Hysteresis check: if user dismissed the keyboard for this exact field, do not re-open
        // on passive focus transitions unless the user explicitly tapped/clicked the field again.
        if (fieldId == userDismissedFieldId && !isClicked) {
            AppLog.d(TAG, "onTextFieldFocused: suppressed due to prior manual dismissal for field $fieldId")
            return
        }

        AppLog.d(TAG, "onTextFieldFocused: opening keyboard for field $fieldId (isClicked=$isClicked)")
        userDismissedFieldId = null
        lastFocusedFieldId = fieldId
        _isKeyboardAutoOpened.value = true

        AppStateManager.setFullscreenKeyboardActive(true)
    }

    /**
     * Called when focus on the primary display shifts to a non-editable element.
     */
    fun onNonEditableFocused() {
        lastFocusedFieldId = null
        userDismissedFieldId = null

        if (_isKeyboardAutoOpened.value) {
            AppLog.d(TAG, "onNonEditableFocused: closing auto-opened keyboard")
            _isKeyboardAutoOpened.value = false
            AppStateManager.setFullscreenKeyboardActive(false)
        }
    }

    /**
     * Called when the active window changes on the primary display.
     */
    fun onWindowStateChanged() {
        if (_isKeyboardAutoOpened.value) {
            AppLog.d(TAG, "onWindowStateChanged: closing auto-opened keyboard due to window change")
            _isKeyboardAutoOpened.value = false
            lastFocusedFieldId = null
            userDismissedFieldId = null
            AppStateManager.setFullscreenKeyboardActive(false)
        }
    }

    /**
     * Called when the keyboard's global active state changes.
     * Detects manual user closure to establish debouncing hysteresis.
     */
    fun onKeyboardVisibilityChanged(isActive: Boolean) {
        if (!isActive && _isKeyboardAutoOpened.value) {
            AppLog.d(TAG, "onKeyboardVisibilityChanged: user manually dismissed auto-opened keyboard for field $lastFocusedFieldId")
            userDismissedFieldId = lastFocusedFieldId
            _isKeyboardAutoOpened.value = false
        }
    }

    /**
     * Clears all tracking state.
     */
    fun reset() {
        AppLog.d(TAG, "reset called")
        _isKeyboardAutoOpened.value = false
        lastFocusedFieldId = null
        userDismissedFieldId = null
    }
}
