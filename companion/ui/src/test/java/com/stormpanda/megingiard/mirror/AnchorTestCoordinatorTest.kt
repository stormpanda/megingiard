package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnchorTestCoordinatorTest {
    @Before
    fun setUp() {
        AnchorTestCoordinator.stopTesting(resumeSuspended = false)
        AnchorPositioningCoordinator.consumeDoneRequest()
    }

    @Test
    fun `isTesting defaults to false`() {
        assertFalse(AnchorTestCoordinator.isTesting.value)
    }

    @Test
    fun `currentMatchRatio defaults to 0`() {
        assertEquals(0f, AnchorTestCoordinator.currentMatchRatio.value, 0.001f)
    }

    @Test
    fun `isAnchorActive defaults to false`() {
        assertFalse(AnchorTestCoordinator.isAnchorActive.value)
    }

    @Test
    fun `referenceBitmap and liveCropBitmap default to null`() {
        assertNull(AnchorTestCoordinator.referenceBitmap.value)
        assertNull(AnchorTestCoordinator.liveCropBitmap.value)
    }

    @Test
    fun `stopTesting resets all test states`() {
        AnchorTestCoordinator.stopTesting(resumeSuspended = false)
        assertFalse(AnchorTestCoordinator.isTesting.value)
        assertFalse(AnchorTestCoordinator.isAnchorActive.value)
        assertEquals(0f, AnchorTestCoordinator.currentMatchRatio.value, 0.001f)
        assertNull(AnchorTestCoordinator.referenceBitmap.value)
        assertNull(AnchorTestCoordinator.liveCropBitmap.value)
    }

    @Test
    fun `AnchorPositioningCoordinator requestDone and consumeDoneRequest roundtrip`() {
        assertFalse(AnchorPositioningCoordinator.isDoneRequested.value)
        AnchorPositioningCoordinator.requestDone()
        assertTrue(AnchorPositioningCoordinator.isDoneRequested.value)
        val consumed = AnchorPositioningCoordinator.consumeDoneRequest()
        assertTrue(consumed)
        assertFalse(AnchorPositioningCoordinator.isDoneRequested.value)
    }
}
