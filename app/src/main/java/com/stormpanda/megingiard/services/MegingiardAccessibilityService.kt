package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator

private const val TAG = "MegingiardAccessService"

/**
 * Event-driven Accessibility Service that monitors foreground window changes
 * on the primary screen and forwards notifications to [AutoSwitchCoordinator]
 * to trigger automatic profile switching.
 *
 * Registered in AndroidManifest.xml and configured by accessibility_service_config.xml.
 */
class MegingiardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.i(TAG, "onServiceConnected: Megingiard Accessibility Service is active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                AppLog.d(TAG, "onAccessibilityEvent: Window state changed, package=$packageName")
                AutoSwitchCoordinator.onPackageChanged(packageName)
            }
        }
    }

    override fun onInterrupt() {
        AppLog.w(TAG, "onInterrupt: Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: Accessibility Service destroyed")
    }
}
