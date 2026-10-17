package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_PC
import com.stormpanda.megingiard.catalog.PcEmulators
import com.stormpanda.megingiard.catalog.RomLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PcLauncher"
private const val PC_EXTRA_APP_ID = "app_id"
private const val PC_EXTRA_ROM = "ROM"
private const val CONTENT_URI_SCHEME_PREFIX = "content://"
private const val MAX_APP_ID_CONTENT_CHARS = 64

class PcLauncher : RomLauncher {
    override val id: String = EMULATOR_ID_PC
    override val displayName: String = "PC"

    override suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
        romUri: String?,
    ): Boolean {
        val packageName = resolveTargetPackage(context, retroArchCore)
        if (packageName == null) {
            AppLog.e(
                TAG,
                "No supported PC emulator is installed (checked ${PcEmulators.SUPPORTED_EMULATOR_OPTIONS.joinToString { it.packageName }})",
            )
            return false
        }

        // Try to parse steam app ID
        val appId = withContext(Dispatchers.IO) { parseSteamAppId(context, romPath, romUri) }
        AppLog.i(
            TAG,
            "Launching PC ROM '$romPath' (appId: $appId) with package '$packageName' on display $displayId",
        )

        return try {
            val intent =
                Intent().apply {
                    component = ComponentName(packageName, "$packageName.MainActivity")
                    if (appId != null) {
                        action = "$packageName.LAUNCH_GAME"
                        putExtra(PC_EXTRA_APP_ID, appId)
                    } else {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra(PC_EXTRA_ROM, romPath)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch PC game in $packageName: ${e.message}", e)
            false
        }
    }

    private fun resolveTargetPackage(
        context: Context,
        preferredPackage: String?,
    ): String? {
        val installed = PcEmulators.getInstalledEmulators(context, forceRefresh = true)
        if (installed.isEmpty()) return null

        if (preferredPackage != null && installed.any { it.packageName == preferredPackage }) {
            return preferredPackage
        }
        return installed.first().packageName
    }

    internal fun parseSteamAppId(
        context: Context,
        romPath: String,
        romUri: String? = null,
    ): Int? {
        // 1. Try parsing filename if it's purely digits (e.g. 620.steam), even if POSIX File access is blocked
        extractNumericFilenameAppId(romPath)?.let { return it }
        if (romUri != null) {
            extractNumericFilenameAppId(romUri)?.let { return it }
        }

        // 2. Try reading file content from POSIX path if accessible
        val file = File(romPath)
        if (file.exists() && file.canRead() && file.length() <= MAX_APP_ID_CONTENT_CHARS) {
            try {
                val content = file.readText().trim()
                content.toIntOrNull()?.let { return it }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to read Steam AppId from file '$romPath': ${e.message}")
            }
        }

        // 3. Fall back to reading via SAF ContentResolver if a content:// URI is available
        val candidateUriString =
            when {
                romUri != null && romUri.startsWith(CONTENT_URI_SCHEME_PREFIX) -> romUri
                romPath.startsWith(CONTENT_URI_SCHEME_PREFIX) -> romPath
                else -> null
            }
        if (candidateUriString != null) {
            try {
                val uri = Uri.parse(candidateUriString)
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(MAX_APP_ID_CONTENT_CHARS)
                    val readCount = reader.read(buffer, 0, MAX_APP_ID_CONTENT_CHARS)
                    if (readCount > 0) {
                        val content = String(buffer, 0, readCount).trim()
                        content.toIntOrNull()?.let { return it }
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to read Steam AppId from URI '$candidateUriString': ${e.message}")
            }
        }
        return null
    }

    private fun extractNumericFilenameAppId(pathOrUri: String): Int? {
        val decoded = Uri.decode(pathOrUri)
        val fileName = decoded.substringAfterLast('/').substringAfterLast(':')
        val nameWithoutExt = fileName.substringBeforeLast('.')
        return nameWithoutExt.toIntOrNull()
    }
}
