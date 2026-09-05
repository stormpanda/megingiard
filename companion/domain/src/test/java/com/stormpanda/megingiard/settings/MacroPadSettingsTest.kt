package com.stormpanda.megingiard.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
    }

    @After
    fun tearDown() {
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

        MacroPadSettings.saveMacroPadData()
    }
}
