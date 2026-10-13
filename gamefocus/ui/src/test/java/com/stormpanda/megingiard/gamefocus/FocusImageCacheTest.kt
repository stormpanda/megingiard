package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FocusImageCacheTest"
private const val ICO_TEST_DIMENSION_PX = 64
private const val ICO_SINGLE_ENTRY_PAYLOAD_OFFSET = 22

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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testDecodeSampledIcon_decodesIcoWrappedPngWithoutTouchingDisk() {
        val pngStream = ByteArrayOutputStream()
        val bmp = Bitmap.createBitmap(ICO_TEST_DIMENSION_PX, ICO_TEST_DIMENSION_PX, Bitmap.Config.ARGB_8888)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
        bmp.recycle()
        val png = pngStream.toByteArray()

        // Single-entry ICO: 6-byte header + 16-byte directory entry + PNG payload at offset 22
        val ico = ByteArrayOutputStream()
        ico.write(byteArrayOf(0, 0, 1, 0, 1, 0))
        ico.write(byteArrayOf(ICO_TEST_DIMENSION_PX.toByte(), ICO_TEST_DIMENSION_PX.toByte(), 0, 0, 1, 0, 32, 0))
        ico.write(intToLittleEndian(png.size))
        ico.write(intToLittleEndian(ICO_SINGLE_ENTRY_PAYLOAD_OFFSET))
        ico.write(png)
        val icoBytes = ico.toByteArray()

        val tempFile = File.createTempFile("ico_icon", ".png")
        try {
            tempFile.writeBytes(icoBytes)
            val sampled = FocusImageCache.decodeSampledIcon(tempFile, maxDimensionPx = 256)
            assertNotNull(sampled)
            AppLog.i(TAG, "ICO decode: wasIcoContainer=${sampled!!.wasIcoContainer}")
            assertEquals(ICO_TEST_DIMENSION_PX, sampled.bitmap.width)
            assertEquals(ICO_TEST_DIMENSION_PX, sampled.bitmap.height)
            // decodeSampledIcon must be side-effect free
            assertArrayEquals(icoBytes, tempFile.readBytes())
            sampled.bitmap.recycle()
        } finally {
            tempFile.delete()
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    @Test
    fun testNegativeCaching_cachesMissingIconsAndInvalidates() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
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
            assertNull(FocusImageCache.getCachedIconBitmap(missingRom))
            assertNull(FocusImageCache.getCachedIconBitmap(missingRom.packageName))

            // Invalidation should remove from missingIconKeys
            FocusImageCache.invalidateIcon(missingRom.packageName)
            assertNull(FocusImageCache.getCachedIconBitmap(missingRom))
        }
    }

    @Test
    fun testNegativeCaching_bypassedWhenLogoLastModifiedChanges() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FocusImageCache.clearForTesting()

            val romPackage = "rom.pc.Halo"
            val initialRom =
                InstalledAppInfo(
                    packageName = romPackage,
                    activityName = "",
                    label = "Halo",
                    isRom = true,
                    logoLastModified = 0L,
                )

            assertNull(FocusImageCache.getIconBitmapAsync(context, initialRom))
            assertNull(FocusImageCache.getCachedIconBitmap(initialRom))

            val logosDir = File(context.cacheDir, "gamefocus_logos").apply { mkdirs() }
            val logoFile = File(logosDir, "$romPackage.png")
            val testBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            FileOutputStream(logoFile).use { out ->
                testBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            testBmp.recycle()

            val updatedTimestamp = 123456789L
            val updatedRom = initialRom.withLogoLastModified(updatedTimestamp)

            val loadedBmp = FocusImageCache.getIconBitmapAsync(context, updatedRom)
            assertNotNull(loadedBmp)
            assertNotNull(FocusImageCache.getCachedIconBitmap(updatedRom))
        }
    }

    @Test
    fun testOversizedLogoRewrite_rewritesDiskFileAsThumbnail() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
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

    @Test
    fun testLibraryIconCachingAndInvalidation() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FocusImageCache.clearForTesting()

            val iconsDir = File(context.cacheDir, "gamefocus_library_icons").apply { mkdirs() }
            val iconFile = File(iconsDir, "com.test.customicon.png")

            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            FileOutputStream(iconFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()

            try {
                val appInfo =
                    InstalledAppInfo(
                        packageName = "com.test.customicon",
                        activityName = "MainActivity",
                        label = "Custom Icon App",
                        libraryIconPath = iconFile.absolutePath,
                        libraryIconLastModified = iconFile.lastModified(),
                    )

                val loaded = FocusImageCache.getLibraryIconBitmapAsync(appInfo)
                assertNotNull(loaded)

                val cached = FocusImageCache.getCachedLibraryIconBitmap(appInfo.packageName, appInfo.libraryIconLastModified)
                assertNotNull(cached)

                // Invalidate by exact timestamp
                FocusImageCache.invalidateLibraryIcon(appInfo.packageName, appInfo.libraryIconLastModified)
                assertNull(FocusImageCache.getCachedLibraryIconBitmap(appInfo.packageName, appInfo.libraryIconLastModified))

                // Re-cache and invalidate by prefix
                val reloaded = FocusImageCache.getLibraryIconBitmapAsync(appInfo)
                assertNotNull(reloaded)
                assertNotNull(FocusImageCache.getCachedLibraryIconBitmap(appInfo.packageName, appInfo.libraryIconLastModified))

                FocusImageCache.invalidateLibraryIcon(appInfo.packageName)
                assertNull(FocusImageCache.getCachedLibraryIconBitmap(appInfo.packageName, appInfo.libraryIconLastModified))
            } finally {
                iconFile.delete()
            }
        }
    }
}
