package com.stormpanda.megingiard.catalog

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val coverPath: String? = null,
    val isGame: Boolean = false,
    val isRom: Boolean = false,
    val romPath: String? = null,
    val systemId: String? = null,
    val retroArchCore: String? = null,
    val coverLastModified: Long = 0L,
) {
    fun withCover(
        coverPath: String?,
        lastModified: Long = System.currentTimeMillis(),
    ): InstalledAppInfo = copy(coverPath = coverPath, coverLastModified = lastModified)
}

fun List<InstalledAppInfo>.withUpdatedCover(
    packageName: String,
    coverPath: String?,
): List<InstalledAppInfo> = map { if (it.packageName == packageName) it.withCover(coverPath) else it }
