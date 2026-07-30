package com.stormpanda.megingiard.config

import java.time.LocalDate

private val FILENAME_UNSAFE = Regex("[^A-Za-z0-9]")
private val VERSION_SAFE = Regex("[^A-Za-z0-9.\\-]")

/**
 * Builds the suggested filename for a full app backup export.
 * Format: megingiard_v<version>_<date>[_<author>][_<description>].mgrd
 */
fun buildExportFilename(metadata: ExportMetadata): String {
    val versionClean = metadata.appVersionName.replace(VERSION_SAFE, "_")
    val parts =
        buildList {
            add("megingiard")
            add("v$versionClean")
            add(LocalDate.now().toString())
            metadata.author?.takeIf { it.isNotBlank() }?.let { raw ->
                add(raw.trim().take(20).replace(FILENAME_UNSAFE, "_"))
            }
            metadata.description?.takeIf { it.isNotBlank() }?.let { raw ->
                add(raw.trim().take(30).replace(FILENAME_UNSAFE, "_"))
            }
        }
    return parts.joinToString("_") + ".mgrd"
}

/**
 * Builds the suggested filename for a shared MacroPad profile.
 * Format: megingiard_profile_v<version>_<date>[_<profileName>][_<author>].mgrd
 */
fun buildProfileExportFilename(
    metadata: ExportMetadata,
    profileName: String,
): String {
    val versionClean = metadata.appVersionName.replace(VERSION_SAFE, "_")
    val parts =
        buildList {
            add("megingiard_profile")
            add("v$versionClean")
            add(LocalDate.now().toString())
            profileName.trim().takeIf { it.isNotBlank() }?.let { raw ->
                add(raw.take(30).replace(FILENAME_UNSAFE, "_"))
            }
            metadata.author?.takeIf { it.isNotBlank() }?.let { raw ->
                add(raw.trim().take(20).replace(FILENAME_UNSAFE, "_"))
            }
        }
    return parts.joinToString("_") + ".mgrd"
}
