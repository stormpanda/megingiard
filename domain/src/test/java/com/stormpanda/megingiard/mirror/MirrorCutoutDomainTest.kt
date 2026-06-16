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
    fun `loadFrom migrates layouts with empty cutouts to default full screen`() {
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
        assertEquals(1, activeLayout!!.mirrorCutouts.size)
        val cutout = activeLayout.mirrorCutouts.first()
        assertEquals("Full Screen", cutout.name)
        assertEquals(0f, cutout.srcX, 0.001f)
        assertEquals(0f, cutout.srcY, 0.001f)
        assertEquals(1f, cutout.srcWidth, 0.001f)
        assertEquals(1f, cutout.srcHeight, 0.001f)
        assertEquals(0f, cutout.destX, 0.001f)
        assertEquals(0f, cutout.destY, 0.001f)
        assertEquals(1f, cutout.destWidth, 0.001f)
        assertEquals(1f, cutout.destHeight, 0.001f)
    }

    @Test
    fun `setSurfaceSize migrates layouts with legacy viewport settings`() {
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

        // Call setSurfaceSize which triggers the migration
        ScreenCaptureManager.setSurfaceSize(1920f, 1080f)

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(1, activeLayout!!.mirrorCutouts.size)
        
        // Assert legacy fields are cleared
        assertEquals(1f, activeLayout.mirrorSavedScale, 0.001f)
        assertEquals(0f, activeLayout.mirrorSavedOffsetX, 0.001f)
        assertEquals(0f, activeLayout.mirrorSavedOffsetY, 0.001f)

        val cutout = activeLayout.mirrorCutouts.first()
        assertEquals(0.2f, cutout.srcWidth, 0.001f)
        assertEquals(0.2f, cutout.srcHeight, 0.001f)
        // srcX = (0.5 - 0.5/5 - 960/(1920*5)) = 0.5 - 0.1 - 0.1 = 0.3f
        assertEquals(0.3f, cutout.srcX, 0.001f)
        // srcY = (0.5 - 0.5/5 - 540/(1080*5)) = 0.5 - 0.1 - 0.1 = 0.3f
        assertEquals(0.3f, cutout.srcY, 0.001f)
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
}
