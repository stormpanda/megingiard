package com.stormpanda.megingiard.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
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
 * Robolectric Compose UI test for [AppModalDialog].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppAlertDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appModalDialog_rendersContentBodyAndTitle() {
        val testColors = paletteFor(ThemeMode.DARK)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                AppModalDialog(onDismiss = {}) {
                    Text("Dialog Title")
                    Text("Dialog Content Body")
                }
            }
        }

        composeTestRule.onNodeWithText("Dialog Title").assertExists()
        composeTestRule.onNodeWithText("Dialog Content Body").assertExists()
    }

    @Test
    fun appModalDialog_dismissTriggeredOnConfirmButtonAction() {
        val testColors = paletteFor(ThemeMode.DARK)
        var confirmed = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                AppModalDialog(onDismiss = {}) {
                    Text("Body")
                    TextButton(onClick = { confirmed = true }) {
                        Text("Confirm")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Confirm").performClick()
        assertTrue("Confirm click handler must be triggered", confirmed)
    }

    @Test
    fun appAlertDialog_rendersTitleTextAndButtons() {
        val testColors = paletteFor(ThemeMode.DARK)
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                AppAlertDialog(
                    onDismissRequest = { dismissed = true },
                    title = { Text("Alert Title") },
                    text = { Text("Alert Body Message") },
                    confirmButton = {
                        TextButton(onClick = { confirmed = true }) {
                            Text("Confirm Button")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dismissed = true }) {
                            Text("Cancel Button")
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Alert Title").assertExists()
        composeTestRule.onNodeWithText("Alert Body Message").assertExists()
        composeTestRule.onNodeWithText("Confirm Button").assertExists()
        composeTestRule.onNodeWithText("Cancel Button").assertExists()

        composeTestRule.onNodeWithText("Confirm Button").performClick()
        assertTrue("Confirm handler must be triggered", confirmed)

        composeTestRule.onNodeWithText("Cancel Button").performClick()
        assertTrue("Dismiss handler must be triggered", dismissed)
    }
}
