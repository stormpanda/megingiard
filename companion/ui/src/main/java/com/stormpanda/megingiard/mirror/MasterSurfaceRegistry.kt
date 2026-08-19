package com.stormpanda.megingiard.mirror

import android.view.Surface
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MasterSurfaceRegistry"

internal object MasterSurfaceRegistry {
    private val _masterSurface = MutableStateFlow<Surface?>(null)
    val masterSurface: StateFlow<Surface?> = _masterSurface.asStateFlow()

    fun setMasterSurface(surface: Surface?) {
        AppLog.d(TAG, "setMasterSurface(${surface != null})")
        _masterSurface.value = surface
    }
}
