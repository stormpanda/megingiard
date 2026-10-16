package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.settings.SettingsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryModalConfigTest {
    @Test
    fun `payload and config default values are expected`() {
        val defaultConfig = PrimaryModalConfig(type = PrimaryModalType.GLOBAL_SETTINGS)
        assertNull(defaultConfig.payload)

        val macroPadPayload = PrimaryModalPayload.MacroPad()
        assertEquals(EditorSection.QUICK_ACTIONS, macroPadPayload.section)
        assertNull(macroPadPayload.profileId)
        assertNull(macroPadPayload.layoutId)
        assertNull(macroPadPayload.macroId)
        assertFalse(macroPadPayload.editPositions)
        assertNull(macroPadPayload.focusStepIndex)

        val settingsPayload = PrimaryModalPayload.GlobalSettings()
        assertEquals(SettingsCategory.GENERAL, settingsPayload.category)
        assertNull(settingsPayload.subPage)

        val profileSettingsPayload = PrimaryModalPayload.ProfileSettings(profileId = "profile_custom")
        assertFalse(profileSettingsPayload.isNewProfile)
        assertNull(profileSettingsPayload.presetName)
    }
}
