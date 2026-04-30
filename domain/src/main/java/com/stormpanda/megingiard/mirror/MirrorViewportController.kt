package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Suppress("unused")
private const val TAG = "MirrorViewportCtrl"

const val VIEWPORT_ZOOM_MIN = 1f
const val VIEWPORT_ZOOM_MAX = 5f
private const val SNAP_BACK_THRESHOLD = 1.15f
private const val VIEWPORT_SAVE_DEBOUNCE_MS = 300L

private data class ViewportSnapshot(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val layoutId: String?,
)

/**
 * Manages the mirror viewport state: scale, offsetX, offsetY.
 *
 * Business logic extracted from MirrorScreen — responsible for:
 * - Constrained zoom / pan calculations
 * - Snap-back logic when pinch drops below threshold
 * - Debounced viewport persistence via [SettingsManager]
 * - Lock/projection session-state save
 *
 * The UI layer reads these [StateFlow]s and may wrap them in `Animatable`
 * for smooth animations. All mutation goes through the controller methods.
 */
object MirrorViewportController {

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offsetX = MutableStateFlow(0f)
    val offsetX: StateFlow<Float> = _offsetX.asStateFlow()

    private val _offsetY = MutableStateFlow(0f)
    val offsetY: StateFlow<Float> = _offsetY.asStateFlow()

    /**
     * Apply a zoom and pan gesture.
     *
     * @param zoom    multiplicative zoom factor from the gesture (1.0 = no change)
     * @param panX    horizontal pan in pixels
     * @param panY    vertical pan in pixels
     * @param surfaceW  width of the mirrored surface in pixels
     * @param surfaceH  height of the mirrored surface in pixels
     */
    fun applyZoomPan(zoom: Float, panX: Float, panY: Float, surfaceW: Float, surfaceH: Float) {
        val newScale = (_scale.value * zoom).coerceIn(VIEWPORT_ZOOM_MIN, VIEWPORT_ZOOM_MAX)
        _scale.value = newScale
        val maxX = (surfaceW * (newScale - 1f)) / 2f
        val maxY = (surfaceH * (newScale - 1f)) / 2f
        _offsetX.value = (_offsetX.value + panX).coerceIn(-maxX, maxX)
        _offsetY.value = (_offsetY.value + panY).coerceIn(-maxY, maxY)
        syncToManager()
    }

    /** Reset viewport to default (no zoom, no pan). */
    fun resetViewport() {
        _scale.value = VIEWPORT_ZOOM_MIN
        _offsetX.value = 0f
        _offsetY.value = 0f
        syncToManager()
    }

    /** Whether the current scale is below the snap-back threshold. */
    fun shouldSnapBack(): Boolean = _scale.value < SNAP_BACK_THRESHOLD

    /**
     * Restore viewport from persisted session state.
     * Called when capture starts or MirrorScreen re-enters composition.
     */
    fun restoreFromManager() {
        val s = ScreenCaptureManager.scale.value
        val ox = ScreenCaptureManager.offsetX.value
        val oy = ScreenCaptureManager.offsetY.value
        AppLog.d(TAG, "restoreFromManager scale=$s offset=($ox,$oy)")
        _scale.value = s
        _offsetX.value = ox
        _offsetY.value = oy
    }

    /**
     * Restore viewport from the active layout's saved values.
     * Called when capture starts (instead of the global SettingsManager restore)
     * and when the active layout changes while capturing.
     */
    fun restoreFromLayout() {
        val layout = MacroPadState.activeLayout.value
        val s = layout?.mirrorSavedScale ?: 1f
        val ox = layout?.mirrorSavedOffsetX ?: 0f
        val oy = layout?.mirrorSavedOffsetY ?: 0f
        AppLog.d(TAG, "restoreFromLayout layoutId=${layout?.id} scale=$s offset=($ox,$oy)")
        _scale.value = s
        _offsetX.value = ox
        _offsetY.value = oy
        syncToManager()
    }

    /** Directly set scale/offset (used when syncing from Animatable). */
    fun setValues(scale: Float, offsetX: Float, offsetY: Float) {
        _scale.value = scale
        _offsetX.value = offsetX
        _offsetY.value = offsetY
        syncToManager()
    }

    /**
     * Start the debounced-save coroutine that saves viewport + lock/projection
     * session state to [SettingsManager] when they change.
     *
     * Call once per lifecycle scope (e.g. in a LaunchedEffect).
     */
    @OptIn(FlowPreview::class)
    fun startPersistence(scope: CoroutineScope) {
        // Debounced viewport save — stored per active layout
        scope.launch {
            combine(
                _scale,
                _offsetX,
                _offsetY,
                MacroPadState.activeLayout,
            ) { s, ox, oy, layout ->
                ViewportSnapshot(s, ox, oy, layout?.id)
            }
                .onEach { snapshot ->
                    ScreenCaptureManager.setScale(snapshot.scale)
                    ScreenCaptureManager.setOffsetX(snapshot.offsetX)
                    ScreenCaptureManager.setOffsetY(snapshot.offsetY)
                }
                .debounce(VIEWPORT_SAVE_DEBOUNCE_MS)
                .collectLatest { snapshot ->
                    if (ScreenCaptureManager.isCapturing.value &&
                        SettingsManager.rememberViewport.value
                    ) {
                        snapshot.layoutId?.let { layoutId ->
                            AppLog.d(
                                TAG,
                                "debounced viewport save layoutId=$layoutId scale=${snapshot.scale} offset=(${snapshot.offsetX},${snapshot.offsetY})",
                            )
                            MacroPadState.saveMirrorViewport(
                                layoutId = layoutId,
                                scale = snapshot.scale,
                                offsetX = snapshot.offsetX,
                                offsetY = snapshot.offsetY,
                            )
                        }
                    }
                }
        }

        // Restore viewport when the active layout changes while capturing
        scope.launch {
            var previousLayoutId: String? = null
            MacroPadState.activeLayout
                .mapNotNull { it?.id }
                .distinctUntilChanged()
                .collectLatest { newLayoutId ->
                    val oldLayoutId = previousLayoutId
                    previousLayoutId = newLayoutId
                    if (oldLayoutId == null) return@collectLatest

                    if (ScreenCaptureManager.isCapturing.value &&
                        SettingsManager.rememberViewport.value
                    ) {
                        AppLog.i(
                            TAG,
                            "layout switch flush oldLayout=$oldLayoutId -> newLayout=$newLayoutId scale=${_scale.value} offset=(${_offsetX.value},${_offsetY.value})",
                        )
                        MacroPadState.saveMirrorViewport(
                            layoutId = oldLayoutId,
                            scale = _scale.value,
                            offsetX = _offsetX.value,
                            offsetY = _offsetY.value,
                        )
                    }

                    if (ScreenCaptureManager.isCapturing.value) {
                        restoreFromLayout()
                        AppLog.i(
                            TAG,
                            "active layout changed -> restored newLayout=$newLayoutId scale=${_scale.value} offset=(${_offsetX.value},${_offsetY.value})",
                        )
                    }
                }
        }

        // Lock/projection state save
        scope.launch {
            combine(
                ScreenCaptureManager.isLocked,
                ScreenCaptureManager.isTouchProjectionActive,
            ) { locked, projection -> Pair(locked, projection) }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    if (ScreenCaptureManager.isCapturing.value &&
                        (SettingsManager.rememberLock.value || SettingsManager.rememberProjection.value)
                    ) {
                        SettingsManager.saveMirrorSessionState()
                    }
                }
        }
    }

    private fun syncToManager() {
        ScreenCaptureManager.setScale(_scale.value)
        ScreenCaptureManager.setOffsetX(_offsetX.value)
        ScreenCaptureManager.setOffsetY(_offsetY.value)
    }
}
