package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private const val TAG = "MirrorFrameSampler"

/**
 * Thread-safe frame sampler that provides downsampled or native video frames from the active
 * screen mirror surface for real-time analysis (e.g. HUD auto-tuning calibration) and
 * freeze-frame capture.
 *
 * Bounding-box crops are extracted via hardware-accelerated [PixelCopy.request] directly from the
 * active [Surface] on a dedicated background [HandlerThread], eliminating main-thread stalls and
 * multi-megabyte frame readbacks.
 */
internal object MirrorFrameSampler {
    @Volatile
    private var activeTextureView: WeakReference<TextureView>? = null

    @Volatile
    private var activeSurface: WeakReference<Surface>? = null

    private val pixelCopyThread = HandlerThread("MirrorPixelCopy").apply { start() }
    private val pixelCopyHandler = Handler(pixelCopyThread.looper)

    fun registerTextureView(
        tv: TextureView,
        surface: Surface? = null,
    ) {
        AppLog.d(TAG, "Registering active TextureView and Surface for frame sampling")
        activeTextureView = WeakReference(tv)
        activeSurface = surface?.let { WeakReference(it) }
    }

    fun unregisterTextureView(tv: TextureView) {
        if (activeTextureView?.get() == tv) {
            AppLog.d(TAG, "Unregistering active TextureView and Surface")
            activeTextureView = null
            activeSurface = null
        }
    }

    /**
     * Captures a 1:1 hardware crop [cropRect] directly from the active mirror surface via [PixelCopy].
     * Renders into [reusableBitmap] if provided and dimensions match, avoiding heap allocations.
     * Returns null if no active surface or frozen frame is available, or if PixelCopy fails.
     */
    suspend fun captureCrop(
        cropRect: Rect,
        reusableBitmap: Bitmap? = null,
    ): Bitmap? {
        val cropW = cropRect.width()
        val cropH = cropRect.height()
        if (cropW <= 0 || cropH <= 0) return null

        val isFrozen = ScreenCaptureManager.isFrozen.value
        val frozen = if (isFrozen) ScreenCaptureManager.frozenBitmap.value else null
        if (frozen != null) {
            val target =
                if (reusableBitmap != null &&
                    reusableBitmap.width == cropW &&
                    reusableBitmap.height == cropH &&
                    !reusableBitmap.isRecycled
                ) {
                    reusableBitmap
                } else {
                    Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                }
            val canvas = Canvas(target)
            val src = Rect(cropRect)
            val dst = Rect(0, 0, cropW, cropH)
            synchronized(frozen) {
                if (!frozen.isRecycled) {
                    canvas.drawBitmap(frozen, src, dst, null)
                    return target
                }
            }
            if (target !== reusableBitmap && !target.isRecycled) {
                target.recycle()
            }
            return null
        }

        val surface = activeSurface?.get() ?: return null
        if (!surface.isValid) return null

        val targetBitmap =
            if (reusableBitmap != null &&
                reusableBitmap.width == cropW &&
                reusableBitmap.height == cropH &&
                !reusableBitmap.isRecycled
            ) {
                reusableBitmap
            } else {
                try {
                    Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    AppLog.e(TAG, "OOM allocating crop bitmap (${cropW}x$cropH)", e)
                    return null
                }
            }

        return suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(
                    surface,
                    cropRect,
                    targetBitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            if (cont.isActive) cont.resume(targetBitmap)
                        } else {
                            AppLog.w(TAG, "PixelCopy.request failed with result code $result")
                            if (targetBitmap !== reusableBitmap && !targetBitmap.isRecycled) {
                                targetBitmap.recycle()
                            }
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                    pixelCopyHandler,
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Exception requesting PixelCopy crop", e)
                if (targetBitmap !== reusableBitmap && !targetBitmap.isRecycled) {
                    targetBitmap.recycle()
                }
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * Captures a frame of size [width] x [height].
     * If [reusableBitmap] is provided and matches [width] x [height], [TextureView.getBitmap]
     * will render directly into it without allocating new heap memory.
     * Returns null if no active TextureView or frozen frame is available.
     *
     * Dispatches to [Dispatchers.Main.immediate] to ensure thread-safe interaction with the
     * underlying [TextureView] and its [android.view.ThreadedRenderer].
     */
    suspend fun captureFrame(
        width: Int,
        height: Int,
        reusableBitmap: Bitmap? = null,
    ): Bitmap? =
        withContext(Dispatchers.Main.immediate) {
            val tv = activeTextureView?.get()
            if (tv != null && tv.isAvailable && tv.isAttachedToWindow && tv.width > 0 && tv.height > 0) {
                return@withContext try {
                    if (reusableBitmap != null &&
                        reusableBitmap.width == width &&
                        reusableBitmap.height == height &&
                        !reusableBitmap.isRecycled
                    ) {
                        tv.getBitmap(reusableBitmap)
                    } else {
                        tv.getBitmap(width, height)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error capturing frame from TextureView", e)
                    null
                }
            }

            val frozen = ScreenCaptureManager.frozenBitmap.value
            if (frozen != null && !frozen.isRecycled) {
                return@withContext try {
                    if (reusableBitmap != null &&
                        reusableBitmap.width == width &&
                        reusableBitmap.height == height &&
                        !reusableBitmap.isRecycled
                    ) {
                        val canvas = Canvas(reusableBitmap)
                        val srcRect = Rect(0, 0, frozen.width, frozen.height)
                        val dstRect = Rect(0, 0, width, height)
                        canvas.drawBitmap(frozen, srcRect, dstRect, null)
                        reusableBitmap
                    } else {
                        Bitmap.createScaledBitmap(frozen, width, height, true)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error scaling frozen bitmap for frame sampling", e)
                    null
                }
            }

            null
        }
}
