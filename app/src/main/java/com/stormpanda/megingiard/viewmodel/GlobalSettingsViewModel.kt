package com.stormpanda.megingiard.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.BootstrapStage
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GlobalSettingsVM"

/**
 * ViewModel for [GlobalSettingsScreen] — exposes the app-global settings state
 * and routes mutations through named functions instead of letting Composables
 * call `SettingsManager.setX(...)` directly.
 *
 * State is sourced from the persistent singletons ([SettingsManager], [MacroPadSettings])
 * which already debounce and persist via DataStore. The ViewModel is a thin facade
 * — no additional logic, just an indirection so that `GlobalSettingsScreen` is
 * decoupled from the settings layer for testing and future refactors.
 */
class GlobalSettingsViewModel : ViewModel() {

    val accentColor: StateFlow<Int> = SettingsManager.accentColor
    val themeMode: StateFlow<ThemeMode> = SettingsManager.themeMode
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val appLanguage: StateFlow<AppLanguage> = SettingsManager.appLanguage
    val logLevel: StateFlow<AppLog.Level> = SettingsManager.logLevel
    val showNavigationCoachMarks: StateFlow<Boolean> = SettingsManager.showNavigationCoachMarks
    val showMirrorControlLabels: StateFlow<Boolean> = SettingsManager.showMirrorControlLabels
    val showFullscreenExitHints: StateFlow<Boolean> = SettingsManager.showFullscreenExitHints
    val mirrorAutoStart: StateFlow<Boolean> = SettingsManager.autoStartCapture
    val gamepadSwapFaceButtons: StateFlow<Boolean> = MacroPadSettings.gamepadSwapFaceButtons

    // Privileged Mode
    val privdState: StateFlow<PrivdState> = PrivdManager.state
    val privdLastError: StateFlow<PrivdError?> = PrivdManager.lastError
    val privdGamepadMergeEnabled: StateFlow<Boolean> = MacroPadSettings.privdGamepadMergeEnabled
    val privdGamepadRecordingEnabled: StateFlow<Boolean> = MacroPadSettings.privdGamepadRecordingEnabled
    val privdAutoConnect: StateFlow<Boolean> = MacroPadSettings.privdAutoConnect
    val privdDeadzoneLeft: StateFlow<Float>  = MacroPadSettings.deadzoneLeft
    val privdDeadzoneRight: StateFlow<Float> = MacroPadSettings.deadzoneRight
    val privdBootstrapStage: StateFlow<BootstrapStage> = PrivdBootstrapper.stage

    fun setAccentColor(argb: Int) = SettingsManager.setAccentColor(argb)
    fun setThemeMode(mode: ThemeMode) = SettingsManager.setThemeMode(mode)
    fun setOverlayAtBottom(value: Boolean) = SettingsManager.setOverlayAtBottom(value)
    fun setAppLanguage(value: AppLanguage) = SettingsManager.setAppLanguage(value)
    fun setLogLevel(value: AppLog.Level) = SettingsManager.setLogLevel(value)
    fun setShowNavigationCoachMarks(value: Boolean) = SettingsManager.setShowNavigationCoachMarks(value)
    fun setShowMirrorControlLabels(value: Boolean) = SettingsManager.setShowMirrorControlLabels(value)
    fun setShowFullscreenExitHints(value: Boolean) = SettingsManager.setShowFullscreenExitHints(value)
    fun setMirrorAutoStart(value: Boolean) = SettingsManager.setAutoStartCapture(value)
    fun setGamepadSwapFaceButtons(value: Boolean) = MacroPadSettings.setGamepadSwapFaceButtons(value)

    // Privileged Mode actions
    /**
     * Initiates a connection to the daemon socket asynchronously on [Dispatchers.IO].
     * The result is reflected in [privdState] — no return value.
     */
    fun privdConnect() {
        AppLog.i(TAG, "privdConnect()")
        viewModelScope.launch(Dispatchers.IO) {
            PrivdManager.connect()
        }
    }

    /**
     * Disconnects from the daemon socket. The daemon binary stays on the device.
     */
    fun privdDisconnect() = PrivdManager.disconnect()

    fun setPrivdGamepadMergeEnabled(value: Boolean) = MacroPadSettings.setPrivdGamepadMergeEnabled(value)
    fun setPrivdGamepadRecordingEnabled(value: Boolean) = MacroPadSettings.setPrivdGamepadRecordingEnabled(value)
    fun setPrivdAutoConnect(value: Boolean) = MacroPadSettings.setPrivdAutoConnect(value)
    fun setPrivdDeadzoneLeft(value: Float)  = MacroPadSettings.setDeadzoneLeft(value)
    fun setPrivdDeadzoneRight(value: Float) = MacroPadSettings.setDeadzoneRight(value)
    fun privdResetBootstrapStage() = PrivdBootstrapper.resetStage()

    /**
     * Pair with the device's ADB Wireless-Debugging service.
     * Result is delivered via [onResult] on the main thread.
     */
    fun privdPair(
        context: Context,
        host: String,
        port: Int,
        code: String,
        onResult: (Boolean) -> Unit,
    ) {
        AppLog.i(TAG, "privdPair($host:$port)")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                PrivdBootstrapper.pair(appContext, host, port, code)
            }
            onResult(ok)
        }
    }

    /**
     * After pairing succeeded: connect directly to [host], push the
     * daemon binary, spawn the daemon, then verify with [PrivdManager.connect].
     * The ADB connect port is detected automatically from the system property.
     * On success, persists the auto-connect flag so future app starts skip the wizard.
     */
    fun privdBootstrap(
        context: Context,
        host: String,
        onResult: (Boolean) -> Unit,
    ) {
        AppLog.i(TAG, "privdBootstrap()")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                PrivdBootstrapper.bootstrapAndConnect(appContext, host)
            }
            if (ok) MacroPadSettings.setPrivdAutoConnect(true)
            onResult(ok)
        }
    }
}
