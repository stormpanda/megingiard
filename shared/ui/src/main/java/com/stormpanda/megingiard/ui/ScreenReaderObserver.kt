package com.stormpanda.megingiard.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.stormpanda.megingiard.AppLog

private const val TAG = "ScreenReaderObserver"

/**
 * Returns `true` when [feedbackType] includes spoken or braille screen-reader feedback.
 * Non-screen-reader generic monitors (such as Megingiard's own `FEEDBACK_GENERIC` service) return `false`.
 */
fun isScreenReaderFeedbackMask(feedbackType: Int): Boolean =
    (feedbackType and (AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_BRAILLE)) != 0

/**
 * Returns `true` when an enabled accessibility service acts as a spoken/braille screen reader
 * capable of inspecting window content.
 */
fun isScreenReaderService(
    feedbackType: Int,
    capabilities: Int,
): Boolean =
    isScreenReaderFeedbackMask(feedbackType) &&
        (capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0

/**
 * Checks whether an interactive screen reader or touch-exploration service (e.g. TalkBack,
 * Voice Access, BrailleBack) is currently active on [context].
 */
fun isScreenReaderAccessibilityActive(context: Context): Boolean {
    val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
    if (!am.isEnabled) return false
    if (am.isTouchExplorationEnabled) return true
    return am
        .getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_BRAILLE,
        ).any { isScreenReaderService(it.feedbackType, it.capabilities) }
}

/**
 * Tracks whether a true screen reader or touch exploration service is active.
 * Used on 120 Hz custom input planes so Companion's own `FEEDBACK_GENERIC` AccessibilityService
 * does not trigger Compose's full-tree `SemanticsNode` bounds-diff walk on every frame.
 */
@Composable
fun rememberScreenReaderActive(context: Context = LocalContext.current): Boolean {
    var isActive by remember(context) { mutableStateOf(isScreenReaderAccessibilityActive(context)) }
    DisposableEffect(context) {
        val am = context.getSystemService(AccessibilityManager::class.java)
        if (am == null) {
            onDispose {}
        } else {
            val a11yListener =
                AccessibilityManager.AccessibilityStateChangeListener {
                    val updated = isScreenReaderAccessibilityActive(context)
                    AppLog.d(TAG, "Accessibility state changed -> isScreenReaderActive=$updated")
                    isActive = updated
                }
            val touchListener =
                AccessibilityManager.TouchExplorationStateChangeListener {
                    val updated = isScreenReaderAccessibilityActive(context)
                    AppLog.d(TAG, "Touch exploration state changed -> isScreenReaderActive=$updated")
                    isActive = updated
                }
            am.addAccessibilityStateChangeListener(a11yListener)
            am.addTouchExplorationStateChangeListener(touchListener)
            isActive = isScreenReaderAccessibilityActive(context)
            onDispose {
                am.removeAccessibilityStateChangeListener(a11yListener)
                am.removeTouchExplorationStateChangeListener(touchListener)
            }
        }
    }
    return isActive
}

/**
 * Clears descendant semantics when no screen reader is active while preserving hardware D-pad
 * `FocusTargetNode` focus traversal and restoring full TalkBack semantics immediately when enabled.
 */
fun Modifier.pruneNonScreenReaderSemantics(isScreenReaderActive: Boolean): Modifier =
    if (isScreenReaderActive) this else this.clearAndSetSemantics {}
