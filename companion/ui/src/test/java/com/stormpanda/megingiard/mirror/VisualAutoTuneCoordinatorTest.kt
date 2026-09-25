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

    @Test
    fun `startLayoutAnchorCalibration preserves existing suspended primary modal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editorModal = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
        val anchorModal = PrimaryModalConfig(type = PrimaryModalType.ANCHOR_SELECTOR)

        AppStateManager.openPrimaryModal(editorModal)
        AppStateManager.suspendCurrentAndOpen(anchorModal)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)
        assertEquals(anchorModal, AppStateManager.activePrimaryModal.value)

        val layout = PadLayout(id = "layout_anchor_test", name = "Test Layout")
        VisualAutoTuneCoordinator.startLayoutAnchorCalibration(context, layout)

        // Overlay closed on Display 0, but original suspended editor modal preserved
        assertNull(AppStateManager.activePrimaryModal.value)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)
        assertTrue(VisualAutoTuneCoordinator.isCalibrating.value)

        // Cancelling restores the original suspended modal
        VisualAutoTuneCoordinator.cancelCalibration(resumeSuspended = true)
        assertFalse(VisualAutoTuneCoordinator.isCalibrating.value)
        assertEquals(editorModal, AppStateManager.activePrimaryModal.value)
        assertNull(AppStateManager.suspendedPrimaryModal.value)
    }

    @Test
    fun `startCalibration preserves existing suspended primary modal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editorModal = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
        val settingsModal = PrimaryModalConfig(type = PrimaryModalType.KEYBOARD_SETTINGS)

        AppStateManager.openPrimaryModal(editorModal)
        AppStateManager.suspendCurrentAndOpen(settingsModal)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)

        val cutout =
            ScreenCutout(
                id = "cutout_test",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 0.5f,
                srcHeight = 0.5f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.5f,
                destHeight = 0.5f,
            )
        VisualAutoTuneCoordinator.startCalibration(context, cutout)

        assertNull(AppStateManager.activePrimaryModal.value)
        assertEquals(editorModal, AppStateManager.suspendedPrimaryModal.value)
        assertTrue(VisualAutoTuneCoordinator.isCalibrating.value)

        VisualAutoTuneCoordinator.cancelCalibration(resumeSuspended = true)
        assertFalse(VisualAutoTuneCoordinator.isCalibrating.value)
        assertEquals(editorModal, AppStateManager.activePrimaryModal.value)
    }
}
