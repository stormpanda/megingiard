package com.stormpanda.megingiard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI test for [MirrorControlCard] button enabled states in Companion Hub mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MirrorControlCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mirrorControlCard_disabledInCompanionHub() {
        val testColors = paletteFor(ThemeMode.DARK)
        var stopClicked = false
        var freezeClicked = false
        var editClicked = false
        var screenshotClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                MirrorControlCard(
                    colors = testColors,
                    isCapturing = true,
                    isFrozen = false,
                    isViewportEditActive = false,
                    isScreenshotEnabled = true,
                    isCompanionHub = true,
                    onStart = {},
                    onStop = { stopClicked = true },
                    onToggleFreeze = { freezeClicked = true },
                    onToggleViewportEdit = { editClicked = true },
                    onTakeScreenshot = { screenshotClicked = true },
                )
            }
        }

        // Stop, Freeze, and Edit controls must be disabled when in Companion Hub
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Freeze").assertIsNotEnabled()

        // Click on Stop / Freeze / Edit should not trigger callbacks
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").performClick()
        composeTestRule.onNodeWithContentDescription("Freeze").performClick()
        composeTestRule.onNodeWithText("Screen Mirroring").performClick()

        assertFalse("stopClicked should be false when in Companion Hub", stopClicked)
        assertFalse("freezeClicked should be false when in Companion Hub", freezeClicked)
        assertFalse("editClicked should be false when in Companion Hub", editClicked)

        // Screenshot button remains enabled
        composeTestRule.onNodeWithContentDescription("Screenshot").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Screenshot").performClick()
        assertTrue("screenshotClicked should be true", screenshotClicked)
    }

    @Test
    fun mirrorControlCard_enabledWhenNotInCompanionHub() {
        val testColors = paletteFor(ThemeMode.DARK)
        var stopClicked = false
        var freezeClicked = false
        var editClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                MirrorControlCard(
                    colors = testColors,
                    isCapturing = true,
                    isFrozen = false,
                    isViewportEditActive = false,
                    isScreenshotEnabled = true,
                    isCompanionHub = false,
                    onStart = {},
                    onStop = { stopClicked = true },
                    onToggleFreeze = { freezeClicked = true },
                    onToggleViewportEdit = { editClicked = true },
                    onTakeScreenshot = {},
                )
            }
        }

        // Controls are enabled when not in Companion Hub and capturing
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Freeze").assertIsEnabled()

        composeTestRule.onNodeWithContentDescription("Stop Mirroring").performClick()
        composeTestRule.onNodeWithContentDescription("Freeze").performClick()
        composeTestRule.onNodeWithText("Screen Mirroring").performClick()

        assertTrue("stopClicked should be true when not in Companion Hub", stopClicked)
        assertTrue("freezeClicked should be true when not in Companion Hub", freezeClicked)
        assertTrue("editClicked should be true when not in Companion Hub", editClicked)
    }
}
