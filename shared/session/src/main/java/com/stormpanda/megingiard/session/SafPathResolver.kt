package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import java.io.File
import java.net.URLDecoder

private const val TAG = "SafPathResolver"

/**
 * Shared utility for resolving Android Storage Access Framework (SAF) content URIs
 * into standard file paths and deriving human-readable game titles.
 */
object SafPathResolver {
    /**
     * Resolves all root storage directory paths available on the device,
     * including primary internal storage (`/storage/emulated/0`, `/sdcard`)
     * and any dynamically mounted external MicroSD card volumes (e.g. `/storage/A1B2-C3D4`).
     */
    fun getStorageVolumeRoots(): List<String> {
        val roots =
            mutableListOf(
                "/storage/emulated/0",
                "/sdcard",
            )
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            val volumes = storageDir.listFiles()
            if (volumes != null) {
                for (volume in volumes) {
                    if (volume.isDirectory && volume.name != "emulated" && volume.name != "self") {
                        val path = volume.absolutePath
                        if (!roots.contains(path)) {
                            roots.add(path)
                        }
                    }
                }
            }
        }
        return roots
    }

    /**
     * Converts SAF percent-encoded tree/document content URIs or relative volume paths
     * into absolute file paths (e.g. `/storage/XXXX-XXXX/ROMs/psp/Game.iso`).
     */
    fun resolveFilePath(uriStr: String?): String? {
        if (uriStr.isNullOrBlank()) return null
        if (uriStr.startsWith("/")) return uriStr

        return try {
            val decoded = URLDecoder.decode(uriStr, "UTF-8")
            val rawPath =
                when {
                    decoded.contains("/document/") -> decoded.substringAfter("/document/")
                    decoded.contains("/tree/") -> decoded.substringAfter("/tree/")
                    else -> decoded
                }

            when {
                rawPath.startsWith("/") -> rawPath
                rawPath.startsWith("primary:") -> "/storage/emulated/0/${rawPath.substringAfter("primary:")}"
                rawPath.contains(":") -> "/storage/${rawPath.replaceFirst(":", "/")}"
                else -> rawPath
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "resolveFilePath: failed to decode URI '$uriStr' - $e")
            uriStr
        }
    }

    /**
     * Derives a human-readable game title from a ROM path or SAF URI by matching against
     * the catalog or stripping file extensions.
     */
    fun deriveGameTitle(
        romPath: String,
        rawUri: String? = null,
        knownExtensions: Set<String> = emptySet(),
    ): String? {
        val romApps = RomManager.romApps.value
        val matchedApp =
            romApps.firstOrNull { app ->
                val appRomPath = app.romPath
                appRomPath.equals(romPath, ignoreCase = true) ||
                    (appRomPath != null && File(appRomPath).name.equals(File(romPath).name, ignoreCase = true))
            }
        if (matchedApp != null) {
            return matchedApp.label
        }

        val pathToUse =
            if (romPath.startsWith("content://") && !rawUri.isNullOrBlank()) {
                resolveFilePath(rawUri) ?: romPath
            } else {
                romPath
            }

        val fileName = pathToUse.substringAfterLast('/').substringAfterLast('\\')
        val nameWithoutExt = fileName.substringBeforeLast('.')

        return nameWithoutExt.trim().takeIf { it.isNotBlank() }
    }
}
