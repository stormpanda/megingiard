package com.stormpanda.megingiard.macropad

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.paletteFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "TouchRecordingSheetTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h600dp")
class TouchRecordingSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun touchRecordingSheet_rendersTapModeAndTriggersCancel() {
        val testColors = paletteFor(ThemeMode.DARK)
        var cancelled = false

        val state =
            TouchRecordingState.Recording(
                mode = TouchRecordingMode.TAP,
                recordedGestureCount = 0,
                startElapsedRealtime = 1000L,
                liveNormX = 0.5f,
                liveNormY = 0.5f,
            )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                TouchRecordingSheet(
                    state = state,
                    onStop = {},
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Single Tap").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue("Cancel button must trigger onCancel callback", cancelled)
    }

    @Test
    fun touchRecordingSheet_rendersGestureModeAndTriggersStop() {
        val testColors = paletteFor(ThemeMode.DARK)
        var stopped = false

        val state =
            TouchRecordingState.Recording(
                mode = TouchRecordingMode.GESTURE,
                recordedGestureCount = 2,
                totalRecordedSampleCount = 48,
                startElapsedRealtime = 1000L,
                liveNormX = 0.25f,
                liveNormY = 0.75f,
            )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                TouchRecordingSheet(
                    state = state,
                    onStop = { stopped = true },
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Touch Gesture").assertExists()
        composeTestRule.onNodeWithText("Stop & Save").assertExists()
        composeTestRule.onNodeWithText("Stop & Save").performClick()

        assertTrue("Stop & Save button must trigger onStop callback", stopped)
    }

    @Test
    fun touchRecordingSheet_rendersMultiTouchPointers() {
        val testColors = paletteFor(ThemeMode.DARK)

        val state =
            TouchRecordingState.Recording(
                mode = TouchRecordingMode.GESTURE,
                recordedGestureCount = 1,
                totalRecordedSampleCount = 12,
                startElapsedRealtime = 1000L,
                activePointers =
                    listOf(
                        TouchPointerState(slot = 0, normX = 0.25f, normY = 0.35f, trail = listOf(Pair(0.2f, 0.3f), Pair(0.25f, 0.35f))),
                        TouchPointerState(slot = 1, normX = 0.75f, normY = 0.85f, trail = listOf(Pair(0.7f, 0.8f), Pair(0.75f, 0.85f))),
                    ),
            )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides testColors) {
                TouchRecordingSheet(
                    state = state,
                    onStop = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Touch Gesture").assertExists()
        composeTestRule.onNodeWithText("P1: 25.0%, 35.0%  •  P2: 75.0%, 85.0%").assertExists()
        composeTestRule.onNodeWithText("Stop & Save").assertExists()
    }
}
