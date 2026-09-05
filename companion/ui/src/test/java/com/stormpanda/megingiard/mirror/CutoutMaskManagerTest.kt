package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
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
class CutoutMaskManagerTest {
    @Test
    fun `getMask on non-existent cutout returns null`() {
        val context = RuntimeEnvironment.getApplication()
        val result = CutoutMaskManager.getMask(context, "non_existent_id")
        assertNull(result)
        assertFalse(CutoutMaskManager.hasMask(context, "non_existent_id"))
    }

    @Test
    fun `saveMask persists mask and getMask retrieves it`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_cutout_123"

        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(10, 10, 0xFFFFFFFF.toInt())
        bitmap.setPixel(20, 20, 0x00000000)

        CutoutMaskManager.saveMask(context, cutoutId, bitmap)
        assertTrue(CutoutMaskManager.hasMask(context, cutoutId))

        val retrieved = CutoutMaskManager.getMask(context, cutoutId)
        assertNotNull(retrieved)
        assertEquals(50, retrieved!!.width)
        assertEquals(50, retrieved.height)

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertFalse(CutoutMaskManager.hasMask(context, cutoutId))
        assertNull(CutoutMaskManager.getMask(context, cutoutId))
    }

    @Test
    fun `getMask with feathering returns feathered bitmap`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_feather_cutout"

        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(10, 10, 0xFFFFFFFF.toInt())

        CutoutMaskManager.saveMask(context, cutoutId, bitmap)

        val unfeathered = CutoutMaskManager.getMask(context, cutoutId, 0)
        assertNotNull(unfeathered)
        assertEquals(0x00000000, unfeathered!!.getPixel(11, 10))

        val feathered = CutoutMaskManager.getMask(context, cutoutId, 3)
        assertNotNull(feathered)
        assertEquals(0xFFFFFFFF.toInt(), feathered!!.getPixel(10, 10))
        val neighborAlpha = (feathered.getPixel(11, 10) ushr 24) and 0xFF
        assertTrue("Feathered neighbor should have opacity > 0", neighborAlpha > 0)

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getMask(context, cutoutId))
    }
}
