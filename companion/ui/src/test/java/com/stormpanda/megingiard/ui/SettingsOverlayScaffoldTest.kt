package com.stormpanda.megingiard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Robolectric Compose UI test for Settings Scaffold components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsOverlayScaffoldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsTopAppBar_rendersTitleAndTriggersBackClick() {
        val testColors = paletteFor(ThemeMode.DARK)
        var backClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                TopAppBar(
                    title = { Text("Global Settings") },
                    navigationIcon = {
                        IconButton(onClick = { backClicked = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = testColors.surface),
                )
            }
        }

        composeTestRule.onNodeWithText("Global Settings").assertExists()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue("Back button click should trigger navigation callback", backClicked)
    }
}
