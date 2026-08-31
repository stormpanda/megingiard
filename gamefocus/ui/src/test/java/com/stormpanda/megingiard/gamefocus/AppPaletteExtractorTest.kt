package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor

private const val TAG = "AppPaletteExtractorTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPaletteExtractorTest {
    @Before
    fun setUp() {
        AppPaletteExtractor.resetForTesting()
    }

    @After
    fun tearDown() {
        AppPaletteExtractor.resetForTesting()
    }

    @Test
    fun extractColors_withVibrantBitmap_returnsVibrantPrimaryAndSecondary() {
        val width = 100
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                when {
                    x < 15 && y < 15 -> bitmap.setPixel(x, y, AndroidColor.RED)
                    x in 15..29 && y < 15 -> bitmap.setPixel(x, y, AndroidColor.CYAN)
                    else -> bitmap.setPixel(x, y, AndroidColor.BLACK)
                }
            }
        }

        val context = org.robolectric.RuntimeEnvironment.getApplication()
        AppPaletteExtractor.init(context)
        val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
        val iconFile = File(iconsDir, "com.test.vibrantapp.png")
        FileOutputStream(iconFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.vibrantapp",
                activityName = "com.test.vibrantapp.MainActivity",
                label = "Vibrant Test App",
            )

        val defaultPrimary = Color.Gray
        val defaultSecondary = Color.DarkGray

        val extracted =
            AppPaletteExtractor.extractColors(
                appInfo = appInfo,
                defaultPrimary = defaultPrimary,
                defaultSecondary = defaultSecondary,
            )

        assertNotEquals(AndroidColor.BLACK, extracted.primaryColor.toArgb())

        val hsvPrimary = FloatArray(3)
        AndroidColor.colorToHSV(extracted.primaryColor.toArgb(), hsvPrimary)
        assertTrue("Primary color saturation should be > 0.3f, was ${hsvPrimary[1]}", hsvPrimary[1] > 0.3f)

        assertNotEquals(extracted.primaryColor, extracted.secondaryColor)
    }

    @Test
    fun testColorDarken_scaling() {
        val red = Color.Red
        val darkened35 = red.darken(0.35f)
        val hsvOriginal = FloatArray(3)
        val hsvDarkened = FloatArray(3)
        AndroidColor.colorToHSV(red.toArgb(), hsvOriginal)
        AndroidColor.colorToHSV(darkened35.toArgb(), hsvDarkened)

        assertEquals("Hue should remain unchanged", hsvOriginal[0], hsvDarkened[0], 0.01f)
        assertEquals("Saturation should remain unchanged", hsvOriginal[1], hsvDarkened[1], 0.01f)
        assertEquals("Brightness should scale by factor 0.35f", 0.35f, hsvDarkened[2], 0.01f)

        assertEquals(Color.Unspecified, Color.Unspecified.darken(0.35f))
    }

    @Test
    fun testExtractedAppPalette_darkenedPrimaryColor() {
        val palette = ExtractedAppPalette(primaryColor = Color.Blue, secondaryColor = Color.Yellow)
        assertEquals(Color.Blue.darken(0.35f), palette.darkenedPrimaryColor)
        assertTrue(palette.isExtracted)
    }

    @Test
    fun testInvalidatePalette_clearsMatchingKeysFromCache() {
        val width = 50
        val height = 50
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        AppPaletteExtractor.init(context)
        val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
        val iconFile = File(iconsDir, "com.test.cacheapp.png")
        FileOutputStream(iconFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.cacheapp",
                activityName = "MainActivity",
                label = "Cache Test App",
            )

        AppPaletteExtractor.extractColors(appInfo, Color.Red, Color.Blue)
        val cachedBefore = AppPaletteExtractor.getCachedColorsOrNull(appInfo)
        assertTrue("Cached palette should be non-null after extraction", cachedBefore != null)

        AppPaletteExtractor.invalidatePalette("com.test.cacheapp")
        val cachedAfter = AppPaletteExtractor.getCachedColorsOrNull(appInfo)
        assertNull("Cached palette should be null after invalidation", cachedAfter)
    }

    @Test
    fun testExtractColors_fallbackWhenNoImageOrIcon() {
        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.noimage",
                activityName = "MainActivity",
                label = "No Image App",
                coverPath = null,
            )

        val defaultPrimary = Color.Magenta
        val defaultSecondary = Color.Cyan

        val palette = AppPaletteExtractor.extractColors(appInfo, defaultPrimary, defaultSecondary)
        assertEquals(defaultPrimary, palette.primaryColor)
        assertEquals(defaultSecondary, palette.secondaryColor)
        assertFalse("isExtracted should be false on fallback", palette.isExtracted)
    }
}
