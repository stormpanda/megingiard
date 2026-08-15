package com.stormpanda.megingiard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroPadFocusPolicyTest {
    @Test
    fun `dual-screen mode always keeps primary game focus`() {
        assertTrue(shouldKeepPrimaryGameFocus(isDualScreen = true))
    }

    @Test
    fun `single-screen mode does not force NOT_FOCUSABLE`() {
        assertFalse(shouldKeepPrimaryGameFocus(isDualScreen = false))
    }

    @Test
    fun `legacy state overload always keeps primary game focus in dual-screen`() {
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(isMacroPadSurfaceActive = true),
            ),
        )
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isQuickMenuOpen = true,
                ),
            ),
        )
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isGlobalSettingsOpen = true,
                ),
            ),
        )
        assertTrue(
            shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(
                    isMacroPadSurfaceActive = true,
                    isEditorActive = true,
                ),
            ),
        )
    }
}
