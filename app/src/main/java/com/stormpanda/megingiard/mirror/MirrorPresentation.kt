package com.stormpanda.megingiard.mirror

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MirrorPresentation(
    context: Context, 
    private val display: Display, 
    private val srcWidth: Int, 
    private val srcHeight: Int
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {
    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null
    private var surfaceView: SurfaceView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBackPressed() {
        AppStateManager.setMode(AppMode.MEDIA)
    }

    override fun cancel() {
        AppStateManager.setMode(AppMode.MEDIA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lifecycleOwner = MirrorPresentationLifecycleOwner()
        window?.decorView?.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        setOnDismissListener {
            scope.cancel()
            lifecycleOwner.destroy()
        }

        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val targetWidth = metrics.widthPixels
        val targetHeight = metrics.heightPixels

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

        var finalWidth = targetWidth
        var finalHeight = targetHeight

        if (srcRatio > targetRatio) {
            // Source is wider than target. Fit width, calculate height to maintain ratio.
            finalHeight = (targetWidth / srcRatio).toInt()
        } else {
            // Source is taller than target. Fit height, calculate width.
            finalWidth = (targetHeight * srcRatio).toInt()
        }

        ScreenCaptureManager.setSurfaceSize(finalWidth.toFloat(), finalHeight.toFloat())

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val sv = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(finalWidth, finalHeight, Gravity.CENTER)
            setZOrderMediaOverlay(true)
        }
        // Force the hardware buffer memory allocation to match the raw screen pixel coordinates
        sv.holder.setFixedSize(srcWidth, srcHeight)
        surfaceView = sv

        container.addView(sv)

        val composeView = ComposeView(context).apply {
            setContent {
                MirrorScreen()
            }
        }
        container.addView(composeView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(container)

        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceReady?.invoke(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // no-op
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed?.invoke()
            }
        })

        bindStateFlows(sv)
    }

    private fun bindStateFlows(sv: SurfaceView) {
        scope.launch {
            combine(
                AppStateManager.currentMode,
                AppStateManager.isActivityResumed,
                AppStateManager.isOnValidScreen
            ) { mode, isResumed, isValid ->
                mode != AppMode.MEDIA && isResumed && isValid
            }.collect { shouldShow ->
                if (shouldShow) show() else hide()
            }
        }
        scope.launch {
            ScreenCaptureManager.scale.collect { 
                sv.scaleX = it
                sv.scaleY = it
            }
        }
        scope.launch {
            ScreenCaptureManager.offsetX.collect { sv.translationX = it }
        }
        scope.launch {
            ScreenCaptureManager.offsetY.collect { sv.translationY = it }
        }
        scope.launch {
            ScreenCaptureManager.isFrozen.collect { frozen ->
                if (frozen && sv.width > 0 && sv.height > 0) {
                    try {
                        val bitmap = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
                        PixelCopy.request(
                            sv,
                            bitmap,
                            { result ->
                                if (result == PixelCopy.SUCCESS) {
                                    ScreenCaptureManager.setFrozenBitmap(bitmap)
                                } else {
                                    Log.e("MegingiardMirror", "PixelCopy failed with result code: $result")
                                    bitmap.recycle()
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    } catch (e: Exception) {
                        Log.e("MegingiardMirror", "PixelCopy exception", e)
                    }
                } else if (!frozen) {
                    ScreenCaptureManager.setFrozenBitmap(null)
                }
            }
        }
    }
}
