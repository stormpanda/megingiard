package com.stormpanda.megingiard

private const val TAG = "MacroPadFocusPolicy"

data class MacroPadFocusPolicyState(
    val isMacroPadSurfaceActive: Boolean,
    val isFullscreenKeyboardActive: Boolean = false,
    val isPillMenuOpen: Boolean = false,
    val isFilePickerOpen: Boolean = false,
    val isEditorActive: Boolean = false,
    val isBackgroundSettingsActive: Boolean = false,
)

/**
 * Returns true when the hosting Android window should be NOT_FOCUSABLE so the
 * primary-display game keeps pointer capture while the secondary-display MacroPad
 * still receives touch input.
 */
fun shouldKeepPrimaryGameFocus(state: MacroPadFocusPolicyState): Boolean {
    val hasInteractiveOverlay = state.isPillMenuOpen ||
        state.isFilePickerOpen ||
        state.isEditorActive ||
        state.isBackgroundSettingsActive
    return state.isFullscreenKeyboardActive ||
        (state.isMacroPadSurfaceActive && !hasInteractiveOverlay)
}