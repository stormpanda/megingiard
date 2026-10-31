package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPaletteExtractorTest {
    private val defaultPrimary = Color(0xFFFF0000)
    private val defaultSecondary = Color(0xFF0000FF)

    @Before
    fun setUp() {
        AppPaletteExtractor.resetForTesting()
    }

    @After
    fun tearDown() {
        AppPaletteExtractor.resetForTesting()
    }

    @Test
    fun testColorDarken() {
        val color = Color(0xFFFF8000)
        val darkened = color.darken(0.5f)
        assertNotNull(darkened)
        assertTrue(darkened != Color.Unspecified)

        val unspecified = Color.Unspecified
        assertEquals(Color.Unspecified, unspecified.darken(0.5f))
    }

    @Test
    fun testExtractedAppPaletteDataModel() {
        val palette =
            ExtractedAppPalette(
                primaryColor = Color(0xFF00FF00),
                secondaryColor = Color(0xFF0000FF),
                isExtracted = true,
            )
        assertEquals(Color(0xFF00FF00), palette.primaryColor)
        assertEquals(Color(0xFF0000FF), palette.secondaryColor)
        assertTrue(palette.isExtracted)
        assertNotNull(palette.darkenedPrimaryColor)

        val unextractedPalette =
            ExtractedAppPalette(
                primaryColor = Color(0xFFFF0000),
                secondaryColor = Color(0xFF0000FF),
                isExtracted = false,
            )
        assertFalse(unextractedPalette.isExtracted)
    }

    @Test
    fun testExtractColorsWithFallbackWhenNoImage() {
        val context = RuntimeEnvironment.getApplication()
        AppPaletteExtractor.init(context)

        val app =
            InstalledAppInfo(
                packageName = "com.test.noimage",
                activityName = "MainActivity",
                label = "No Image App",
            )

        assertNull(AppPaletteExtractor.getCachedColorsOrNull(app))

        val result = AppPaletteExtractor.extractColors(app, defaultPrimary, defaultSecondary)
        assertEquals(defaultPrimary, result.primaryColor)
        assertEquals(defaultSecondary, result.secondaryColor)
        assertFalse(result.isExtracted)

        // Fallback unextracted palettes are not cached to prevent stale flashes
        assertNull(AppPaletteExtractor.getCachedColorsOrNull(app))
    }

    @Test
    fun testExtractColorsWithAppIcon() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            AppPaletteExtractor.init(context)

            // Create a fake icon PNG in gamefocus_icons cache
            val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
            val iconFile = File(iconsDir, "com.test.appicon.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.BLUE)
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val app =
                InstalledAppInfo(
                    packageName = "com.test.appicon",
                    activityName = "MainActivity",
                    label = "App Icon Test",
                    coverPath = null,
                )

            assertNull(AppPaletteExtractor.getCachedColorsOrNull(app))

            val result = AppPaletteExtractor.extractColors(app, defaultPrimary, defaultSecondary)
            assertTrue(result.isExtracted)
            assertNotNull(result.primaryColor)
            assertNotNull(result.secondaryColor)

            // Cache hit via deterministic packageName:icon key
            val cached = AppPaletteExtractor.getCachedColorsOrNull(app)
            assertNotNull(cached)
            assertEquals(result.primaryColor, cached!!.primaryColor)
            assertTrue(cached.isExtracted)

            // Invalidation
            AppPaletteExtractor.invalidatePalette("com.test.appicon")
            assertNull(AppPaletteExtractor.getCachedColorsOrNull(app))
            iconFile.delete()
        }

    @Test
    fun testExtractColorsWithAppIcon_bypassesAndEvictsStaleCacheWhenLastUpdateTimeChanges() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            AppPaletteExtractor.init(context)

            val pkg = "com.test.versionedicon"
            val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
            val iconFile = File(iconsDir, "$pkg.png")
            val v1Time = 1_000_000L
            val bmpV1 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bmpV1.eraseColor(AndroidColor.BLUE)
            FileOutputStream(iconFile).use { out ->
                bmpV1.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmpV1.recycle()
            iconFile.setLastModified(v1Time + 5_000L)

            try {
                val appV1 =
                    InstalledAppInfo(
                        packageName = pkg,
                        activityName = "MainActivity",
                        label = "Versioned Icon App",
                        lastUpdateTime = v1Time,
                    )

                val paletteV1 = AppPaletteExtractor.extractColors(appV1, defaultPrimary, defaultSecondary)
                assertTrue(paletteV1.isExtracted)
                assertNotNull(AppPaletteExtractor.getCachedColorsOrNull(appV1))

                // Update icon on disk to green and advance lastUpdateTime
                val v2Time = 2_000_000L
                val bmpV2 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                bmpV2.eraseColor(AndroidColor.GREEN)
                FileOutputStream(iconFile).use { out ->
                    bmpV2.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmpV2.recycle()
                iconFile.setLastModified(v2Time + 5_000L)

                val appV2 = appV1.copy(lastUpdateTime = v2Time)
                assertNull(AppPaletteExtractor.getCachedColorsOrNull(appV2))

                val paletteV2 = AppPaletteExtractor.extractColors(appV2, defaultPrimary, defaultSecondary)
                assertTrue(paletteV2.isExtracted)
                assertTrue(paletteV1.primaryColor != paletteV2.primaryColor)
                // Stale v1 entry is evicted when v2 is stored
                assertNull(AppPaletteExtractor.getCachedColorsOrNull(appV1))
                assertNotNull(AppPaletteExtractor.getCachedColorsOrNull(appV2))
            } finally {
                iconFile.delete()
            }
        }

    @Test
    fun testExtractColorsWithBitmapCover() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            AppPaletteExtractor.init(context)

            // Create a fake PNG cover bitmap
            val coverFile = File(context.cacheDir, "test_cover.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.RED)
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val app =
                InstalledAppInfo(
                    packageName = "com.test.withcover",
                    activityName = "MainActivity",
                    label = "Cover App",
                    coverPath = coverFile.absolutePath,
                )

            val result = AppPaletteExtractor.extractColors(app, defaultPrimary, defaultSecondary)
            assertTrue(result.isExtracted)
            assertNotNull(result.primaryColor)
            assertNotNull(result.secondaryColor)

            // Cache hit
            val cached = AppPaletteExtractor.getCachedColorsOrNull(app)
            assertNotNull(cached)
            assertEquals(result.primaryColor, cached!!.primaryColor)

            // Async extraction
            val asyncResult = AppPaletteExtractor.extractColorsAsync(app, defaultPrimary, defaultSecondary)
            assertEquals(result.primaryColor, asyncResult.primaryColor)

            // Invalidation
            AppPaletteExtractor.invalidatePalette("com.test.withcover")
            assertNull(AppPaletteExtractor.getCachedColorsOrNull(app))
        }
}
