package com.stormpanda.megingiard.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.MirrorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenCaptureFollowTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val cutoutId = "test-cutout-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MacroPadState.loadFrom(emptyList(), null)
        
        val dummyDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = emptyFlow()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return androidx.datastore.preferences.core.emptyPreferences()
            }
        }
        MirrorSettings.init(dummyDataStore, CoroutineScope(testDispatcher))
        
        // Setup default layout with one follow-touch enabled cutout
        val layout = MacroPadState.activeLayout.value!!
        val testCutout = ScreenCutout(
            id = cutoutId,
            name = "Test Cutout",
            srcX = 0.25f,
            srcY = 0.25f,
            srcWidth = 0.5f,
            srcHeight = 0.5f,
            destX = 0f,
            destY = 0f,
            destWidth = 0.5f,
            destHeight = 0.5f,
            followTouch = true,
            motionSmoothing = false
        )
        MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(testCutout)))

        ScreenCaptureManager.scope = CoroutineScope(SupervisorJob() + testDispatcher)
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setSurfaceSize(1920f, 1080f)
        ScreenCaptureManager.setCaptureSourceSize(1920, 1080)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleFollow updates isFollowActive`() {
        assertFalse(ScreenCaptureManager.isFollowActive.value)
        ScreenCaptureManager.toggleFollow()
        assertTrue(ScreenCaptureManager.isFollowActive.value)

        ScreenCaptureManager.toggleFollow()
        assertFalse(ScreenCaptureManager.isFollowActive.value)
    }

    @Test
    fun `onTouchReceived centers cutout crop correctly within bounds`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Center touch (0.5, 0.5)
        // targetSrcX = 0.5 - 0.5/2 = 0.25f
        // targetSrcY = 0.5 - 0.5/2 = 0.25f
        ScreenCaptureManager.onTouchReceived(0.5f, 0.5f)
        val cutout1 = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.25f, cutout1.srcX, 0.001f)
        assertEquals(0.25f, cutout1.srcY, 0.001f)

        // Touch at (0.2f, 0.2f)
        // targetSrcX = 0.2 - 0.25 = -0.05 -> coerced to 0f
        // targetSrcY = 0.2 - 0.25 = -0.05 -> coerced to 0f
        ScreenCaptureManager.onTouchReceived(0.2f, 0.2f)
        val cutout2 = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0f, cutout2.srcX, 0.001f)
        assertEquals(0f, cutout2.srcY, 0.001f)

        // Touch at (0.9f, 0.9f)
        // targetSrcX = 0.9 - 0.25 = 0.65 -> coerced to 0.5f (since 1.0 - srcWidth = 0.5)
        // targetSrcY = 0.9 - 0.25 = 0.65 -> coerced to 0.5f
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)
        val cutout3 = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.5f, cutout3.srcX, 0.001f)
        assertEquals(0.5f, cutout3.srcY, 0.001f)
    }

    @Test
    fun `onTouchReceived with smoothing enabled performs exponential decay interpolation`() = runTest(testDispatcher) {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)
        
        // Enable motion smoothing on the cutout
        val layout = MacroPadState.activeLayout.value!!
        val testCutout = layout.mirrorCutouts.find { it.id == cutoutId }!!.copy(motionSmoothing = true)
        MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(testCutout)))
        
        // Trigger capture manager collection updates
        ScreenCaptureManager.setFollowActive(true)

        // Initial position is at (0.25, 0.25)
        var cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.25f, cutout.srcX, 0.001f)

        // Touch at (0.9f, 0.9f) -> targetSrcX = 0.5f
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)

        // Wait 100ms
        delay(100)
        cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertTrue("cutout.srcX (${cutout.srcX}) should have moved from 0.25", cutout.srcX > 0.25f)
        assertTrue("cutout.srcX (${cutout.srcX}) should be less than target 0.5", cutout.srcX < 0.5f)

        // Wait another 500ms to allow Lerp to snap
        delay(500)
        cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.5f, cutout.srcX, 0.001f)
    }

    @Test
    fun `toggleFollow persists follow state to active layout`() {
        // Initially, follow mode should be false in the layout
        val layout = MacroPadState.activeLayout.value!!
        assertFalse(layout.mirrorFollowActive)

        // Toggling follow on should persist true to the layout
        ScreenCaptureManager.toggleFollow()
        assertTrue(MacroPadState.activeLayout.value!!.mirrorFollowActive)

        // Toggling follow off should persist false to the layout
        ScreenCaptureManager.toggleFollow()
        assertFalse(MacroPadState.activeLayout.value!!.mirrorFollowActive)
    }

    @Test
    fun `restoreFromLayout restores follow active state`() {
        // Setup layout with follow mode active
        val layout = MacroPadState.activeLayout.value!!
        MacroPadState.setLayoutMirrorFollowActive(layout.id, true)
        assertTrue(MacroPadState.activeLayout.value!!.mirrorFollowActive)

        // restoreFromLayout should activate follow in ScreenCaptureManager
        assertFalse(ScreenCaptureManager.isFollowActive.value)
        MirrorViewportController.restoreFromLayout()
        assertTrue(ScreenCaptureManager.isFollowActive.value)
    }

    @Test
    fun `onTouchReceived ignores touch when a macro is running`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Initial offsets should be 0.25f
        var cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.25f, cutout.srcX, 0.001f)

        // Mock running macro
        MacroExecutor.setRunningMacroIdsForTest(setOf("test-macro-id"))

        // Send touch event (0.9f, 0.9f)
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)

        // Offsets should remain 0.25f (ignored)
        cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.25f, cutout.srcX, 0.001f)

        // Clear running macros
        MacroExecutor.setRunningMacroIdsForTest(emptySet())

        // Send touch event again
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)

        // Now it should center: targetSrcX = 0.5f
        cutout = ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }!!
        assertEquals(0.5f, cutout.srcX, 0.001f)
    }
}
