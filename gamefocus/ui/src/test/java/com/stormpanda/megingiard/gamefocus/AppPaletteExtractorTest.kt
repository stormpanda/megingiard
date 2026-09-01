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
    }

    @Test
    fun testExtractColorsWithBitmapCover() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            AppPaletteExtractor.init(context)

            // Create a fake PNG cover bitmap
            val coverFile = File(context.cacheDir, "test_cover.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
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
