package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.TextureView

internal class ThrottledTextureView(
    context: Context,
) : TextureView(context) {
    var maxFps: Int = 60
        set(value) {
            if (field != value) {
                field = value
                if (isScheduled) {
                    removeCallbacks(invalidateRunnable)
                    isScheduled = false
                }
                invalidate()
            }
        }
    private var lastInvalidateTime: Long = 0L
    private var isScheduled = false
    private val invalidateRunnable =
        Runnable {
            isScheduled = false
            invalidate()
        }

    override fun invalidate() {
        val now = SystemClock.elapsedRealtime()
        val fps = maxFps.coerceAtLeast(1)
        val interval = if (fps >= 60) 0L else (1000L / fps)
        if (interval == 0L || now - lastInvalidateTime >= interval) {
            if (isScheduled) {
                removeCallbacks(invalidateRunnable)
                isScheduled = false
            }
            lastInvalidateTime = now
            super.invalidate()
        } else {
            if (!isScheduled) {
                isScheduled = true
                val delay = interval - (now - lastInvalidateTime)
                postDelayed(invalidateRunnable, delay)
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in parent class")
    override fun invalidate(dirty: Rect?) {
        invalidate()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in parent class")
    override fun invalidate(
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        invalidate()
    }
}
