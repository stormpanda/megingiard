package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.session.GameNativeDetector
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MegingiardSettingsClient {
    private const val TAG = "MegingiardSettingsClient"

    fun observeSteamGridDbApiToken(context: Context): Flow<String> {
        MegingiardIpcContract.init(context)
        return observeContentProvider(
            context = context,
            uri = MegingiardIpcContract.SETTINGS_URI,
            parser = { resolver, uri -> IpcSettingsParser.parse(resolver, uri) },
        ).map { ipcConfig ->
            if (ipcConfig.steamGridDbApiToken.isNotBlank()) {
                ipcConfig.steamGridDbApiToken
            } else {
                SettingsManager.steamGridDbApiToken.value
            }
        }
    }

    fun updateClientState(
        context: Context,
        isActive: Boolean,
        focusedPackage: String? = null,
        focusedRomPath: String? = null,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
        hoveredRomPath: String? = null,
        hoveredSystemId: String? = null,
        hoveredPrimaryColor: Int? = null,
        hoveredSecondaryColor: Int? = null,
    ) {
        MegingiardIpcContract.init(context)

        // Translate pseudo-packages to actual emulator packages and attach ROM metadata
        val focusedRomApp =
            if (focusedPackage != null && focusedPackage.startsWith("rom.")) {
                RomManager.romApps.value.firstOrNull { it.packageName == focusedPackage }
            } else {
                null
            }
        val finalFocusedPackage = focusedRomApp?.let { getActualPackageName(context, it.systemId) } ?: focusedPackage
        val finalFocusedRomPath = focusedRomApp?.romPath ?: focusedRomPath

        val hoveredRomApp =
            if (hoveredPackage != null && hoveredPackage.startsWith("rom.")) {
                RomManager.romApps.value.firstOrNull { it.packageName == hoveredPackage }
            } else {
                null
            }
        val finalHoveredPackage = hoveredRomApp?.let { getActualPackageName(context, it.systemId) } ?: hoveredPackage
        val finalHoveredRomPath = hoveredRomApp?.romPath ?: hoveredRomPath
        val finalHoveredSystemId = hoveredRomApp?.systemId ?: hoveredSystemId
        val finalHoveredLabel = hoveredRomApp?.label ?: hoveredLabel

        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}")
        val extras =
            Bundle().apply {
                putInt(
                    MegingiardIpcContract.COLUMN_API_VERSION,
                    MegingiardIpcContract.DEFAULT_API_VERSION,
                )
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, context.packageName)
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, isActive)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, finalFocusedPackage)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_PATH, finalFocusedRomPath)
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, finalHoveredPackage)
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, finalHoveredLabel)
                putString(MegingiardIpcContract.COLUMN_HOVERED_ROM_PATH, finalHoveredRomPath)
                putString(MegingiardIpcContract.COLUMN_HOVERED_SYSTEM_ID, finalHoveredSystemId)
                if (hoveredPrimaryColor != null) {
                    putInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR, hoveredPrimaryColor)
                }
                if (hoveredSecondaryColor != null) {
                    putInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR, hoveredSecondaryColor)
                }
            }
        try {
            AppLog.d(
                TAG,
                "updateClientState: isActive=$isActive, pkg=$finalFocusedPackage, rom=$finalFocusedRomPath, hovered=$finalHoveredLabel",
            )
            context.contentResolver.call(uri, "updateClientState", null, extras)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update integration client state", e)
        }
    }

    private fun getActualPackageName(
        context: Context,
        systemId: String?,
    ): String {
        if (systemId == null) return "com.retroarch.aarch64"
        val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }
        val emulatorId = systemDef?.emulatorId ?: "retroarch"
        return if (emulatorId == "retroarch") {
            val pm = context.packageManager
            listOf("com.retroarch.aarch64", "com.retroarch").firstOrNull { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            } ?: "com.retroarch.aarch64"
        } else {
            val pm = context.packageManager
            GameNativeDetector.supportedPackages.firstOrNull { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            } ?: "app.gamenative"
        }
    }
}
