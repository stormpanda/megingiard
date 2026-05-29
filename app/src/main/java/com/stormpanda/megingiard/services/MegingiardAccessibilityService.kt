package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.R

private const val TAG = "MegingiardAccessService"

/**
 * Event-driven Accessibility Service that monitors foreground window changes
 * on the primary screen and forwards notifications to [AutoSwitchCoordinator]
 * to trigger automatic profile switching.
 *
 * Registered in AndroidManifest.xml and configured by accessibility_service_config.xml.
 */
class MegingiardAccessibilityService : AccessibilityService() {

    private var lastHomePressTime = 0L
    private var shouldConsumeCurrentPress = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!SettingsManager.blockHomeMinimization.value) {
            return super.onKeyEvent(event)
        }

        val isActualHomeButton = event.scanCode == 102
        if (event.keyCode == KeyEvent.KEYCODE_HOME && isActualHomeButton) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHomePressTime > 5000) {
                    lastHomePressTime = currentTime
                    shouldConsumeCurrentPress = true
                    AppLog.i(TAG, "onKeyEvent → hardware Home button first press detected, consuming event")

                    // Show Toast reminder (run on main looper)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            applicationContext.getString(R.string.home_press_again_to_exit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    lastHomePressTime = 0L
                    shouldConsumeCurrentPress = false
                    AppLog.i(TAG, "onKeyEvent → hardware Home button second press within 5s, passing to system")

                    // Set user leaving to true so presentations can hide
                    AppStateManager.setUserLeaving(true)
                }
            }
            return shouldConsumeCurrentPress
        }

        return super.onKeyEvent(event)
    }

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

    companion object {
        /**
         * Checks if the Megingiard Accessibility Service is currently enabled in Android system settings.
         */
        fun isEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(
                context.applicationContext,
                MegingiardAccessibilityService::class.java
            )
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val enabledService = splitter.next()
                val component = ComponentName.unflattenFromString(enabledService)
                if (component != null && component == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }
}
