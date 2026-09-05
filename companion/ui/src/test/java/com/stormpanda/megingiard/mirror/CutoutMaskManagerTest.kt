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
}
