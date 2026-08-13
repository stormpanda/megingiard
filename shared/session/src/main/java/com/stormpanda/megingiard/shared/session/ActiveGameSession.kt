package com.stormpanda.megingiard.session

/**
 * Metadata snapshot of an actively running or recently launched game session.
 *
 * @param packageName Android package name of the active emulator or game container.
 * @param romPath Absolute file path to the launched ROM or game image.
 * @param gameTitle Sanitized human-readable game title.
 * @param systemId Megingiard system identifier (e.g. "snes", "n64", "ps1", "gba", "switch").
 * @param coreOrBackend Name of the active emulation core, backend, or engine.
 * @param titleId Unique game title ID (e.g. 16-character Switch Title ID).
 */
data class ActiveGameSession(
    val packageName: String,
    val romPath: String?,
    val gameTitle: String,
    val systemId: String,
    val coreOrBackend: String? = null,
    val titleId: String? = null,
)
