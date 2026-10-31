package com.stormpanda.megingiard.catalog

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val coverPath: String? = null,
    val isGame: Boolean = false,
    val isRom: Boolean = false,
    val romPath: String? = null,
    val romUri: String? = null,
    val systemId: String? = null,
    val retroArchCore: String? = null,
    val emulatorPackage: String? = null,
    val coverLastModified: Long = 0L,
    val coverImageId: Int? = null,
    /** Title shown when no custom display name is set (alias-mapped app label or cleaned ROM name). */
    val defaultLabel: String? = null,
    /** Absolute path to the cached custom library icon image (from SteamGridDB icons or logos), or null if using default. */
    val libraryIconPath: String? = null,
    /** SteamGridDB image ID of the custom library icon for selection badges, or null if unassigned. */
    val libraryIconImageId: Int? = null,
    /** SteamGridDB image type of the custom library icon ("icons" or "logos"), or null if unassigned. */
    val libraryIconType: String? = null,
    /** Last modified timestamp of the cached library icon file for cache-busting. */
    val libraryIconLastModified: Long = 0L,
    /** True if the user explicitly opted out of having cover artwork (persisted preference). */
    val coverOptedOut: Boolean = false,
    /** True if the user explicitly opted out of having custom library icon artwork (persisted preference). */
    val libraryIconOptedOut: Boolean = false,
    /** Last modified timestamp of the cached ROM logo file for cache-busting and reactive updates. */
    val logoLastModified: Long = 0L,
    /** Folder-level RetroArch core before any per-game override is applied. */
    val defaultRetroArchCore: String? = null,
    /** Folder-level standalone emulator package before any per-game override is applied. */
    val defaultEmulatorPackage: String? = null,
    /** Explicit per-game emulator package or RetroArch core override, or null when using folder defaults. */
    val customRomRunner: String? = null,
    /** Package last-update timestamp ([android.content.pm.PackageInfo.lastUpdateTime]) for Android apps, or 0L for ROMs. */
    val lastUpdateTime: Long = 0L,
) {
    /** True when the user has overridden [defaultLabel] with a custom display name. */
    val hasCustomLabel: Boolean
        get() = defaultLabel != null && label != defaultLabel

    /** True when the user has overridden the folder's default emulator or RetroArch core for this ROM. */
    val hasCustomRomRunner: Boolean
        get() = customRomRunner != null
}
