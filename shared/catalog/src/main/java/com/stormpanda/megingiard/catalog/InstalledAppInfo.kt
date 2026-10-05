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
) {
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

    fun withLabel(newLabel: String): InstalledAppInfo = copy(label = newLabel)
}

fun List<InstalledAppInfo>.withUpdatedCover(
    packageName: String,
    coverPath: String?,
    coverImageId: Int? = null,
): List<InstalledAppInfo> =
    map {
        if (it.packageName == packageName) {
            it.withCover(coverPath, coverImageId = if (coverPath == null) null else (coverImageId ?: it.coverImageId))
        } else {
            it
        }
    }

fun List<InstalledAppInfo>.withUpdatedLabel(
    packageName: String,
    newLabel: String,
): List<InstalledAppInfo> = map { if (it.packageName == packageName) it.withLabel(newLabel) else it }
