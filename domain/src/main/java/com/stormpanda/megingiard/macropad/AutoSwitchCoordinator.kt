package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AutoSwitchCoordinator"
private const val APP_PACKAGE_SELF = "com.stormpanda.megingiard"
private val IGNORED_PACKAGES = setOf("com.android.systemui", "android")

/**
 * Coordinates automatic profile switching when foreground application changes are detected.
 *
 * Excludes Megingiard itself from triggering switches to allow editing without dropping active profile context.
 */
object AutoSwitchCoordinator {

    private val _foregroundApp = MutableStateFlow<String?>(null)
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

    fun onPackageChanged(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return

        if (normalized == APP_PACKAGE_SELF) {
            AppLog.d(TAG, "onPackageChanged: Ignoring self-package ($normalized)")
            return
        }

        if (normalized in IGNORED_PACKAGES) {
            AppLog.d(TAG, "onPackageChanged: Ignoring system/transient package ($normalized)")
            return
        }

        if (_foregroundApp.value == normalized) {
            return
        }

        AppLog.i(TAG, "onPackageChanged: foreground package changed to $normalized")
        _foregroundApp.value = normalized

        if (!SettingsManager.autoSwitchProfiles.value) {
            AppLog.d(TAG, "onPackageChanged: auto-switch is disabled in settings")
            return
        }

        val matchedProfile = MacroPadState.profiles.value.firstOrNull {
            it.associatedPackage.equals(normalized, ignoreCase = true)
        }

        if (matchedProfile != null) {
            val currentActiveId = MacroPadState.activeProfileId.value
            if (matchedProfile.id != currentActiveId) {
                AppLog.i(TAG, "onPackageChanged: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for app '$normalized'")
                MacroPadState.setActiveProfileId(matchedProfile.id)
            } else {
                AppLog.d(TAG, "onPackageChanged: profile '${matchedProfile.name}' is already active")
            }
        } else {
            AppLog.d(TAG, "onPackageChanged: no profile mapped to package '$normalized'")
        }
    }

    internal fun resetForTesting() {
        _foregroundApp.value = null
    }
}

