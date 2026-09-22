package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisualAutoTuneCoordinatorTest {
    @Before
    fun setUp() {
        VisualAutoTuneCoordinator.cancelCalibration(resumeSuspended = false)
    }

    @Test
    fun `isPaused defaults to false`() {
        assertFalse(VisualAutoTuneCoordinator.isPaused.value)
    }

    @Test
    fun `togglePause does not change state when not calibrating`() {
        assertFalse(VisualAutoTuneCoordinator.isCalibrating.value)
        VisualAutoTuneCoordinator.togglePause()
        assertFalse(VisualAutoTuneCoordinator.isPaused.value)
    }

    @Test
    fun `setPaused does not change state when not calibrating`() {
        assertFalse(VisualAutoTuneCoordinator.isCalibrating.value)
        VisualAutoTuneCoordinator.setPaused(true)
        assertFalse(VisualAutoTuneCoordinator.isPaused.value)
    }

    @Test
    fun `cancelCalibration resets isPaused state`() {
        VisualAutoTuneCoordinator.cancelCalibration(resumeSuspended = false)
        assertFalse(VisualAutoTuneCoordinator.isCalibrating.value)
        assertFalse(VisualAutoTuneCoordinator.isPaused.value)
    }
}
