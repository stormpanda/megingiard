package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.TextureView
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

private const val TAG = "MirrorFrameSampler"

/**
 * Thread-safe frame sampler that provides downsampled or native video frames from the active
 * screen mirror surface for real-time analysis (e.g. HUD auto-tuning calibration) and
 * freeze-frame capture.
 *
 * Capturing frames via [TextureView.getBitmap] requires execution on the main UI thread to prevent
 * native HWUI race conditions and segmentation faults in RenderThread's [DeferredLayerUpdater].
 */
internal object MirrorFrameSampler {
    @Volatile
    private var activeTextureView: WeakReference<TextureView>? = null

    fun registerTextureView(tv: TextureView) {
        AppLog.d(TAG, "Registering active TextureView for frame sampling")
        activeTextureView = WeakReference(tv)
    }

    fun unregisterTextureView(tv: TextureView) {
        if (activeTextureView?.get() == tv) {
            AppLog.d(TAG, "Unregistering active TextureView")
            activeTextureView = null
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
