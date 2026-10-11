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
    val libraryIconPath: String? = null,
    val libraryIconImageId: Int? = null,
    val libraryIconType: String? = null,
    val libraryIconLastModified: Long = 0L,
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
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withLibraryIcon(iconPath, imageId = imageId, type = type)
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLabel(
    packageName: String,
    newLabel: String,
): List<InstalledAppInfo> = map { if (it.packageName == packageName) it.withLabel(newLabel) else it }
