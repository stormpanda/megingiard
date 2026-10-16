package com.stormpanda.megingiard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI test for [AppSelectableChip].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppSelectableChipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appSelectableChip_rendersTextAndHandlesClick() {
        val testColors = paletteFor(ThemeMode.DARK)
        var clicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                AppSelectableChip(
                    text = "Test Pill",
                    selected = false,
                    onClick = { clicked = true },
                    unselectedContentColor = testColors.accent,
                )
            }
        }

        composeTestRule.onNodeWithText("Test Pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Pill").performClick()
        assertTrue(clicked)
    }

    @Test
    fun appSelectableChip_selectedStateSetsSemantics() {
        val testColors = paletteFor(ThemeMode.DARK)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                AppSelectableChip(
                    text = "Selected Pill",
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Selected Pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Selected Pill").assertIsSelected()
    }
}
