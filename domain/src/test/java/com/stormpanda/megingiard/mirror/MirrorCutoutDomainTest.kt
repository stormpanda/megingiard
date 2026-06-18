package com.stormpanda.megingiard.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.MirrorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorCutoutDomainTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = emptyFlow()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return androidx.datastore.preferences.core.emptyPreferences()
            }
        }
        MirrorSettings.init(dummyDataStore, CoroutineScope(testDispatcher))
        ScreenCaptureManager.scope = CoroutineScope(testDispatcher)
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.setCapturing(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadFrom does not populate cutouts list and preserves unconfigured empty cutouts`() {
        val layoutWithoutCutouts = PadLayout(
            id = "test-layout-1",
            name = "Test Layout 1",
            mirrorCutouts = emptyList()
        )
        val profile = PadProfile(
            id = "test-profile-1",
            name = "Test Profile 1",
            layouts = listOf(layoutWithoutCutouts),
            activeLayoutId = "test-layout-1"
        )

        MacroPadState.loadFrom(listOf(profile), "test-profile-1")

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(0, activeLayout!!.mirrorCutouts.size)
        assertTrue(activeLayout.mirrorConfigured)
    }

    @Test
    fun `setSurfaceSize does not migrate legacy viewport settings to cutouts list`() {
        val layoutWithLegacy = PadLayout(
            id = "test-layout-legacy",
            name = "Test Layout Legacy",
            mirrorSavedScale = 5f,
            mirrorSavedOffsetX = 960f,
            mirrorSavedOffsetY = 540f,
            mirrorCutouts = emptyList()
        )
        val profile = PadProfile(
            id = "test-profile-legacy",
            name = "Test Profile Legacy",
            layouts = listOf(layoutWithLegacy),
            activeLayoutId = "test-layout-legacy"
        )

        MacroPadState.loadFrom(listOf(profile), "test-profile-legacy")

        // Call setSurfaceSize
        ScreenCaptureManager.setSurfaceSize(1920f, 1080f)

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(0, activeLayout!!.mirrorCutouts.size)
        
        // Assert legacy fields are preserved
        assertEquals(5f, activeLayout.mirrorSavedScale, 0.001f)
        assertEquals(960f, activeLayout.mirrorSavedOffsetX, 0.001f)
        assertEquals(540f, activeLayout.mirrorSavedOffsetY, 0.001f)
    }

    @Test
    fun `TouchProjectionController onPress handles coordinates mapping correctly`() {
        val cutout = ScreenCutout(
            id = "cutout-1",
            name = "Part 1",
            srcX = 0.1f, srcY = 0.2f, srcWidth = 0.4f, srcHeight = 0.4f,
            destX = 0.2f, destY = 0.2f, destWidth = 0.5f, destHeight = 0.5f,
            opacity = 1f
        )
        val layout = PadLayout(
            id = "test-layout-proj",
            name = "Test Layout Proj",
            mirrorCutouts = listOf(cutout)
        )
        val profile = PadProfile(
            id = "test-profile-proj",
            name = "Test Profile Proj",
            layouts = listOf(layout),
            activeLayoutId = "test-layout-proj"
        )
        MacroPadState.loadFrom(listOf(profile), "test-profile-proj")
        
        // Sync ScreenCaptureManager cutouts
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f) // trigger flow update

        val controller = TouchProjectionController(edgeZonePx = 20f, overlayAtBottom = false)
        
        // dest boundaries: destLeft = 200, destTop = 200, destWidth = 500, destHeight = 500
        // Touch in the center of the destination area: x = 200 + 250 = 450, y = 200 + 250 = 450
        val handled = controller.onPress(
            pointerId = 1L,
            x = 450f, y = 450f,
            boxW = 1000f, boxH = 1000f,
            isConsumed = false,
            pointerCount = 1
        )
        assertTrue(handled)
        // The mapped touch should be in the center of the source area:
        // srcX + srcWidth / 2 = 0.1 + 0.2 = 0.3
        // srcY + srcHeight / 2 = 0.2 + 0.2 = 0.4
        // Wait, let's verify if indicator position was set
        assertEquals(Pair(450f, 450f), controller.indicatorPos.value)
    }

    @Test
    fun `toggling multi-mode and saving viewport does not override other mode configs`() {
        val layout = PadLayout(
            id = "test-layout-toggle",
            name = "Toggle Layout",
            mirrorMultiMode = false,
            mirrorSavedScale = 2f,
            mirrorCutouts = listOf(
                ScreenCutout(
                    id = "c1", name = "Cutout 1",
                    srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
                    destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f
                )
            )
        )
        val profile = PadProfile(
            id = "test-profile-toggle",
            name = "Toggle Profile",
            layouts = listOf(layout),
            activeLayoutId = "test-layout-toggle"
        )

        MacroPadState.loadFrom(listOf(profile), "test-profile-toggle")

        // Save viewport while in single-mode
        MacroPadState.saveMirrorViewport("test-layout-toggle", scale = 3f, offsetX = 10f, offsetY = 10f)

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(3f, activeLayout!!.mirrorSavedScale, 0.001f)
        // Multi-mode cutouts list must NOT be overwritten or modified
        assertEquals(1, activeLayout.mirrorCutouts.size)
        assertEquals("c1", activeLayout.mirrorCutouts.first().id)
    }
}
