package com.stormpanda.megingiard.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.stormpanda.megingiard.settings.MirrorSettings
import com.stormpanda.megingiard.macropad.MacroPadState
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
        val layout = MacroPadState.activeLayout.value!!
        MacroPadState.updateLayout(layout.copy(mirrorSmoothing = false, mirrorZoom = 5f, mirrorAcceleration = 0.05f))

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
        assertEquals(5f, ScreenCaptureManager.scale.value, 0.001f)

        ScreenCaptureManager.toggleFollow()
        assertFalse(ScreenCaptureManager.isFollowActive.value)
        assertEquals(1f, ScreenCaptureManager.scale.value, 0.001f)
    }

    @Test
    fun `onTouchReceived centers correctly without clamping`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Center touch
        ScreenCaptureManager.onTouchReceived(0.5f, 0.5f)
        assertEquals(0f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(0f, ScreenCaptureManager.offsetY.value, 0.001f)

        // Top-left touch -> should slide viewport to bottom-right to keep content in view
        // nx = 0.2f, ny = 0.2f
        // targetOffsetX = -(0.2 - 0.5) * 1920 * 5 = 0.3 * 9600 = 2880f
        ScreenCaptureManager.onTouchReceived(0.2f, 0.2f)
        assertEquals(2880f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(1620f, ScreenCaptureManager.offsetY.value, 0.001f)

        // Extrema touch -> should NOT be clamped to bounds, allowing black bars
        // nx = 0.0f, ny = 0.0f
        // targetOffsetX = -(0.0 - 0.5) * 1920 * 5 = 4800f
        // targetOffsetY = -(0.0 - 0.5) * 1080 * 5 = 2700f
        ScreenCaptureManager.onTouchReceived(0f, 0f)
        assertEquals(4800f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(2700f, ScreenCaptureManager.offsetY.value, 0.001f)
    }

    @Test
    fun `onMouseMoved updates virtual cursor and centers correctly`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Initial cursor starts at center (960, 540)
        // Move mouse by (100, -50)
        // dx = 100, dy = -50
        val dx = 100
        val dy = -50
        val velocity = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
        
        val lowThreshold = 2f
        val highThreshold = 15f
        val accel = MacroPadState.activeLayout.value!!.mirrorAcceleration
        val gain = when {
            velocity <= lowThreshold -> 1f
            velocity >= highThreshold -> 1f + accel * (highThreshold - lowThreshold)
            else -> 1f + accel * (velocity - lowThreshold)
        }
        
        val expectedCursorX = (960f + dx * gain).coerceIn(0f, 1920f)
        val expectedCursorY = (540f + dy * gain).coerceIn(0f, 1080f)

        ScreenCaptureManager.onMouseMoved(dx, dy)
        
        val expectedNx = expectedCursorX / 1920f
        val expectedNy = expectedCursorY / 1080f
        
        val sw = 1920f
        val sh = 1080f
        val scale = 5f
        
        val expectedOffsetX = -(expectedNx - 0.5f) * sw * scale
        val expectedOffsetY = -(expectedNy - 0.5f) * sh * scale
        
        assertEquals(expectedOffsetX, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(expectedOffsetY, ScreenCaptureManager.offsetY.value, 0.001f)
    }

    @Test
    fun `onTouchReceived with smoothing enabled performs exponential decay interpolation`() = runTest(testDispatcher) {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)
        val layout = MacroPadState.activeLayout.value!!
        MacroPadState.updateLayout(layout.copy(mirrorSmoothing = true))

        // Initial position is at (0, 0)
        assertEquals(0f, ScreenCaptureManager.offsetX.value, 0.001f)

        // Move to target at top-left (nx=0.2f, ny=0.2f) -> targetOffsetX=2880f
        ScreenCaptureManager.onTouchReceived(0.2f, 0.2f)

        // Wait 100ms
        delay(100)
        val intermediateX = ScreenCaptureManager.offsetX.value
        assertTrue("intermediateX ($intermediateX) should have moved from 0", intermediateX > 0f)
        assertTrue("intermediateX ($intermediateX) should be less than target 2880", intermediateX < 2880f)

        // Wait another 500ms to allow Lerp to snap (requires ~540ms total)
        delay(500)
        assertEquals(2880f, ScreenCaptureManager.offsetX.value, 0.001f)
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
}
