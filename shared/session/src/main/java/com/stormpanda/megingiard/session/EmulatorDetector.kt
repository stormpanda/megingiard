package com.stormpanda.megingiard.session

/**
 * Standard contract for emulator detector engines that extract active game metadata
 * from specific emulator processes, configuration files, or launch streams.
 */
interface EmulatorDetector {
    /**
     * Set of Android package names supported by this detector.
     */
    val supportedPackages: Set<String>

    /**
     * Primary Megingiard system identifier associated with this detector.
     */
    val systemId: String

    /**
     * Detects and returns the active game session for the given foreground package.
     * Returns `null` if no active game or playlist history could be resolved.
     */
    suspend fun detectActiveSession(packageName: String): ActiveGameSession? = detectActiveSession(packageName, null, null)

    /**
     * Detects and returns the active game session for the given foreground package, optionally
     * using the focused ROM path and identifier from external launcher context to resolve
     * container process mismatches.
     */
    suspend fun detectActiveSession(
        packageName: String,
        focusedRomPath: String?,
        focusedRomIdentifier: String?,
    ): ActiveGameSession? = detectActiveSession(packageName)
}
