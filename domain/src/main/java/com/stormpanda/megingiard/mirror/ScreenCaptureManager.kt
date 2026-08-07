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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ScreenCaptureManager"

object ScreenCaptureManager {
    const val SCREENSHOT_SUBDIR = "Pictures/Megingiard"

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offsetX = MutableStateFlow(0f)
    val offsetX: StateFlow<Float> = _offsetX.asStateFlow()

    private val _offsetY = MutableStateFlow(0f)
    val offsetY: StateFlow<Float> = _offsetY.asStateFlow()

    private val _surfaceWidth = MutableStateFlow(0f)
    val surfaceWidth: StateFlow<Float> = _surfaceWidth.asStateFlow()

    private val _surfaceHeight = MutableStateFlow(0f)
    val surfaceHeight: StateFlow<Float> = _surfaceHeight.asStateFlow()

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

    private val _screenshotPreview = MutableStateFlow<Bitmap?>(null)
    val screenshotPreview: StateFlow<Bitmap?> = _screenshotPreview.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isTouchProjectionActive = MutableStateFlow(false)
    val isTouchProjectionActive: StateFlow<Boolean> = _isTouchProjectionActive.asStateFlow()

    private val _isFollowActive = MutableStateFlow(false)
    val isFollowActive: StateFlow<Boolean> = _isFollowActive.asStateFlow()

    private val _isPrivilegedMirror = MutableStateFlow(false)
    val isPrivilegedMirror: StateFlow<Boolean> = _isPrivilegedMirror.asStateFlow()

    private var activeLayoutJob: Job? = null
    private var activeCutoutsJob: Job? = null

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        set(value) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            field = value
            restartCollectors()
        }

    init {
        restartCollectors()
    }

    private fun restartCollectors() {
        activeLayoutJob?.cancel()
        activeCutoutsJob?.cancel()

        activeLayoutJob =
            scope.launch {
                MacroPadState.activeLayout.collect { layout ->
                    if (layout != null) {
                        _edgeBlendWidthDp.value = layout.mirrorEdgeBlendWidth
                        _maxFps.value = layout.mirrorMaxFps
                        _cutouts.value = layout.mirrorCutouts
                    } else {
                        _edgeBlendWidthDp.value = 0f
                        _maxFps.value = 60
                        _cutouts.value = emptyList()
                    }
                }
            }

        activeCutoutsJob =
            scope.launch {
                _cutouts.collect { list ->
                    val touchActive = list.any { it.touchProjectionEnabled }
                    _isTouchProjectionActive.value = touchActive
                    if (touchActive) {
                        _isLocked.value = true
                    }
                }
            }
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

    fun setScale(scale: Float) {
        _scale.value = scale
    }

    fun setOffsetX(x: Float) {
        _offsetX.value = x
    }

    fun setOffsetY(y: Float) {
        _offsetY.value = y
    }

    fun setSurfaceSize(
        width: Float,
        height: Float,
    ) {
        _surfaceWidth.value = width
        _surfaceHeight.value = height
    }

    fun setFrozen(frozen: Boolean) {
        AppLog.d(TAG, "setFrozen($frozen)")
        _isFrozen.value = frozen
    }

    fun setFrozenBitmap(bitmap: Bitmap?) {
        AppLog.d(TAG, "setFrozenBitmap(${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"})")
        _frozenBitmap.value?.recycle()
        _frozenBitmap.value = bitmap
    }

    fun toggleFrozen() {
        val next = !_isFrozen.value
        AppLog.d(TAG, "toggleFrozen → $next")
        _isFrozen.value = next
    }

    fun requestScreenshot() {
        AppLog.d(TAG, "requestScreenshot")
        _screenshotRequested.value = true
    }

    fun consumeScreenshotRequest() {
        AppLog.d(TAG, "consumeScreenshotRequest")
        _screenshotRequested.value = false
    }

    fun showScreenshotPreview(bitmap: Bitmap) {
        AppLog.d(TAG, "showScreenshotPreview")
        _screenshotPreview.value?.recycle()
        _screenshotPreview.value = bitmap
    }

    fun clearScreenshotPreview() {
        AppLog.d(TAG, "clearScreenshotPreview")
        _screenshotPreview.value?.recycle()
        _screenshotPreview.value = null
    }

    fun setLocked(locked: Boolean) {
        AppLog.d(TAG, "setLocked($locked)")
        _isLocked.value = locked
    }

    /**
     * Activates or deactivates touch projection.
     * Enabling projection automatically enables lock (zoom/pan while forwarding is
     * unusable). Disabling projection does not automatically release the lock —
     * the user can unlock independently.
     */
    fun setTouchProjectionActive(active: Boolean) {
        AppLog.i(TAG, "setTouchProjectionActive($active)${if (active) " → auto-enabling lock" else ""}")
        val layout = MacroPadState.activeLayout.value
        if (layout != null) {
            val updated = layout.mirrorCutouts.map { it.copy(touchProjectionEnabled = active) }
            MacroPadState.updateLayout(layout.copy(mirrorCutouts = updated))
        }
        _isTouchProjectionActive.value = active
        if (active) _isLocked.value = true
    }

    /**
     * Toggles the lock state. If touch projection is currently active, toggling
     * off also deactivates touch projection (since lock is required for it).
     */
    fun toggleLocked() {
        val newLocked = !_isLocked.value
        AppLog.d(TAG, "toggleLocked → $newLocked${if (!newLocked && _isTouchProjectionActive.value) " (deactivating projection)" else ""}")
        _isLocked.value = newLocked
        if (!newLocked) {
            setTouchProjectionActive(false)
        }
    }

    fun toggleTouchProjection() {
        setTouchProjectionActive(!_isTouchProjectionActive.value)
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
        val targetSrcX = (nx - targetCutout.srcWidth / 2f).coerceIn(0f, 1f - targetCutout.srcWidth)
        val targetSrcY = (ny - targetCutout.srcHeight / 2f).coerceIn(0f, 1f - targetCutout.srcHeight)

        val smoothing = targetCutout.motionSmoothing
        if (!smoothing) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            followAnimationCutoutId = null
            val updated =
                _cutouts.value.map {
                    if (it.id == targetCutout.id) it.copy(srcX = targetSrcX, srcY = targetSrcY) else it
                }
            _cutouts.value = updated
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
                    val curCutout = list.find { it.id == cutoutId }
                    if (curCutout == null) break
                    val curX = curCutout.srcX
                    val curY = curCutout.srcY

                    val dx = currTargetX - curX
                    val dy = currTargetY - curY

                    if (abs(dx) < epsilon && abs(dy) < epsilon) {
                        val updated =
                            _cutouts.value.map {
                                if (it.id == cutoutId) it.copy(srcX = currTargetX, srcY = currTargetY) else it
                            }
                        _cutouts.value = updated
                        break
                    } else {
                        val nextX = curX + dx * lerpFactor
                        val nextY = curY + dy * lerpFactor
                        val updated =
                            _cutouts.value.map {
                                if (it.id == cutoutId) it.copy(srcX = nextX, srcY = nextY) else it
                            }
                        _cutouts.value = updated
                    }
                    delay(10)
                }
            }
    }

    fun setPrivilegedMirror(active: Boolean) {
        AppLog.d(TAG, "setPrivilegedMirror($active)")
        _isPrivilegedMirror.value = active
    }

    /** Resets all transient mirror session state (lock, projection, freeze, follow). */
    fun resetMirrorSessionState() {
        AppLog.i(TAG, "resetMirrorSessionState")
        _isTouchProjectionActive.value = false
        _isLocked.value = false
        _isFrozen.value = false
        setFrozenBitmap(null)
        clearScreenshotPreview()
        if (_isFollowActive.value) setFollowActive(false)
    }
}
