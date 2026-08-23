package com.stormpanda.megingiard.mirror

import android.view.Surface
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MasterSurfaceRegistry"

internal object MasterSurfaceRegistry {
    const val OWNER_MACROPAD = "MacroPad"
    const val OWNER_TOUCHPAD = "Touchpad"

    const val PRIORITY_MACROPAD = 10
    const val PRIORITY_TOUCHPAD = 20

    private val _masterSurface = MutableStateFlow<Surface?>(null)
    val masterSurface: StateFlow<Surface?> = _masterSurface.asStateFlow()

    private val registry = mutableMapOf<String, SurfaceEntry>()

    private data class SurfaceEntry(
        val surface: Surface,
        val priority: Int,
    )

    @Synchronized
    fun registerMasterSurface(
        owner: String,
        surface: Surface,
        priority: Int,
    ) {
        if (!surface.isValid) {
            AppLog.w(TAG, "registerMasterSurface($owner, priority=$priority): surface is not valid, ignoring")
            return
        }
        val previous = registry[owner]
        if (previous?.surface == surface && previous.priority == priority) {
            return
        }
        AppLog.d(TAG, "registerMasterSurface(owner=$owner, priority=$priority)")
        registry[owner] = SurfaceEntry(surface, priority)
        recomputeActiveSurface()
    }

    @Synchronized
    fun unregisterMasterSurface(
        owner: String,
        surface: Surface? = null,
    ) {
        val entry = registry[owner] ?: return
        if (surface != null && entry.surface != surface) {
            AppLog.d(TAG, "unregisterMasterSurface(owner=$owner): ignoring stale surface unregister")
            return
        }
        AppLog.d(TAG, "unregisterMasterSurface(owner=$owner)")
        registry.remove(owner)
        recomputeActiveSurface()
    }

    @Synchronized
    private fun recomputeActiveSurface() {
        val bestEntry =
            registry.entries
                .filter { it.value.surface.isValid }
                .maxByOrNull { it.value.priority }

        val newSurface = bestEntry?.value?.surface
        if (_masterSurface.value != newSurface) {
            AppLog.i(TAG, "recomputeActiveSurface: switching active surface to owner '${bestEntry?.key}' (surface=${newSurface != null})")
            _masterSurface.value = newSurface
        }
    }

    @Synchronized
    fun clearAll() {
        AppLog.d(TAG, "clearAll")
        registry.clear()
        _masterSurface.value = null
    }
}
