package com.stormpanda.megingiard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroPadFocusPolicyTest {

    @Test
    fun `normal secondary macropad surface keeps primary game focus`() {
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(isMacroPadSurfaceActive = true)
            )
        )
    }

    @Test
    fun `inactive macropad surface does not force primary game focus`() {
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(isMacroPadSurfaceActive = false)
            )
        )
    }

    @Test
    fun `pill menu restores app focus`() {
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isPillMenuOpen = true,
                )
            )
        )
    }

    @Test
    fun `file picker restores app focus`() {
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isFilePickerOpen = true,
                )
            )
        )
    }

    @Test
    fun `editor restores app focus`() {
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isEditorActive = true,
                )
            )
        )
    }

    @Test
    fun `ambient settings restore app focus`() {
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isBackgroundSettingsActive = true,
                )
            )
        )
    }

    @Test
    fun `fullscreen keyboard keeps primary game focus for key injection`() {
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = false,
                    isFullscreenKeyboardActive = true,
                )
            )
        )
    }

    @Test
    fun `fullscreen keyboard on presentation keeps primary game focus even without macropad active`() {
        // Regression guard: MirrorPresentation passes isMacroPadSurfaceActive=true always,
        // but the flag combination with keyboard active must still keep game focus.
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isFullscreenKeyboardActive = true,
                )
            )
        )
    }

    @Test
    fun `interactive overlay takes priority over fullscreen keyboard in clearing focus`() {
        // PillMenu open + keyboard active: interactive overlay wins, app needs focus.
        assertFalse(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isFullscreenKeyboardActive = false,
                    isPillMenuOpen = true,
                )
            )
        )
    }
}