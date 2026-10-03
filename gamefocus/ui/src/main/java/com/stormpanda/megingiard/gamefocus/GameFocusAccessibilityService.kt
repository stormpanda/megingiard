package com.stormpanda.megingiard.gamefocus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import com.stormpanda.megingiard.gamefocus.domain.COMPANION_PACKAGE
import com.stormpanda.megingiard.gamefocus.domain.COMPANION_PACKAGE_DEBUG
import com.stormpanda.megingiard.gamefocus.domain.GameFocusSessionTracker
import com.stormpanda.megingiard.ipc.MegingiardIpcContract

private const val TAG = "GameFocusAccessibilityService"
private const val THOR_SECONDARY_DISPLAY_FALLBACK = 4
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val ANDROID_FRAMEWORK_PACKAGE = "android"
private const val ODIN_PACKAGE_PREFIX = "com.odin."
private const val GMS_PACKAGE_PREFIX = "com.google.android.gms"
private const val PLAY_GAMES_PACKAGE_PREFIX = "com.google.android.play.games"
private const val GBOARD_PACKAGE_PREFIX = "com.google.android.inputmethod"

/**
 * Optional accessibility service for Megingiard Game Focus that passively tracks window state
 * changes across both displays (Display 0 and Display 4) to support restoring external apps
 * when pressing Back on the main gallery.
 */
class GameFocusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        SystemRoleClassifier.init(this)
        AppLog.i(TAG, "GameFocusAccessibilityService connected and listening for window state changes")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (isIgnoredPackage(this, packageName)) {
            return
        }

        val displayId = event.displayId
        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(this)
        val secondaryDisplayId = secondaryDisplay?.displayId ?: THOR_SECONDARY_DISPLAY_FALLBACK

        GameFocusSessionTracker.recordWindowChanged(displayId, packageName, secondaryDisplayId)
    }

    override fun onInterrupt() {
        AppLog.d(TAG, "GameFocusAccessibilityService interrupted")
    }

    internal fun isIgnoredPackage(
        context: Context,
        packageName: String,
    ): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == context.packageName) return true
        if (packageName.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE)) return true
        if (packageName == COMPANION_PACKAGE || packageName == COMPANION_PACKAGE_DEBUG) return true
        if (packageName == SYSTEM_UI_PACKAGE || packageName == ANDROID_FRAMEWORK_PACKAGE) return true
        if (packageName.startsWith(GBOARD_PACKAGE_PREFIX)) return true
        if (packageName.startsWith(ODIN_PACKAGE_PREFIX)) return true
        if (packageName.startsWith(GMS_PACKAGE_PREFIX)) return true
        if (packageName.startsWith(PLAY_GAMES_PACKAGE_PREFIX)) return true
        if (SystemRoleClassifier.isLauncherOrSystemUi(packageName)) return true

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            AppLog.d(TAG, "Ignored package without launch intent: $packageName")
            return true
        }
        return false
    }
}
