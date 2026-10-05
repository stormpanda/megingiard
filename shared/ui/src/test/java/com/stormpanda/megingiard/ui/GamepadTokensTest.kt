package com.stormpanda.megingiard.ui

import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TAG = "GamepadTokensTest"

/**
 * Guards the shared gamepad card dimension tokens. LocalFullscreenKeyboardRequester is not covered
 * here because its behaviour can only be observed inside a composition (needs Compose UI tests).
 */
class GamepadTokensTest {
    @Test
    fun testGamepadTokens_constantsHaveExpectedValues() {
        AppLog.d(TAG, "Testing GamepadTokens dimension tokens")
        assertEquals(12.dp, GC_CARD_CORNER)
        assertEquals(56.dp, GC_CARD_MIN_HEIGHT)
        assertEquals(16.dp, GC_CARD_H_PADDING)
        assertEquals(12.dp, GC_CARD_V_PADDING)
        assertEquals(2.5.dp, GC_FOCUS_BORDER_WIDTH)
        assertEquals(1.dp, GC_DEFAULT_BORDER_WIDTH)
    }
}
