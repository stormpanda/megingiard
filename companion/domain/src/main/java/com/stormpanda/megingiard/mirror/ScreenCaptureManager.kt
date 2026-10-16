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

object ScreenCaptureManager {
    const val SCREENSHOT_SUBDIR = "Pictures/Megingiard"

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _cutouts = MutableStateFlow<List<ScreenCutout>>(emptyList())
    val cutouts: StateFlow<List<ScreenCutout>> = _cutouts.asStateFlow()

    private val _edgeBlendWidthDp = MutableStateFlow(0f)
    val edgeBlendWidthDp: StateFlow<Float> = _edgeBlendWidthDp.asStateFlow()

    private val _maxFps = MutableStateFlow(60)
    val maxFps: StateFlow<Int> = _maxFps.asStateFlow()

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
        _cutouts
            .map { list -> list.any { it.touchProjectionEnabled } }
            .stateIn(scope, SharingStarted.Eagerly, false)

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
                        _edgeBlendWidthDp.value = layout.mirrorEdgeBlendWidth
                        _maxFps.value = layout.mirrorMaxFps
                        _cutouts.value = layout.mirrorCutouts
                        if (oldLayoutId != null && oldLayoutId != layout.id && _isCapturing.value) {
                            setFollowActive(layout.mirrorFollowActive)
                        }
                    } else {
                        previousLayoutId = null
                        _edgeBlendWidthDp.value = 0f
                        _maxFps.value = 60
                        _cutouts.value = emptyList()
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

    fun setCutouts(cutouts: List<ScreenCutout>) {
        AppLog.d(TAG, "setCutouts count=${cutouts.size}")
        _cutouts.value = cutouts
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
        if (persist) {
            val layout = MacroPadState.activeLayout.value
            if (layout != null) {
                MacroPadState.setLayoutMirrorFollowActive(layout.id, active)
            }
        }
        val layout = MacroPadState.activeLayout.value
        if (active) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            if (layout != null) {
                _cutouts.value = layout.mirrorCutouts
            }
            AppStateManager.setViewportEditActive(false)
        } else {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            if (layout != null) {
                _cutouts.value = layout.mirrorCutouts
            }
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
        val targetCutout = _cutouts.value.find { it.followTouch } ?: return
        InteractiveCutoutController.onFollowTouchReceived(targetCutout.id)
        val targetSrcX = (nx - targetCutout.srcWidth / 2f).coerceIn(0f, (1f - targetCutout.srcWidth).coerceAtLeast(0f))
        val targetSrcY = (ny - targetCutout.srcHeight / 2f).coerceIn(0f, (1f - targetCutout.srcHeight).coerceAtLeast(0f))

        val smoothing = targetCutout.motionSmoothing
        if (!smoothing) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            if (targetCutout.srcX != targetSrcX || targetCutout.srcY != targetSrcY) {
                val currentList = _cutouts.value
                val index = currentList.indexOfFirst { it.id == targetCutout.id }
                if (index != -1) {
                    val newList = ArrayList(currentList)
                    newList[index] = targetCutout.copy(srcX = targetSrcX, srcY = targetSrcY)
                    _cutouts.value = newList
                }
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
                val lerpFactor = 0.15f
                val epsilon = 0.001f

                while (isActive) {
                    val currTargetX = targetFollowX
                    val currTargetY = targetFollowY

                    val list = _cutouts.value
                    val index = list.indexOfFirst { it.id == cutoutId }
                    if (index == -1) break
                    val curCutout = list[index]
                    val curX = curCutout.srcX
                    val curY = curCutout.srcY

                    val dx = currTargetX - curX
                    val dy = currTargetY - curY

                    if (abs(dx) < epsilon && abs(dy) < epsilon) {
                        if (curX != currTargetX || curY != currTargetY) {
                            val newList = ArrayList(list)
                            newList[index] = curCutout.copy(srcX = currTargetX, srcY = currTargetY)
                            _cutouts.value = newList
                        }
                        break
                    } else {
                        val nextX = curX + dx * lerpFactor
                        val nextY = curY + dy * lerpFactor
                        val newList = ArrayList(list)
                        newList[index] = curCutout.copy(srcX = nextX, srcY = nextY)
                        _cutouts.value = newList
                    }
                    delay(16)
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
