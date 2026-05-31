package com.stormpanda.megingiard.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.MainActivity
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import java.lang.ref.WeakReference

private const val TAG = "TransitionOverlayManager"
private const val BACKUP_TIMEOUT_MS = 200L

/**
 * Manages capturing the current Megingiard UI state and presenting it in a persistent,
 * non-interactive accessibility overlay window on the secondary display during the Home relaunch.
 */
object TransitionOverlayManager {

    private var mainActivityRef: WeakReference<MainActivity>? = null
    private var activeViewRef: WeakReference<ImageView>? = null
    private var activeBitmap: Bitmap? = null

    /**
     * Registers the active [MainActivity] reference.
     */
    fun registerActivity(activity: MainActivity) {
        AppLog.d(TAG, "registerActivity")
        mainActivityRef = WeakReference(activity)
    }

    /**
     * Unregisters the [MainActivity] reference to avoid memory leaks.
     */
    fun unregisterActivity() {
        AppLog.d(TAG, "unregisterActivity")
        mainActivityRef = null
    }

    /**
     * Captures a pixel-perfect screenshot of the active [MainActivity] window and
     * displays it on the secondary display inside a non-interactive accessibility overlay.
     * Executes the [onCompleted] callback once the overlay is successfully
     * shown and laid out on the screen (or immediately if capture fails).
     */
    fun captureAndShowOverlay(serviceContext: Context, onCompleted: () -> Unit) {
        val activity = mainActivityRef?.get()
        if (activity == null || activity.isDestroyed || activity.isFinishing) {
            AppLog.w(TAG, "captureAndShowOverlay → cannot capture: activity is null or finishing")
            onCompleted()
            return
        }

        // Only capture and show if screen mirror is NOT already capturing.
        // If mirroring is active, the MirrorPresentation stays visible and flicker-free.
        if (ScreenCaptureManager.isCapturing.value) {
            AppLog.i(TAG, "captureAndShowOverlay → screen mirror is active; skipping transition overlay")
            onCompleted()
            return
        }

        val window = activity.window
        val view = window.decorView
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            AppLog.w(TAG, "captureAndShowOverlay → cannot capture: invalid dimensions ${width}x${height}")
            onCompleted()
            return
        }

        var completedCalled = false
        val completeTask = { reason: String ->
            if (!completedCalled) {
                completedCalled = true
                AppLog.i(TAG, "captureAndShowOverlay → completeTask executed (reason: $reason)")
                onCompleted()
            }
        }

        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            AppLog.i(TAG, "captureAndShowOverlay → requesting PixelCopy on MainActivity window")
            PixelCopy.request(window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    AppLog.i(TAG, "captureAndShowOverlay → PixelCopy succeeded, displaying transition overlay view")
                    
                    // Temporary color inversion for diagnostics
                    val invertedBitmap = invertBitmapColors(bitmap)
                    
                    // Show overlay view using accessibility service context to enable TYPE_ACCESSIBILITY_OVERLAY
                    val overlayView = showOverlay(serviceContext, invertedBitmap)
                    if (overlayView != null) {
                        val setupPreDrawListener = { v: View ->
                            AppLog.i(TAG, "captureAndShowOverlay → overlayView attached/attaching! Registering OnPreDrawListener")
                            val observer = v.viewTreeObserver
                            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                                override fun onPreDraw(): Boolean {
                                    if (v.viewTreeObserver.isAlive) {
                                        v.viewTreeObserver.removeOnPreDrawListener(this)
                                    }
                                    AppLog.i(TAG, "captureAndShowOverlay → onPreDraw triggered! View has finished measure/layout and is rendering. Posting completion to run after draw pass.")
                                    v.post {
                                        completeTask("overlay_render_finished")
                                    }
                                    return true
                                }
                            })
                        }

                        if (overlayView.isAttachedToWindow) {
                            setupPreDrawListener(overlayView)
                        } else {
                            AppLog.d(TAG, "captureAndShowOverlay → registering attach state change listener on overlayView")
                            overlayView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {
                                    setupPreDrawListener(v)
                                }
                                override fun onViewDetachedFromWindow(v: View) {
                                    AppLog.d(TAG, "captureAndShowOverlay → overlayView detached from window")
                                }
                            })
                        }
                    } else {
                        AppLog.w(TAG, "captureAndShowOverlay → overlayView is null, executing backup completion")
                        completeTask("overlay_null")
                    }

                    // Backup safety timeout to ensure we always continue if view attachment / draw events don't fire
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!completedCalled) {
                            AppLog.w(TAG, "captureAndShowOverlay → attach/draw listener timed out; forcing completion")
                            completeTask("backup_timeout")
                        }
                    }, BACKUP_TIMEOUT_MS)
                } else {
                    AppLog.w(TAG, "captureAndShowOverlay → PixelCopy failed with result code $result")
                    bitmap.recycle()
                    completeTask("pixelcopy_failed")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            AppLog.e(TAG, "captureAndShowOverlay → exception during PixelCopy request", e)
            completeTask("exception")
        }
    }

    /**
     * Spawns the transition overlay view directly on the secondary display using WindowManager.
     */
    private fun showOverlay(context: Context, bitmap: Bitmap): ImageView? {
        // Dismiss any existing overlay first to avoid window / bitmap leaks
        dismissOverlay()

        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val secondaryDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            if (secondaryDisplay == null) {
                AppLog.w(TAG, "showOverlay → no secondary display found for transition overlay")
                bitmap.recycle()
                return null
            }

            activeBitmap = bitmap
            
            // Create a window context specifically with TYPE_ACCESSIBILITY_OVERLAY on the target display
            val windowContext = context.createWindowContext(
                secondaryDisplay,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                null
            )
            
            val windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val imageView = ImageView(windowContext).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(bitmap)
            }
            
            // Set type to accessibility overlay so it sits above everything, including Launcher and standard dialogs
            val lp = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                windowAnimations = 0
            }
            
            windowManager.addView(imageView, lp)
            activeViewRef = WeakReference(imageView)
            AppLog.i(TAG, "showOverlay → successfully showed transition overlay view on display ${secondaryDisplay.displayId}")
            return imageView
        } catch (e: Exception) {
            AppLog.e(TAG, "showOverlay → exception showing transition overlay view", e)
            bitmap.recycle()
            return null
        }
    }

    /**
     * Dismisses the transition overlay and recycles its captured bitmap.
     */
    fun dismissOverlay() {
        activeViewRef?.get()?.let { view ->
            AppLog.i(TAG, "dismissOverlay → removing active transition overlay view")
            try {
                val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) {
                AppLog.e(TAG, "dismissOverlay → error removing view", e)
            }
        }
        activeViewRef = null
        activeBitmap?.let {
            AppLog.d(TAG, "dismissOverlay → recycling transition bitmap")
            try {
                it.recycle()
            } catch (e: Exception) {
                AppLog.e(TAG, "dismissOverlay → error recycling bitmap", e)
            }
            activeBitmap = null
        }
    }

    /**
     * Helper function to temporarily invert the colors of the captured bitmap to aid in diagnostics.
     */
    private fun invertBitmapColors(src: Bitmap): Bitmap {
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val dest = Bitmap.createBitmap(src.width, src.height, config)
        val canvas = Canvas(dest)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -1.0f,  0.0f,  0.0f,  0.0f, 255.0f,
                 0.0f, -1.0f,  0.0f,  0.0f, 255.0f,
                 0.0f,  0.0f, -1.0f,  0.0f, 255.0f,
                 0.0f,  0.0f,  0.0f,  1.0f,   0.0f
            )))
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle() // Recycle the original captured bitmap
        return dest
    }
}
