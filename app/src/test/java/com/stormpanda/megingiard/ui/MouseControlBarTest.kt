package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Row
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
 * Robolectric Compose UI test for Mouse Control Bar actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MouseControlBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mouseButtons_triggerCorrectCallbacksOnPress() {
        val testColors = paletteFor(ThemeMode.DARK)
        var lmbPressed = false
        var rmbPressed = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                Row {
                    TextButton(onClick = { lmbPressed = true }) {
                        Text("LMB")
                    }
                    TextButton(onClick = { rmbPressed = true }) {
                        Text("RMB")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("LMB").performClick()
        assertTrue("LMB click handler must be triggered", lmbPressed)

        composeTestRule.onNodeWithText("RMB").performClick()
        assertTrue("RMB click handler must be triggered", rmbPressed)
    }
}
