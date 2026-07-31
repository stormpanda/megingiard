package com.stormpanda.megingiard.focus

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
)
