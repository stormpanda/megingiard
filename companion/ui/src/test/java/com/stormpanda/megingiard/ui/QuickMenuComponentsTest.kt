package com.stormpanda.megingiard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI test for [ProfileRow] and [LayoutRow] in QuickMenuComponents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickMenuComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileRow_rendersProfilesAndTriggersSelection() {
        val testColors = paletteFor(ThemeMode.DARK)
        val profiles =
            listOf(
                PadProfile(id = "p1", name = "Default"),
                PadProfile(id = "p2", name = "Game Profile"),
            )
        var selectedProfile: PadProfile? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                ProfileRow(
                    profiles = profiles,
                    activeProfile = profiles[0],
                    colors = testColors,
                    onProfileSelected = { selectedProfile = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Default").assertExists()
        composeTestRule.onNodeWithText("Game Profile").assertExists()

        composeTestRule.onNodeWithText("Game Profile").performClick()
        assertEquals("p2", selectedProfile?.id)
    }

    @Test
    fun layoutRow_rendersLayoutsAndTriggersSelection() {
        val testColors = paletteFor(ThemeMode.DARK)
        val layouts =
            listOf(
                PadLayout(id = "l1", name = "Main Layout"),
                PadLayout(id = "l2", name = "Secondary Layout"),
            )
        val profile = PadProfile(id = "p1", name = "Default", layouts = layouts)
        var selectedLayoutId: String? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                LayoutRow(
                    activeProfile = profile,
                    activeLayout = layouts[0],
                    colors = testColors,
                    onLayoutSelected = { selectedLayoutId = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Main Layout").assertExists()
        composeTestRule.onNodeWithText("Secondary Layout").assertExists()

        composeTestRule.onNodeWithText("Secondary Layout").performClick()
        assertEquals("l2", selectedLayoutId)
    }
}
