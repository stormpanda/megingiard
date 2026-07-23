package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        instance = this
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
        if (instance == this) instance = null
        AppLog.i(TAG, "onDestroy: Accessibility Service destroyed")
    }

    companion object {
        private var instance: MegingiardAccessibilityService? = null

        /**
         * Returns true if the service instance is active and connected.
         */
        fun isInstanceActive(): Boolean = instance != null

        /**
         * Captures a screenshot of the specified display (default: primary screen / Display 0)
         * without showing system prompt dialogs.
         */
        fun captureDisplayScreenshot(
            displayId: Int = Display.DEFAULT_DISPLAY,
            callback: (Bitmap?) -> Unit,
        ) {
            val service = instance
            if (service == null) {
                AppLog.w(TAG, "captureDisplayScreenshot failed: service instance is null")
                callback(null)
                return
            }
            AppLog.i(TAG, "captureDisplayScreenshot requested for displayId=$displayId")
            try {
                service.takeScreenshot(
                    displayId,
                    service.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            AppLog.d(TAG, "takeScreenshot onSuccess")
                            try {
                                val buffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val bitmap =
                                    try {
                                        Bitmap
                                            .wrapHardwareBuffer(buffer, colorSpace)
                                            ?.copy(Bitmap.Config.ARGB_8888, false)
                                    } finally {
                                        buffer.close()
                                    }
                                callback(bitmap)
                            } catch (e: Exception) {
                                AppLog.w(TAG, "Failed to extract bitmap from ScreenshotResult: $e")
                                callback(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            AppLog.w(TAG, "takeScreenshot onFailure errorCode=$errorCode")
                            callback(null)
                        }
                    },
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "takeScreenshot threw: $e")
                callback(null)
            }
        }

        /**
         * Scans visible accessibility text nodes on the specified display (default: primary screen / Display 0)
         * and returns the aggregated text.
         */
        fun scanActiveWindowText(targetDisplayId: Int = Display.DEFAULT_DISPLAY): String {
            val service = instance ?: return ""
            val sb = StringBuilder()
            try {
                for (window in service.windows) {
                    if (window.displayId == targetDisplayId) {
                        val windowRoot = window.root
                        if (windowRoot != null) {
                            collectNodeText(windowRoot, sb)
                        }
                    }
                }
                // Fallback: if no windows matched targetDisplayId, check rootInActiveWindow
                if (sb.isEmpty()) {
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        collectNodeText(rootNode, sb)
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "scanActiveWindowText threw: $e")
            }
            return sb.toString()
        }

        private fun collectNodeText(
            node: AccessibilityNodeInfo,
            sb: StringBuilder,
        ) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                sb.append(text).append("\n")
            }
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && contentDesc != text) {
                sb.append(contentDesc).append("\n")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectNodeText(child, sb)
            }
        }

        /**
         * Checks if the Megingiard Accessibility Service is currently enabled in Android system settings.
         */
        fun isEnabled(context: Context): Boolean {
            val expectedComponentName =
                ComponentName(
                    context.applicationContext,
                    MegingiardAccessibilityService::class.java,
                )
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
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
