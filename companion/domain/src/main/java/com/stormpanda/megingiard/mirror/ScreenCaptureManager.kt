package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ScreenCaptureManager"
private const val DEFAULT_MAX_FPS = 60
private const val FOLLOW_LERP_FACTOR = 0.15f
private const val FOLLOW_EPSILON = 0.001f
private const val FOLLOW_FRAME_DELAY_MS = 16L

object ScreenCaptureManager {
    const val SCREENSHOT_SUBDIR = "Pictures/Megingiard"

    private val derivedStateScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    val cutouts: StateFlow<List<ScreenCutout>> =
        MacroPadState.activeLayout
            .map { it?.mirrorCutouts.orEmpty() }
            .stateIn(derivedStateScope, SharingStarted.Eagerly, emptyList())

    val edgeBlendWidthDp: StateFlow<Float> =
        MacroPadState.activeLayout
            .map { it?.mirrorEdgeBlendWidth ?: 0f }
            .stateIn(derivedStateScope, SharingStarted.Eagerly, 0f)

    val maxFps: StateFlow<Int> =
        MacroPadState.activeLayout
            .map { it?.mirrorMaxFps ?: DEFAULT_MAX_FPS }
            .stateIn(derivedStateScope, SharingStarted.Eagerly, DEFAULT_MAX_FPS)

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    private val _frozenBitmap = MutableStateFlow<Bitmap?>(null)
    val frozenBitmap: StateFlow<Bitmap?> = _frozenBitmap.asStateFlow()

    private val _screenshotRequested = MutableStateFlow(false)
    val screenshotRequested: StateFlow<Boolean> = _screenshotRequested.asStateFlow()

    private val _pendingScreenshotTarget = MutableStateFlow<ScreenshotTarget?>(null)
    val pendingScreenshotTarget: StateFlow<ScreenshotTarget?> = _pendingScreenshotTarget.asStateFlow()

    private val _screenshotPreview = MutableStateFlow<Bitmap?>(null)
    val screenshotPreview: StateFlow<Bitmap?> = _screenshotPreview.asStateFlow()

    private val _isFollowActive = MutableStateFlow(false)
    val isFollowActive: StateFlow<Boolean> = _isFollowActive.asStateFlow()

    private var activeLayoutJob: Job? = null

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        set(value) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            field = value
            restartCollectors()
        }

    val isTouchProjectionActive: StateFlow<Boolean> =
        cutouts
            .map { list -> list.any { it.touchProjectionEnabled } }
            .stateIn(derivedStateScope, SharingStarted.Eagerly, false)

    init {
        restartCollectors()
    }

    private fun restartCollectors() {
        activeLayoutJob?.cancel()

        activeLayoutJob =
            scope.launch {
                var previousLayoutId: String? = null
                MacroPadState.activeLayout.collect { layout ->
                    if (layout != null) {
                        val oldLayoutId = previousLayoutId
                        previousLayoutId = layout.id
                        if (oldLayoutId != null && oldLayoutId != layout.id && _isCapturing.value) {
                            setFollowActive(layout.mirrorFollowActive)
                        }
                    } else {
                        previousLayoutId = null
                    }
                }
            }
    }

    fun restoreFromLayout() {
        val layout = MacroPadState.activeLayout.value ?: return
        AppLog.d(TAG, "restoreFromLayout layoutId=${layout.id} follow=${layout.mirrorFollowActive}")
        setFollowActive(layout.mirrorFollowActive)
    }

    private var targetFollowX = 0f
    private var targetFollowY = 0f
    private var followAnimationJob: Job? = null
    private var followAnimationCutoutId: String? = null

    private val _captureSourceWidth = MutableStateFlow(0)
    val captureSourceWidth: StateFlow<Int> = _captureSourceWidth.asStateFlow()

    private val _captureSourceHeight = MutableStateFlow(0)
    val captureSourceHeight: StateFlow<Int> = _captureSourceHeight.asStateFlow()

    fun setCaptureSourceSize(
        width: Int,
        height: Int,
    ) {
        AppLog.d(TAG, "setCaptureSourceSize ${width}x$height")
        _captureSourceWidth.value = width
        _captureSourceHeight.value = height
    }

    fun setCapturing(capturing: Boolean) {
        AppLog.i(TAG, "setCapturing($capturing)")
        _isCapturing.value = capturing
        if (!capturing) {
            _isFrozen.value = false
            setFrozenBitmap(null)
        }
    }

    fun setFrozen(frozen: Boolean) {
        AppLog.d(TAG, "setFrozen($frozen)")
        _isFrozen.value = frozen
    }

    fun setFrozenBitmap(bitmap: Bitmap?) {
        AppLog.d(TAG, "setFrozenBitmap(${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"})")
        val old = _frozenBitmap.value
        _frozenBitmap.value = bitmap
        if (old != null && old != bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    fun toggleFrozen() {
        val next = !_isFrozen.value
        AppLog.d(TAG, "toggleFrozen → $next")
        _isFrozen.value = next
    }

    fun requestScreenshot(target: ScreenshotTarget = ScreenshotTarget.TOP) {
        AppLog.d(TAG, "requestScreenshot(target=$target)")
        _pendingScreenshotTarget.value = target
        _screenshotRequested.value = true
    }

    fun consumeScreenshotRequest() {
        AppLog.d(TAG, "consumeScreenshotRequest")
        _screenshotRequested.value = false
        _pendingScreenshotTarget.value = null
    }

    fun showScreenshotPreview(bitmap: Bitmap) {
        val old = _screenshotPreview.value
        _screenshotPreview.value = bitmap
        if (old != null && old !== bitmap && !old.isRecycled) {
            old.recycle()
        }
        AppLog.d(TAG, "Showing screenshot preview (${bitmap.width}x${bitmap.height})")
    }

    fun clearScreenshotPreview() {
        val old = _screenshotPreview.value
        _screenshotPreview.value = null
        if (old != null && !old.isRecycled) {
            old.recycle()
        }
        AppLog.d(TAG, "Cleared screenshot preview")
    }

    fun setTouchProjectionActive(active: Boolean) {
        AppLog.i(TAG, "setTouchProjectionActive($active)")
        val layout = MacroPadState.activeLayout.value
        if (layout != null) {
            val updated = layout.mirrorCutouts.map { it.copy(touchProjectionEnabled = active) }
            MacroPadState.updateLayout(layout.copy(mirrorCutouts = updated))
        }
    }

    fun toggleTouchProjection() {
        setTouchProjectionActive(!isTouchProjectionActive.value)
    }

    fun setFollowActive(
        active: Boolean,
        persist: Boolean = false,
    ) {
        AppLog.i(TAG, "setFollowActive(active=$active, persist=$persist)")
        _isFollowActive.value = active
        val layout = MacroPadState.activeLayout.value
        if (persist && layout != null) {
            MacroPadState.setLayoutMirrorFollowActive(layout.id, active)
        }
        followAnimationJob?.cancel()
        followAnimationJob = null
        followAnimationCutoutId = null
        layout?.mirrorCutouts?.filter { it.followTouch }?.forEach { cutout ->
            InteractiveCutoutController.removeOverrideCrop(cutout.id)
        }
        if (active) {
            AppStateManager.setViewportEditActive(false)
        }
    }

    fun toggleFollow() {
        setFollowActive(!_isFollowActive.value, persist = true)
    }

    fun onTouchReceived(
        nx: Float,
        ny: Float,
    ) {
        if (!_isCapturing.value || !_isFollowActive.value) return
        if (MacroExecutor.runningMacroIds.value.isNotEmpty()) {
            return
        }
        updateFollowCenter(nx, ny)
    }

    private fun updateFollowCenter(
        nx: Float,
        ny: Float,
    ) {
        val targetCutout = cutouts.value.find { it.followTouch } ?: return
        InteractiveCutoutController.onFollowTouchReceived(targetCutout.id)
        val targetSrcX = (nx - targetCutout.srcWidth / 2f).coerceIn(0f, (1f - targetCutout.srcWidth).coerceAtLeast(0f))
        val targetSrcY = (ny - targetCutout.srcHeight / 2f).coerceIn(0f, (1f - targetCutout.srcHeight).coerceAtLeast(0f))

        if (!targetCutout.motionSmoothing) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            val currentCrop = InteractiveCutoutController.getEffectiveCrop(targetCutout)
            if (currentCrop.srcX != targetSrcX || currentCrop.srcY != targetSrcY) {
                InteractiveCutoutController.updateOverrideCrop(
                    targetCutout.id,
                    NormalizedCrop(
                        srcX = targetSrcX,
                        srcY = targetSrcY,
                        srcWidth = targetCutout.srcWidth,
                        srcHeight = targetCutout.srcHeight,
                    ),
                )
            }
        } else {
            targetFollowX = targetSrcX
            targetFollowY = targetSrcY
            ensureFollowAnimationRunning(targetCutout.id)
        }
    }

    private fun ensureFollowAnimationRunning(cutoutId: String) {
        if (followAnimationJob?.isActive == true && followAnimationCutoutId == cutoutId) return
        followAnimationJob?.cancel()
        followAnimationCutoutId = cutoutId
        followAnimationJob =
            scope.launch {
                while (isActive) {
                    val currTargetX = targetFollowX
                    val currTargetY = targetFollowY

                    val curCutout = cutouts.value.find { it.id == cutoutId } ?: break
                    val curCrop = InteractiveCutoutController.getEffectiveCrop(curCutout)
                    val curX = curCrop.srcX
                    val curY = curCrop.srcY

                    val dx = currTargetX - curX
                    val dy = currTargetY - curY

                    if (abs(dx) < FOLLOW_EPSILON && abs(dy) < FOLLOW_EPSILON) {
                        if (curX != currTargetX || curY != currTargetY) {
                            InteractiveCutoutController.updateOverrideCrop(
                                cutoutId,
                                NormalizedCrop(
                                    srcX = currTargetX,
                                    srcY = currTargetY,
                                    srcWidth = curCutout.srcWidth,
                                    srcHeight = curCutout.srcHeight,
                                ),
                            )
                        }
                        break
                    } else {
                        val nextX = curX + dx * FOLLOW_LERP_FACTOR
                        val nextY = curY + dy * FOLLOW_LERP_FACTOR
                        InteractiveCutoutController.updateOverrideCrop(
                            cutoutId,
                            NormalizedCrop(
                                srcX = nextX,
                                srcY = nextY,
                                srcWidth = curCutout.srcWidth,
                                srcHeight = curCutout.srcHeight,
                            ),
                        )
                    }
                    delay(FOLLOW_FRAME_DELAY_MS)
                }
            }
    }

    /** Resets all transient mirror session state (freeze, follow, interactive cutouts). */
    fun resetMirrorSessionState() {
        AppLog.i(TAG, "resetMirrorSessionState")
        _isFrozen.value = false
        setFrozenBitmap(null)
        clearScreenshotPreview()
        if (_isFollowActive.value) setFollowActive(false)
        InteractiveCutoutController.reset()
    }
}
