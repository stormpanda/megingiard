package com.stormpanda.megingiard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.CompositionLocalProvider
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
 * Robolectric Compose UI test for [GamepadConfirmModal].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GamepadConfirmModalTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun gamepadConfirmModal_whenVisibleFalse_doesNotRender() {
        val testColors = paletteFor(ThemeMode.DARK)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadConfirmModal(
                    visible = false,
                    title = "Modal Title",
                    description = "Modal Description",
                    confirmTitle = "Confirm Action",
                    dismissTitle = "Dismiss Action",
                    onConfirm = {},
                    onDismissAction = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Modal Title").assertDoesNotExist()
        composeTestRule.onNodeWithText("Modal Description").assertDoesNotExist()
        composeTestRule.onNodeWithText("Confirm Action").assertDoesNotExist()
        composeTestRule.onNodeWithText("Dismiss Action").assertDoesNotExist()
    }

    @Test
    fun gamepadConfirmModal_whenVisibleTrue_rendersTitleDescriptionAndActions() {
        val testColors = paletteFor(ThemeMode.DARK)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadConfirmModal(
                    visible = true,
                    title = "Auto Switch Deactivated",
                    description = "Would you like to turn it back on?",
                    confirmTitle = "Turn Auto Switch Back On",
                    confirmDescription = "Resume automatic profile switching",
                    confirmIcon = Icons.Rounded.AutoFixHigh,
                    dismissTitle = "Keep Off",
                    dismissDescription = "Leave companion in manual mode",
                    dismissIcon = Icons.Rounded.Close,
                    headerIcon = Icons.Rounded.AutoFixHigh,
                    onConfirm = {},
                    onDismissAction = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Auto Switch Deactivated").assertExists()
        composeTestRule.onNodeWithText("Would you like to turn it back on?").assertExists()
        composeTestRule.onNodeWithText("Turn Auto Switch Back On").assertExists()
        composeTestRule.onNodeWithText("Resume automatic profile switching").assertExists()
        composeTestRule.onNodeWithText("Keep Off").assertExists()
        composeTestRule.onNodeWithText("Leave companion in manual mode").assertExists()
    }

    @Test
    fun gamepadConfirmModal_confirmClick_invokesOnConfirm() {
        val testColors = paletteFor(ThemeMode.DARK)
        var confirmed = false
        var dismissed = false
        var cancelled = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadConfirmModal(
                    visible = true,
                    title = "Test Modal",
                    description = "Test Desc",
                    confirmTitle = "Confirm Button",
                    dismissTitle = "Dismiss Button",
                    onConfirm = { confirmed = true },
                    onDismissAction = { dismissed = true },
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm Button").performClick()

        assertTrue("onConfirm must be invoked", confirmed)
        assertFalse("onDismissAction must not be invoked", dismissed)
        assertFalse("onCancel must not be invoked", cancelled)
    }

    @Test
    fun gamepadConfirmModal_dismissClick_invokesOnDismissAction() {
        val testColors = paletteFor(ThemeMode.DARK)
        var confirmed = false
        var dismissed = false
        var cancelled = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadConfirmModal(
                    visible = true,
                    title = "Test Modal",
                    description = "Test Desc",
                    confirmTitle = "Confirm Button",
                    dismissTitle = "Dismiss Button",
                    onConfirm = { confirmed = true },
                    onDismissAction = { dismissed = true },
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Dismiss Button").performClick()

        assertFalse("onConfirm must not be invoked", confirmed)
        assertTrue("onDismissAction must be invoked", dismissed)
        assertFalse("onCancel must not be invoked", cancelled)
    }

    @Test
    fun gamepadConfirmModal_closeButtonClick_invokesOnCancel() {
        val testColors = paletteFor(ThemeMode.DARK)
        var confirmed = false
        var dismissed = false
        var cancelled = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadConfirmModal(
                    visible = true,
                    title = "Test Modal",
                    description = "Test Desc",
                    confirmTitle = "Confirm Button",
                    dismissTitle = "Dismiss Button",
                    onConfirm = { confirmed = true },
                    onDismissAction = { dismissed = true },
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close").performClick()

        assertFalse("onConfirm must not be invoked", confirmed)
        assertFalse("onDismissAction must not be invoked", dismissed)
        assertTrue("onCancel must be invoked on close button click", cancelled)
    }
}
