package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroPadSettingsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MacroPadSettings.resetForTesting()
    }

    @After
    fun tearDown() {
        MacroPadSettings.resetForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun setters_updateStateFlowDirectly() {
        MacroPadSettings.setGamepadSwapFaceButtons(false)
        assertFalse(MacroPadSettings.gamepadSwapFaceButtons.value)

        MacroPadSettings.setGamepadSwapFaceButtons(true)
        assertTrue(MacroPadSettings.gamepadSwapFaceButtons.value)

        MacroPadSettings.setPrivdPromptDismissed(true)
        assertTrue(MacroPadSettings.privdPromptDismissed.value)

        MacroPadSettings.setDeadzoneLeft(0.25f)
        assertEquals(0.25f, MacroPadSettings.deadzoneLeft.value, 0.001f)

        MacroPadSettings.setDeadzoneRight(0.30f)
        assertEquals(0.30f, MacroPadSettings.deadzoneRight.value, 0.001f)

        MacroPadSettings.setButtonAlignmentSnapping(false)
        assertFalse(MacroPadSettings.buttonAlignmentSnapping.value)
        MacroPadSettings.setButtonAlignmentSnapping(true)
        assertTrue(MacroPadSettings.buttonAlignmentSnapping.value)

        MacroPadSettings.saveMacroPadData()
    }

    @Test
    fun loadFrom_loadOnceGuard_preventsOverwritingInMemoryProfiles() {
        val profile1 =
            PadProfile(
                id = "p1",
                name = "Profile 1",
                layouts = listOf(PadLayout(id = "l1", name = "Layout 1")),
                activeLayoutId = "l1",
            )
        val profile2 =
            PadProfile(
                id = "p2",
                name = "Profile 2",
                layouts = listOf(PadLayout(id = "l2", name = "Layout 2")),
                activeLayoutId = "l2",
            )
        val json1 = Json.encodeToString(listOf(profile1))
        val json2 = Json.encodeToString(listOf(profile2))

        val prefs1 =
            mutablePreferencesOf(
                KEY_MACROPAD_PROFILES to json1,
                KEY_MACROPAD_ACTIVE_PROFILE_ID to "p1",
            )
        MacroPadSettings.loadFrom(prefs1)
        assertEquals(1, MacroPadState.profiles.value.size)
        assertEquals("p1", MacroPadState.activeProfile.value?.id)
        assertEquals("Profile 1", MacroPadState.activeProfile.value?.name)

        // Second call with different prefs without calling resetForTesting()
        val prefs2 =
            mutablePreferencesOf(
                KEY_MACROPAD_PROFILES to json2,
                KEY_MACROPAD_ACTIVE_PROFILE_ID to "p2",
            )
        MacroPadSettings.loadFrom(prefs2)

        // In-memory profiles must not be overwritten
        assertEquals(1, MacroPadState.profiles.value.size)
        assertEquals("p1", MacroPadState.activeProfile.value?.id)
        assertEquals("Profile 1", MacroPadState.activeProfile.value?.name)
    }
}
