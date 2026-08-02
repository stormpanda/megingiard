package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
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

        if (normalized == APP_PACKAGE_SELF || normalized == "$APP_PACKAGE_SELF.debug") {
            AppLog.d(TAG, "onPackageChanged: Ignoring self-package ($normalized)")
            return
        }

        if (normalized in IGNORED_PACKAGES) {
            AppLog.d(TAG, "onPackageChanged: Ignoring system/transient package ($normalized)")
            return
        }

        val clientActive = AppStateManager.isExternalClientActive.value
        val clientPackage = AppStateManager.externalClientPackage.value
        val focusedGame = AppStateManager.focusedAppPackageName.value

        // 1. Focus Collision Guard: Ignore client focus event if client-reported game is running
        if (clientActive && normalized == clientPackage && focusedGame != null && focusedGame != normalized) {
            AppLog.d(
                TAG,
                "onPackageChanged: Ignoring client package '$normalized' because focused game '$focusedGame' is currently running",
            )
            return
        }

        // 2. Auto-Deactivation Fallback: Deactivate integration state if switching to unrelated app
        if (clientActive && normalized != clientPackage && normalized != focusedGame) {
            AppLog.i(
                TAG,
                "onPackageChanged: User switched focus away from launcher client '$clientPackage' and game '$focusedGame' to '$normalized'. Deactivating integration state.",
            )
            AppStateManager.setExternalClientState(
                isActive = false,
                packageName = null,
                focusedApp = null,
                hoveredPackage = null,
                hoveredLabel = null,
                hoveredPrimaryColor = null,
                hoveredSecondaryColor = null,
            )
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

        val matchedProfile =
            MacroPadState.profiles.value.firstOrNull {
                it.associatedPackage.equals(normalized, ignoreCase = true)
            }

        if (matchedProfile != null) {
            val currentActiveId = MacroPadState.activeProfileId.value
            if (matchedProfile.id != currentActiveId) {
                AppLog.i(
                    TAG,
                    "onPackageChanged: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for app '$normalized'",
                )
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
