package com.stormpanda.megingiard.ui

import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenReaderObserverTest {
    @Test
    fun `isScreenReaderFeedbackMask detects spoken and braille feedback only`() {
        assertTrue(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_SPOKEN))
        assertTrue(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_BRAILLE))
        assertTrue(
            isScreenReaderFeedbackMask(
                AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_HAPTIC,
            ),
        )
        assertFalse(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_GENERIC))
        assertFalse(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_HAPTIC))
        assertFalse(isScreenReaderFeedbackMask(0))
    }

    @Test
    fun `isScreenReaderService requires both spoken or braille feedback and window content capability`() {
        assertTrue(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN,
                capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT,
            ),
        )
        assertFalse(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN,
                capabilities = 0,
            ),
        )
        assertFalse(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC,
                capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT,
            ),
        )
    }
}
