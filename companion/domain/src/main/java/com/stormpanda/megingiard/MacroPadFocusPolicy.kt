package com.stormpanda.megingiard

private const val TAG = "MacroPadFocusPolicy"

data class MacroPadFocusPolicyState(
    val isMacroPadSurfaceActive: Boolean,
    val isFullscreenKeyboardActive: Boolean = false,
    val isQuickMenuOpen: Boolean = false,
    val isFilePickerOpen: Boolean = false,
    val isEditorActive: Boolean = false,
    val isBackgroundSettingsActive: Boolean = false,
    val isGlobalSettingsOpen: Boolean = false,
    val isKeyboardSettingsOpen: Boolean = false,
    val isTouchpadSettingsOpen: Boolean = false,
)

/**
 * Returns true when the secondary display companion window should have FLAG_NOT_FOCUSABLE.
 *
 * In the primary-screen heavy architecture, ALL interactive settings, dialogs, editors,
 * and wizards render on Display 0 (Primary Display). Consequently, the companion window
 * on Display 4 (Secondary Display) is ALWAYS marked FLAG_NOT_FOCUSABLE in dual-screen mode,
 * ensuring the background game or primary overlay maintains uninterrupted window focus.
 */
fun shouldKeepPrimaryGameFocus(isDualScreen: Boolean = true): Boolean {
    AppLog.d(TAG, "shouldKeepPrimaryGameFocus=$isDualScreen (static dual-screen invariant)")
    return isDualScreen
}

/**
 * Legacy compatibility overload evaluating [MacroPadFocusPolicyState].
 * In dual-screen mode, returns true as the secondary screen never steals focus.
 */
fun shouldKeepPrimaryGameFocus(state: MacroPadFocusPolicyState): Boolean = true
