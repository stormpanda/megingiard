package com.stormpanda.megingiard.splitplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class SplitPlayRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isTopHalf = true
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var currentBitmap: Bitmap? = null
    private val srcRect = Rect()

    fun setHalf(topHalf: Boolean) {
        this.isTopHalf = topHalf
        invalidate()
    }

    fun updateBitmap(bitmap: Bitmap?) {
        this.currentBitmap = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = currentBitmap
        if (bmp == null || bmp.isRecycled) return

        val gameW = bmp.width.toFloat()
        val gameH = bmp.height.toFloat()

        val screenW = width.toFloat()
        val screenH = height.toFloat()

        // Centered box destination (960x1080) on landscape (1920x1080) screen
        val destW = 960f
        val destH = 1080f
        val offsetX = (screenW - destW) / 2f

        canvas.save()

        // Move to the centered box
        canvas.translate(offsetX, 0f)

        // Scale from 960x1080 to physical width and height of the box if different
        val scaleX = destW / 960f
        val scaleY = destH / 1080f
        canvas.scale(scaleX, scaleY)

        // Rotate 90 degrees Clockwise:
        // Move to the right edge and rotate, which maps portrait top to landscape right.
        canvas.translate(960f, 0f)
        canvas.rotate(90f)

        // In the rotated landscape space:
        // Width of the half is 1080, height is 960.
        if (isTopHalf) {
            srcRect.set(0, 0, gameW.toInt(), (gameH / 2).toInt())
        } else {
            srcRect.set(0, (gameH / 2).toInt(), gameW.toInt(), gameH.toInt())
        }

        val drawDest = Rect(0, 0, 1080, 960)
        canvas.drawBitmap(bmp, srcRect, drawDest, paint)

        canvas.restore()
    }
}
