package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.MirrorSettings
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

private const val TAG = "MirrorViewportCtrl"

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
 * Responsible for:
 * - Debounced viewport persistence via [SettingsManager]
 * - Lock/projection session-state save
 */
object MirrorViewportController {
    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offsetX = MutableStateFlow(0f)
    val offsetX: StateFlow<Float> = _offsetX.asStateFlow()

    private val _offsetY = MutableStateFlow(0f)
    val offsetY: StateFlow<Float> = _offsetY.asStateFlow()

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
        val layout = MacroPadState.activeLayout.value ?: return
        val sw = ScreenCaptureManager.surfaceWidth.value
        val sh = ScreenCaptureManager.surfaceHeight.value

        val firstCutout = layout.mirrorCutouts.firstOrNull()
        val (s, ox, oy) =
            if (firstCutout != null && sw > 0f && sh > 0f) {
                val scale = 1.0f / firstCutout.srcWidth
                val offsetX = -(firstCutout.srcX - 0.5f + firstCutout.srcWidth / 2f) * (sw * scale)
                val offsetY = -(firstCutout.srcY - 0.5f + firstCutout.srcHeight / 2f) * (sh * scale)
                Triple(scale, offsetX, offsetY)
            } else {
                Triple(layout.mirrorSavedScale, layout.mirrorSavedOffsetX, layout.mirrorSavedOffsetY)
            }

        val follow = layout.mirrorFollowActive
        AppLog.d(TAG, "restoreFromLayout layoutId=${layout.id} scale=$s offset=($ox,$oy) follow=$follow")
        _scale.value = s
        _offsetX.value = ox
        _offsetY.value = oy
        syncToManager()
        ScreenCaptureManager.setFollowActive(follow)
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
            }.onEach { snapshot ->
                ScreenCaptureManager.setScale(snapshot.scale)
                ScreenCaptureManager.setOffsetX(snapshot.offsetX)
                ScreenCaptureManager.setOffsetY(snapshot.offsetY)
            }.debounce(VIEWPORT_SAVE_DEBOUNCE_MS)
                .collectLatest { snapshot ->
                    if (ScreenCaptureManager.isCapturing.value &&
                        MirrorSettings.rememberViewport.value
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
                        MirrorSettings.rememberViewport.value
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
                        (MirrorSettings.rememberLock.value || MirrorSettings.rememberProjection.value)
                    ) {
                        MirrorSettings.saveMirrorSessionState()
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
