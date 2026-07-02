package com.stormpanda.megingiard.viewmodel

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.BootstrapStage
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.stormpanda.megingiard.config.InternalBackup

private const val TAG = "GlobalSettingsVM"
private const val GETPROP_TIMEOUT_MS = 2000L

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

    val internalBackups: StateFlow<List<InternalBackup>> = SettingsManager.internalBackups

    val accentColor: StateFlow<Int> = SettingsManager.accentColor
    val themeMode: StateFlow<ThemeMode> = SettingsManager.themeMode
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val overlayFadeOut: StateFlow<Boolean> = SettingsManager.overlayFadeOut
    val appLanguage: StateFlow<AppLanguage> = SettingsManager.appLanguage
    val logLevel: StateFlow<AppLog.Level> = SettingsManager.logLevel
    
    val showWelcomeTutorial: StateFlow<Boolean> = SettingsManager.showWelcomeTutorial
    val autoSwitchProfiles: StateFlow<Boolean> = SettingsManager.autoSwitchProfiles
    val excludeFromRecents: StateFlow<Boolean> = SettingsManager.excludeFromRecents
    val gamepadSwapFaceButtons: StateFlow<Boolean> = MacroPadSettings.gamepadSwapFaceButtons

    // Privileged Mode
    val privdState: StateFlow<PrivdState> = PrivdManager.state
    val privdLastError: StateFlow<PrivdError?> = PrivdManager.lastError
    val privdDeadzoneLeft: StateFlow<Float>  = MacroPadSettings.deadzoneLeft
    val privdDeadzoneRight: StateFlow<Float> = MacroPadSettings.deadzoneRight
    val privdShowAdbPrompt: StateFlow<Boolean> = MacroPadSettings.privdShowAdbPrompt
    val privdBootstrapStage: StateFlow<BootstrapStage> = PrivdBootstrapper.stage

    private val _isWirelessDebuggingActive = MutableStateFlow<Boolean?>(null)
    val isWirelessDebuggingActive: StateFlow<Boolean?> = _isWirelessDebuggingActive.asStateFlow()

    private val _hasCredentials = MutableStateFlow<Boolean?>(null)
    val hasCredentials: StateFlow<Boolean?> = _hasCredentials.asStateFlow()

    fun setAccentColor(argb: Int) = SettingsManager.setAccentColor(argb)
    fun setThemeMode(mode: ThemeMode) = SettingsManager.setThemeMode(mode)
    fun setOverlayAtBottom(value: Boolean) = SettingsManager.setOverlayAtBottom(value)
    fun setOverlayFadeOut(value: Boolean) = SettingsManager.setOverlayFadeOut(value)
    fun setAppLanguage(value: AppLanguage) = SettingsManager.setAppLanguage(value)
    fun setLogLevel(value: AppLog.Level) = SettingsManager.setLogLevel(value)
    fun requestSaveLogReport() = LogReportManager.requestSaveReport()

    fun setShowWelcomeTutorial(value: Boolean) = SettingsManager.setShowWelcomeTutorial(value)
    fun resetAllTutorials() = SettingsManager.resetAllTutorials()
    fun setAutoSwitchProfiles(value: Boolean) = SettingsManager.setAutoSwitchProfiles(value)
    fun setExcludeFromRecents(value: Boolean) = SettingsManager.setExcludeFromRecents(value)
    fun setGamepadSwapFaceButtons(value: Boolean) = MacroPadSettings.setGamepadSwapFaceButtons(value)

    // Privileged Mode actions
    /**
     * Initiates a connection to the daemon socket asynchronously on [Dispatchers.IO].
     * The result is reflected in [privdState] — no return value.
     */
    fun privdConnect(context: Context) {
        AppLog.i(TAG, "privdConnect()")
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            PrivdManager.connect(appContext)
        }
    }

    /**
     * Disconnects from the daemon socket. The daemon binary stays on the device.
     */
    fun privdDisconnect() = PrivdManager.disconnect()

    fun setPrivdDeadzoneLeft(value: Float)  = MacroPadSettings.setDeadzoneLeft(value)
    fun setPrivdDeadzoneRight(value: Float) = MacroPadSettings.setDeadzoneRight(value)
    fun setPrivdShowAdbPrompt(value: Boolean) = MacroPadSettings.setPrivdShowAdbPrompt(value)
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
            onResult(ok)
        }
    }

    /**
     * Checks if the Megingiard Accessibility Service is currently enabled in Android system settings.
     */
    fun checkAccessibilityActive(context: Context): Boolean {
        return MegingiardAccessibilityService.isEnabled(context)
    }

    fun checkPrivilegedModeStatus(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val keyFile = File(appContext.noBackupFilesDir, "privd_adb_key.bin")
            val certFile = File(appContext.noBackupFilesDir, "privd_adb_cert.bin")
            _hasCredentials.value = keyFile.exists() && certFile.exists()

            var proc: Process? = null
            val port = try {
                proc = ProcessBuilder("getprop", "service.adb.tls.port")
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().use { reader ->
                    reader.readLine()?.trim().orEmpty()
                }
                proc.waitFor(GETPROP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                output.toIntOrNull() ?: 0
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to read service.adb.tls.port: $e")
                proc?.destroyForcibly()
                0
            }
            _isWirelessDebuggingActive.value = port > 0
            AppLog.d(TAG, "checkPrivilegedModeStatus: hasCredentials=${_hasCredentials.value} isWirelessDebuggingActive=${_isWirelessDebuggingActive.value}")
        }
    }
}
