package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

private const val TAG = "GamepadScaffoldTest"
private const val FOCUS_SETTLE_ADVANCE_MS = 150L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GamepadScaffoldAndCardFocusTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        AppLog.d(TAG, "setUp: resetting joystick and input modality state")
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    @After
    fun tearDown() {
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    private fun advanceFocusDelayAndIdle() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(FOCUS_SETTLE_ADVANCE_MS)
        ShadowLooper.idleMainLooper(FOCUS_SETTLE_ADVANCE_MS, TimeUnit.MILLISECONDS)
        composeTestRule.waitForIdle()
    }

    private fun sendHardwareKey(keyCode: Int) {
        composeTestRule.runOnIdle {
            composeTestRule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            composeTestRule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun touchSubPageNavigation_doesNotBounceBackToFirstCategory_andIsolatesSidebar() {
        val testColors = paletteFor(ThemeMode.DARK)
        var selectedCategory by mutableStateOf("General")
        var subPageStack by mutableStateOf<List<String>>(emptyList())

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadTwoPaneScaffold(
                    scrollableDeck = false,
                    isCustomBackActive = subPageStack.isNotEmpty(),
                    onCustomBack = { subPageStack = subPageStack.dropLast(1) },
                    navigationKey = subPageStack,
                    categoryKey = selectedCategory,
                    sidebarContent = {
                        GamepadCategoryTile(
                            title = "General",
                            icon = Icons.Rounded.Settings,
                            selected = selectedCategory == "General",
                            onClick = {
                                selectedCategory = "General"
                                subPageStack = emptyList()
                            },
                        )
                        GamepadCategoryTile(
                            title = "Appearance",
                            icon = Icons.Rounded.Palette,
                            selected = selectedCategory == "Appearance",
                            onClick = {
                                selectedCategory = "Appearance"
                                subPageStack = emptyList()
                            },
                        )
                    },
                    content = {
                        val subPage = subPageStack.lastOrNull()
                        if (subPage == null) {
                            GamepadDeck(title = selectedCategory) {
                                if (selectedCategory == "General") {
                                    GamepadActionCard(
                                        title = "General Item",
                                        onClick = {},
                                    )
                                } else {
                                    GamepadActionCard(
                                        title = "Open Custom Accent",
                                        onClick = { subPageStack = listOf("custom_accent") },
                                    )
                                }
                            }
                        } else {
                            GamepadDeck(title = "Custom Accent SubPage") {
                                GamepadActionCard(
                                    title = "Apply Custom Accent",
                                    onClick = {},
                                )
                            }
                        }
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to touch input modality and navigate to Appearance -> Open Custom Accent
        PrimaryOverlayInputBridge.notifyTouchInput()
        composeTestRule.onNodeWithText("Appearance").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Appearance", selectedCategory)

        composeTestRule.onNodeWithText("Open Custom Accent").performClick()
        advanceFocusDelayAndIdle()

        assertEquals(
            "Opening a sub-page via touch must not reset selectedCategory to the first tab",
            "Appearance",
            selectedCategory,
        )
        composeTestRule.onNodeWithText("CUSTOM ACCENT SUBPAGE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply Custom Accent").assertIsDisplayed()

        // Even if gamepad D-pad Left is pressed while sub-page is active, sidebar tiles cannot take focus
        PrimaryOverlayInputBridge.notifyGamepadInput()
        composeTestRule.onNodeWithText("Apply Custom Accent").requestFocus()
        composeTestRule.waitForIdle()
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_LEFT)
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_UP)

        assertEquals(
            "Sidebar must remain focus-isolated while isCustomBackActive is true",
            "Appearance",
            selectedCategory,
        )
    }

    @Test
    fun bumperCategoryChange_whenDeckWasActive_keepsFocusInDeck() {
        val testColors = paletteFor(ThemeMode.DARK)
        var selectedCategory by mutableStateOf("Cat1")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadTwoPaneScaffold(
                    scrollableDeck = false,
                    categoryKey = selectedCategory,
                    sidebarContent = {
                        GamepadCategoryTile(
                            title = "Cat1",
                            icon = Icons.Rounded.Settings,
                            selected = selectedCategory == "Cat1",
                            onClick = { selectedCategory = "Cat1" },
                            modifier = Modifier.testTag("tile_cat1"),
                        )
                        GamepadCategoryTile(
                            title = "Cat2",
                            icon = Icons.Rounded.Palette,
                            selected = selectedCategory == "Cat2",
                            onClick = { selectedCategory = "Cat2" },
                            modifier = Modifier.testTag("tile_cat2"),
                        )
                    },
                    content = {
                        GamepadDeck(title = "Deck") {
                            if (selectedCategory == "Cat1") {
                                GamepadActionCard(
                                    title = "Cat1 Card",
                                    onClick = {},
                                    modifier = Modifier.testTag("card_cat1"),
                                )
                            } else {
                                GamepadActionCard(
                                    title = "Cat2 Card",
                                    onClick = {},
                                    modifier = Modifier.testTag("card_cat2"),
                                )
                            }
                        }
                    },
                )
            }
        }
        advanceFocusDelayAndIdle()

        // Move focus into the right-hand deck
        composeTestRule.onNodeWithTag("card_cat1").requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_cat1").assertIsFocused()

        // Simulate R1 shoulder bumper switching category while focused in the deck
        composeTestRule.runOnIdle {
            selectedCategory = "Cat2"
        }
        advanceFocusDelayAndIdle()

        assertEquals("Cat2", selectedCategory)
        composeTestRule.onNodeWithTag("card_cat2").assertIsFocused()
    }

    @Test
    fun bumperCategoryChange_whenSidebarWasActive_focusesNewCategoryTile() {
        val testColors = paletteFor(ThemeMode.DARK)
        var selectedCategory by mutableStateOf("Cat1")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadTwoPaneScaffold(
                    scrollableDeck = false,
                    categoryKey = selectedCategory,
                    sidebarContent = {
                        GamepadCategoryTile(
                            title = "Cat1",
                            icon = Icons.Rounded.Settings,
                            selected = selectedCategory == "Cat1",
                            onClick = { selectedCategory = "Cat1" },
                            modifier = Modifier.testTag("tile_cat1"),
                        )
                        GamepadCategoryTile(
                            title = "Cat2",
                            icon = Icons.Rounded.Palette,
                            selected = selectedCategory == "Cat2",
                            onClick = { selectedCategory = "Cat2" },
                            modifier = Modifier.testTag("tile_cat2"),
                        )
                    },
                    content = {
                        GamepadDeck(title = "Deck") {
                            GamepadActionCard(
                                title = "Card for $selectedCategory",
                                onClick = {},
                            )
                        }
                    },
                )
            }
        }
        advanceFocusDelayAndIdle()
        composeTestRule.onNodeWithTag("tile_cat1").assertIsFocused()

        // Switch category via bumper while focus is in the sidebar
        composeTestRule.runOnIdle {
            selectedCategory = "Cat2"
        }
        advanceFocusDelayAndIdle()

        composeTestRule.onNodeWithTag("tile_cat2").assertIsFocused()
    }

    @Test
    fun gamepadFocusCard_separatesGamepadOnClickFromOnTouchClick() {
        val testColors = paletteFor(ThemeMode.DARK)
        var gamepadClicks = 0
        var touchClicks = 0

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadFocusCard(
                    onClick = { gamepadClicks++ },
                    onTouchClick = { touchClicks++ },
                    modifier = Modifier.testTag("dual_action_card"),
                ) {
                    Text("Dual Action Card")
                }
            }
        }
        composeTestRule.waitForIdle()

        // Touch / accessibility click invokes onTouchClick without firing gamepad onClick
        composeTestRule.onNodeWithTag("dual_action_card").performClick()
        assertEquals(1, touchClicks)
        assertEquals(0, gamepadClicks)

        // Focus + DPAD_CENTER invokes gamepad onClick via onPreviewKeyEvent without firing onTouchClick
        composeTestRule.onNodeWithTag("dual_action_card").requestFocus()
        composeTestRule.waitForIdle()
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, touchClicks)
        assertEquals(1, gamepadClicks)

        // Focus + BUTTON_A also invokes gamepad onClick directly
        sendHardwareKey(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(1, touchClicks)
        assertEquals(2, gamepadClicks)
    }

    @Test
    fun stepperCard_preservesIsAdjustingAcrossStateRestoration_andAllowsDirectCapsuleArrowTap() {
        val testColors = paletteFor(ThemeMode.DARK)
        val restorationTester = StateRestorationTester(composeTestRule)
        var currentStep = 1

        restorationTester.setContent {
            var step by rememberSaveable { mutableIntStateOf(1) }
            currentStep = step
            CompositionLocalProvider(LocalAppColors provides testColors) {
                GamepadStepperCard(
                    title = "Theme Stepper",
                    valueText = "Option $step",
                    onDecrement = { step-- },
                    onIncrement = { step++ },
                    modifier = Modifier.testTag("theme_stepper"),
                )
            }
        }
        composeTestRule.waitForIdle()

        // Touching the capsule's Next arrow increments directly without entering stuck adjustment mode
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, currentStep)

        // Focus the stepper card and press DPAD_CENTER to enter Tier-2 adjustment mode
        composeTestRule.onNodeWithTag("theme_stepper").requestFocus()
        composeTestRule.waitForIdle()
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER)

        // Verify Tier-2 adjustment mode is active
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(3, currentStep)

        // Emulate Activity recreation (e.g. OS FabricatedOverlay theme sync) mid-adjustment
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // Refocus restored card: mount-time isFocused=false must NOT have cleared saved isAdjusting=true
        composeTestRule.onNodeWithTag("theme_stepper").requestFocus()
        composeTestRule.waitForIdle()
        sendHardwareKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(
            "Restored card must remain in Tier-2 adjustment mode after Activity recreation",
            4,
            currentStep,
        )
    }
}
