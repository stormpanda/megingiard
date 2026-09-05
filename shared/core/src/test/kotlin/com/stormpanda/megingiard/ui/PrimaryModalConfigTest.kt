package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryModalConfigTest {
    @Test
    fun testPrimaryModalTypeEnumValues() {
        val types = PrimaryModalType.entries
        assertTrue(types.contains(PrimaryModalType.GLOBAL_SETTINGS))
        assertTrue(types.contains(PrimaryModalType.MACROPAD_EDITOR))
        assertTrue(types.contains(PrimaryModalType.LAYOUT_SETTINGS))
        assertTrue(types.contains(PrimaryModalType.PROFILE_SETTINGS))
        assertTrue(types.contains(PrimaryModalType.MACRO_TIMELINE_EDITOR))
        assertTrue(types.contains(PrimaryModalType.MACROPAD_INSPECTOR))
    }

    @Test
    fun testPrimaryModalConfigCreationAndEquality() {
        val payload =
            PrimaryModalPayload.MacroPad(
                section = EditorSection.MACROS,
                macroId = "macro_123",
                focusStepIndex = 2,
            )
        val config1 = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR, payload = payload)
        val config2 = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR, payload = payload)
        val config3 = PrimaryModalConfig(type = PrimaryModalType.GLOBAL_SETTINGS, payload = null)

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
        assertNotEquals(config1, config3)
        assertEquals(PrimaryModalType.MACROPAD_EDITOR, config1.type)
        assertEquals(payload, config1.payload)
    }

    @Test
    fun testMacroPadPayloadDefaultsAndExplicitValues() {
        val defaultPayload = PrimaryModalPayload.MacroPad()
        assertEquals(EditorSection.QUICK_ACTIONS, defaultPayload.section)
        assertNull(defaultPayload.profileId)
        assertNull(defaultPayload.layoutId)
        assertNull(defaultPayload.macroId)
        assertFalse(defaultPayload.editPositions)
        assertNull(defaultPayload.focusStepIndex)

        val explicitPayload =
            PrimaryModalPayload.MacroPad(
                section = EditorSection.BUTTONS,
                profileId = "prof_1",
                layoutId = "lay_1",
                macroId = "mac_1",
                editPositions = true,
                focusStepIndex = 4,
            )
        assertEquals(EditorSection.BUTTONS, explicitPayload.section)
        assertEquals("prof_1", explicitPayload.profileId)
        assertEquals("lay_1", explicitPayload.layoutId)
        assertEquals("mac_1", explicitPayload.macroId)
        assertTrue(explicitPayload.editPositions)
        assertEquals(4, explicitPayload.focusStepIndex)
    }

    @Test
    fun testGlobalSettingsPayloadDefaults() {
        val defaultPayload = PrimaryModalPayload.GlobalSettings()
        assertEquals(SettingsCategory.GENERAL, defaultPayload.category)
        assertNull(defaultPayload.subPage)

        val explicitPayload =
            PrimaryModalPayload.GlobalSettings(
                category = SettingsCategory.CONFIGURATION,
                subPage = SettingsSubPage.RESTORE_REVIEW,
            )
        assertEquals(SettingsCategory.CONFIGURATION, explicitPayload.category)
        assertEquals(SettingsSubPage.RESTORE_REVIEW, explicitPayload.subPage)
    }

    @Test
    fun testMacroTimelinePayloadDefaults() {
        val defaultPayload = PrimaryModalPayload.MacroTimeline()
        assertNull(defaultPayload.macroId)
        assertNull(defaultPayload.focusStepIndex)

        val explicitPayload =
            PrimaryModalPayload.MacroTimeline(
                macroId = "macro_timeline_1",
                focusStepIndex = 3,
            )
        assertEquals("macro_timeline_1", explicitPayload.macroId)
        assertEquals(3, explicitPayload.focusStepIndex)
    }

    @Test
    fun testInspectorPayloads() {
        val buttonInspector = PrimaryModalPayload.ButtonInspector(buttonId = "btn_abc")
        assertEquals("btn_abc", buttonInspector.buttonId)

        val cutoutInspector = PrimaryModalPayload.CutoutInspector(cutoutId = "cutout_xyz")
        assertEquals("cutout_xyz", cutoutInspector.cutoutId)

        val cropSelector = PrimaryModalPayload.CropSelector(cutoutId = "crop_123")
        assertEquals("crop_123", cropSelector.cutoutId)

        val layoutSettings = PrimaryModalPayload.LayoutSettings(layoutId = "layout_custom")
        assertEquals("layout_custom", layoutSettings.layoutId)

        val profileSettings = PrimaryModalPayload.ProfileSettings(profileId = "profile_custom")
        assertEquals("profile_custom", profileSettings.profileId)
        assertEquals(false, profileSettings.isNewProfile)

        val newProfileSettings =
            PrimaryModalPayload.ProfileSettings(
                isNewProfile = true,
                presetName = "Preset Name",
            )
        assertEquals(null, newProfileSettings.profileId)
        assertEquals(true, newProfileSettings.isNewProfile)
        assertEquals("Preset Name", newProfileSettings.presetName)
    }
}
