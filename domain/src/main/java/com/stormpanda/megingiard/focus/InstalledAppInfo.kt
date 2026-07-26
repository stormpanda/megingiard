package com.stormpanda.megingiard.focus

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable? = null,
    val coverPath: String? = null,
)
