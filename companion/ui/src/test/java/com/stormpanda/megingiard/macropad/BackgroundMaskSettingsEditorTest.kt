package com.stormpanda.megingiard.macropad

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests verifying save and discard lifecycle transitions in [LayoutBackgroundSubPageContent]
 * and [LayoutMaskSubPageContent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundMaskSettingsEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        BackgroundPickerManager.clearPickedUri()
    }

    private fun createTestImageFile(): Pair<File, Uri> {
        val tempFile = File.createTempFile("test_sample", ".png").apply { deleteOnExit() }
        val testBmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        tempFile.outputStream().use { out ->
            testBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        testBmp.recycle()
        return tempFile to Uri.fromFile(tempFile)
    }

    @Test
    fun layoutBackgroundSubPageContent_rendersInitialControls() {
        val testColors = paletteFor(ThemeMode.DARK)
        val initialLayout = PadLayout(id = "layout-1", name = "Main Layout")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LayoutBackgroundSubPageContent(
                        layout = initialLayout,
                        profileName = "Default Profile",
                        accentColor = Color.Cyan,
                        onOpenScrape = {},
                        onConfirm = { _, _, _, _, _, _, _ -> },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Browse local images").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scrape from SteamGridDB").assertIsDisplayed()
    }

    @Test
    fun layoutBackgroundSubPageContent_whenImagePickedAndSaved_callsConfirmAndResets() {
        val testColors = paletteFor(ThemeMode.DARK)
        var currentLayout by mutableStateOf(PadLayout(id = "layout-1", name = "Main Layout"))
        var confirmedPath: String? = null
        var confirmCallCount = 0

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LayoutBackgroundSubPageContent(
                        layout = currentLayout,
                        profileName = "Default Profile",
                        accentColor = Color.Cyan,
                        onOpenScrape = {},
                        onConfirm = { path, _, scale, offX, offY, dim, mode ->
                            confirmCallCount++
                            confirmedPath = path
                            currentLayout =
                                currentLayout.copy(
                                    backgroundImagePath = path,
                                    bgImageScale = scale,
                                    bgImageOffsetX = offX,
                                    bgImageOffsetY = offY,
                                    backgroundImageDim = dim,
                                    bgScaleMode = mode,
                                )
                        },
                    )
                }
            }
        }

        // Pick an image
        val (tempFile, testUri) = createTestImageFile()
        try {
            BackgroundPickerManager.setPickedUri(testUri)
            composeTestRule.waitForIdle()

            // Perform Save click
            composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
            composeTestRule.waitUntil(5000) { confirmCallCount == 1 }
            composeTestRule.waitForIdle()

            assertEquals(1, confirmCallCount)
            // Discard & Exit prompt shouldn't exist because changes are saved
            composeTestRule.onNodeWithText("Discard & Exit").assertDoesNotExist()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun layoutMaskSubPageContent_whenImagePickedAndSaved_callsConfirmAndResets() {
        val testColors = paletteFor(ThemeMode.DARK)
        var currentLayout by mutableStateOf(PadLayout(id = "layout-1", name = "Main Layout"))
        var confirmedPath: String? = null
        var confirmCallCount = 0

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LayoutMaskSubPageContent(
                        layout = currentLayout,
                        profileName = "Default Profile",
                        accentColor = Color.Cyan,
                        onConfirm = { path, _, scale, offX, offY, dim, mode ->
                            confirmCallCount++
                            confirmedPath = path
                            currentLayout =
                                currentLayout.copy(
                                    maskImagePath = path,
                                    maskImageScale = scale,
                                    maskImageOffsetX = offX,
                                    maskImageOffsetY = offY,
                                    maskImageDim = dim,
                                    maskScaleMode = mode,
                                )
                        },
                    )
                }
            }
        }

        // Pick a mask image
        val (tempFile, testUri) = createTestImageFile()
        try {
            BackgroundPickerManager.setPickedUri(testUri)
            composeTestRule.waitForIdle()

            // Perform Save click
            composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
            composeTestRule.waitUntil(5000) { confirmCallCount == 1 }
            composeTestRule.waitForIdle()

            assertEquals(1, confirmCallCount)
            // Discard & Exit prompt shouldn't exist because changes are saved
            composeTestRule.onNodeWithText("Discard & Exit").assertDoesNotExist()
        } finally {
            tempFile.delete()
        }
    }
}
