package com.stormpanda.megingiard

import kotlinx.coroutines.flow.MutableStateFlow

enum class AppMode { MIRROR, MEDIA }

object AppStateManager {
    val currentMode = MutableStateFlow(AppMode.MIRROR)
    val isActivityResumed = MutableStateFlow(true)
    
    fun nextMode() {
        val values = AppMode.values()
        val nextOrdinal = (currentMode.value.ordinal + 1) % values.size
        currentMode.value = values[nextOrdinal]
    }
    
    fun prevMode() {
        val values = AppMode.values()
        val prevOrdinal = (currentMode.value.ordinal - 1 + values.size) % values.size
        currentMode.value = values[prevOrdinal]
    }
}
