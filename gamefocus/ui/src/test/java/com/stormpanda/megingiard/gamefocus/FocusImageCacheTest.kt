package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FocusImageCacheTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FocusImageCacheTest {
    @Test
    fun testGetCoverBitmap_returnsNullWhenCoverPathIsNull() {
        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                coverPath = null,
            )

        assertNull(FocusImageCache.getCoverBitmap(appInfo))
    }

    @Test
    fun testGetCoverBitmap_returnsNullWhenFileDoesNotExist() {
        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                coverPath = "/tmp/non_existent_file_12345.png",
            )

        assertNull(FocusImageCache.getCoverBitmap(appInfo))
    }

    @Test
    fun testGetCachedIconBitmap_returnsNullWhenNotCached() {
        assertNull(FocusImageCache.getCachedIconBitmap("com.test.uncached"))
    }

    @Test
    fun testDecodeSampledIconBitmap_scalesDownLargeImages() {
        val tempFile = File.createTempFile("large_logo", ".png")
        try {
            val largeBmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
            FileOutputStream(tempFile).use { out ->
                largeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            largeBmp.recycle()

            val decoded = FocusImageCache.decodeSampledIconBitmap(tempFile, maxDimensionPx = 256)
            assertNotNull(decoded)
            assertTrue(decoded!!.width <= 256)
            assertTrue(decoded.height <= 256)
            decoded.recycle()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDecodeSampledIconBitmap_preservesSmallImages() {
        val tempFile = File.createTempFile("small_icon", ".png")
        try {
            val smallBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            FileOutputStream(tempFile).use { out ->
                smallBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            smallBmp.recycle()

            val decoded = FocusImageCache.decodeSampledIconBitmap(tempFile, maxDimensionPx = 256)
            assertNotNull(decoded)
            assertEquals(64, decoded!!.width)
            assertEquals(64, decoded.height)
            decoded.recycle()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDecodeSampledIconBitmap_returnsNullForNonExistentFile() {
        val nonExistent = File("/tmp/does_not_exist_${System.currentTimeMillis()}.png")
        assertNull(FocusImageCache.decodeSampledIconBitmap(nonExistent))
    }

    @Test
    fun testNegativeCaching_cachesMissingIconsAndInvalidates() {
        kotlinx.coroutines.runBlocking {
            val context =
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext<android.content.Context>()
            FocusImageCache.clearForTesting()

            val missingRom =
                InstalledAppInfo(
                    packageName = "rom:switch:NonExistentGame.nsp",
                    activityName = "",
                    label = "Non-existent",
                    isRom = true,
                    systemId = "switch",
                )

            val result1 = FocusImageCache.getIconBitmapAsync(context, missingRom)
            assertNull(result1)

            // Second check should be caught by missingIconKeys
            assertNull(FocusImageCache.getCachedIconBitmap(missingRom.packageName))

            // Invalidation should remove from missingIconKeys
            FocusImageCache.invalidateIcon(missingRom.packageName)
            assertNull(FocusImageCache.getCachedIconBitmap(missingRom.packageName))
        }
    }

    @Test
    fun testOversizedLogoRewrite_rewritesDiskFileAsThumbnail() {
        kotlinx.coroutines.runBlocking {
            val context =
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext<android.content.Context>()
            FocusImageCache.clearForTesting()

            val logosDir = File(context.cacheDir, "gamefocus_logos").apply { mkdirs() }
            val logoFile = File(logosDir, "rom:switch:HugeGame.nsp.png")

            val hugeBmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            // Fill with varied pixels to ensure uncompressed PNG exceeds 256KB
            for (x in 0 until 800 step 10) {
                for (y in 0 until 800 step 10) {
                    hugeBmp.setPixel(x, y, android.graphics.Color.rgb(x % 255, y % 255, (x + y) % 255))
                }
            }
            FileOutputStream(logoFile).use { out ->
                hugeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            hugeBmp.recycle()

            val originalLength = logoFile.length()
            assertTrue("Original file should exceed 256KB for this test, was $originalLength", originalLength > 256 * 1024L)

            val romApp =
                InstalledAppInfo(
                    packageName = "rom:switch:HugeGame.nsp",
                    activityName = "",
                    label = "Huge Game",
                    isRom = true,
                    systemId = "switch",
                )

            val bitmap = FocusImageCache.getIconBitmapAsync(context, romApp)
            assertNotNull(bitmap)

            val newLength = logoFile.length()
            assertTrue("Rewritten file should be smaller than original ($originalLength -> $newLength)", newLength < originalLength)
            logoFile.delete()
        }
    }
}
