package com.stormpanda.megingiard.ui

import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private const val TAG = "LocalFullscreenKeyboardRequesterTest"

class LocalFullscreenKeyboardRequesterTest {
    @Test
    fun testKeyboardRequester_defaultIsNull() {
        AppLog.d(TAG, "Testing LocalFullscreenKeyboardRequester default value")
        assertNotNull(LocalFullscreenKeyboardRequester)
    }

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
