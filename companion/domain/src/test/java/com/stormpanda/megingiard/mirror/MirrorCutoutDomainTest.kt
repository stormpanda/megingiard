package com.stormpanda.megingiard.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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

    private fun loadLayout(
        id: String = "test-layout",
        name: String = "Test Layout",
        mirrorCutouts: List<ScreenCutout> = emptyList(),
        mirrorMultiMode: Boolean = false,
        mirrorSavedScale: Float = 1f,
        mirrorSavedOffsetX: Float = 0f,
        mirrorSavedOffsetY: Float = 0f,
    ): PadLayout {
        val layout =
            PadLayout(
                id = id,
                name = name,
                mirrorCutouts = mirrorCutouts,
                mirrorMultiMode = mirrorMultiMode,
                mirrorSavedScale = mirrorSavedScale,
                mirrorSavedOffsetX = mirrorSavedOffsetX,
                mirrorSavedOffsetY = mirrorSavedOffsetY,
            )
        val profile = PadProfile(id = "profile-$id", name = "Profile", layouts = listOf(layout), activeLayoutId = id)
        MacroPadState.loadFrom(listOf(profile), profile.id)
        return layout
    }

    private fun sampleCutout(
        id: String = "cutout-1",
        touchProjectionEnabled: Boolean = true,
    ) = ScreenCutout(
        id = id,
        name = "Part 1",
        srcX = 0.1f,
        srcY = 0.2f,
        srcWidth = 0.4f,
        srcHeight = 0.4f,
        destX = 0.2f,
        destY = 0.2f,
        destWidth = 0.5f,
        destHeight = 0.5f,
        opacity = 1f,
        touchProjectionEnabled = touchProjectionEnabled,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = emptyPreferences()
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
        loadLayout("test-layout-1")

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(0, activeLayout!!.mirrorCutouts.size)
        assertTrue(activeLayout.mirrorConfigured)
    }

    @Test
    fun `setSurfaceSize does not migrate legacy viewport settings to cutouts list`() {
        loadLayout(
            id = "test-layout-legacy",
            mirrorSavedScale = 5f,
            mirrorSavedOffsetX = 960f,
            mirrorSavedOffsetY = 540f,
        )

        ScreenCaptureManager.setSurfaceSize(1920f, 1080f)

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(0, activeLayout!!.mirrorCutouts.size)
        assertEquals(5f, activeLayout.mirrorSavedScale, 0.001f)
        assertEquals(960f, activeLayout.mirrorSavedOffsetX, 0.001f)
        assertEquals(540f, activeLayout.mirrorSavedOffsetY, 0.001f)
    }

    @Test
    fun `TouchProjectionController onPress handles coordinates mapping correctly`() {
        loadLayout("test-layout-proj", mirrorCutouts = listOf(sampleCutout()))
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f)

        val controller = TouchProjectionController(edgeZonePx = 20f, overlayAtBottom = false)

        val handled =
            controller.onPress(
                pointerId = 1L,
                x = 450f,
                y = 450f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue(handled)
        assertEquals(Pair(450f, 450f), controller.indicatorPos.value)
    }

    @Test
    fun `toggling multi-mode and saving viewport does not override other mode configs`() {
        loadLayout(
            id = "test-layout-toggle",
            mirrorMultiMode = false,
            mirrorSavedScale = 2f,
            mirrorCutouts = listOf(sampleCutout("c1")),
        )

        MacroPadState.saveMirrorViewport("test-layout-toggle", scale = 3f, offsetX = 10f, offsetY = 10f)

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(3f, activeLayout!!.mirrorSavedScale, 0.001f)
        assertEquals(1, activeLayout.mirrorCutouts.size)
        assertEquals("c1", activeLayout.mirrorCutouts.first().id)
    }

    @Test
    fun `restoreFromLayout on layout with empty cutouts preserves empty cutouts`() {
        loadLayout("test-layout-empty-cutouts")

        ScreenCaptureManager.setCaptureSourceSize(1920, 1080)
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f)

        MirrorViewportController.restoreFromLayout()

        val activeLayout = MacroPadState.activeLayout.value
        assertNotNull(activeLayout)
        assertEquals(0, activeLayout!!.mirrorCutouts.size)
        assertTrue(activeLayout.mirrorCutouts.isEmpty())
    }

    @Test
    fun `TouchProjectionController multi touch allocates distinct slots`() {
        loadLayout("test-layout-proj", mirrorCutouts = listOf(sampleCutout()))
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f)

        val controller = TouchProjectionController(edgeZonePx = 20f, overlayAtBottom = false)

        val handled1 =
            controller.onPress(
                pointerId = 1L,
                x = 450f,
                y = 450f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue(handled1)
        assertEquals(Pair(450f, 450f), controller.indicatorPos.value)

        val handled2 =
            controller.onPress(
                pointerId = 2L,
                x = 500f,
                y = 500f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 2,
            )
        assertTrue(handled2)
        assertEquals(Pair(450f, 450f), controller.indicatorPos.value)

        controller.onRelease(1L, 450f, 450f, 1000f, 1000f)
        controller.onRelease(2L, 500f, 500f, 1000f, 1000f)
    }
}
