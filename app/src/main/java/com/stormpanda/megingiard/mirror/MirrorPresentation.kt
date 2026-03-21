package com.stormpanda.megingiard.mirror

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup

class MirrorPresentation(
    context: Context, 
    display: Display, 
    private val srcWidth: Int, 
    private val srcHeight: Int
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {

    var onSurfaceReady: ((Surface) -> Unit)? = null
    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MegingiardMirror", "MirrorPresentation onCreate launched")

        val sv = SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setZOrderMediaOverlay(true)
        }
        sv.holder.setFixedSize(srcWidth, srcHeight)
        surfaceView = sv
        setContentView(sv)

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
