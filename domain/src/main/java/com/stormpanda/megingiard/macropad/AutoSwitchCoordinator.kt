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

private val ROM_CONTAINER_PACKAGES =
    setOf(
        // PC / Windows Containers
        "app.gamenative",
        "com.winlator",
        "com.winlator.vanilla",
        "com.winlator.ludashi",
        "com.youstone.mobox",
        "com.eltechs.ed",
        "com.eltechs.ed.crv5",
        // RetroArch (Multi-system) & Frontends
        "com.retroarch",
        "com.retroarch.aarch64",
        "org.retroarch",
        "com.swordfish.lemuroid",
        // Nintendo Switch
        "org.yuzu.yuzu_emu",
        "org.yuzu.yuzu_emu.ea",
        "org.suyu.suyu_emu",
        "org.sudachi.sudachi_emu",
        "dev.eden.eden_emulator",
        "dev.eden.eden_nightly",
        "dev.legacy.eden_emulator",
        // Nintendo 3DS / DS / N64 / GameBoy / NES / SNES
        "org.citra.citra_emu",
        "org.citra.citra_emu.canary",
        "com.citra.emu",
        "io.github.lime3ds.lime3ds",
        "com.dsemu.drastic",
        "me.magnum.melonds",
        "org.mupen64plusae.v3.fzurita",
        "com.fastemulator.gba",
        "com.fastemulator.gbafree",
        "com.fastemulator.gbc",
        "com.fastemulator.gbcfree",
        "it.dbtecno.pizzaboygba",
        "it.dbtecno.pizzaboygbafree",
        "it.dbtecno.pizzaboygbapro",
        "it.dbtecno.pizzaboygbc",
        "it.dbtecno.pizzaboygbcfree",
        "it.dbtecno.pizzaboygbcpro",
        "com.johnemulators.johnness",
        "com.johnemulators.johngbac",
        // PlayStation (PS1 / PS2 / PSP)
        "xyz.aethersx2.android",
        "ru.aethersx2.android",
        "com.tahlrex.aethersx2",
        "link.carsonli.aethersx2",
        "com.github.stenzek.duckstation",
        "com.epsxe.epsxe",
        "com.emulator.fpse",
        "org.ppsspp.ppsspp",
        "org.ppsspp.ppssppgold",
        // Nintendo GameCube / Wii
        "org.dolphinemu.dolphinemu",
        // Sega / Dreamcast
        "com.reios.flycast",
        "io.recompiled.redream",
    )

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

        // 2. Emulator/Container Focus Guard: Ignore container package focus events if a ROM game is running
        if (clientActive && focusedGame != null && focusedGame.startsWith("rom.") && normalized in ROM_CONTAINER_PACKAGES) {
            AppLog.d(
                TAG,
                "onPackageChanged: Ignoring ROM container package '$normalized' because ROM game '$focusedGame' is currently running",
            )
            return
        }

        // 3. Auto-Deactivation Fallback: Deactivate integration state if switching to unrelated app
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

        val isRegisteredEmulator = EmulatorDetectionFunnel.isRegisteredEmulator(normalized)

        if (_foregroundApp.value == normalized && !isRegisteredEmulator) {
            return
        }

        AppLog.i(TAG, "onPackageChanged: foreground package changed to $normalized")
        _foregroundApp.value = normalized

        if (!SettingsManager.autoSwitchProfiles.value) {
            AppLog.d(TAG, "onPackageChanged: auto-switch is disabled in settings")
            return
        }

        val directMatchedProfile =
            MacroPadState.profiles.value.firstOrNull {
                it.associatedPackage.equals(normalized, ignoreCase = true)
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
            if (EmulatorDetectionFunnel.isRegisteredEmulator(normalized)) {
                coordinatorScope.launch {
                    EmulatorDetectionFunnel.onPackageForeground(normalized)
                }
            } else {
                EmulatorDetectionFunnel.clearSession()
            }
            return
        }

        if (EmulatorDetectionFunnel.isRegisteredEmulator(normalized)) {
            coordinatorScope.launch {
                val session = EmulatorDetectionFunnel.onPackageForeground(normalized)
                val matchedProfile =
                    MacroPadState.profiles.value.firstOrNull { profile ->
                        val assoc = profile.associatedPackage
                        session != null && (
                            assoc.equals(session.romPath, ignoreCase = true) ||
                                assoc.equals("rom.${session.systemId}.${session.gameTitle}", ignoreCase = true) ||
                                assoc.equals(session.systemId, ignoreCase = true)
                        )
                    }

                if (matchedProfile != null) {
                    val currentActiveId = MacroPadState.activeProfileId.value
                    if (matchedProfile.id != currentActiveId) {
                        AppLog.i(
                            TAG,
                            "onPackageChanged: auto-switching to profile '${matchedProfile.name}' (id=${matchedProfile.id}) for emulator session '$normalized' (${session?.gameTitle})",
                        )
                        MacroPadState.setActiveProfileId(matchedProfile.id)
                    }
                }
            }
        } else {
            EmulatorDetectionFunnel.clearSession()
            AppLog.d(TAG, "onPackageChanged: no profile mapped to package '$normalized'")
        }
    }

    internal fun resetForTesting() {
        _foregroundApp.value = null
        EmulatorDetectionFunnel.clearSession()
    }
}
