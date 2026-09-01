package com.stormpanda.megingiard

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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapUtilsTest {
    @Test
    fun testGetScreenTargetDimensions() {
        val context = RuntimeEnvironment.getApplication()
        val dims = BitmapUtils.getScreenTargetDimensions(context)
        assertTrue(dims.first > 0)
        assertEquals(dims.first, dims.second)
    }

    @Test
    fun testDecodeScaledBitmap_nonExistentFile_returnsNull() {
        val file = File("/path/that/does/not/exist/image.png")
        val result = BitmapUtils.decodeScaledBitmap(file, 100, 100)
        assertNull(result)
    }

    @Test
    fun testDecodeScaledBitmap_validImageFile() {
        val tempFile = File.createTempFile("test_bmp", ".png").apply { deleteOnExit() }
        val testBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        tempFile.outputStream().use { out ->
            testBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        testBmp.recycle()

        val decoded = BitmapUtils.decodeScaledBitmap(tempFile, 100, 100)
        assertNotNull(decoded)
        decoded?.recycle()
        tempFile.delete()
    }

    @Test
    fun testSaveScaledWebp_validFile() {
        val context = RuntimeEnvironment.getApplication()
        val srcFile = File.createTempFile("test_src", ".png").apply { deleteOnExit() }
        val destFile = File.createTempFile("test_dest", ".webp").apply { deleteOnExit() }

        val testBmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        srcFile.outputStream().use { out ->
            testBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        testBmp.recycle()

        val saved =
            BitmapUtils.saveScaledWebp(
                context = context,
                srcUri = null,
                srcFile = srcFile,
                destFile = destFile,
                targetW = 150,
                targetH = 150,
            )

        assertTrue(saved)
        assertTrue(destFile.exists())
        assertTrue(destFile.length() > 0)

        srcFile.delete()
        destFile.delete()
    }

    @Test
    fun testSaveScaledWebp_nullInputs_returnsFalse() {
        val context = RuntimeEnvironment.getApplication()
        val destFile = File.createTempFile("test_dest", ".webp").apply { deleteOnExit() }

        val saved =
            BitmapUtils.saveScaledWebp(
                context = context,
                srcUri = null,
                srcFile = null,
                destFile = destFile,
                targetW = 100,
                targetH = 100,
            )

        assertFalse(saved)
        destFile.delete()
    }
}
