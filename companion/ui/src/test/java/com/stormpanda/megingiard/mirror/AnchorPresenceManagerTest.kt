package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.macropad.LayoutVisualAnchor
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.test.runTest
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
class AnchorPresenceManagerTest {
    @Test
    fun `isLayoutAnchorLost returns false for unknown layout`() {
        assertFalse(AnchorPresenceManager.isLayoutAnchorLost("unknown_layout_123"))
    }

    @Test
    fun `getLayoutPresenceState returns null for unknown layout`() {
        assertNull(AnchorPresenceManager.getLayoutPresenceState("unknown_layout_123"))
    }

    @Test
    fun `clearLayout removes presence state`() {
        val layoutId = "test_layout_clear"
        AnchorPresenceManager.clearLayout(layoutId)
        assertFalse(AnchorPresenceManager.isLayoutAnchorLost(layoutId))
    }

    @Test
    fun `getDelayedFrame returns null when delay is zero`() {
        val frame = AnchorPresenceManager.getDelayedFrame("cutout_123", delayFrames = 0)
        assertNull(frame)
    }

    @Test
    fun `getDelayedFrame returns null when no frames buffered`() {
        val frame = AnchorPresenceManager.getDelayedFrame("non_existent_cutout", delayFrames = 2)
        assertNull(frame)
    }

    @Test
    fun `getFrozenFrame returns null when neither cache nor disk has frame`() {
        val context = RuntimeEnvironment.getApplication()
        val frame = AnchorPresenceManager.getFrozenFrame(context, "cutout_no_frame")
        assertNull(frame)
    }

    @Test
    fun `getFrozenFrame retrieves saved freeze frame from disk if not in memory cache`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "cutout_disk_frame_test"
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        CutoutMaskManager.saveFreezeFrame(context, cutoutId, bitmap)

        val retrieved = AnchorPresenceManager.getFrozenFrame(context, cutoutId)
        assertNotNull(retrieved)
        assertEquals(10, retrieved!!.width)
        assertEquals(10, retrieved.height)

        CutoutMaskManager.deleteMask(context, cutoutId)
        AnchorPresenceManager.clearCutout(cutoutId)
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

        AnchorPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layout,
            profile = profile,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertTrue(AnchorPresenceManager.isMonitoring)

        AnchorPresenceManager.updateMonitoringLoop(
            isCapturing = false,
            layout = layout,
            profile = profile,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertFalse(AnchorPresenceManager.isMonitoring)
        assertEquals(0, AnchorPresenceManager.ringBufferCount)
        assertEquals(0, AnchorPresenceManager.lastValidFrameCount)
    }

    @Test
    fun `clearAllBuffers empties all ring buffers and freeze frame bitmaps`() {
        AnchorPresenceManager.clearAllBuffers()
        assertEquals(0, AnchorPresenceManager.ringBufferCount)
        assertEquals(0, AnchorPresenceManager.lastValidFrameCount)
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

        AnchorPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layoutWithAnchor,
            profile = profileWithAnchor,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertTrue(AnchorPresenceManager.isMonitoring)

        // Switch to profile without anchor
        AnchorPresenceManager.updateMonitoringLoop(
            isCapturing = true,
            layout = layoutWithoutAnchor,
            profile = profileWithoutAnchor,
            viewMode = CompanionViewMode.MACROPAD,
        )
        assertFalse(AnchorPresenceManager.isMonitoring)
    }

    @Test
    fun `scanMatchingCandidates returns all matching candidates in profile sequence`() =
        runTest {
            val layout1 = PadLayout(id = "layout_inventory", name = "Inventory")
            val layout2 = PadLayout(id = "layout_map", name = "Map")
            val layout3 = PadLayout(id = "layout_skills", name = "Skills")

            val candidates = listOf(layout1, layout2, layout3)

            val matched =
                AnchorPresenceManager.scanMatchingCandidates(candidates) { candidate ->
                    // Both inventory and map match at the same time
                    candidate.id == "layout_inventory" || candidate.id == "layout_map"
                }

            assertEquals(2, matched.size)
            assertEquals("layout_inventory", matched[0].id)
            assertEquals("layout_map", matched[1].id)
            // Prioritizes first match
            assertEquals("layout_inventory", matched.first().id)
        }

    @Test
    fun `scanMatchingCandidates returns empty list when no candidate matches`() =
        runTest {
            val layout1 = PadLayout(id = "layout_inventory", name = "Inventory")
            val candidates = listOf(layout1)

            val matched =
                AnchorPresenceManager.scanMatchingCandidates(candidates) {
                    false
                }

            assertTrue(matched.isEmpty())
        }

    @Test
    fun `formatAnchorConflictToast formats two conflicting layouts properly`() {
        val context = RuntimeEnvironment.getApplication()
        val message =
            AnchorPresenceManager.formatAnchorConflictToast(
                context = context,
                layoutNames = listOf("Inventory", "Map"),
            )
        assertTrue(message.contains("Inventory"))
        assertTrue(message.contains("Map"))
        assertTrue(message.contains("both match") || message.contains("stimmen überein") || message.contains("同時符合"))
    }

    @Test
    fun `formatAnchorConflictToast formats three or more conflicting layouts properly`() {
        val context = RuntimeEnvironment.getApplication()
        val message =
            AnchorPresenceManager.formatAnchorConflictToast(
                context = context,
                layoutNames = listOf("Inventory", "Map", "Skills"),
            )
        assertTrue(message.contains("\"Inventory\""))
        assertTrue(message.contains("\"Map\""))
        assertTrue(message.contains("\"Skills\""))
        assertTrue(message.contains("all match") || message.contains("stimmen überein") || message.contains("同時符合"))
    }
}
