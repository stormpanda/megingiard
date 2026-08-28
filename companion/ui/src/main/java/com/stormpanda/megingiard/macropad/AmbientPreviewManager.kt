package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AmbientPreviewManager"

/** Type of value being live-previewed in ambient settings preview mode. */
enum class AmbientPreviewType { DIM, EDGE_BLENDING }

/**
 * Shared between Screen Mirroring editor (primary screen) and [BackgroundMacroPadOverlay]
 * (secondary screen). Non-null while a preview slider is active.
 */
data class AmbientPreviewConfig(
    val type: AmbientPreviewType,
    val label: String,
    val originalValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
)

/**
 * Manages live preview slider state when editing ambient background settings.
 */
object AmbientPreviewManager {
    private val _config = MutableStateFlow<AmbientPreviewConfig?>(null)
    val config: StateFlow<AmbientPreviewConfig?> = _config.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun setConfig(config: AmbientPreviewConfig?) {
        AppLog.d(TAG, "setConfig(${config?.type})")
        _config.value = config
        _isActive.value = config != null
    }
}
