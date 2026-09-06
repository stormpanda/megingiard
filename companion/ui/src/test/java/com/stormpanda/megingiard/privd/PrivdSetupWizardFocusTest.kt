package com.stormpanda.megingiard.privd

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "PrivdSetupWizardFocusTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivdSetupWizardFocusTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testPrivdSetupWizardDialog_clearsFlagNotFocusableOnMount_andRestoresOnDispose() {
        val activity = composeTestRule.activity
        activity.runOnUiThread {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        composeTestRule.waitForIdle()

        var showDialog by mutableStateOf(true)
        val testColors = paletteFor(ThemeMode.DARK)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                if (showDialog) {
                    PrivdSetupWizardDialog(onDismiss = { showDialog = false })
                }
            }
        }
        composeTestRule.waitForIdle()

        val flagsWhileMounted = activity.window.attributes.flags
        assertEquals(
            "FLAG_NOT_FOCUSABLE should be cleared while wizard is mounted",
            0,
            flagsWhileMounted and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )

        showDialog = false
        composeTestRule.waitForIdle()

        val flagsAfterDispose = activity.window.attributes.flags
        assertNotEquals(
            "FLAG_NOT_FOCUSABLE should be restored after wizard is unmounted",
            0,
            flagsAfterDispose and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
    }
}
