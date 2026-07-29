package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.megingiard.focus.InstalledAppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color as AndroidColor

private const val TAG = "AppPaletteExtractorTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPaletteExtractorTest {
    @Test
    fun extractColors_withVibrantBitmap_returnsVibrantPrimaryAndSecondary() {
        // Create a bitmap with 80% black background (high population) and 20% vivid red & vivid cyan
        val width = 100
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                when {
                    x < 15 && y < 15 -> bitmap.setPixel(x, y, AndroidColor.RED)

                    // Vibrant Red
                    x in 15..29 && y < 15 -> bitmap.setPixel(x, y, AndroidColor.CYAN)

                    // Vibrant Cyan
                    else -> bitmap.setPixel(x, y, AndroidColor.BLACK) // Common Black background
                }
            }
        }

        val iconDrawable = android.graphics.drawable.BitmapDrawable(null, bitmap)

        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.vibrantapp",
                activityName = "com.test.vibrantapp.MainActivity",
                label = "Vibrant Test App",
                icon = iconDrawable,
            )

        val defaultPrimary = Color.Gray
        val defaultSecondary = Color.DarkGray

        // Directly test palette extraction on bitmap using Palette library logic via extractor
        val extracted =
            AppPaletteExtractor.extractColors(
                appInfo = appInfo,
                defaultPrimary = defaultPrimary,
                defaultSecondary = defaultSecondary,
            )

        // Verify that the extracted primary color is NOT the black background (which had highest population),
        // but rather one of the vibrant colors (Red or Cyan)
        assertNotEquals(AndroidColor.BLACK, extracted.primaryColor.toArgb())

        // Verify primary color has high saturation (not grayscale)
        val hsvPrimary = FloatArray(3)
        AndroidColor.colorToHSV(extracted.primaryColor.toArgb(), hsvPrimary)
        assertTrue("Primary color saturation should be > 0.3f, was ${hsvPrimary[1]}", hsvPrimary[1] > 0.3f)

        // Verify secondary color is also populated and distinct
        assertNotEquals(extracted.primaryColor, extracted.secondaryColor)
    }
}
