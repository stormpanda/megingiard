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
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdPairScreenTextScanner
import com.stormpanda.megingiard.privd.PrivdState
import org.lsposed.hiddenapibypass.HiddenApiBypass
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
private const val AUTO_SETUP_WARMUP_DELAY_MS = 400L
private const val DEV_MODE_CLICK_COUNT = 7
private const val POST_PAIRING_STABILIZATION_DELAY_MS = 1500L

private enum class AutoSetupTargetStage {
    STAGE_B_WIRELESS_DEBUG,
    STAGE_C_PAIRING,
}

private enum class AutoToggleStage {
    ACTIVATE_DEV_MODE,
    TOGGLE_USB_DEBUG,
    TOGGLE_WIRELESS_DEBUG,
    CLICK_PAIR_DIALOG,
    SCAN_PAIRING_CODE_AND_PAIR,
    POST_PAIRING_STABILIZATION,
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
    private var autoToggleStage = AutoToggleStage.TOGGLE_WIRELESS_DEBUG
    private var autoSetupTargetStage = AutoSetupTargetStage.STAGE_C_PAIRING

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions("L")
            }
        }
        instance = this
        AppLog.i(TAG, "onServiceConnected: Megingiard Accessibility Service is active")
        AppStateManager.setAccessibilityActive(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val displayId = event.displayId
            if (displayId == Display.DEFAULT_DISPLAY || displayId == Display.INVALID_DISPLAY) {
                val packageName = event.packageName?.toString()
                if (!packageName.isNullOrBlank()) {
                    AppLog.d(TAG, "onAccessibilityEvent: Window state changed on primary display ($displayId), package=$packageName")
                    AutoSwitchCoordinator.onPackageChanged(packageName)
                }
            } else {
                AppLog.d(
                    TAG,
                    "onAccessibilityEvent: Ignoring window state change on secondary display (displayId=$displayId, package=${event.packageName})",
                )
            }
        }
        handleAutoToggleEvent(event)
    }

    private fun handleAutoToggleEvent(event: AccessibilityEvent) {
        if (!_isAutoSetupActive) {
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
                    "startAutoToggleLoop: Starting deep-link based auto-toggle loop in stage $autoToggleStage using language '${config.languageCode}'",
                )
                var attempts = 0
                var lastStage = autoToggleStage
                val context = applicationContext
                val displayOptions = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

                while (isActive && attempts < AUTO_TOGGLE_MAX_ATTEMPTS && _isAutoSetupActive) {
                    val rootNode = getRootNodeForDisplay(Display.DEFAULT_DISPLAY)
                    if (rootNode != null) {
                        val clickedAllow = findAndClickAllowDialogButton(rootNode, config.allowButtonKeywords)
                        if (clickedAllow) {
                            AppLog.i(TAG, "startAutoToggleLoop: Confirmed Wireless Debugging network trust dialog")
                        }

                        when (autoToggleStage) {
                            AutoToggleStage.ACTIVATE_DEV_MODE -> {
                                if (isDevModeActive(context)) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Developer Mode is active. Transitioning to TOGGLE_USB_DEBUG")
                                    autoToggleStage = AutoToggleStage.TOGGLE_USB_DEBUG
                                    launchSettingsScreen(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, displayOptions)
                                } else {
                                    val clicked = findAndClickBuildNumber(rootNode, config.buildNumberQueryAndKeyword)
                                    if (clicked) {
                                        AppLog.d(TAG, "startAutoToggleLoop: Clicked build number. Waiting for active verification.")
                                    } else {
                                        AppLog.d(TAG, "startAutoToggleLoop: Build number not visible, scrolling forward.")
                                        performScrollForward(rootNode)
                                    }
                                }
                            }

                            AutoToggleStage.TOGGLE_USB_DEBUG -> {
                                if (isUsbDebuggingActive(context)) {
                                    AppLog.i(TAG, "startAutoToggleLoop: USB Debugging is active. Transitioning to TOGGLE_WIRELESS_DEBUG")
                                    autoToggleStage = AutoToggleStage.TOGGLE_WIRELESS_DEBUG
                                    launchSettingsScreen(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, displayOptions)
                                } else {
                                    val toggled = findAndToggleSwitch(rootNode, config.usbDebuggingQueryAndKeyword)
                                    if (toggled) {
                                        AppLog.d(TAG, "startAutoToggleLoop: Issued toggle for USB Debugging.")
                                    } else {
                                        AppLog.d(TAG, "startAutoToggleLoop: USB Debugging switch not visible, scrolling forward.")
                                        performScrollForward(rootNode)
                                    }
                                }
                            }

                            AutoToggleStage.TOGGLE_WIRELESS_DEBUG -> {
                                val isWirelessOn = isWirelessDebuggingActive(context)
                                val hasLocalCreds = PrivdBootstrapper.hasCredentials(context)

                                val isSubScreen = isWirelessDebuggingSubScreen(rootNode, config)

                                if (isSubScreen) {
                                    if (isWirelessOn) {
                                        val screenText = scanActiveWindowText(Display.DEFAULT_DISPLAY)
                                        val connectPort = PrivdPairScreenTextScanner.parseConnectPortFromText(screenText, config)
                                        if (connectPort > 0) {
                                            PrivdBootstrapper.setScreenConnectPort(connectPort)
                                        }

                                        if (hasLocalCreds) {
                                            AppLog.i(
                                                TAG,
                                                "startAutoToggleLoop: Wireless Debugging active and stored credentials exist. Attempting connection first...",
                                            )
                                            val ok =
                                                withContext(Dispatchers.IO) {
                                                    PrivdManager.connect(context)
                                                }
                                            if (ok) {
                                                AppLog.i(TAG, "startAutoToggleLoop: Connection succeeded using stored credentials!")
                                                _isAutoSetupActive = false
                                                withContext(Dispatchers.Main) {
                                                    restoreTopScreenApp(context)
                                                }
                                                break
                                            } else {
                                                val lastError = PrivdManager.lastError.value
                                                if (lastError == PrivdError.ADB_PAIRING_REQUIRED) {
                                                    AppLog.w(
                                                        TAG,
                                                        "startAutoToggleLoop: Connection/bootstrap failed with stored credentials because pairing is required. Clearing credentials and falling back to pairing code.",
                                                    )
                                                    PrivdBootstrapper.clearCredentials(context)
                                                    autoToggleStage = AutoToggleStage.CLICK_PAIR_DIALOG
                                                } else {
                                                    AppLog.w(
                                                        TAG,
                                                        "startAutoToggleLoop: Connection/bootstrap failed with stored credentials (non-auth error: $lastError). Retrying connection next tick without clearing credentials.",
                                                    )
                                                    // Retain credentials, loop will retry and scan the fresh port automatically
                                                }
                                            }
                                        } else {
                                            AppLog.i(
                                                TAG,
                                                "startAutoToggleLoop: Wireless Debugging active but unpaired (no credentials), advancing to CLICK_PAIR_DIALOG",
                                            )
                                            autoToggleStage = AutoToggleStage.CLICK_PAIR_DIALOG
                                        }
                                    } else {
                                        val toggled = findAndToggleMainSwitchOnSubScreen(rootNode)
                                        if (toggled) {
                                            AppLog.d(TAG, "startAutoToggleLoop: Issued toggle for Wireless Debugging main switch.")
                                        }
                                    }
                                } else {
                                    val clickedRow = findAndClickPreferenceRow(rootNode, config.wirelessDebuggingQueryAndKeyword)
                                    if (clickedRow) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Clicked Wireless Debugging row to enter sub-screen")
                                    } else {
                                        AppLog.d(
                                            TAG,
                                            "startAutoToggleLoop: Wireless Debugging preference row not visible, scrolling forward.",
                                        )
                                        performScrollForward(rootNode)
                                    }
                                }
                            }

                            AutoToggleStage.CLICK_PAIR_DIALOG -> {
                                val screenText = scanActiveWindowText(Display.DEFAULT_DISPLAY)
                                val connectPort = PrivdPairScreenTextScanner.parseConnectPortFromText(screenText, config)
                                if (connectPort > 0) {
                                    PrivdBootstrapper.setScreenConnectPort(connectPort)
                                }

                                val clickedPair = findAndClickPairDialog(rootNode, config.pairDeviceKeywords)
                                if (clickedPair) {
                                    AppLog.i(TAG, "startAutoToggleLoop: Clicked Pair Dialog row, advancing to SCAN_PAIRING_CODE_AND_PAIR")
                                    autoToggleStage = AutoToggleStage.SCAN_PAIRING_CODE_AND_PAIR
                                } else {
                                    AppLog.d(TAG, "startAutoToggleLoop: Pair Dialog row not visible, scrolling forward.")
                                    performScrollForward(rootNode)
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
                                        val ok =
                                            withContext(Dispatchers.IO) {
                                                PrivdBootstrapper.pair(context, "127.0.0.1", portInt, codeStr)
                                            }
                                        if (ok) {
                                            AppLog.i(
                                                TAG,
                                                "startAutoToggleLoop: Pairing succeeded! Transitioning to POST_PAIRING_STABILIZATION",
                                            )
                                            autoToggleStage = AutoToggleStage.POST_PAIRING_STABILIZATION
                                        } else {
                                            AppLog.w(TAG, "startAutoToggleLoop: Pairing failed.")
                                            _isAutoSetupActive = false
                                            break
                                        }
                                    }
                                }
                            }

                            AutoToggleStage.POST_PAIRING_STABILIZATION -> {
                                val allText = scanActiveWindowText(Display.DEFAULT_DISPLAY)
                                if (!PrivdPairScreenTextScanner.hasPairingCode(allText)) {
                                    AppLog.i(
                                        TAG,
                                        "startAutoToggleLoop: Pairing dialog dismissed. Waiting 1.5s for adbd key stabilization...",
                                    )
                                    delay(POST_PAIRING_STABILIZATION_DELAY_MS) // Wait for adbd to write & reload keys

                                    // Rescan the main Wireless Debugging screen for the connect port
                                    val screenText = scanActiveWindowText(Display.DEFAULT_DISPLAY)
                                    val connectPort = PrivdPairScreenTextScanner.parseConnectPortFromText(screenText, config)
                                    if (connectPort > 0) {
                                        AppLog.i(TAG, "startAutoToggleLoop: Scanned connect port after pairing: $connectPort")
                                        PrivdBootstrapper.setScreenConnectPort(connectPort)
                                    } else {
                                        AppLog.w(TAG, "startAutoToggleLoop: Failed to scan connect port after pairing.")
                                    }

                                    // Attempt connection
                                    AppLog.i(TAG, "startAutoToggleLoop: Connecting daemon via PrivdManager.connect()")
                                    val connected =
                                        withContext(Dispatchers.IO) {
                                            PrivdManager.connect(context)
                                        }
                                    AppLog.i(TAG, "startAutoToggleLoop: Connection after pairing result: $connected")
                                    _isAutoSetupActive = false
                                    if (connected) {
                                        withContext(Dispatchers.Main) {
                                            restoreTopScreenApp(context)
                                        }
                                    }
                                    break
                                } else {
                                    AppLog.d(TAG, "startAutoToggleLoop: Waiting for pairing dialog to dismiss...")
                                }
                            }
                        }
                    }
                    if (autoToggleStage != lastStage) {
                        attempts = 0
                        lastStage = autoToggleStage
                    } else {
                        attempts++
                    }
                    delay(AUTO_TOGGLE_STEP_DELAY_MS)
                }
                if (_isAutoSetupActive) {
                    AppLog.w(TAG, "startAutoToggleLoop: Timed out in stage $autoToggleStage after $AUTO_TOGGLE_MAX_ATTEMPTS attempts")
                    _isAutoSetupActive = false
                }
            }
    }

    private fun findAndClickAllowDialogButton(
        rootNode: AccessibilityNodeInfo,
        allowKeywords: List<String>,
    ): Boolean {
        val buttonNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1") ?: emptyList()
        val settingsButtonNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/button1") ?: emptyList()
        val allButtons = (buttonNodes + settingsButtonNodes).distinct()

        for (btn in allButtons) {
            val text = btn.text?.toString() ?: ""
            val contentDesc = btn.contentDescription?.toString() ?: ""
            val combined = "$text $contentDesc".lowercase().trim()

            val isMatch =
                allowKeywords.any { kw ->
                    val cleanKw = kw.lowercase().trim()
                    combined.contains(cleanKw) || text.equals(cleanKw, ignoreCase = true)
                } || (combined.isNotBlank() && !combined.contains("cancel") && !combined.contains("abbrechen"))

            if (isMatch) {
                val clickable = findClickableAncestorOrSelf(btn) ?: btn
                clickable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    AppLog.i(TAG, "findAndClickAllowDialogButton: Clicked network trust dialog button via ID: '$text'")
                    return true
                }
            }
        }

        return findAndClickAllowDialogButtonRecursive(rootNode, allowKeywords)
    }

    private fun findAndClickAllowDialogButtonRecursive(
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
                combined == cleanKw || text.equals(cleanKw, ignoreCase = true)
            } || (viewId.contains("button1") && combined.isNotBlank() && !combined.contains("cancel") && !combined.contains("abbrechen"))

        if (isMatch) {
            val clickable = findClickableAncestorOrSelf(node) ?: node
            val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickAllowDialogButtonRecursive(child, allowKeywords)) return true
        }
        return false
    }

    private fun findAndToggleSwitch(
        rootNode: AccessibilityNodeInfo,
        targetKeyword: String,
    ): Boolean {
        // Special fallback for main switch bar on sub-screens (Wireless Debugging page)
        val mainSwitchNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/main_switch") ?: emptyList()
        val switchBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/main_switch_bar") ?: emptyList()
        val allMainSwitches = (mainSwitchNodes + switchBarNodes).distinct()
        for (ms in allMainSwitches) {
            val switchNode = findSwitchOrCheckable(ms) ?: ms
            if (!switchNode.isChecked) {
                AppLog.i(TAG, "findAndToggleSwitch: Found main switch bar. Toggling ON.")
                return switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                AppLog.d(TAG, "findAndToggleSwitch: Main switch bar is already ON")
                return true
            }
        }

        val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/title") ?: emptyList()
        val settingsTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/title") ?: emptyList()
        val allTitles = (titleNodes + settingsTitleNodes).distinct()

        val cleanKw = targetKeyword.lowercase()

        for (titleNode in allTitles) {
            val titleText = titleNode.text?.toString() ?: ""
            val matches =
                titleText.lowercase().contains(cleanKw) ||
                    titleText.lowercase().replace("-", " ").contains(cleanKw.replace("-", " ")) ||
                    titleText.lowercase().replace("-", "").contains(cleanKw.replace("-", ""))

            if (matches) {
                val parentRow = titleNode.parent ?: continue
                val switchIds =
                    listOf(
                        "android:id/switch_widget",
                        "com.android.settings:id/switch_widget",
                        "com.android.settings:id/main_switch",
                    )
                var switchNode: AccessibilityNodeInfo? = null
                for (sid in switchIds) {
                    val found = parentRow.findAccessibilityNodeInfosByViewId(sid) ?: continue
                    if (found.isNotEmpty()) {
                        switchNode = found.first()
                        break
                    }
                }

                if (switchNode == null) {
                    switchNode = findSwitchOrCheckable(parentRow)
                }

                if (switchNode != null) {
                    if (!switchNode.isChecked) {
                        AppLog.i(TAG, "findAndToggleSwitch: Target '$targetKeyword' is OFF, toggling switch")
                        val rowClickable =
                            findClickableAncestorOrSelf(titleNode)
                                ?: findClickableAncestorOrSelf(parentRow)
                                ?: switchNode
                        var clicked = rowClickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!clicked) {
                            clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        return clicked
                    } else {
                        AppLog.d(TAG, "findAndToggleSwitch: Switch for '$targetKeyword' is already ON")
                        return true
                    }
                }
            }
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
        return null
    }

    private fun findAndClickBuildNumber(
        rootNode: AccessibilityNodeInfo,
        targetKeyword: String,
    ): Boolean {
        val buildNumberIds =
            listOf(
                "com.android.settings:id/build_number",
                "android:id/title",
            )

        for (bid in buildNumberIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(bid) ?: continue
            for (node in nodes) {
                val text = node.text?.toString() ?: ""
                val matches =
                    text.contains(targetKeyword, ignoreCase = true) ||
                        node.viewIdResourceName?.contains("build_number") == true

                if (matches) {
                    val clickable = findClickableAncestorOrSelf(node) ?: node
                    AppLog.i(TAG, "findAndClickBuildNumber: Found build number row ('$text'), clicking $DEV_MODE_CLICK_COUNT times")
                    repeat(DEV_MODE_CLICK_COUNT) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    return true
                }
            }
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
        rootNode: AccessibilityNodeInfo,
        pairKeywords: List<String>,
    ): Boolean {
        // 1. Language-agnostic Resource ID lookup (AOSP Settings resource IDs for Wireless ADB pairing preference)
        val knownPairingResourceIds =
            listOf(
                "com.android.settings:id/adb_pair_choice",
                "com.android.settings:id/pair_with_code",
                "com.android.settings:id/adb_pair_code",
                "com.android.settings:id/adb_pairing_code",
                "com.android.settings:id/adb_pair_with_code_pref",
                "com.android.settings:id/adb_pair_by_code_preference",
            )

        for (resId in knownPairingResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId) ?: emptyList()
            for (node in nodes) {
                val clickable = findClickableAncestorOrSelf(node) ?: node
                AppLog.i(TAG, "findAndClickPairDialog: Found pair dialog row via resource ID '$resId', clicking")
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }

        // 2. Language-agnostic Resource ID pattern matching in node tree
        val resIdMatchNode = findNodeByResourceIdPattern(rootNode)
        if (resIdMatchNode != null) {
            val clickable = findClickableAncestorOrSelf(resIdMatchNode) ?: resIdMatchNode
            AppLog.i(
                TAG,
                "findAndClickPairDialog: Found pair dialog row via resource ID pattern '${resIdMatchNode.viewIdResourceName}', clicking",
            )
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        // 3. Fallback: Keyword matching (multi-language fallback)
        val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/title") ?: emptyList()
        val settingsTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/title") ?: emptyList()
        val allTitles = (titleNodes + settingsTitleNodes).distinct()

        for (titleNode in allTitles) {
            val titleText = titleNode.text?.toString() ?: ""
            val isPairItem = pairKeywords.any { kw -> titleText.contains(kw, ignoreCase = true) }

            if (isPairItem) {
                val clickable = findClickableAncestorOrSelf(titleNode) ?: titleNode
                AppLog.i(TAG, "findAndClickPairDialog: Found pair dialog row via keyword ('$titleText'), clicking")
                return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    private fun findNodeByResourceIdPattern(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val resId = node.viewIdResourceName?.lowercase() ?: ""
        if (resId.contains("adb_pair") || resId.contains("pair_code") || resId.contains("pair_choice") || resId.contains("pairing_code")) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByResourceIdPattern(child)
            if (found != null) return found
        }
        return null
    }

    private fun performScrollForward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (success) {
                AppLog.d(TAG, "performScrollForward: Scrolled container successfully")
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (performScrollForward(child)) return true
        }
        return false
    }

    private fun pairKeywordsMatchPresent(
        rootNode: AccessibilityNodeInfo,
        pairKeywords: List<String>,
    ): Boolean {
        val knownPairingResourceIds =
            listOf(
                "com.android.settings:id/adb_pair_choice",
                "com.android.settings:id/pair_with_code",
                "com.android.settings:id/adb_pair_code",
                "com.android.settings:id/adb_pairing_code",
                "com.android.settings:id/adb_pair_with_code_pref",
                "com.android.settings:id/adb_pair_by_code_preference",
            )
        for (resId in knownPairingResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId) ?: emptyList()
            if (nodes.isNotEmpty()) return true
        }
        if (findNodeByResourceIdPattern(rootNode) != null) return true

        val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/title") ?: emptyList()
        val settingsTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/title") ?: emptyList()
        val allTitles = (titleNodes + settingsTitleNodes).distinct()

        for (titleNode in allTitles) {
            val titleText = titleNode.text?.toString() ?: ""
            if (pairKeywords.any { kw -> titleText.contains(kw, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private fun findAndClickPreferenceRow(
        rootNode: AccessibilityNodeInfo,
        targetKeyword: String,
    ): Boolean {
        val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/title") ?: emptyList()
        val settingsTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/title") ?: emptyList()
        val allTitles = (titleNodes + settingsTitleNodes).distinct()

        for (titleNode in allTitles) {
            val titleText = titleNode.text?.toString() ?: ""
            if (titleText.contains(targetKeyword, ignoreCase = true)) {
                val clickable = findClickableAncestorOrSelf(titleNode) ?: titleNode
                return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    private fun isWirelessDebuggingSubScreen(
        rootNode: AccessibilityNodeInfo,
        config: AutoSetupLanguageConfig,
    ): Boolean {
        val sb = StringBuilder()
        collectAllText(rootNode, sb)
        val allText = sb.toString().lowercase()

        val devOptionsKeywords = config.developerOptionsKeywords
        val isDevOptionsPresent = devOptionsKeywords.any { kw -> allText.contains(kw.lowercase()) }

        val isWirelessDebuggingPresent = allText.contains(config.wirelessDebuggingQueryAndKeyword.lowercase())

        return isWirelessDebuggingPresent && !isDevOptionsPresent
    }

    private fun findAndToggleMainSwitchOnSubScreen(rootNode: AccessibilityNodeInfo): Boolean {
        val mainSwitches = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/main_switch") ?: emptyList()
        val settingsSwitches = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget") ?: emptyList()
        val genericSwitches = rootNode.findAccessibilityNodeInfosByViewId("android:id/switch_widget") ?: emptyList()
        val allSwitches = (mainSwitches + settingsSwitches + genericSwitches).distinct()

        for (switchNode in allSwitches) {
            if (switchNode.isCheckable) {
                if (!switchNode.isChecked) {
                    AppLog.i(TAG, "findAndToggleMainSwitchOnSubScreen: Main switch is OFF, toggling ON")
                    val target = findClickableAncestorOrSelf(switchNode) ?: switchNode
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    AppLog.i(TAG, "findAndToggleMainSwitchOnSubScreen: Main switch is already ON")
                    return true
                }
            }
            for (i in 0 until switchNode.childCount) {
                val child = switchNode.getChild(i) ?: continue
                if (child.isCheckable) {
                    if (!child.isChecked) {
                        AppLog.i(TAG, "findAndToggleMainSwitchOnSubScreen: Child switch is OFF, toggling ON")
                        val target = findClickableAncestorOrSelf(child) ?: child
                        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        AppLog.i(TAG, "findAndToggleMainSwitchOnSubScreen: Child switch is already ON")
                        return true
                    }
                }
            }
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
        private var _isAutoSetupActive = false
        private var appToRestoreAfterSetup: String? = null

        val isAutoSetupActive: Boolean
            get() = _isAutoSetupActive

        /**
         * Returns true if the service instance is active and connected.
         */
        fun isInstanceActive(): Boolean = instance != null

        /**
         * Returns the active service instance, or null if not bound.
         */
        fun getInstance(): MegingiardAccessibilityService? = instance

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

        fun isUsbDebuggingActive(context: Context): Boolean =
            try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
            } catch (e: Exception) {
                false
            }

        fun isDevicePaired(context: Context): Boolean = PrivdBootstrapper.hasCredentials(context)

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

        fun performBackAction(): Boolean {
            val inst = instance ?: return false
            AppLog.d(TAG, "performBackAction: issuing GLOBAL_ACTION_BACK")
            return inst.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun clickPairDialogRow(
            pairKeywords: List<String> = AutoSetupLanguageConfig.fromLocale(java.util.Locale.getDefault()).pairDeviceKeywords,
        ): Boolean {
            val inst = instance ?: return false
            val rootNode = inst.rootInActiveWindow ?: return false
            AppLog.d(TAG, "clickPairDialogRow: attempting to click pair dialog row")
            if (inst.findAndClickPairDialog(rootNode, pairKeywords)) {
                return true
            }
            AppLog.d(TAG, "clickPairDialogRow: pair dialog row not visible, scrolling forward...")
            inst.performScrollForward(rootNode)
            val scrolledRoot = inst.rootInActiveWindow ?: rootNode
            return inst.findAndClickPairDialog(scrolledRoot, pairKeywords)
        }

        fun triggerWirelessDebuggingAutoToggle(context: Context) = startMultiStageAutoSetup(context)

        /**
         * Triggers multi-stage automated setup (Stage A: Dev Mode, Stage B: Wireless Debugging & USB Debugging, Stage C: Pairing)
         * based on current device starting conditions.
         */
        fun startMultiStageAutoSetup(context: Context) {
            val currentForeground = AutoSwitchCoordinator.foregroundApp.value
            if (currentForeground != null &&
                currentForeground != "com.android.settings" &&
                currentForeground != "com.android.settings.intelligence" &&
                !currentForeground.contains("com.stormpanda.megingiard")
            ) {
                appToRestoreAfterSetup = currentForeground
                AppLog.i(TAG, "startMultiStageAutoSetup: Remembering top screen app to restore: $appToRestoreAfterSetup")
            } else {
                appToRestoreAfterSetup = null
                AppLog.i(TAG, "startMultiStageAutoSetup: No restorable top screen app running (foreground app: $currentForeground)")
            }

            val displayOptions = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

            val inst = instance
            if (inst == null || !isEnabled(context)) {
                AppLog.w(TAG, "startMultiStageAutoSetup: Accessibility Service is not active or enabled")
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

            _isAutoSetupActive = true

            val devModeActive = isDevModeActive(context)
            val usbActive = isUsbDebuggingActive(context)
            val wirelessActive = isWirelessDebuggingActive(context)
            val paired = isDevicePaired(context)

            AppLog.i(
                TAG,
                "startMultiStageAutoSetup: devMode=$devModeActive, usbActive=$usbActive, wirelessActive=$wirelessActive, paired=$paired",
            )

            if (devModeActive && usbActive && wirelessActive && paired) {
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
                                inst.autoSetupTargetStage = AutoSetupTargetStage.STAGE_C_PAIRING
                                inst.autoToggleStage = AutoToggleStage.TOGGLE_WIRELESS_DEBUG
                                _isAutoSetupActive = true
                                launchSettingsScreenWarmedUp(
                                    context,
                                    Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                                    displayOptions,
                                    inst.serviceScope,
                                ) {
                                    inst.startAutoToggleLoop()
                                }
                            }
                        } else {
                            _isAutoSetupActive = false
                            withContext(Dispatchers.Main) {
                                restoreTopScreenApp(context)
                            }
                        }
                    }
                    return
                }
                Toast.makeText(context, R.string.privd_toast_all_set, Toast.LENGTH_LONG).show()
                _isAutoSetupActive = false
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
                    !devModeActive -> AutoToggleStage.ACTIVATE_DEV_MODE
                    !usbActive -> AutoToggleStage.TOGGLE_USB_DEBUG
                    else -> AutoToggleStage.TOGGLE_WIRELESS_DEBUG
                }

            inst.autoSetupTargetStage = targetStage
            inst.autoToggleStage = initialStage
            _isAutoSetupActive = true

            val actionToLaunch =
                when (initialStage) {
                    AutoToggleStage.ACTIVATE_DEV_MODE -> Settings.ACTION_DEVICE_INFO_SETTINGS
                    AutoToggleStage.TOGGLE_USB_DEBUG -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                    AutoToggleStage.TOGGLE_WIRELESS_DEBUG -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                    else -> null
                }

            if (actionToLaunch != null) {
                launchSettingsScreenWarmedUp(
                    context,
                    actionToLaunch,
                    displayOptions,
                    inst.serviceScope,
                ) {
                    inst.startAutoToggleLoop()
                }
            } else {
                inst.startAutoToggleLoop()
            }
        }

        private fun launchSettingsScreen(
            context: Context,
            action: String,
            displayOptions: Bundle,
        ) {
            val intents =
                listOf(
                    Intent(action),
                    Intent(Settings.ACTION_SETTINGS),
                )

            for (intent in intents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try {
                    AppLog.d(TAG, "launchSettingsScreen: Attempting to launch intent: $intent")
                    context.startActivity(intent, displayOptions)
                    return
                } catch (e: Exception) {
                    AppLog.w(TAG, "launchSettingsScreen: Failed to launch intent '$intent': ${e.message}")
                }
            }
            AppLog.e(TAG, "launchSettingsScreen: All settings intents failed to launch.")
        }

        private fun launchSettingsScreenWarmedUp(
            context: Context,
            targetAction: String,
            displayOptions: Bundle,
            scope: CoroutineScope,
            onDone: () -> Unit,
        ) {
            scope.launch(Dispatchers.Main) {
                AppLog.d(TAG, "launchSettingsScreenWarmedUp: Warming up Settings task stack with general settings")
                launchSettingsScreen(context, Settings.ACTION_SETTINGS, displayOptions)
                delay(AUTO_SETUP_WARMUP_DELAY_MS)
                AppLog.d(TAG, "launchSettingsScreenWarmedUp: Launching target Settings deep-link: $targetAction")
                launchSettingsScreen(context, targetAction, displayOptions)
                onDone()
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

        private fun restoreTopScreenApp(context: Context) {
            val pkg = appToRestoreAfterSetup
            appToRestoreAfterSetup = null
            try {
                if (!pkg.isNullOrBlank()) {
                    AppLog.i(TAG, "restoreTopScreenApp: Attempting to reopen $pkg on the top screen")
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        val options =
                            ActivityOptions.makeBasic().apply {
                                setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                            }
                        context.startActivity(intent, options.toBundle())
                        AppLog.i(TAG, "restoreTopScreenApp: Reopened $pkg successfully on display ${Display.DEFAULT_DISPLAY}")
                    } else {
                        AppLog.w(TAG, "restoreTopScreenApp: No launch intent found for package $pkg, falling back to home screen")
                        goHomeOnTopScreen(context)
                    }
                } else {
                    AppLog.i(
                        TAG,
                        "restoreTopScreenApp: No top screen app remembered, returning to home screen on display ${Display.DEFAULT_DISPLAY}",
                    )
                    goHomeOnTopScreen(context)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "restoreTopScreenApp: Failed to restore app/go home: ${e.message}", e)
            }
        }

        private fun goHomeOnTopScreen(context: Context) {
            AppLog.i(TAG, "goHomeOnTopScreen: Launching home screen on display ${Display.DEFAULT_DISPLAY}")
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                }
            context.startActivity(intent, options.toBundle())
        }
    }
}
