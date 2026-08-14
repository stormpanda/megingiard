package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.catalog.RomManager
import java.io.File

/**
 * Shared utility for resolving Android Storage Access Framework (SAF) content URIs
 * into standard file paths and deriving human-readable game titles.
 */
object SafPathResolver {
    /**
     * Converts SAF percent-encoded tree/document content URIs or relative volume paths
     * into absolute file paths (e.g. `/storage/6914-318F/ROMs/psp/Game.iso`).
     */
    fun resolveFilePath(uriStr: String?): String? {
        if (uriStr.isNullOrBlank()) return null
        if (uriStr.startsWith("/")) return uriStr

        return try {
            val decoded = java.net.URLDecoder.decode(uriStr, "UTF-8")
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
