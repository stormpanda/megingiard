package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledEmulatorRegistry
import com.stormpanda.megingiard.catalog.RetroArchEmulators
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full snapshot of the Game Focus integration client state sent to Megingiard Companion.
 * Every request fully supersedes the previous one, which makes latest-wins conflation safe.
 */
internal data class ClientStateRequest(
    val appContext: Context,
    val isActive: Boolean,
    val focusedPackage: String?,
    val focusedRomPath: String?,
    val focusedRomIdentifier: String?,
    val hoveredPackage: String?,
    val hoveredLabel: String?,
    val hoveredRomPath: String?,
    val hoveredRomIdentifier: String?,
    val hoveredSystemId: String?,
    val hoveredPrimaryColor: Int?,
    val hoveredSecondaryColor: Int?,
)

object MegingiardSettingsClient {
    private const val TAG = "MegingiardSettingsCli"
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Conflated so only the newest pending snapshot is kept while an IPC call is in flight.
     * A single consumer guarantees strict ordering (e.g. a late hover update can never
     * overwrite the `isActive = false` sent from `onStop`).
     */
    private val pendingRequests = Channel<ClientStateRequest>(Channel.CONFLATED)

    init {
        clientScope.launch {
            for (request in pendingRequests) {
                dispatchClientState(request)
            }
        }
    }

    fun observeSteamGridDbApiToken(context: Context): Flow<String> {
        MegingiardIpcContract.init(context)
        return observeContentProvider(
            context = context,
            uri = MegingiardIpcContract.SETTINGS_URI,
            parser = IpcSettingsParser::parse,
        ).map { it.steamGridDbApiToken }
    }

    private fun resolveRomApp(pkg: String?): InstalledAppInfo? =
        if (pkg != null && pkg.startsWith("rom.")) {
            RomManager.romApps.value.firstOrNull { it.packageName == pkg }
        } else {
            null
        }

    /**
     * Enqueues a full client state snapshot for asynchronous delivery to Megingiard Companion.
     * Non-blocking and safe to call from the main thread; delivery is strictly ordered and
     * conflated (only the newest pending snapshot is sent if several are queued).
     */
    fun updateClientState(
        context: Context,
        isActive: Boolean,
        focusedPackage: String? = null,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
        hoveredRomPath: String? = null,
        hoveredRomIdentifier: String? = null,
        hoveredSystemId: String? = null,
        hoveredPrimaryColor: Int? = null,
        hoveredSecondaryColor: Int? = null,
    ) {
        val request =
            ClientStateRequest(
                appContext = context.applicationContext,
                isActive = isActive,
                focusedPackage = focusedPackage,
                focusedRomPath = focusedRomPath,
                focusedRomIdentifier = focusedRomIdentifier,
                hoveredPackage = hoveredPackage,
                hoveredLabel = hoveredLabel,
                hoveredRomPath = hoveredRomPath,
                hoveredRomIdentifier = hoveredRomIdentifier,
                hoveredSystemId = hoveredSystemId,
                hoveredPrimaryColor = hoveredPrimaryColor,
                hoveredSecondaryColor = hoveredSecondaryColor,
            )
        val result = pendingRequests.trySend(request)
        if (result.isFailure) {
            AppLog.e(TAG, "Failed to enqueue integration client state (isActive=$isActive)")
        }
    }

    /** Performs the blocking ContentResolver IPC for [request]. Runs on the single IO consumer. */
    internal fun dispatchClientState(request: ClientStateRequest) {
        val appContext = request.appContext
        MegingiardIpcContract.init(appContext)

        // Translate pseudo-packages to actual emulator packages and attach ROM metadata
        val focusedRomApp = resolveRomApp(request.focusedPackage)
        val finalFocusedPackage =
            focusedRomApp?.let { getActualPackageName(appContext, it.systemId, it.emulatorPackage) } ?: request.focusedPackage
        val finalFocusedRomPath = focusedRomApp?.romPath ?: request.focusedRomPath
        val finalFocusedRomIdentifier =
            request.focusedRomIdentifier ?: finalFocusedRomPath?.let { File(it).name }

        val hoveredRomApp = resolveRomApp(request.hoveredPackage)
        val finalHoveredPackage =
            hoveredRomApp?.let { getActualPackageName(appContext, it.systemId, it.emulatorPackage) } ?: request.hoveredPackage
        val finalHoveredRomPath = hoveredRomApp?.romPath ?: request.hoveredRomPath
        val finalHoveredRomIdentifier =
            request.hoveredRomIdentifier ?: finalHoveredRomPath?.let { File(it).name }
        val finalHoveredSystemId = hoveredRomApp?.systemId ?: request.hoveredSystemId
        val finalHoveredLabel = hoveredRomApp?.label ?: request.hoveredLabel

        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}")
        val extras =
            Bundle().apply {
                putInt(
                    MegingiardIpcContract.COLUMN_API_VERSION,
                    MegingiardIpcContract.DEFAULT_API_VERSION,
                )
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, appContext.packageName)
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, request.isActive)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, finalFocusedPackage)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_PATH, finalFocusedRomPath)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_IDENTIFIER, finalFocusedRomIdentifier)
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, finalHoveredPackage)
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, finalHoveredLabel)
                putString(MegingiardIpcContract.COLUMN_HOVERED_ROM_PATH, finalHoveredRomPath)
                putString(MegingiardIpcContract.COLUMN_HOVERED_ROM_IDENTIFIER, finalHoveredRomIdentifier)
                putString(MegingiardIpcContract.COLUMN_HOVERED_SYSTEM_ID, finalHoveredSystemId)
                request.hoveredPrimaryColor?.let { putInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR, it) }
                request.hoveredSecondaryColor?.let { putInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR, it) }
            }
        try {
            AppLog.d(
                TAG,
                "updateClientState: isActive=${request.isActive}, pkg=$finalFocusedPackage, " +
                    "rom=$finalFocusedRomPath, hovered=$finalHoveredLabel",
            )
            appContext.contentResolver.call(uri, "updateClientState", null, extras)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update integration client state", e)
        }
    }

    internal fun getActualPackageName(
        context: Context,
        systemId: String?,
        emulatorPackage: String? = null,
    ): String {
        val defaultRetroArchPkg = RetroArchEmulators.SUPPORTED_EMULATOR_OPTIONS.first().packageName
        if (systemId == null) return defaultRetroArchPkg
        val registry = InstalledEmulatorRegistry.forSystem(systemId)
        val candidates =
            if (registry != null) {
                val supportedPackages = registry.SUPPORTED_EMULATOR_OPTIONS.map { it.packageName }
                if (!emulatorPackage.isNullOrBlank()) {
                    listOf(emulatorPackage) + supportedPackages.filterNot { it == emulatorPackage }
                } else {
                    supportedPackages
                }
            } else {
                RetroArchEmulators.SUPPORTED_EMULATOR_OPTIONS.map { it.packageName }
            }
        val pm = context.packageManager
        return candidates.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                true
            } catch (_: Exception) {
                false
            }
        } ?: candidates.first()
    }
}
