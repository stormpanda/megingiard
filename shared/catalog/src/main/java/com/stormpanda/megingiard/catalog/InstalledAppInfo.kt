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
) {
    /** True when the user has overridden [defaultLabel] with a custom display name. */
    val hasCustomLabel: Boolean
        get() = defaultLabel != null && label != defaultLabel

    fun withCover(
        coverPath: String?,
        coverImageId: Int? = this.coverImageId,
        lastModified: Long = System.currentTimeMillis(),
    ): InstalledAppInfo =
        copy(
            coverPath = coverPath,
            coverImageId = if (coverPath == null) null else coverImageId,
            coverLastModified = lastModified,
        )

    fun withLibraryIcon(
        iconPath: String?,
        imageId: Int? = this.libraryIconImageId,
        type: String? = this.libraryIconType,
        lastModified: Long = System.currentTimeMillis(),
    ): InstalledAppInfo =
        copy(
            libraryIconPath = iconPath,
            libraryIconImageId = if (iconPath == null) null else imageId,
            libraryIconType = if (iconPath == null) null else type,
            libraryIconLastModified = lastModified,
        )

    fun withLabel(newLabel: String): InstalledAppInfo = copy(label = newLabel)

    fun withCoverOptedOut(optedOut: Boolean): InstalledAppInfo = copy(coverOptedOut = optedOut)

    fun withLibraryIconOptedOut(optedOut: Boolean): InstalledAppInfo = copy(libraryIconOptedOut = optedOut)

    fun withLogoLastModified(lastModified: Long): InstalledAppInfo = copy(logoLastModified = lastModified)
}

fun List<InstalledAppInfo>.withUpdatedCover(
    packageName: String,
    coverPath: String?,
    coverImageId: Int? = null,
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withCover(coverPath, coverImageId = coverImageId)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLibraryIcon(
    packageName: String,
    iconPath: String?,
    imageId: Int? = null,
    type: String? = null,
    lastModified: Long = System.currentTimeMillis(),
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withLibraryIcon(iconPath, imageId = imageId, type = type, lastModified = lastModified)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLogo(
    packageName: String,
    lastModified: Long,
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withLogoLastModified(lastModified)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedCoverOptOut(
    packageName: String,
    optedOut: Boolean,
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withCoverOptedOut(optedOut)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLibraryIconOptOut(
    packageName: String,
    optedOut: Boolean,
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withLibraryIconOptedOut(optedOut)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLabel(
    packageName: String,
    newLabel: String,
): List<InstalledAppInfo> = map { if (it.packageName == packageName) it.withLabel(newLabel) else it }
