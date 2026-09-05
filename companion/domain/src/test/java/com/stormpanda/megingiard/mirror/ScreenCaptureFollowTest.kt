package com.stormpanda.megingiard.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.MirrorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private fun getCutout(id: String = cutoutId) = ScreenCaptureManager.cutouts.value.find { it.id == id }!!

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MacroPadState.loadFrom(emptyList(), null)

        val dummyDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = emptyPreferences()
            }
        MirrorSettings.init(dummyDataStore, CoroutineScope(testDispatcher))

        val layout = MacroPadState.activeLayout.value!!
        val testCutout =
            ScreenCutout(
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
                motionSmoothing = false,
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
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.scope.cancel()
        ScreenCaptureManager.scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

        ScreenCaptureManager.onTouchReceived(0.5f, 0.5f)
        val cutout1 = getCutout()
        assertEquals(0.25f, cutout1.srcX, 0.001f)
        assertEquals(0.25f, cutout1.srcY, 0.001f)

        ScreenCaptureManager.onTouchReceived(0.2f, 0.2f)
        val cutout2 = getCutout()
        assertEquals(0f, cutout2.srcX, 0.001f)
        assertEquals(0f, cutout2.srcY, 0.001f)

        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)
        val cutout3 = getCutout()
        assertEquals(0.5f, cutout3.srcX, 0.001f)
        assertEquals(0.5f, cutout3.srcY, 0.001f)
    }

    @Test
    fun `onTouchReceived with smoothing enabled performs exponential decay interpolation`() =
        runTest(testDispatcher) {
            ScreenCaptureManager.setCapturing(true)
            ScreenCaptureManager.setFollowActive(true)

            val layout = MacroPadState.activeLayout.value!!
            val testCutout = layout.mirrorCutouts.find { it.id == cutoutId }!!.copy(motionSmoothing = true)
            MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(testCutout)))
            ScreenCaptureManager.setFollowActive(true)

            assertEquals(0.25f, getCutout().srcX, 0.001f)

            ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)
            delay(100)
            val moving = getCutout()
            assertTrue(moving.srcX > 0.25f)
            assertTrue(moving.srcX < 0.5f)

            delay(500)
            assertEquals(0.5f, getCutout().srcX, 0.001f)
        }

    @Test
    fun `toggleFollow persists follow state to active layout`() {
        val layout = MacroPadState.activeLayout.value!!
        assertFalse(layout.mirrorFollowActive)

        ScreenCaptureManager.toggleFollow()
        assertTrue(MacroPadState.activeLayout.value!!.mirrorFollowActive)

        ScreenCaptureManager.toggleFollow()
        assertFalse(MacroPadState.activeLayout.value!!.mirrorFollowActive)
    }

    @Test
    fun `restoreFromLayout restores follow active state`() {
        val layout = MacroPadState.activeLayout.value!!
        MacroPadState.setLayoutMirrorFollowActive(layout.id, true)
        assertTrue(MacroPadState.activeLayout.value!!.mirrorFollowActive)

        assertFalse(ScreenCaptureManager.isFollowActive.value)
        MirrorViewportController.restoreFromLayout()
        assertTrue(ScreenCaptureManager.isFollowActive.value)
    }

    @Test
    fun `onTouchReceived ignores touch when a macro is running`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        assertEquals(0.25f, getCutout().srcX, 0.001f)

        MacroExecutor.setRunningMacroIdsForTest(setOf("test-macro-id"))
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)
        assertEquals(0.25f, getCutout().srcX, 0.001f)

        MacroExecutor.setRunningMacroIdsForTest(emptySet())
        ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)
        assertEquals(0.5f, getCutout().srcX, 0.001f)
    }

    @Test
    fun `ensureFollowAnimationRunning cancels previous job when follow cutout target changes`() =
        runTest(testDispatcher) {
            ScreenCaptureManager.setCapturing(true)
            ScreenCaptureManager.setFollowActive(true)

            val layout = MacroPadState.activeLayout.value!!
            val testCutout1 = layout.mirrorCutouts[0].copy(id = "cutout-1", followTouch = true, motionSmoothing = true)
            val testCutout2 = layout.mirrorCutouts[0].copy(id = "cutout-2", followTouch = false, motionSmoothing = true)
            MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(testCutout1, testCutout2)))

            ScreenCaptureManager.setFollowActive(true)
            ScreenCaptureManager.onTouchReceived(0.9f, 0.9f)

            delay(50)
            assertTrue(getCutout("cutout-1").srcX > 0.25f)

            val updatedLayout = MacroPadState.activeLayout.value!!
            MacroPadState.updateLayout(
                updatedLayout.copy(
                    mirrorCutouts = listOf(testCutout1.copy(followTouch = false), testCutout2.copy(followTouch = true)),
                ),
            )

            ScreenCaptureManager.onTouchReceived(0.1f, 0.1f)
            delay(50)
            assertTrue(getCutout("cutout-2").srcX < 0.25f)

            val c1XBefore = getCutout("cutout-1").srcX
            delay(100)
            val c1XAfter = getCutout("cutout-1").srcX
            assertEquals(c1XBefore, c1XAfter, 0.001f)
        }
}
