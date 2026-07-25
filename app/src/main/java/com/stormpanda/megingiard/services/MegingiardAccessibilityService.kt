package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MegingiardAccessService"
private const val AUTO_TOGGLE_MAX_ATTEMPTS = 20
private const val AUTO_TOGGLE_STEP_DELAY_MS = 350L

private enum class AutoToggleStage {
    CLICK_SEARCH_BAR,
    ENTER_SEARCH_QUERY,
    CLICK_SEARCH_RESULT,
    TOGGLE_SWITCH,
}

/**
 * Event-driven Accessibility Service that monitors foreground window changes
 * on the primary screen and forwards notifications to [AutoSwitchCoordinator]
 * to trigger automatic profile switching.
 *
 * Registered in AndroidManifest.xml and configured by accessibility_service_config.xml.
 */
class MegingiardAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoToggleJob: Job? = null
    private var autoToggleStage = AutoToggleStage.CLICK_SEARCH_BAR

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
        handleAutoToggleEvent(event)
    }

    private fun handleAutoToggleEvent(event: AccessibilityEvent) {
        val pendingTime = autoTogglePendingTimestamp
        if (pendingTime == 0L || System.currentTimeMillis() - pendingTime > AUTO_TOGGLE_TIMEOUT_MS) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.android.settings" &&
            packageName != "com.android.settings.intelligence" &&
            !packageName.contains("inputmethod")
        ) {
            return
        }

        startAutoToggleLoop()
    }

    private fun startAutoToggleLoop() {
        if (autoToggleJob?.isActive == true) return
        autoToggleJob =
            serviceScope.launch {
                AppLog.i(TAG, "startAutoToggleLoop: Starting Search-based auto-toggle loop")
                var attempts = 0
                autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                while (isActive && attempts < AUTO_TOGGLE_MAX_ATTEMPTS && autoTogglePendingTimestamp != 0L) {
                    val rootNode = getRootNodeForDisplay(Display.DEFAULT_DISPLAY)
                    if (rootNode != null) {
                        when (autoToggleStage) {
                            AutoToggleStage.CLICK_SEARCH_BAR -> {
                                val clicked = findAndClickSearchBar(rootNode)
                                if (clicked) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Search Bar")
                                    autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                                }
                            }

                            AutoToggleStage.ENTER_SEARCH_QUERY -> {
                                val entered = findAndSetSearchQuery(rootNode)
                                if (entered) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Entered search query 'Wireless debugging'")
                                    autoToggleStage = AutoToggleStage.CLICK_SEARCH_RESULT
                                } else {
                                    val clickedBar = findAndClickSearchBar(rootNode)
                                    if (clickedBar) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Clicked Search Bar fallback")
                                        autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                                    }
                                }
                            }

                            AutoToggleStage.CLICK_SEARCH_RESULT -> {
                                val clickedResult = findAndClickSearchResult(rootNode)
                                if (clickedResult) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Wireless Debugging search result")
                                    autoToggleStage = AutoToggleStage.TOGGLE_SWITCH
                                }
                            }

                            AutoToggleStage.TOGGLE_SWITCH -> {
                                val toggled = findAndToggleSwitch(rootNode)
                                if (toggled) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Successfully toggled Wireless Debugging switch ON")
                                    autoTogglePendingTimestamp = 0L
                                    break
                                }
                            }
                        }
                    }
                    attempts++
                    delay(AUTO_TOGGLE_STEP_DELAY_MS)
                }
                if (autoTogglePendingTimestamp != 0L) {
                    AppLog.w(TAG, "startAutoToggleLoop: Timed out in stage $autoToggleStage after $AUTO_TOGGLE_MAX_ATTEMPTS attempts")
                    autoTogglePendingTimestamp = 0L
                }
            }
    }

    private fun findAndClickSearchBar(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val isSearchNode =
            viewId.contains("search_action_bar") ||
                viewId.contains("search_bar") ||
                combined.contains("search settings") ||
                combined.contains("einstellungen suchen") ||
                combined.contains("search") ||
                combined.contains("suchen")

        if (isSearchNode) {
            val clickableNode = findClickableAncestorOrSelf(node)
            if (clickableNode != null) {
                val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSearchBar(child)) return true
        }
        return false
    }

    private fun findAndSetSearchQuery(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val isEditTextNode =
            viewId.contains("search_src_text") ||
                viewId.contains("search_view") ||
                className.contains("EditText") ||
                className.contains("AutoCompleteTextView")

        if (isEditTextNode) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val arguments =
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "Wireless debugging",
                    )
                }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                AppLog.i(TAG, "findAndSetSearchQuery: Successfully set text via ACTION_SET_TEXT on $viewId ($className)")
                return true
            } else {
                AppLog.w(TAG, "findAndSetSearchQuery: ACTION_SET_TEXT returned false on $viewId ($className)")
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndSetSearchQuery(child)) return true
        }
        return false
    }

    private fun findAndClickSearchResult(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        // Skip the search input box / search bar at the top of the screen
        if (viewId.contains("search_src_text") ||
            viewId.contains("search_view") ||
            className.contains("EditText") ||
            className.contains("AutoCompleteTextView")
        ) {
            return false
        }

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val combined = "$text $contentDesc".lowercase()

        val isMatch =
            combined.contains("wireless debugging") ||
                combined.contains("wireless-debugging") ||
                combined.contains("drahtloses debugging") ||
                combined.contains("drahtlos-debugging")

        if (isMatch) {
            val clickable = findClickableAncestorOrSelf(node)
            if (clickable != null) {
                val clickedId = clickable.viewIdResourceName ?: ""
                if (!clickedId.contains("search_src") && !clickedId.contains("search_view")) {
                    val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        AppLog.i(TAG, "findAndClickSearchResult: Successfully clicked search result item for '$text'")
                        return true
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSearchResult(child)) return true
        }
        return false
    }

    private fun findAndToggleSwitch(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val isWirelessText =
            combined.contains("wireless debugging") ||
                combined.contains("wireless-debugging") ||
                combined.contains("drahtloses debugging") ||
                combined.contains("drahtlos-debugging")

        if (isWirelessText) {
            val switchNode = findSwitchOrCheckable(node) ?: node
            if (switchNode.isCheckable) {
                if (!switchNode.isChecked) {
                    AppLog.i(TAG, "findAndToggleSwitch: Wireless Debugging switch is OFF, clicking switch")
                    val clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (!clicked) {
                        val parent = findClickableAncestorOrSelf(switchNode)
                        parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                } else {
                    AppLog.i(TAG, "findAndToggleSwitch: Wireless Debugging switch is already ON")
                }
                return true
            } else if (node.isClickable) {
                AppLog.i(TAG, "findAndToggleSwitch: Clicking Wireless Debugging item row")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndToggleSwitch(child)) return true
        }
        return false
    }

    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findSwitchOrCheckable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isCheckable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSwitchOrCheckable(child)
            if (found != null) return found
        }
        val parent = node.parent
        if (parent != null && parent.isCheckable) return parent
        return null
    }

    private fun getRootNodeForDisplay(targetDisplayId: Int): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                if (window.displayId == targetDisplayId &&
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root != null
                ) {
                    return window.root
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "getRootNodeForDisplay threw: $e")
        }
        return rootInActiveWindow
    }

    override fun onInterrupt() {
        AppLog.w(TAG, "onInterrupt: Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (instance == this) instance = null
        AppLog.i(TAG, "onDestroy: Accessibility Service destroyed")
    }

    companion object {
        private var instance: MegingiardAccessibilityService? = null
        private var autoTogglePendingTimestamp = 0L
        private const val AUTO_TOGGLE_TIMEOUT_MS = 15000L

        /**
         * Returns true if the service instance is active and connected.
         */
        fun isInstanceActive(): Boolean = instance != null

        /**
         * Triggers Direct Page Launch to Settings Search on the primary screen (Display 0)
         * and activates Search-based Wireless Debugging auto-toggle via Accessibility Service.
         */
        fun triggerWirelessDebuggingAutoToggle(context: Context) {
            val displayOptions = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

            if (!isEnabled(context)) {
                AppLog.w(TAG, "triggerWirelessDebuggingAutoToggle: Accessibility Service is not enabled")
                Toast.makeText(context, R.string.privd_toast_accessibility_required, Toast.LENGTH_LONG).show()
                val accessibilityIntent =
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                context.startActivity(accessibilityIntent, displayOptions)
                return
            }

            autoTogglePendingTimestamp = System.currentTimeMillis()
            AppLog.i(TAG, "triggerWirelessDebuggingAutoToggle: Launching Settings Search on Display 0")

            instance?.startAutoToggleLoop()

            val searchActivityIntent =
                Intent().apply {
                    component =
                        ComponentName(
                            "com.android.settings.intelligence",
                            "com.android.settings.intelligence.search.SearchActivity",
                        )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            val searchActionIntent =
                Intent("android.settings.SETTINGS_SEARCH_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            val settingsIntent =
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

            try {
                context.startActivity(searchActivityIntent, displayOptions)
            } catch (e: Exception) {
                try {
                    context.startActivity(searchActionIntent, displayOptions)
                } catch (e2: Exception) {
                    try {
                        context.startActivity(settingsIntent, displayOptions)
                    } catch (e3: Exception) {
                        AppLog.e(TAG, "Failed to launch Settings: $e3")
                    }
                }
            }
        }

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
