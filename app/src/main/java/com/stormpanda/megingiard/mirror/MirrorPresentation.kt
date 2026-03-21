package com.stormpanda.megingiard.mirror

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout

class MirrorPresentation(
    context: Context, 
    private val display: Display, 
    private val srcWidth: Int, 
    private val srcHeight: Int
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {

    var onSurfaceReady: ((Surface) -> Unit)? = null
    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MegingiardMirror", "MirrorPresentation onCreate launched")

        val metrics = android.util.DisplayMetrics()
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
        
        android.util.Log.d("MegingiardMirror", "MirrorPresentation Ratio scaling: Source $srcWidth x $srcHeight -> Scaled $finalWidth x $finalHeight in Target $targetWidth x $targetHeight")

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
        setContentView(container)

        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                android.util.Log.d("MegingiardMirror", "SurfaceView surfaceCreated fired")
                onSurfaceReady?.invoke(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                android.util.Log.d("MegingiardMirror", "SurfaceView surfaceChanged to ${width}x${height}")
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                android.util.Log.d("MegingiardMirror", "SurfaceView surfaceDestroyed")
            }
        })
    }
}
