package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.macropad.LayoutVisualAnchor
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HudPresenceManagerTest {
    @Test
    fun `isLayoutHudLost returns false for unknown layout`() {
        assertFalse(HudPresenceManager.isLayoutHudLost("unknown_layout_123"))
    }

    @Test
    fun `clearLayout removes presence state`() {
        val layoutId = "test_layout_clear"
        HudPresenceManager.clearLayout(layoutId)
        assertFalse(HudPresenceManager.isLayoutHudLost(layoutId))
    }

    @Test
    fun `getDelayedFrame returns null when delay is zero`() {
        val frame = HudPresenceManager.getDelayedFrame("cutout_123", delayFrames = 0)
        assertNull(frame)
    }

    @Test
    fun `getDelayedFrame returns null when no frames buffered`() {
        val frame = HudPresenceManager.getDelayedFrame("non_existent_cutout", delayFrames = 2)
        assertNull(frame)
    }

    @Test
    fun `getFrozenFrame returns null when neither cache nor disk has frame`() {
        val context = RuntimeEnvironment.getApplication()
        val frame = HudPresenceManager.getFrozenFrame(context, "cutout_no_frame")
        assertNull(frame)
    }

    @Test
    fun `getFrozenFrame retrieves saved freeze frame from disk if not in memory cache`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "cutout_disk_frame_test"
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        CutoutMaskManager.saveFreezeFrame(context, cutoutId, bitmap)

        val retrieved = HudPresenceManager.getFrozenFrame(context, cutoutId)
        assertNotNull(retrieved)
        assertEquals(10, retrieved!!.width)
        assertEquals(10, retrieved.height)

        CutoutMaskManager.deleteMask(context, cutoutId)
        HudPresenceManager.clearCutout(cutoutId)
    }

    @Test
    fun `updateMonitoringLoop starts when capturing with visual anchor and stops cleanly`() {
        val anchor =
            LayoutVisualAnchor(
                enabled = true,
                srcX = 0.1f,
                srcY = 0.1f,
                srcWidth = 0.1f,
                srcHeight = 0.1f,
            )
        val layout =
            PadLayout(
                id = "layout_with_anchor",
                name = "Gameplay",
                visualAnchor = anchor,
            )
        val profile =
            PadProfile(
                id = "profile_1",
                name = "Test Profile",
                layouts = listOf(layout),
            )

        HudPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layout,
            profile = profile,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertTrue(HudPresenceManager.isMonitoring)

        HudPresenceManager.updateMonitoringLoop(
            isCapturing = false,
            layout = layout,
            profile = profile,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertFalse(HudPresenceManager.isMonitoring)
    }

    @Test
    fun `updateMonitoringLoop stops when active layout has no anchor and auto switch is disabled`() {
        val anchor =
            LayoutVisualAnchor(
                enabled = true,
                srcX = 0.1f,
                srcY = 0.1f,
                srcWidth = 0.1f,
                srcHeight = 0.1f,
            )
        val layoutWithAnchor =
            PadLayout(
                id = "layout_with_anchor",
                name = "Gameplay",
                visualAnchor = anchor,
            )
        val profileWithAnchor =
            PadProfile(
                id = "profile_1",
                name = "Test Profile",
                layouts = listOf(layoutWithAnchor),
            )
        val layoutWithoutAnchor =
            PadLayout(
                id = "layout_no_anchor",
                name = "Map",
                visualAnchor = LayoutVisualAnchor(enabled = false),
            )
        val profileWithoutAnchor =
            PadProfile(
                id = "profile_2",
                name = "Empty Profile",
                layouts = listOf(layoutWithoutAnchor),
            )

        HudPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layoutWithAnchor,
            profile = profileWithAnchor,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertTrue(HudPresenceManager.isMonitoring)

        // Switch to profile without anchor
        HudPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layoutWithoutAnchor,
            profile = profileWithoutAnchor,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertFalse(HudPresenceManager.isMonitoring)
    }
}
