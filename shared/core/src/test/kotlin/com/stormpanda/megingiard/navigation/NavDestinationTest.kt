package com.stormpanda.megingiard.navigation

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavDestinationTest {
    @Test
    fun `GlobalSettings destination maps correctly to PrimaryModalConfig`() {
        val dest =
            NavDestination.GlobalSettings(
                category = SettingsCategory.APPEARANCE,
                subPage = SettingsSubPage.CUSTOM_ACCENT,
            )
        val config = dest.toPrimaryModalConfig()

        assertEquals(PrimaryModalType.GLOBAL_SETTINGS, config.type)
        val payload = config.payload as? PrimaryModalPayload.GlobalSettings
        assertNotNull(payload)
        assertEquals(SettingsCategory.APPEARANCE, payload?.category)
        assertEquals(SettingsSubPage.CUSTOM_ACCENT, payload?.subPage)
    }

    @Test
    fun `MacroPad destination maps correctly to PrimaryModalConfig`() {
        val dest =
            NavDestination.MacroPad(
                section = EditorSection.BUTTONS,
                editPositions = true,
            )
        val config = dest.toPrimaryModalConfig()

        assertEquals(PrimaryModalType.MACROPAD_EDITOR, config.type)
        val payload = config.payload as? PrimaryModalPayload.MacroPad
        assertNotNull(payload)
        assertEquals(EditorSection.BUTTONS, payload?.section)
        assertEquals(true, payload?.editPositions)
    }

    @Test
    fun `MacroTimeline destination maps correctly to PrimaryModalConfig`() {
        val dest =
            NavDestination.MacroTimeline(
                macroId = "macro-123",
                focusStepIndex = 2,
            )
        val config = dest.toPrimaryModalConfig()

        assertEquals(PrimaryModalType.MACRO_TIMELINE_EDITOR, config.type)
        val payload = config.payload as? PrimaryModalPayload.MacroTimeline
        assertNotNull(payload)
        assertEquals("macro-123", payload?.macroId)
        assertEquals(2, payload?.focusStepIndex)
    }

    @Test
    fun `LayoutSettings destination maps correctly to PrimaryModalConfig`() {
        val dest = NavDestination.LayoutSettings(layoutId = "layout-abc")
        val config = dest.toPrimaryModalConfig()

        assertEquals(PrimaryModalType.LAYOUT_SETTINGS, config.type)
        val payload = config.payload as? PrimaryModalPayload.LayoutSettings
        assertNotNull(payload)
        assertEquals("layout-abc", payload?.layoutId)
    }
}
