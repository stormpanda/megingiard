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

        val unfeathered = CutoutMaskManager.getMask(context, cutoutId, translucency = 0, featheringPx = 0)
        assertNotNull(unfeathered)
        assertEquals(0x00000000, unfeathered!!.getPixel(11, 10))

        val feathered = CutoutMaskManager.getMask(context, cutoutId, translucency = 0, featheringPx = 3)
        assertNotNull(feathered)
        assertEquals(0xFFFFFFFF.toInt(), feathered!!.getPixel(10, 10))
        val neighborAlpha = (feathered.getPixel(11, 10) ushr 24) and 0xFF
        assertTrue("Feathered neighbor should have opacity > 0", neighborAlpha > 0)

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getMask(context, cutoutId))
    }

    @Test
    fun `saveMask with varianceMap persists variance and getVarianceMap retrieves it`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_var_cutout"
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val varianceMap = ByteArray(100) { (it % 50).toByte() }

        CutoutMaskManager.saveMask(context, cutoutId, bitmap, varianceMap)
        val retrievedVar = CutoutMaskManager.getVarianceMap(context, cutoutId)
        assertNotNull(retrievedVar)
        assertEquals(100, retrievedVar!!.size)
        assertEquals(varianceMap[25], retrievedVar[25])

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getVarianceMap(context, cutoutId))
    }

    @Test
    fun `getMask with translucency dynamically generates tuned mask via variance map`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_translucent_cutout"
        val width = 20
        val height = 20
        val pixelCount = width * height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Variance map: core anchor at (8..11, 8..11) with variance 5, neighbor with variance 30
        val varianceMap = ByteArray(pixelCount) { 30.toByte() }
        for (y in 8..11) {
            for (x in 8..11) {
                varianceMap[y * width + x] = 5.toByte()
            }
        }

        CutoutMaskManager.saveMask(context, cutoutId, bitmap, varianceMap)

        // At translucency 0, neighbor (7, 8) with variance 30 is transparent
        val baseMask = CutoutMaskManager.getMask(context, cutoutId, translucency = 0, featheringPx = 0)
        assertNotNull(baseMask)

        // At translucency 60%, neighbor (7, 8) within halo is dynamically recovered
        val tunedMask = CutoutMaskManager.getMask(context, cutoutId, translucency = 60, featheringPx = 0)
        assertNotNull(tunedMask)
        val neighborAlpha = (tunedMask!!.getPixel(7, 8) ushr 24) and 0xFF
        assertTrue("Neighbor within halo should have recovered alpha", neighborAlpha > 0)

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getMask(context, cutoutId))
    }

    @Test
    fun `saveMask with freezeFrame persists both and deleteMask removes them`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_freeze_cutout"
        val maskBitmap = Bitmap.createBitmap(15, 15, Bitmap.Config.ARGB_8888)
        val freezeBitmap = Bitmap.createBitmap(15, 15, Bitmap.Config.ARGB_8888)
        freezeBitmap.setPixel(5, 5, 0xFF00FF00.toInt())

        CutoutMaskManager.saveMask(
            context = context,
            cutoutId = cutoutId,
            bitmap = maskBitmap,
            freezeFrame = freezeBitmap,
        )

        val retrievedFreeze = CutoutMaskManager.getFreezeFrame(context, cutoutId)
        assertNotNull(retrievedFreeze)
        assertEquals(0xFF00FF00.toInt(), retrievedFreeze!!.getPixel(5, 5))

        // Cleanup
        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getFreezeFrame(context, cutoutId))
    }

    @Test
    fun `isCalibrated returns true when mask exists and false after deletion`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_calibrated_cutout"
        assertFalse(CutoutMaskManager.isCalibrated(context, cutoutId))

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        CutoutMaskManager.saveMask(context, cutoutId, bitmap)
        assertTrue(CutoutMaskManager.isCalibrated(context, cutoutId))

        CutoutMaskManager.deleteMask(context, cutoutId)
        assertFalse(CutoutMaskManager.isCalibrated(context, cutoutId))
    }

    @Test
    fun `saveFreezeFrame persists native resolution frame and updates existing disk frame`() {
        val context = RuntimeEnvironment.getApplication()
        val cutoutId = "test_native_freeze_cutout"

        val lowRes = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        CutoutMaskManager.saveFreezeFrame(context, cutoutId, lowRes)

        val retrievedLowRes = CutoutMaskManager.getFreezeFrame(context, cutoutId)
        assertNotNull(retrievedLowRes)
        assertEquals(480, retrievedLowRes!!.width)
        assertEquals(270, retrievedLowRes.height)

        val highRes = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        CutoutMaskManager.saveFreezeFrame(context, cutoutId, highRes)

        val retrievedHighRes = CutoutMaskManager.getFreezeFrame(context, cutoutId)
        assertNotNull(retrievedHighRes)
        assertEquals(1920, retrievedHighRes!!.width)
        assertEquals(1080, retrievedHighRes.height)

        CutoutMaskManager.deleteMask(context, cutoutId)
        assertNull(CutoutMaskManager.getFreezeFrame(context, cutoutId))
    }

    @Test
    fun `saveLayoutAnchorSignature persists signature and getLayoutAnchorSignature retrieves it`() {
        val context = RuntimeEnvironment.getApplication()
        val layoutId = "test_layout_anchor_persist"
        val signature =
            HudAnchorSignature(
                cutoutId = layoutId,
                points =
                    listOf(
                        AnchorPoint(0.2f, 0.3f, 10, 20, 30),
                        AnchorPoint(0.4f, 0.5f, 40, 50, 60),
                    ),
            )

        CutoutMaskManager.saveLayoutAnchorSignature(context, layoutId, signature)
        val retrieved = CutoutMaskManager.getLayoutAnchorSignature(context, layoutId)

        assertNotNull(retrieved)
        assertEquals(layoutId, retrieved!!.cutoutId)
        assertEquals(2, retrieved.points.size)
        assertEquals(10, retrieved.points[0].r)
        assertEquals(60, retrieved.points[1].b)

        // Cleanup
        CutoutMaskManager.deleteLayoutAnchorSignature(context, layoutId)
        assertNull(CutoutMaskManager.getLayoutAnchorSignature(context, layoutId))
    }

    @Test
    fun `isLayoutAnchorCalibrated returns true when signature exists and false after deletion`() {
        val context = RuntimeEnvironment.getApplication()
        val layoutId = "test_layout_calibrated"
        assertFalse(CutoutMaskManager.isLayoutAnchorCalibrated(context, layoutId))

        val signature =
            HudAnchorSignature(
                cutoutId = layoutId,
                points = listOf(AnchorPoint(0.1f, 0.1f, 100, 100, 100)),
            )

        CutoutMaskManager.saveLayoutAnchorSignature(context, layoutId, signature)
        assertTrue(CutoutMaskManager.isLayoutAnchorCalibrated(context, layoutId))

        CutoutMaskManager.deleteLayoutAnchorSignature(context, layoutId)
        assertFalse(CutoutMaskManager.isLayoutAnchorCalibrated(context, layoutId))
    }

    @Test
    fun `duplicateLayoutAnchorSignature clones signature with new layoutId`() {
        val context = RuntimeEnvironment.getApplication()
        val sourceId = "source_layout"
        val targetId = "target_layout"

        val signature =
            HudAnchorSignature(
                cutoutId = sourceId,
                points = listOf(AnchorPoint(0.1f, 0.2f, 11, 22, 33)),
            )

        CutoutMaskManager.saveLayoutAnchorSignature(context, sourceId, signature)
        assertTrue(CutoutMaskManager.isLayoutAnchorCalibrated(context, sourceId))
        assertFalse(CutoutMaskManager.isLayoutAnchorCalibrated(context, targetId))

        CutoutMaskManager.duplicateLayoutAnchorSignature(context, sourceId, targetId)
        assertTrue(CutoutMaskManager.isLayoutAnchorCalibrated(context, targetId))

        val cloned = CutoutMaskManager.getLayoutAnchorSignature(context, targetId)
        assertNotNull(cloned)
        assertEquals(targetId, cloned!!.cutoutId)
        assertEquals(1, cloned.points.size)
        assertEquals(11, cloned.points[0].r)

        // Cleanup
        CutoutMaskManager.deleteLayoutAnchorSignature(context, sourceId)
        CutoutMaskManager.deleteLayoutAnchorSignature(context, targetId)
    }
}
