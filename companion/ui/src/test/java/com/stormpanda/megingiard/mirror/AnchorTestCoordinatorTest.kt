package com.stormpanda.megingiard.mirror

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalType
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
    fun `pointMatches and targetPoints default to empty and counts to zero`() {
        assertTrue(AnchorTestCoordinator.pointMatches.value.isEmpty())
        assertTrue(AnchorTestCoordinator.targetPoints.value.isEmpty())
        assertEquals(0, AnchorTestCoordinator.matchedPointCount.value)
        assertEquals(0, AnchorTestCoordinator.totalPointCount.value)
    }

    @Test
    fun `stopTesting resets all test states`() {
        AnchorTestCoordinator.stopTesting(resumeSuspended = false)
        assertFalse(AnchorTestCoordinator.isTesting.value)
        assertFalse(AnchorTestCoordinator.isAnchorActive.value)
        assertEquals(0f, AnchorTestCoordinator.currentMatchRatio.value, 0.001f)
        assertNull(AnchorTestCoordinator.referenceBitmap.value)
        assertNull(AnchorTestCoordinator.liveCropBitmap.value)
        assertTrue(AnchorTestCoordinator.pointMatches.value.isEmpty())
        assertTrue(AnchorTestCoordinator.targetPoints.value.isEmpty())
        assertEquals(0, AnchorTestCoordinator.matchedPointCount.value)
        assertEquals(0, AnchorTestCoordinator.totalPointCount.value)
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

    @Test
    fun `startTesting preserves existing suspended primary modal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editorModal = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
        val testModal = PrimaryModalConfig(type = PrimaryModalType.ANCHOR_SELECTOR)

        AppStateManager.openPrimaryModal(editorModal)
        AppStateManager.suspendCurrentAndOpen(testModal)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)

        val layout = PadLayout(id = "test_layout", name = "Test Layout")
        AnchorTestCoordinator.startTesting(context, layout)

        assertNull(AppStateManager.activePrimaryModal.value)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)
        assertTrue(AnchorTestCoordinator.isTesting.value)

        AnchorTestCoordinator.stopTesting(resumeSuspended = true)
        assertFalse(AnchorTestCoordinator.isTesting.value)
        assertEquals(editorModal, AppStateManager.activePrimaryModal.value)
    }
}
