package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "PrimaryOverlayFreezeTest"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryOverlayFreezeTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setFrozen(false)
        AppStateManager.closePrimaryModal()
        AppStateManager.setActiveCropCutoutId(null)
    }

    @After
    fun tearDown() {
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setFrozen(false)
        AppStateManager.closePrimaryModal()
        AppStateManager.setActiveCropCutoutId(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testPrimaryOverlayActivity_freezesAndResumesCapture() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFrozen(false)
        AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.GLOBAL_SETTINGS))

        val controller = Robolectric.buildActivity(PrimaryOverlayActivity::class.java).setup()
        assertTrue("Mirror capture should freeze when primary overlay activity opens", ScreenCaptureManager.isFrozen.value)

        controller.get().finish()
        controller.pause().stop().destroy()
        assertFalse("Live mirror capture should resume when primary overlay activity finishes", ScreenCaptureManager.isFrozen.value)
    }

    @Test
    fun testPrimaryOverlayActivity_preservesInitialManualFreeze() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFrozen(true)
        AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.GLOBAL_SETTINGS))

        val controller = Robolectric.buildActivity(PrimaryOverlayActivity::class.java).setup()
        assertTrue("Mirror capture should remain frozen", ScreenCaptureManager.isFrozen.value)

        controller.get().finish()
        controller.pause().stop().destroy()
        assertTrue(
            "Mirror capture should remain frozen if it was manually frozen before overlay opened",
            ScreenCaptureManager.isFrozen.value,
        )
    }

    @Test
    fun testPrimaryOverlayActivity_whenNotCapturing_doesNotFreeze() {
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setFrozen(false)
        AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.GLOBAL_SETTINGS))

        val controller = Robolectric.buildActivity(PrimaryOverlayActivity::class.java).setup()
        assertFalse("Mirror capture should not be marked frozen when capture is not active", ScreenCaptureManager.isFrozen.value)

        controller.get().finish()
        controller.pause().stop().destroy()
        assertFalse(ScreenCaptureManager.isFrozen.value)
    }
}
