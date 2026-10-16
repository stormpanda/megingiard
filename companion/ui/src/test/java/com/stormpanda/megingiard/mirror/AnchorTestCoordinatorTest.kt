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
    }

    @Test
    fun `isTesting defaults to false`() {
        assertFalse(AnchorTestCoordinator.uiState.value.isTesting)
    }

    @Test
    fun `currentMatchRatio defaults to 0`() {
        assertEquals(0f, AnchorTestCoordinator.uiState.value.currentMatchRatio, 0.001f)
    }

    @Test
    fun `isAnchorActive defaults to false`() {
        assertFalse(AnchorTestCoordinator.uiState.value.isAnchorActive)
    }

    @Test
    fun `referenceBitmap and liveCropBitmap default to null`() {
        assertNull(AnchorTestCoordinator.referenceBitmap.value)
        assertNull(AnchorTestCoordinator.liveCropBitmap.value)
    }

    @Test
    fun `pointMatches and targetPoints default to empty and counts to zero`() {
        val state = AnchorTestCoordinator.uiState.value
        assertTrue(state.pointMatches.isEmpty())
        assertTrue(state.targetPoints.isEmpty())
        assertEquals(0, state.matchedPointCount)
        assertEquals(0, state.totalPointCount)
    }

    @Test
    fun `stopTesting resets all test states`() {
        AnchorTestCoordinator.stopTesting(resumeSuspended = false)
        val state = AnchorTestCoordinator.uiState.value
        assertFalse(state.isTesting)
        assertFalse(state.isAnchorActive)
        assertEquals(0f, state.currentMatchRatio, 0.001f)
        assertNull(AnchorTestCoordinator.referenceBitmap.value)
        assertNull(AnchorTestCoordinator.liveCropBitmap.value)
        assertTrue(state.pointMatches.isEmpty())
        assertTrue(state.targetPoints.isEmpty())
        assertEquals(0, state.matchedPointCount)
        assertEquals(0, state.totalPointCount)
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
        assertTrue(AnchorTestCoordinator.uiState.value.isTesting)

        AnchorTestCoordinator.stopTesting(resumeSuspended = true)
        assertFalse(AnchorTestCoordinator.uiState.value.isTesting)
        assertEquals(editorModal, AppStateManager.activePrimaryModal.value)
    }
}
