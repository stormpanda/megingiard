package com.stormpanda.megingiard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Robolectric Compose UI test for [MirrorControlCard] button enabled states and screenshot actions.
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
        var topScreenshotClicked = false
        var bottomScreenshotClicked = false
        var bothScreenshotClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                MirrorControlCard(
                    colors = testColors,
                    isCapturing = true,
                    isFrozen = false,
                    isTopScreenshotEnabled = true,
                    isBottomScreenshotEnabled = true,
                    isBothScreenshotEnabled = true,
                    isCompanionHub = true,
                    onStart = {},
                    onStop = { stopClicked = true },
                    onToggleFreeze = { freezeClicked = true },
                    onTakeTopScreenshot = { topScreenshotClicked = true },
                    onTakeBottomScreenshot = { bottomScreenshotClicked = true },
                    onTakeBothScreenshot = { bothScreenshotClicked = true },
                )
            }
        }

        // Stop and Freeze controls must be disabled when in Companion Hub
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Freeze").assertIsNotEnabled()

        // Click on Stop / Freeze should not trigger callbacks
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").performClick()
        composeTestRule.onNodeWithContentDescription("Freeze").performClick()

        assertFalse("stopClicked should be false when in Companion Hub", stopClicked)
        assertFalse("freezeClicked should be false when in Companion Hub", freezeClicked)

        // Screenshot buttons remain enabled
        composeTestRule.onNodeWithContentDescription("Take screenshot of top screen").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Take screenshot of top screen").performClick()
        assertTrue("topScreenshotClicked should be true", topScreenshotClicked)

        composeTestRule.onNodeWithContentDescription("Take screenshot of bottom screen").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Take screenshot of bottom screen").performClick()
        assertTrue("bottomScreenshotClicked should be true", bottomScreenshotClicked)

        composeTestRule.onNodeWithContentDescription("Take screenshot of both screens").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Take screenshot of both screens").performClick()
        assertTrue("bothScreenshotClicked should be true", bothScreenshotClicked)
    }

    @Test
    fun mirrorControlCard_enabledWhenNotInCompanionHub() {
        val testColors = paletteFor(ThemeMode.DARK)
        var stopClicked = false
        var freezeClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                MirrorControlCard(
                    colors = testColors,
                    isCapturing = true,
                    isFrozen = false,
                    isTopScreenshotEnabled = true,
                    isBottomScreenshotEnabled = true,
                    isBothScreenshotEnabled = true,
                    isCompanionHub = false,
                    onStart = {},
                    onStop = { stopClicked = true },
                    onToggleFreeze = { freezeClicked = true },
                    onTakeTopScreenshot = {},
                    onTakeBottomScreenshot = {},
                    onTakeBothScreenshot = {},
                )
            }
        }

        // Controls are enabled when not in Companion Hub and capturing
        composeTestRule.onNodeWithContentDescription("Stop Mirroring").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Freeze").assertIsEnabled()

        composeTestRule.onNodeWithContentDescription("Stop Mirroring").performClick()
        composeTestRule.onNodeWithContentDescription("Freeze").performClick()

        assertTrue("stopClicked should be true when not in Companion Hub", stopClicked)
        assertTrue("freezeClicked should be true when not in Companion Hub", freezeClicked)
    }
}
