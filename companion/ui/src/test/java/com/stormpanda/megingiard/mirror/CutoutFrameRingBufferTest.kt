package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CutoutFrameRingBufferTest {
    @Test
    fun `getDelayedFrame returns null when empty`() {
        val ring = CutoutFrameRingBuffer(100, 100, 5)
        assertNull(ring.getDelayedFrame(0))
        assertNull(ring.getDelayedFrame(2))
        ring.recycle()
    }

    @Test
    fun `pushing single frame makes it available with correct dimensions`() {
        val ring = CutoutFrameRingBuffer(50, 40, 5)
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        ring.pushFrame(src, 10, 20)

        val frame0 = ring.getDelayedFrame(0)
        assertNotNull(frame0)
        assertEquals(50, frame0!!.width)
        assertEquals(40, frame0.height)

        // Clamps to available count (1) when requesting larger delay
        val frame3 = ring.getDelayedFrame(3)
        assertNotNull(frame3)
        assertEquals(frame0, frame3)

        ring.recycle()
        src.recycle()
    }

    @Test
    fun `pushing multiple frames provides exact historical FIFO instance retrieval`() {
        val ring = CutoutFrameRingBuffer(20, 20, 5)
        val src = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        // Push 4 frames sequentially
        ring.pushFrame(src, 0, 0)
        val f1 = ring.getDelayedFrame(0)

        ring.pushFrame(src, 0, 0)
        val f2 = ring.getDelayedFrame(0)

        ring.pushFrame(src, 0, 0)
        val f3 = ring.getDelayedFrame(0)

        ring.pushFrame(src, 0, 0)
        val f4 = ring.getDelayedFrame(0)

        // All 4 frames are distinct pre-allocated buffer instances
        assertTrue(f1 !== f2)
        assertTrue(f2 !== f3)
        assertTrue(f3 !== f4)

        // Current frame (delay 0) is f4
        assertEquals(f4, ring.getDelayedFrame(0))
        // 1 tick in the past is f3
        assertEquals(f3, ring.getDelayedFrame(1))
        // 2 ticks in the past is f2
        assertEquals(f2, ring.getDelayedFrame(2))
        // 3 ticks in the past is f1
        assertEquals(f1, ring.getDelayedFrame(3))
        // Clamped to oldest available (f1)
        assertEquals(f1, ring.getDelayedFrame(4))
        assertEquals(f1, ring.getDelayedFrame(10))

        ring.recycle()
        src.recycle()
    }

    @Test
    fun `ring buffer cleanly overwrites older frames when capacity exceeded`() {
        val ring = CutoutFrameRingBuffer(10, 10, 3)
        val src = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        // Push 3 frames
        ring.pushFrame(src, 0, 0)
        val f1 = ring.getDelayedFrame(0)

        ring.pushFrame(src, 0, 0)
        val f2 = ring.getDelayedFrame(0)

        ring.pushFrame(src, 0, 0)
        val f3 = ring.getDelayedFrame(0)

        // Push 4th frame -> overwrites slot 0 (which was f1)
        ring.pushFrame(src, 0, 0)
        val f4 = ring.getDelayedFrame(0)
        assertTrue(f4 === f1) // Slot 0 reused!

        // Most recent is f4
        assertEquals(f4, ring.getDelayedFrame(0))
        // 1 tick back is f3
        assertEquals(f3, ring.getDelayedFrame(1))
        // 2 ticks back is f2
        assertEquals(f2, ring.getDelayedFrame(2))
        // Older than capacity clamps to oldest (f2)
        assertEquals(f2, ring.getDelayedFrame(3))

        ring.recycle()
        src.recycle()
    }

    @Test
    fun `recycle releases all bitmaps`() {
        val ring = CutoutFrameRingBuffer(20, 20, 3)
        val src = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        ring.pushFrame(src, 0, 0)

        val bmpBefore = ring.getDelayedFrame(0)
        assertNotNull(bmpBefore)
        assertTrue(!bmpBefore!!.isRecycled)

        ring.recycle()
        assertTrue(bmpBefore.isRecycled)
        assertNull(ring.getDelayedFrame(0))

        src.recycle()
    }
}
