package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
