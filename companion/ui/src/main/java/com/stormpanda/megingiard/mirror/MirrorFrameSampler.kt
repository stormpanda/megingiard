package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.view.TextureView
import com.stormpanda.megingiard.AppLog
import java.lang.ref.WeakReference

private const val TAG = "MirrorFrameSampler"

/**
 * Thread-safe frame sampler that provides downsampled video frames from the active
 * screen mirror surface for real-time analysis (e.g. HUD auto-tuning calibration).
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
     * Captures a downsampled frame of size [width] x [height].
     * Returns null if no active TextureView or frozen frame is available.
     */
    fun captureFrame(
        width: Int,
        height: Int,
    ): Bitmap? {
        val tv = activeTextureView?.get()
        if (tv != null && tv.width > 0 && tv.height > 0) {
            return try {
                tv.getBitmap(width, height)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error capturing downsampled frame from TextureView", e)
                null
            }
        }

        val frozen = ScreenCaptureManager.frozenBitmap.value
        if (frozen != null && !frozen.isRecycled) {
            return try {
                Bitmap.createScaledBitmap(frozen, width, height, true)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error scaling frozen bitmap for frame sampling", e)
                null
            }
        }

        return null
    }
}
