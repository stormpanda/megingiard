package com.stormpanda.megingiard

import kotlinx.coroutines.flow.MutableStateFlow

enum class AppMode { MIRROR, MEDIA }

object AppStateManager {
    val currentMode = MutableStateFlow(AppMode.MIRROR)
}
