package com.stormpanda.megingiard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Path
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.privd.AutoSetupLanguageConfig
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdPairScreenTextScanner
import com.stormpanda.megingiard.privd.PrivdState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "MegingiardAccessService"
private const val AUTO_TOGGLE_MAX_ATTEMPTS = 25
private const val AUTO_TOGGLE_STEP_DELAY_MS = 350L

private enum class AutoSetupTargetStage {
    STAGE_B_WIRELESS_DEBUG,
    STAGE_C_PAIRING,
}

private enum class AutoToggleStage {
    ACTIVATE_DEV_MODE_SEARCH_BUILD_NUMBER,
    ACTIVATE_DEV_MODE_CLICK_SEARCH_RESULT,
    ACTIVATE_DEV_MODE_CLICK_BUILD_NUMBER,
    CLICK_SEARCH_BAR,
    ENTER_SEARCH_QUERY,
    CLICK_SEARCH_RESULT,
    TOGGLE_SWITCH,
    CLICK_PAIR_DIALOG,
    SCAN_PAIRING_CODE_AND_PAIR,
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
    private var autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
    private var autoSetupTargetStage = AutoSetupTargetStage.STAGE_C_PAIRING

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.i(TAG, "onServiceConnected: Megingiard Accessibility Service is active")
        AppStateManager.setAccessibilityActive(true)
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

    private fun getSystemAutoSetupConfig(context: Context): AutoSetupLanguageConfig {
        val lmSysLoc =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val lm = context.getSystemService(LocaleManager::class.java)
                val sysLocales = lm?.systemLocales
                if (sysLocales != null && sysLocales.size() > 0) {
                    sysLocales.get(0)?.toLanguageTag()
                } else {
                    null
                }
            } else {
                null
            }

        val rawSysLocales = Settings.System.getString(context.contentResolver, "system_locales")
        val rawSysTag =
            rawSysLocales
                ?.split(",")
                ?.firstOrNull()
                ?.trim()

        val appTag = Locale.getDefault().toLanguageTag()

        val detectedTag = listOfNotNull(lmSysLoc, rawSysTag, appTag).firstOrNull { it.isNotBlank() } ?: "en-US"
        val config = AutoSetupLanguageConfig.fromLanguageTag(detectedTag)
        AppLog.i(
            TAG,
            "getSystemAutoSetupConfig: System locale tag detected as '$detectedTag' (lmSysLoc=$lmSysLoc, rawSysTag=$rawSysTag, appTag=$appTag) => mapped config localeTag='${config.localeTag}'",
        )
        return config
    }

    private fun startAutoToggleLoop() {
        if (autoToggleJob?.isActive == true) return
        autoToggleJob =
            serviceScope.launch {
                val config = getSystemAutoSetupConfig(applicationContext)
                AppLog.i(
                    TAG,
                    "startAutoToggleLoop: Starting Search-based auto-toggle loop in stage $autoToggleStage using language '${config.languageCode}'",
                )
                var attempts = 0
                val context = applicationContext
                val displayOptions = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

                while (isActive && attempts < AUTO_TOGGLE_MAX_ATTEMPTS && autoTogglePendingTimestamp != 0L) {
                    val rootNode = getRootNodeForDisplay(Display.DEFAULT_DISPLAY)
                    if (rootNode != null) {
                        val clickedAllow = findAndClickAllowDialogButton(rootNode, config.allowButtonKeywords)
                        if (clickedAllow) {
                            AppLog.i(TAG, "startAutoToggleLoop: Confirmed Wireless Debugging network trust dialog")
                        }

                        when (autoToggleStage) {
                            AutoToggleStage.ACTIVATE_DEV_MODE_SEARCH_BUILD_NUMBER -> {
                                val entered = findAndSetSearchQuery(rootNode, config.buildNumberQueryAndKeyword)
                                if (entered) {
                                    AppLog.i(
                                        TAG,
                                        "startAutoToggleLoop: Entered search query '${config.buildNumberQueryAndKeyword}' for Stage A",
                                    )
                                    autoToggleStage = AutoToggleStage.ACTIVATE_DEV_MODE_CLICK_SEARCH_RESULT
                                }
                            }

                            AutoToggleStage.ACTIVATE_DEV_MODE_CLICK_SEARCH_RESULT -> {
                                val clicked = findAndClickSearchResultItem(rootNode, config.buildNumberQueryAndKeyword)
                                if (clicked) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Build number search result")
                                    autoToggleStage = AutoToggleStage.ACTIVATE_DEV_MODE_CLICK_BUILD_NUMBER
                                }
                            }

                            AutoToggleStage.ACTIVATE_DEV_MODE_CLICK_BUILD_NUMBER -> {
                                val clicked = findAndClickBuildNumber(rootNode, config.buildNumberQueryAndKeyword)
                                if (clicked || isDevModeActive(context)) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Unlocked Developer Mode (Stage A), advancing to Stage B")
                                    autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                                    launchSearchActivity(context, displayOptions)
                                }
                            }

                            AutoToggleStage.CLICK_SEARCH_BAR -> {
                                val clicked = findAndClickSearchBar(rootNode, config.searchBarKeywords)
                                if (clicked) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Search Bar")
                                    autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                                }
                            }

                            AutoToggleStage.ENTER_SEARCH_QUERY -> {
                                val entered = findAndSetSearchQuery(rootNode, config.wirelessDebuggingQueryAndKeyword)
                                if (entered) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Entered search query '${config.wirelessDebuggingQueryAndKeyword}'")
                                    autoToggleStage = AutoToggleStage.CLICK_SEARCH_RESULT
                                } else {
                                    val clickedBar = findAndClickSearchBar(rootNode, config.searchBarKeywords)
                                    if (clickedBar) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Clicked Search Bar fallback")
                                        autoToggleStage = AutoToggleStage.ENTER_SEARCH_QUERY
                                    }
                                }
                            }

                            AutoToggleStage.CLICK_SEARCH_RESULT -> {
                                val clickedResult = findAndClickSearchResultItem(rootNode, config.wirelessDebuggingQueryAndKeyword)
                                if (clickedResult) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Wireless Debugging search result")
                                    autoToggleStage = AutoToggleStage.TOGGLE_SWITCH
                                }
                            }

                            AutoToggleStage.TOGGLE_SWITCH -> {
                                val toggled = findAndToggleSwitch(rootNode, config.wirelessDebuggingQueryAndKeyword)
                                if (toggled) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Successfully toggled Wireless Debugging switch ON")
                                    val isPairedOnScreen = isMegingiardInPairedDevices(rootNode)
                                    val hasLocalCreds = isDevicePaired(context)
                                    AppLog.i(TAG, "startAutoToggleLoop: isPairedOnScreen=$isPairedOnScreen, hasLocalCreds=$hasLocalCreds")

                                    if (hasLocalCreds && !isPairedOnScreen) {
                                        AppLog.w(
                                            TAG,
                                            "startAutoToggleLoop: Stored credentials present but Megingiard not listed in system paired devices! Clearing stale credentials and triggering pairing.",
                                        )
                                        PrivdBootstrapper.clearCredentials(context)
                                        autoSetupTargetStage = AutoSetupTargetStage.STAGE_C_PAIRING
                                    }

                                    if (autoSetupTargetStage == AutoSetupTargetStage.STAGE_C_PAIRING &&
                                        !isMegingiardInPairedDevices(rootNode)
                                    ) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Advancing to Stage C (Clicking Pair Dialog row)")
                                        autoToggleStage = AutoToggleStage.CLICK_PAIR_DIALOG
                                    } else {
                                        AppLog.i(TAG, "startAutoToggleLoop: Auto-setup pipeline completed successfully, connecting daemon")
                                        autoTogglePendingTimestamp = 0L
                                        serviceScope.launch(Dispatchers.IO) {
                                            PrivdManager.connect(context)
                                        }
                                        break
                                    }
                                }
                            }

                            AutoToggleStage.CLICK_PAIR_DIALOG -> {
                                val clickedPair = findAndClickPairDialog(rootNode, config.pairDeviceKeywords)
                                if (clickedPair) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Pair Dialog row, advancing to SCAN_PAIRING_CODE_AND_PAIR")
                                    autoToggleStage = AutoToggleStage.SCAN_PAIRING_CODE_AND_PAIR
                                } else {
                                    val clickedSubScreen =
                                        findAndClickSearchResultItem(rootNode, config.wirelessDebuggingQueryAndKeyword)
                                    if (clickedSubScreen) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Clicked Wireless Debugging row text to enter sub-screen")
                                    }
                                }
                            }

                            AutoToggleStage.SCAN_PAIRING_CODE_AND_PAIR -> {
                                val allText = scanActiveWindowText(Display.DEFAULT_DISPLAY)
                                val scanResult = PrivdPairScreenTextScanner.parsePairingInfoFromText(allText, config)
                                if (scanResult.isComplete) {
                                    val portInt = scanResult.port?.toIntOrNull()
                                    val codeStr = scanResult.code
                                    if (portInt != null && !codeStr.isNullOrBlank()) {
                                        AppLog.i(
                                            TAG,
                                            "startAutoToggleLoop: Auto-discovered pairing params port=$portInt, code=$codeStr. Triggering PrivdBootstrapper.pair()",
                                        )
                                        serviceScope.launch(Dispatchers.IO) {
                                            val ok = PrivdBootstrapper.pair(context, "127.0.0.1", portInt, codeStr)
                                            if (ok) {
                                                AppLog.i(
                                                    TAG,
                                                    "startAutoToggleLoop: Pairing succeeded! Connecting daemon via PrivdManager.connect()",
                                                )
                                                PrivdManager.connect(context)
                                            }
                                        }
                                        autoTogglePendingTimestamp = 0L
                                        break
                                    }
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

    private fun findAndClickAllowDialogButton(
        node: AccessibilityNodeInfo,
        allowKeywords: List<String>,
    ): Boolean {
        if (node.isCheckable || node.className?.toString()?.contains("CheckBox") == true) {
            return false
        }

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc".lowercase().trim()

        val isMatch =
            allowKeywords.any { kw ->
                val cleanKw = kw.lowercase().trim()
                combined == cleanKw ||
                    combined.startsWith(cleanKw) ||
                    cleanKw in combined.split(Regex("\\s+")) ||
                    text.equals(cleanKw, ignoreCase = true) ||
                    contentDesc.equals(cleanKw, ignoreCase = true)
            } || (viewId.contains("button1") && combined.isNotBlank() && !combined.contains("cancel") && !combined.contains("abbrechen"))

        if (isMatch) {
            val clickableNode = findClickableAncestorOrSelf(node) ?: node
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                AppLog.i(TAG, "findAndClickAllowDialogButton: Clicked network trust dialog positive button '$text' ($viewId)")
                return true
            } else {
                val directSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (directSuccess) {
                    AppLog.i(
                        TAG,
                        "findAndClickAllowDialogButton: Clicked network trust dialog positive button directly '$text' ($viewId)",
                    )
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickAllowDialogButton(child, allowKeywords)) return true
        }
        return false
    }

    private fun findAndClickSearchBar(
        node: AccessibilityNodeInfo,
        searchKeywords: List<String>,
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val isSearchNode =
            viewId.contains("search_action_bar") ||
                viewId.contains("search_bar") ||
                searchKeywords.any { combined.contains(it.lowercase()) }

        if (isSearchNode) {
            val clickableNode = findClickableAncestorOrSelf(node)
            if (clickableNode != null) {
                val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSearchBar(child, searchKeywords)) return true
        }
        return false
    }

    private fun findAndSetSearchQuery(
        node: AccessibilityNodeInfo,
        query: String,
    ): Boolean {
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
                        query,
                    )
                }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                AppLog.i(TAG, "findAndSetSearchQuery: Successfully set text '$query' via ACTION_SET_TEXT on $viewId ($className)")
                return true
            } else {
                AppLog.w(TAG, "findAndSetSearchQuery: ACTION_SET_TEXT returned false on $viewId ($className)")
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndSetSearchQuery(child, query)) return true
        }
        return false
    }

    private fun findAndClickSearchResultItem(
        node: AccessibilityNodeInfo,
        vararg targetKeywords: String,
    ): Boolean {
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
            targetKeywords.any { kw ->
                val cleanKw = kw.lowercase()
                combined.contains(cleanKw) ||
                    combined.contains(cleanKw.replace("-", " ")) ||
                    combined.contains(cleanKw.replace("-", ""))
            }

        if (isMatch) {
            val clickable = findClickableAncestorOrSelf(node)
            if (clickable != null) {
                val clickedId = clickable.viewIdResourceName ?: ""
                if (!clickedId.contains("search_src") && !clickedId.contains("search_view")) {
                    val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        AppLog.i(TAG, "findAndClickSearchResultItem: Successfully clicked search result item for '$text'")
                        return true
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSearchResultItem(child, *targetKeywords)) return true
        }
        return false
    }

    private fun findAndToggleSwitch(
        node: AccessibilityNodeInfo,
        targetKeyword: String,
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val cleanKw = targetKeyword.lowercase()
        val isWirelessText =
            combined.contains(cleanKw) ||
                combined.contains(cleanKw.replace("-", " ")) ||
                combined.contains(cleanKw.replace("-", "")) ||
                combined.contains("wireless debugging") ||
                combined.contains("debugging über wlan")

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
            if (findAndToggleSwitch(child, targetKeyword)) return true
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

    private fun findAndClickBuildNumber(
        node: AccessibilityNodeInfo,
        targetKeyword: String,
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val cleanKw = targetKeyword.lowercase()
        val isBuildNumber =
            combined.contains(cleanKw) ||
                combined.contains(cleanKw.replace("-", " ")) ||
                combined.contains(cleanKw.replace("-", "")) ||
                viewId.contains("build_number")

        if (isBuildNumber) {
            val clickable = findClickableAncestorOrSelf(node) ?: node
            AppLog.i(TAG, "findAndClickBuildNumber: Found Build Number item, clicking 7 times")
            for (k in 0..6) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickBuildNumber(child, targetKeyword)) return true
        }
        return false
    }

    private fun isMegingiardInPairedDevices(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val combined = "$text $contentDesc".lowercase()

        if (combined.contains("megingiard")) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isMegingiardInPairedDevices(child)) return true
        }
        return false
    }

    private fun findAndClickPairDialog(
        node: AccessibilityNodeInfo,
        pairKeywords: List<String>,
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        val isPairItem =
            pairKeywords.any { kw ->
                combined.contains(kw.lowercase())
            }

        if (isPairItem) {
            val clickable = findClickableAncestorOrSelf(node) ?: node
            AppLog.i(TAG, "findAndClickPairDialog: Found pair dialog row, clicking")
            return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickPairDialog(child, pairKeywords)) return true
        }
        return false
    }

    private fun collectAllText(
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
            collectAllText(child, sb)
        }
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

    override fun onUnbind(intent: Intent?): Boolean {
        AppLog.w(TAG, "onUnbind: Megingiard Accessibility Service disabled")
        if (instance == this) instance = null
        AppStateManager.setAccessibilityActive(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (instance == this) instance = null
        AppLog.i(TAG, "onDestroy: Accessibility Service destroyed")
        AppStateManager.setAccessibilityActive(false)
    }

    companion object {
        private var instance: MegingiardAccessibilityService? = null
        private var autoTogglePendingTimestamp = 0L
        private const val AUTO_TOGGLE_TIMEOUT_MS = 15000L

        /**
         * Returns true if the service instance is active and connected.
         */
        fun isInstanceActive(): Boolean = instance != null

        fun isWifiActive(context: Context): Boolean =
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiManager?.isWifiEnabled == true
            } catch (e: Exception) {
                try {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.WIFI_ON, 0) != 0
                } catch (e2: Exception) {
                    false
                }
            }

        fun isDevModeActive(context: Context): Boolean =
            try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
            } catch (e: Exception) {
                false
            }

        fun isWirelessDebuggingActive(context: Context): Boolean =
            try {
                Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0
            } catch (e: Exception) {
                false
            }

        fun isDevicePaired(context: Context): Boolean =
            PrivdBootstrapper.hasCredentials(context) && PrivdManager.state.value != PrivdState.FAILED

        fun dismissNotificationShade(): Boolean {
            val inst = instance ?: return false
            val shadeDismissed = inst.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            try {
                val rootNode = inst.rootInActiveWindow
                val pkgName = rootNode?.packageName?.toString()
                if (pkgName == "com.android.systemui" || pkgName?.contains("panel") == true) {
                    AppLog.i(TAG, "dismissNotificationShade: System UI / Internet dialog active ($pkgName), issuing GLOBAL_ACTION_BACK")
                    inst.performGlobalAction(GLOBAL_ACTION_BACK)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "dismissNotificationShade: Exception checking active window node: ${e.message}")
            }
            return shadeDismissed
        }

        fun triggerWirelessDebuggingAutoToggle(context: Context) = startMultiStageAutoSetup(context)

        /**
         * Triggers multi-stage automated setup (Stage A: Dev Mode, Stage B: Wireless Debugging, Stage C: Pairing)
         * based on current device starting conditions.
         */
        fun startMultiStageAutoSetup(context: Context) {
            val displayOptions = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

            if (!isEnabled(context)) {
                AppLog.w(TAG, "startMultiStageAutoSetup: Accessibility Service is not enabled")
                Toast.makeText(context, R.string.privd_toast_accessibility_required, Toast.LENGTH_LONG).show()
                val accessibilityIntent =
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                context.startActivity(accessibilityIntent, displayOptions)
                return
            }

            AppLog.i(TAG, "startMultiStageAutoSetup: Dismissing Quick Settings / Notification shade if expanded")
            dismissNotificationShade()

            if (!isWifiActive(context)) {
                AppLog.w(TAG, "startMultiStageAutoSetup: Wi-Fi is not active")
                Toast.makeText(context, R.string.onboarding_privd_wifi_warning, Toast.LENGTH_LONG).show()
                val wifiIntent =
                    Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                try {
                    context.startActivity(wifiIntent, displayOptions)
                } catch (e: Exception) {
                    val wirelessIntent =
                        Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    context.startActivity(wirelessIntent, displayOptions)
                }
                return
            }

            val devModeActive = isDevModeActive(context)
            val wirelessActive = isWirelessDebuggingActive(context)
            val paired = isDevicePaired(context)

            AppLog.i(TAG, "startMultiStageAutoSetup: devMode=$devModeActive, wirelessActive=$wirelessActive, paired=$paired")

            if (devModeActive && wirelessActive && paired) {
                if (PrivdManager.state.value != PrivdState.RUNNING) {
                    AppLog.i(TAG, "startMultiStageAutoSetup: Prerequisites active, attempting PrivdManager.connect()")
                    instance?.serviceScope?.launch(Dispatchers.IO) {
                        val ok = PrivdManager.connect(context)
                        if (!ok) {
                            AppLog.w(
                                TAG,
                                "startMultiStageAutoSetup: Connect/bootstrap failed despite saved credentials. Falling back to Stage C (Pairing).",
                            )
                            withContext(Dispatchers.Main) {
                                instance?.autoSetupTargetStage = AutoSetupTargetStage.STAGE_C_PAIRING
                                instance?.autoToggleStage = AutoToggleStage.CLICK_PAIR_DIALOG
                                autoTogglePendingTimestamp = System.currentTimeMillis()
                                instance?.startAutoToggleLoop()
                                launchWirelessDebuggingSettings(context, displayOptions)
                            }
                        }
                    }
                    return
                }
                Toast.makeText(context, R.string.privd_toast_all_set, Toast.LENGTH_LONG).show()
                return
            }

            val targetStage =
                when {
                    !devModeActive -> AutoSetupTargetStage.STAGE_C_PAIRING
                    !wirelessActive && !paired -> AutoSetupTargetStage.STAGE_C_PAIRING
                    !wirelessActive && paired -> AutoSetupTargetStage.STAGE_B_WIRELESS_DEBUG
                    else -> AutoSetupTargetStage.STAGE_C_PAIRING
                }

            val initialStage =
                when {
                    !devModeActive -> AutoToggleStage.ACTIVATE_DEV_MODE_SEARCH_BUILD_NUMBER
                    !wirelessActive -> AutoToggleStage.ENTER_SEARCH_QUERY
                    else -> AutoToggleStage.CLICK_PAIR_DIALOG
                }

            instance?.autoSetupTargetStage = targetStage
            instance?.autoToggleStage = initialStage
            autoTogglePendingTimestamp = System.currentTimeMillis()

            instance?.startAutoToggleLoop()

            when (initialStage) {
                AutoToggleStage.ACTIVATE_DEV_MODE_SEARCH_BUILD_NUMBER -> {
                    launchSearchActivity(context, displayOptions)
                }

                AutoToggleStage.ENTER_SEARCH_QUERY -> {
                    launchSearchActivity(context, displayOptions)
                }

                AutoToggleStage.CLICK_PAIR_DIALOG -> {
                    launchWirelessDebuggingSettings(context, displayOptions)
                }

                else -> {}
            }
        }

        private fun launchSearchActivity(
            context: Context,
            displayOptions: Bundle,
        ) {
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

        private fun launchWirelessDebuggingSettings(
            context: Context,
            displayOptions: Bundle,
        ) {
            val wirelessDebuggingIntent =
                Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            val devSettingsIntent =
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            try {
                context.startActivity(wirelessDebuggingIntent, displayOptions)
            } catch (e: Exception) {
                try {
                    context.startActivity(devSettingsIntent, displayOptions)
                } catch (e2: Exception) {
                    AppLog.e(TAG, "Failed to launch Wireless Debugging settings: $e2")
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
