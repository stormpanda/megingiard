package com.stormpanda.megingiard.catalog

import android.net.Uri
import android.provider.DocumentsContract
import com.stormpanda.megingiard.AppLog
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "SafPathResolver"
private const val PRIMARY_STORAGE_PREFIX = "/storage/emulated/0/"
private const val STORAGE_PREFIX = "/storage/"
private const val PRIMARY_DOC_PREFIX = "primary:"

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
        val extraRoots =
            File("/storage")
                .listFiles()
                ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
                ?.map { it.absolutePath }
                .orEmpty()
        return (listOf("/storage/emulated/0", "/sdcard") + extraRoots).distinct()
    }

    /**
     * Converts SAF percent-encoded tree/document content URIs or relative volume paths
     * into absolute file paths (e.g. `/storage/XXXX-XXXX/ROMs/psp/Game.iso`).
     */
    fun resolveFilePath(uriStr: String?): String? {
        if (uriStr.isNullOrBlank()) return null
        if (uriStr.startsWith("/")) return uriStr

        return try {
            val decoded = URLDecoder.decode(uriStr, StandardCharsets.UTF_8)
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
     * Resolves an absolute file path (e.g. `/storage/XXXX-XXXX/...` or `/storage/emulated/0/...`)
     * to a SAF content URI using the provided list of registered SAF tree URIs.
     * If the path is already a content URI, returns it directly as a parsed [Uri].
     */
    fun resolveContentUri(
        filePath: String?,
        treeUris: List<String>,
    ): Uri? {
        if (filePath.isNullOrBlank()) return null
        if (filePath.startsWith("content://")) return Uri.parse(filePath)

        for (treeUriStr in treeUris) {
            val treePath = resolveFilePath(treeUriStr) ?: continue
            if (filePath == treePath || filePath.startsWith("$treePath/")) {
                val docId =
                    when {
                        filePath.startsWith(PRIMARY_STORAGE_PREFIX) -> {
                            "$PRIMARY_DOC_PREFIX${filePath.removePrefix(PRIMARY_STORAGE_PREFIX)}"
                        }

                        filePath.startsWith(STORAGE_PREFIX) -> {
                            val withoutStorage = filePath.removePrefix(STORAGE_PREFIX)
                            val volumeId = withoutStorage.substringBefore('/')
                            val relativePath = withoutStorage.substringAfter('/')
                            "$volumeId:$relativePath"
                        }

                        else -> {
                            null
                        }
                    }
                if (docId != null) {
                    return try {
                        val treeUri = Uri.parse(treeUriStr)
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    } catch (e: Exception) {
                        AppLog.w(
                            TAG,
                            "resolveContentUri: failed to build document URI from tree '$treeUriStr' for docId '$docId' - $e",
                        )
                        null
                    }
                }
            }
        }
        return null
    }

    /**
     * Derives a human-readable game title from a ROM path or SAF URI by matching against
     * the catalog or stripping file extensions.
     */
    fun deriveGameTitle(
        romPath: String,
        rawUri: String? = null,
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
