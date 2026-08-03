package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.focus.rom.EmulatorDetectionFunnel
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AutoSwitchCoordinator"
private const val APP_PACKAGE_SELF = "com.stormpanda.megingiard"
private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val IGNORED_PACKAGES =
    setOf(
        "com.android.systemui",
        "android",
        "com.odin.gameassistant",
    )

/**
 * Coordinates automatic profile switching when foreground application changes are detected.
 *
 * Excludes Megingiard itself from triggering switches to allow editing without dropping active profile context.
 */
object AutoSwitchCoordinator {
    private val _foregroundApp = MutableStateFlow<String?>(null)
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

    init {
        coordinatorScope.launch {
            EmulatorDetectionFunnel.activeSession.collect { session ->
                if (session != null && SettingsManager.autoSwitchProfiles.value) {
                    val matchedProfile =
                        MacroPadState.profiles.value.let { profiles ->
                            val romFileName = session.romPath?.substringAfterLast('/')

                            // 1. Look for ROM-specific match (matching packageName, systemId, and romFileName)
                            val romMatch =
                                profiles.firstOrNull { profile ->
                                    val assoc = profile.association ?: return@firstOrNull false
                                    val packageMatches = assoc.packageName.equals(session.packageName, ignoreCase = true)
                                    val systemMatches = assoc.systemId == null || assoc.systemId.equals(session.systemId, ignoreCase = true)
                                    val fileMatches =
                                        assoc.romFileName != null && romFileName != null &&
                                            assoc.romFileName.equals(romFileName, ignoreCase = true)
                                    packageMatches && systemMatches && fileMatches
                                }

                            // 2. Fallback to generic emulator package match
                            romMatch ?: profiles.firstOrNull { profile ->
                                val assoc = profile.association ?: return@firstOrNull false
                                val packageMatches = assoc.packageName.equals(session.packageName, ignoreCase = true)
                                val isGenericAppProfile = assoc.romFileName == null
                                packageMatches && isGenericAppProfile
                            }
                        }
                    if (matchedProfile != null) {
                        val currentActiveId = MacroPadState.activeProfileId.value
                        if (matchedProfile.id != currentActiveId) {
                            AppLog.i(
                                TAG,
                                "activeSession observed: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for emulator session (${session.gameTitle})",
                            )
                            MacroPadState.setActiveProfileId(matchedProfile.id)
                        }
                    }
                }
            }
        }
    }

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

        val isRegisteredEmulator = EmulatorDetectionFunnel.isRegisteredEmulator(normalized)
        AppLog.d(
            TAG,
            "onPackageChanged normalized=$normalized isRegisteredEmulator=$isRegisteredEmulator activeSession=${EmulatorDetectionFunnel.activeSession.value?.romPath}",
        )

        // 1. Process emulator package changes for ROM detection
        if (isRegisteredEmulator) {
            coordinatorScope.launch {
                EmulatorDetectionFunnel.onPackageForeground(normalized)
            }
        } else {
            EmulatorDetectionFunnel.clearSession()
        }

        val clientActive = AppStateManager.isExternalClientActive.value
        val clientPackage = AppStateManager.externalClientPackage.value
        val focusedGame = AppStateManager.focusedAppPackageName.value

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

        if (_foregroundApp.value == normalized && !isRegisteredEmulator) {
            return
        }

        if (isRegisteredEmulator && EmulatorDetectionFunnel.activeSession.value != null) {
            AppLog.d(TAG, "onPackageChanged: active ROM session exists for emulator '$normalized' - skipping generic switch")
            return
        }

        AppLog.i(TAG, "onPackageChanged: foreground package changed to $normalized")
        _foregroundApp.value = normalized

        if (!SettingsManager.autoSwitchProfiles.value) {
            AppLog.d(TAG, "onPackageChanged: auto-switch is disabled in settings")
            return
        }

        val directMatchedProfile =
            MacroPadState.profiles.value.firstOrNull { profile ->
                val assoc = profile.association ?: return@firstOrNull false
                assoc.packageName.equals(normalized, ignoreCase = true) && assoc.romFileName == null
            }

        if (directMatchedProfile != null) {
            val currentActiveId = MacroPadState.activeProfileId.value
            if (directMatchedProfile.id != currentActiveId) {
                AppLog.i(
                    TAG,
                    "onPackageChanged: auto-switching to profile '${directMatchedProfile.name}' (id=${directMatchedProfile.id}) for app '$normalized'",
                )
                MacroPadState.setActiveProfileId(directMatchedProfile.id)
            } else {
                AppLog.d(TAG, "onPackageChanged: profile '${directMatchedProfile.name}' is already active")
            }
        } else {
            if (!isRegisteredEmulator) {
                AppLog.d(TAG, "onPackageChanged: no profile mapped to package '$normalized'")
            }
        }
    }

    internal fun resetForTesting() {
        _foregroundApp.value = null
        EmulatorDetectionFunnel.clearSession()
    }
}
