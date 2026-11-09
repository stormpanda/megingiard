package com.stormpanda.megingiard.privd

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.onboarding.OnboardingStepId
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.tabs.GeneralSettingsTab
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.onboarding.SystemSetupStepContent
import com.stormpanda.megingiard.ui.onboarding.rememberRootBridgeActions
import com.stormpanda.megingiard.ui.onboarding.rememberSystemSetupStatus
import com.stormpanda.megingiard.ui.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "PrivdWizardFocusTest"

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

    @Test
    fun testGeneralSettingsTab_hidesRerunWizardAndIgnoresRunningClickWhenRootBridgeAvailable() {
        val activity = composeTestRule.activity
        val testColors = paletteFor(ThemeMode.DARK)
        var openSetupCount = 0
        var connectCount = 0
        var currentState by mutableStateOf(PrivdState.RUNNING)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                Column {
                    GeneralSettingsTab(
                        updateAvailable = false,
                        latestReleaseInfo = null,
                        privdState = currentState,
                        appLanguage = AppLanguage.SYSTEM,
                        excludeFromRecents = false,
                        onNavigateToSubPage = {},
                        onStartWelcomeTour = {},
                        onOpenPrivdSetup = { openSetupCount++ },
                        onConnectPrivd = { connectCount++ },
                        isRootBridgeAvailable = true,
                        onLanguageChange = {},
                        onExcludeFromRecentsChange = {},
                        onResetTutorials = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val rerunTitle = activity.getString(R.string.privd_rerun_wizard)
        val privdTitle = activity.getString(R.string.privd_title)
        val connectingLabel = activity.getString(R.string.privd_status_connecting)

        composeTestRule.onNodeWithText(rerunTitle).assertDoesNotExist()

        // Tapping the status card while RUNNING must not open the manual ADB wizard or re-connect
        composeTestRule.onNodeWithText(privdTitle).performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, openSetupCount)
        assertEquals(0, connectCount)

        // BOOTSTRAPPING state must display "Connecting…" and also ignore status card clicks
        currentState = PrivdState.BOOTSTRAPPING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(connectingLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(privdTitle).performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, openSetupCount)
        assertEquals(0, connectCount)
    }

    @Test
    fun testSystemSetupStepContent_usesReconnectCopyAndBootstrappingStateForRootBridge() {
        val activity = composeTestRule.activity
        val testColors = paletteFor(ThemeMode.DARK)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                val setupStatus = rememberSystemSetupStatus(OnboardingStepId.PRIVILEGED, isRootBridgeAvailable = true)
                val rootBridgeActions = rememberRootBridgeActions()
                SystemSetupStepContent(
                    stepId = OnboardingStepId.PRIVILEGED,
                    setupStatus = setupStatus,
                    rootBridgeActions = rootBridgeActions,
                    privdState = PrivdState.BOOTSTRAPPING,
                    isRootBridgeAvailable = true,
                    onStartAutoSetup = {},
                    isReconnectPrompt = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(activity.getString(R.string.privd_reconnect_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.privd_reconnect_root_bridge_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.onboarding_privd_activating)).assertIsDisplayed()
        assertFalse(
            composeTestRule
                .onAllNodes(hasText(activity.getString(R.string.onboarding_privd_root_bridge_title)))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }
}
