package com.stormpanda.megingiard.splitplay

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "SplitPlayActivity"

class SplitPlayActivity : Activity() {

    private lateinit var renderView: SplitPlayRenderView
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLog.i(TAG, "onCreate")

        // Force full screen, hide status/navigation bars, keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        renderView = SplitPlayRenderView(this).apply {
            setHalf(true) // Top screen shows the top half
        }
        setContentView(renderView)

        // Observe frame buffer updates from the virtual display sandbox
        SplitPlayManager.onFrameAvailable = { bitmap ->
            if (!isFinishing && !isDestroyed) {
                renderView.updateBitmap(bitmap)
            }
        }

        // Observe SplitPlayState lifecycle — auto-finish when inactive
        activityScope.launch {
            SplitPlayManager.state.collectLatest { state ->
                if (state is SplitPlayState.Inactive || state is SplitPlayState.Error) {
                    AppLog.i(TAG, "SplitPlay state is $state, finishing activity")
                    finish()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val actionMasked = ev.actionMasked
        val pointerCount = ev.pointerCount
        val displayId = SplitPlayManager.currentDisplayId
        
        if (displayId < 0) {
            return super.dispatchTouchEvent(ev)
        }

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = ev.actionIndex
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)
                val mapped = SplitPlayTouchMapper.mapTouch(0, x, y)
                if (mapped != null) {
                    SplitPlayManager.injectTouch(displayId, pointerId, 0, mapped.first, mapped.second)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until pointerCount) {
                    val pointerId = ev.getPointerId(i)
                    val x = ev.getX(i)
                    val y = ev.getY(i)
                    val mapped = SplitPlayTouchMapper.mapTouch(0, x, y)
                    if (mapped != null) {
                        SplitPlayManager.injectTouch(displayId, pointerId, 1, mapped.first, mapped.second)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val actionIndex = ev.actionIndex
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)
                val mapped = SplitPlayTouchMapper.mapTouch(0, x, y)
                if (mapped != null) {
                    SplitPlayManager.injectTouch(displayId, pointerId, 2, mapped.first, mapped.second)
                }
            }
        }
        return true // Absorb touches so they don't leak underneath the overlay
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy")
        activityScope.cancel()
        if (SplitPlayManager.onFrameAvailable != null) {
            SplitPlayManager.onFrameAvailable = null
        }
    }
}
