package com.stormpanda.megingiard.viewmodel

import androidx.lifecycle.ViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.gyro.GyroOutput
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.GyroSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "GlobalSettingsVM"

/**
 * ViewModel for [GlobalSettingsScreen] — exposes the app-global settings state
 * and routes mutations through named functions instead of letting Composables
 * call `SettingsManager.setX(...)` directly.
 *
 * State is sourced from the persistent singletons ([SettingsManager], [MacroPadSettings],
 * [GyroSettings]) which already debounce and persist via DataStore. The ViewModel is a
 * thin facade — no additional logic, just an indirection so that `GlobalSettingsScreen` is
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
    val gamepadSwapFaceButtons: StateFlow<Boolean> = MacroPadSettings.gamepadSwapFaceButtons

    // Gyro settings
    val gyroEnabled: StateFlow<Boolean> = GyroSettings.enabled
    val gyroOutput: StateFlow<GyroOutput> = GyroSettings.gyroOutput
    val gyroSensitivity: StateFlow<Float> = GyroSettings.sensitivity
    val gyroDeadZone: StateFlow<Float> = GyroSettings.deadZone

    fun setAccentColor(argb: Int) = SettingsManager.setAccentColor(argb)
    fun setThemeMode(mode: ThemeMode) = SettingsManager.setThemeMode(mode)
    fun setOverlayAtBottom(value: Boolean) = SettingsManager.setOverlayAtBottom(value)
    fun setAppLanguage(value: AppLanguage) = SettingsManager.setAppLanguage(value)
    fun setLogLevel(value: AppLog.Level) = SettingsManager.setLogLevel(value)
    fun setShowNavigationCoachMarks(value: Boolean) = SettingsManager.setShowNavigationCoachMarks(value)
    fun setShowMirrorControlLabels(value: Boolean) = SettingsManager.setShowMirrorControlLabels(value)
    fun setShowFullscreenExitHints(value: Boolean) = SettingsManager.setShowFullscreenExitHints(value)
    fun setGamepadSwapFaceButtons(value: Boolean) = MacroPadSettings.setGamepadSwapFaceButtons(value)

    // Gyro setters
    fun setGyroEnabled(value: Boolean) = GyroSettings.setEnabled(value)
    fun setGyroOutput(value: GyroOutput) = GyroSettings.setGyroOutput(value)
    fun setGyroSensitivity(value: Float) = GyroSettings.setSensitivity(value)
    fun setGyroDeadZone(value: Float) = GyroSettings.setDeadZone(value)
}
