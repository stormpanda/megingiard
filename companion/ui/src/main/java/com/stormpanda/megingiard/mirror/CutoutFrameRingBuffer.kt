package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.stormpanda.megingiard.AppLog

private const val TAG = "CutoutFrameRingBuffer"

/**
 * Thread-safe, zero-allocation circular buffer of cropped frame bitmaps for a single cutout.
 *
 * Pre-allocates an array of [capacity] bitmaps and backing [Canvas] instances of size ([width], [height])
 * to delay the live cutout stream by 1..[capacity] ticks (~16..160 ms) without runtime heap allocations.
 *
 * @param width Width in pixels of the cropped cutout.
 * @param height Height in pixels of the cropped cutout.
 * @param capacity Maximum number of historical frames stored in the ring buffer.
 */
internal class CutoutFrameRingBuffer(
    val width: Int,
    val height: Int,
    val capacity: Int,
) {
    private val frames: Array<Bitmap> =
        Array(capacity) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    private val canvases: Array<Canvas> =
        Array(capacity) {
            Canvas(frames[it])
        }

    private val srcRect = Rect()
    private val dstRect = Rect(0, 0, width, height)
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var writeHead = -1
    private var count = 0

    init {
        AppLog.d(TAG, "Allocated ring buffer: ${width}x$height, capacity=$capacity")
    }

    /**
     * Copies a cropped subregion of [srcBitmap] at ([cropX], [cropY]) into the next ring slot.
     */
    @Synchronized
    fun pushFrame(
        srcBitmap: Bitmap,
        cropX: Int,
        cropY: Int,
    ) {
        val nextIdx = (writeHead + 1) % capacity
        val targetCanvas = canvases[nextIdx]
        srcRect.set(cropX, cropY, cropX + width, cropY + height)
        targetCanvas.drawBitmap(srcBitmap, srcRect, dstRect, blitPaint)
        writeHead = nextIdx
        if (count < capacity) {
            count++
        }
    }

    /**
     * Retrieves the historical frame from [delayFrames] ticks in the past.
     * Clamps delay between 0 (most recent frame) and [count] - 1 (oldest buffered frame).
     *
     * Returns null if no frames have been pushed yet.
     */
    @Synchronized
    fun getDelayedFrame(delayFrames: Int): Bitmap? {
        if (count == 0 || writeHead < 0) return null
        val effectiveDelay = delayFrames.coerceIn(0, count - 1)
        val readIdx = (writeHead - effectiveDelay + capacity) % capacity
        val bmp = frames[readIdx]
        return if (!bmp.isRecycled) bmp else null
    }

    /**
     * Recycles all internal bitmaps and resets the buffer.
     */
    @Synchronized
    fun recycle() {
        AppLog.d(TAG, "Recycling ring buffer: ${width}x$height, capacity=$capacity")
        for (bmp in frames) {
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        count = 0
        writeHead = -1
    }
}
