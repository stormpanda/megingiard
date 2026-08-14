package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.session.EmulatorDetectionFunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AutoSwitchCoordinator"
private const val APP_PACKAGE_SELF = "com.stormpanda.megingiard"
private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private val IGNORED_PACKAGES =
    setOf(
        "com.android.systemui",
        "android",
    )

private val IGNORED_PACKAGE_PREFIXES =
    listOf(
        "com.odin.",
        "com.google.android.gms",
        "com.google.android.play.games",
    )

private fun isIgnoredPackage(packageName: String): Boolean {
    if (packageName in IGNORED_PACKAGES) return true
    return IGNORED_PACKAGE_PREFIXES.any { packageName.startsWith(it) }
}

private fun isLauncherOrTaskSwitcher(packageName: String): Boolean {
    val pkg = packageName.lowercase().trim()
    return pkg.startsWith("com.stormpanda.megingiard.gamefocus") ||
        pkg.contains("launcher") ||
        pkg.contains("home") ||
        pkg == "com.android.systemui"
}

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
                if (session != null) {
                    if (AppStateManager.companionViewMode.value == CompanionViewMode.AUTO) {
                        val matchedProfile =
                            MacroPadState.findBestMatchingProfile(
                                session.packageName,
                                session.romIdentifier ?: session.romPath,
                                session.systemId,
                            )
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
                    } else {
                        AppLog.d(TAG, "activeSession observed: auto-mode disabled, skipping profile switch for ${session.gameTitle}")
                    }
                    AppStateManager.setStandaloneForegroundState(
                        focusedApp = session.packageName,
                        focusedRomPath = session.romPath ?: session.romIdentifier,
                        focusedRomIdentifier = session.romIdentifier ?: session.romPath,
                    )
                } else {
                    val currentForeground = _foregroundApp.value
                    if (currentForeground != null) {
                        AppStateManager.setStandaloneForegroundState(currentForeground, null)
                    }
                }
            }
        }
    }

    fun reevaluateAutoState() {
        if (AppStateManager.companionViewMode.value != CompanionViewMode.AUTO) {
            AppLog.d(TAG, "reevaluateAutoState: auto-mode disabled, skipping")
            return
        }
        val session = EmulatorDetectionFunnel.activeSession.value
        if (session != null) {
            val matchedProfile =
                MacroPadState.findBestMatchingProfile(
                    session.packageName,
                    session.romIdentifier ?: session.romPath,
                    session.systemId,
                )
            if (matchedProfile != null && matchedProfile.id != MacroPadState.activeProfileId.value) {
                AppLog.i(
                    TAG,
                    "reevaluateAutoState: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for session (${session.gameTitle})",
                )
                MacroPadState.setActiveProfileId(matchedProfile.id)
            }
            return
        }

        val pkg = AppStateManager.focusedAppPackageName.value ?: _foregroundApp.value
        if (!pkg.isNullOrBlank()) {
            val romPath = AppStateManager.focusedRomPath.value
            val matchedProfile = MacroPadState.findBestMatchingProfile(pkg, romPath)
            if (matchedProfile != null && matchedProfile.id != MacroPadState.activeProfileId.value) {
                AppLog.i(
                    TAG,
                    "reevaluateAutoState: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for package '$pkg'",
                )
                MacroPadState.setActiveProfileId(matchedProfile.id)
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

        if (isIgnoredPackage(normalized)) {
            AppLog.d(TAG, "onPackageChanged: Ignoring system/transient package ($normalized)")
            return
        }

        AppLog.d(TAG, "onPackageChanged: foreground package changed to $normalized")
        _foregroundApp.value = normalized

        val isRegisteredEmulator = EmulatorDetectionFunnel.isRegisteredEmulator(normalized)
        val isLauncherOrSwitcher = isLauncherOrTaskSwitcher(normalized)

        // 1. Process emulator package changes for ROM detection
        if (isRegisteredEmulator) {
            coordinatorScope.launch {
                EmulatorDetectionFunnel.onPackageForeground(normalized)
            }
        } else if (!isLauncherOrSwitcher) {
            EmulatorDetectionFunnel.clearSession()
        }

        val clientActive = AppStateManager.isExternalClientActive.value
        val clientPackage = AppStateManager.externalClientPackage.value
        val focusedGame = AppStateManager.focusedAppPackageName.value

        // 2. Auto-Deactivation Fallback: Deactivate integration state if switching to unrelated app
        if (clientActive && normalized != clientPackage && normalized != focusedGame) {
            if (isLauncherOrSwitcher) {
                AppLog.d(
                    TAG,
                    "onPackageChanged: Task switcher/launcher '$normalized' focused. Preserving client integration state ($clientPackage -> $focusedGame).",
                )
            } else {
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
        }

        // 3. Sync standalone foreground package state with AppStateManager
        if (isLauncherOrSwitcher) {
            AppLog.d(TAG, "onPackageChanged: Preserving focused game state while task switcher/launcher '$normalized' is active.")
            return
        } else if (!isRegisteredEmulator) {
            AppStateManager.setStandaloneForegroundState(normalized, null)
        } else {
            val session = EmulatorDetectionFunnel.activeSession.value ?: EmulatorDetectionFunnel.lastDetectedSession.value
            if (session != null && session.packageName == normalized) {
                AppStateManager.setStandaloneForegroundState(
                    focusedApp = session.packageName,
                    focusedRomPath = session.romPath ?: session.romIdentifier,
                    focusedRomIdentifier = session.romIdentifier ?: session.romPath,
                )
            } else {
                val currentFocusedPkg = AppStateManager.focusedAppPackageName.value
                val currentFocusedRom = AppStateManager.focusedRomPath.value
                val currentActiveProfile = MacroPadState.activeProfile.value
                if (currentFocusedPkg == normalized &&
                    currentActiveProfile?.matches(normalized, currentFocusedRom, isActiveProfile = true) == true
                ) {
                    AppLog.d(TAG, "onPackageChanged: preserving active ROM path '$currentFocusedRom' for emulator '$normalized'")
                } else {
                    AppStateManager.setStandaloneForegroundState(normalized, null)
                }
            }
        }

        // 4. Auto profile switching
        val session = EmulatorDetectionFunnel.activeSession.value ?: EmulatorDetectionFunnel.lastDetectedSession.value
        if (isRegisteredEmulator && session != null && session.packageName == normalized) {
            AppLog.d(
                TAG,
                "onPackageChanged: active ROM session exists for emulator '$normalized' (${session.romIdentifier ?: session.romPath})",
            )
            if (AppStateManager.companionViewMode.value == CompanionViewMode.AUTO) {
                val matchedProfile =
                    MacroPadState.findBestMatchingProfile(
                        session.packageName,
                        session.romIdentifier ?: session.romPath,
                        session.systemId,
                    )
                if (matchedProfile != null) {
                    val currentActiveId = MacroPadState.activeProfileId.value
                    if (matchedProfile.id != currentActiveId) {
                        AppLog.i(
                            TAG,
                            "onPackageChanged: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for emulator session (${session.gameTitle})",
                        )
                        MacroPadState.setActiveProfileId(matchedProfile.id)
                    }
                }
            } else {
                AppLog.d(TAG, "onPackageChanged: auto-mode disabled, skipping profile switch for ${session.gameTitle}")
            }
            return
        }

        val directMatchedProfile = MacroPadState.findBestMatchingProfile(normalized)
        if (directMatchedProfile != null) {
            val currentActiveId = MacroPadState.activeProfileId.value
            if (AppStateManager.companionViewMode.value == CompanionViewMode.AUTO && directMatchedProfile.id != currentActiveId) {
                AppLog.i(
                    TAG,
                    "onPackageChanged: auto-switching to profile '${directMatchedProfile.name}' (id=${directMatchedProfile.id}) for app '$normalized'",
                )
                MacroPadState.setActiveProfileId(directMatchedProfile.id)
            } else {
                AppLog.d(TAG, "onPackageChanged: profile '${directMatchedProfile.name}' is already active or auto-mode disabled")
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
