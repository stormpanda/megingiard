package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "MegingiardAccessService"
private const val SCAN_CODE_HOME = 102
private const val HOME_PRESS_TIMEOUT_MS = 5000L

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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                AppLog.d(TAG, "onAccessibilityEvent: Window state changed, package=$packageName")
                AutoSwitchCoordinator.onPackageChanged(packageName)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!SettingsManager.blockHomeMinimization.value) {
            return super.onKeyEvent(event)
        }

        val isActualHomeButton = event.scanCode == SCAN_CODE_HOME
        if (event.keyCode == KeyEvent.KEYCODE_HOME && isActualHomeButton) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHomePressTime > HOME_PRESS_TIMEOUT_MS) {
                    lastHomePressTime = currentTime
                    shouldConsumeCurrentPress = true
                    AppLog.i(TAG, "onKeyEvent → hardware Home button first press detected: sending primary screen to Home, keeping secondary screen open")

                    // Shift focus to the default display to trigger GLOBAL_ACTION_HOME exclusively on the default display
                    try {
                        DisplayFocusActivity.launch(this, Display.DEFAULT_DISPLAY) {
                            AppLog.i(TAG, "onKeyEvent → focus acquired on default display: triggering GLOBAL_ACTION_HOME")
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "onKeyEvent → failed to launch focus activity on primary screen", e)
                    }

                    // Show Toast reminder specifically on the secondary display (run on main looper)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                            // The secondary screen is any screen that is NOT the default display
                            val secondaryDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
                            val contextForToast = if (secondaryDisplay != null) {
                                createDisplayContext(secondaryDisplay)
                            } else {
                                applicationContext
                            }
                            Toast.makeText(
                                contextForToast,
                                contextForToast.getString(R.string.home_press_again_to_exit),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            AppLog.e(TAG, "onKeyEvent → failed to show Toast on secondary screen, falling back", e)
                            Toast.makeText(
                                applicationContext,
                                applicationContext.getString(R.string.home_press_again_to_exit),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    lastHomePressTime = 0L
                    shouldConsumeCurrentPress = false
                    AppLog.i(TAG, "onKeyEvent → hardware Home button second press within 5s: minimizing secondary screen")

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
