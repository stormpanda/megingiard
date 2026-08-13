package com.stormpanda.megingiard.ui

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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

private const val TAG = "AppModalDialogTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AppModalDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appModalDialog_rendersContentAndTriggersConfirm() {
        var confirmed = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides paletteFor(ThemeMode.DARK)) {
                AppModalDialog(
                    onDismiss = {},
                    content = {
                        Text(text = "Test Dialog Header")
                        TextButton(onClick = { confirmed = true }) {
                            Text(text = "Confirm Action")
                        }
                    },
                )
            }
        }

        // Verify title and button are displayed in the dialog surface
        composeTestRule.onNodeWithText("Test Dialog Header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm Action").assertIsDisplayed()

        // Perform click and verify callback mutation
        composeTestRule.onNodeWithText("Confirm Action").performClick()
        assertTrue("Expected confirm action callback to be triggered", confirmed)
    }
}
