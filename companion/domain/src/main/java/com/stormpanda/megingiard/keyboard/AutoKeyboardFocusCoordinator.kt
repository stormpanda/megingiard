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

    private var activePackage: String? = null
    private var lastFocusedFieldId: String? = null
    private var userDismissedFieldId: String? = null

    /**
     * Called when an editable view on the primary display is focused or clicked.
     *
     * @param fieldId Unique identifier for the focused field (e.g. package:uniqueId).
     * @param packageName Application package of the focused field.
     * @param isClicked Whether the event was explicitly triggered by user tap/click.
     */
    fun onTextFieldFocused(
        fieldId: String,
        packageName: String? = null,
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

        AppLog.d(TAG, "onTextFieldFocused: opening keyboard for field $fieldId (pkg=$packageName, isClicked=$isClicked)")
        userDismissedFieldId = null
        lastFocusedFieldId = fieldId
        activePackage = packageName
        _isKeyboardAutoOpened.value = true

        AppStateManager.setFullscreenKeyboardActive(true)
    }

    /**
     * Called when focus on the primary display shifts to a non-editable element.
     */
    fun onNonEditableFocused() {
        lastFocusedFieldId = null
        userDismissedFieldId = null
        activePackage = null

        if (_isKeyboardAutoOpened.value) {
            AppLog.d(TAG, "onNonEditableFocused: closing auto-opened keyboard")
            _isKeyboardAutoOpened.value = false
            AppStateManager.setFullscreenKeyboardActive(false)
        }
    }

    /**
     * Called when the active window changes on the primary display.
     *
     * @param newPackage The package owning the new top/active window on the primary display.
     * If [newPackage] matches the current [activePackage], the keyboard is kept open to avoid
     * flickering on intra-app popups, suggestion dropdowns, or dialogs.
     */
    fun onWindowStateChanged(newPackage: String? = null) {
        if (_isKeyboardAutoOpened.value) {
            if (newPackage != null && activePackage != null && newPackage == activePackage) {
                AppLog.d(TAG, "onWindowStateChanged: intra-app window change in $newPackage, keeping keyboard open")
                return
            }
            AppLog.d(TAG, "onWindowStateChanged: closing auto-opened keyboard due to package change (from=$activePackage to=$newPackage)")
            _isKeyboardAutoOpened.value = false
            lastFocusedFieldId = null
            userDismissedFieldId = null
            activePackage = null
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
        activePackage = null
    }
}
