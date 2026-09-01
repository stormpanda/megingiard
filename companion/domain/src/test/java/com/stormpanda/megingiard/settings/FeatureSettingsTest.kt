package com.stormpanda.megingiard.settings

import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeatureSettingsTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tempFile: java.io.File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile =
            java.io.File
                .createTempFile("feat_settings_test", ".preferences_pb")
                .apply { deleteOnExit() }
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher)
        val testDataStore =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { tempFile },
                scope = testScope,
            )
        TouchpadSettings.init(testDataStore, testScope)
        KeyboardSettings.init(testDataStore, testScope)
        MirrorSettings.init(testDataStore, testScope)
        MacroPadSettings.init(testDataStore, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        TouchpadSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        KeyboardSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        MirrorSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        MacroPadSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        SettingsManager.resetForTesting(RuntimeEnvironment.getApplication())
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun testTouchpadSettingsSetters() {
        TouchpadSettings.setTouchpadUseMouse(true)
        assertTrue(TouchpadSettings.touchpadUseMouse.value)

        TouchpadSettings.setTouchpadTapToClick(false)
        assertFalse(TouchpadSettings.touchpadTapToClick.value)

        TouchpadSettings.setTouchpadTwoFingerTap(false)
        assertFalse(TouchpadSettings.touchpadTwoFingerTap.value)

        TouchpadSettings.setTouchpadThreeFingerTap(false)
        assertFalse(TouchpadSettings.touchpadThreeFingerTap.value)

        TouchpadSettings.setTouchpadTapDrag(false)
        assertFalse(TouchpadSettings.touchpadTapDrag.value)

        TouchpadSettings.setTouchpadTwoFingerScroll(false)
        assertFalse(TouchpadSettings.touchpadTwoFingerScroll.value)

        TouchpadSettings.setTouchpadMirroringEnabled(true)
        assertTrue(TouchpadSettings.touchpadMirroringEnabled.value)

        TouchpadSettings.setTouchpadMirrorDim(75)
        assertEquals(75, TouchpadSettings.touchpadMirrorDim.value)

        TouchpadSettings.setTouchpadMouse45Enabled(true)
        assertTrue(TouchpadSettings.touchpadMouse45Enabled.value)

        TouchpadSettings.setTouchpadSensitivity(2.0f)
        assertEquals(2.0f, TouchpadSettings.touchpadSensitivity.value, 0.001f)

        TouchpadSettings.setTouchpadNaturalScroll(false)
        assertFalse(TouchpadSettings.touchpadNaturalScroll.value)

        TouchpadSettings.setTouchpadScrollSpeed(2.5f)
        assertEquals(2.5f, TouchpadSettings.touchpadScrollSpeed.value, 0.001f)

        TouchpadSettings.setTouchpadHapticsEnabled(false)
        assertFalse(TouchpadSettings.touchpadHapticsEnabled.value)
    }

    @Test
    fun testKeyboardSettingsSetters() {
        KeyboardSettings.setKbLayout(KbLayout.QWERTY)
        assertEquals(KbLayout.QWERTY, KeyboardSettings.kbLayout.value)

        KeyboardSettings.setKbTrackpointEnabled(false)
        assertFalse(KeyboardSettings.kbTrackpointEnabled.value)

        KeyboardSettings.setKbRepeatEnabled(false)
        assertFalse(KeyboardSettings.kbRepeatEnabled.value)

        KeyboardSettings.setKbFullscreen(true)
        assertTrue(KeyboardSettings.kbFullscreen.value)

        KeyboardSettings.setKbMouseBtnPos(KbMouseBtnPos.RIGHT)
        assertEquals(KbMouseBtnPos.RIGHT, KeyboardSettings.kbMouseBtnPos.value)

        KeyboardSettings.setKbTouchpadEnabled(false)
        assertFalse(KeyboardSettings.kbTouchpadEnabled.value)
    }

    @Test
    fun testMirrorSettingsSetters() {
        MirrorSettings.setRememberViewport(true)
        assertTrue(MirrorSettings.rememberViewport.value)

        MirrorSettings.setRememberLock(true)
        assertTrue(MirrorSettings.rememberLock.value)

        MirrorSettings.setRememberProjection(true)
        assertTrue(MirrorSettings.rememberProjection.value)

        MirrorSettings.saveMirrorSessionState()
    }

    @Test
    fun testMacroPadSettingsSetters() {
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
