package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout
import com.stormpanda.megingiard.math.ViewportMath
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MCC_TOUCH_TOLERANCE = 0.005f
private const val MCC_UNCROPPED_THRESHOLD = 0.999f

internal class MultiCutoutContainer(
    context: Context,
    private val srcWidth: Int,
    private val srcHeight: Int,
) : FrameLayout(context) {
    private val bgDimPaint = Paint()
    private val bgSrcRect = Rect()
    private val bgDestRect = RectF()
    var cutouts: List<ScreenCutout> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var isFrozen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var frozenBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    var bgBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    var useAsMask: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var bgImageScale: Float = 1f
        set(value) {
            field = value
            invalidate()
        }
    var bgImageOffsetX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var bgImageOffsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var bgImageDim: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateBgDimPaint()
                invalidate()
            }
        }

    private fun updateBgDimPaint() {
        val dim = bgImageDim
        if (dim > 0f) {
            val scale = 1f - dim
            val matrix =
                ColorMatrix().apply {
                    setScale(scale, scale, scale, 1f)
                }
            bgDimPaint.colorFilter = ColorMatrixColorFilter(matrix)
        } else {
            bgDimPaint.colorFilter = null
        }
    }

    var viewportScale: Float = 1f
        set(value) {
            field = value
            invalidate()
        }
    var viewportOffsetX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var viewportOffsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val addXfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val transparentToBlackColors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
    private val blackToTransparentColors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
    private val circleBlendColors = intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT)
    private val circleBlendStops = floatArrayOf(0f, 0f, 1f)

    private val horizontalGradientShader = LinearGradient(0f, 0f, 1f, 0f, transparentToBlackColors, null, Shader.TileMode.CLAMP)
    private val horizontalReverseGradientShader = LinearGradient(0f, 0f, 1f, 0f, blackToTransparentColors, null, Shader.TileMode.CLAMP)
    private val verticalGradientShader = LinearGradient(0f, 0f, 0f, 1f, transparentToBlackColors, null, Shader.TileMode.CLAMP)
    private val verticalReverseGradientShader = LinearGradient(0f, 0f, 0f, 1f, blackToTransparentColors, null, Shader.TileMode.CLAMP)
    private val shaderMatrix = Matrix()

    private var cachedCircleRadius = -1f
    private var cachedCircleStop = -1f
    private var cachedCircleShader: Shader? = null

    private val cutoutPaint = Paint()
    private val blendPaint =
        Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    private val circlePath = Path()
    private val maskPaint =
        Paint().apply {
            color = Color.BLACK
        }

    init {
        clipChildren = true
        setWillNotDraw(false)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (childCount > 0) {
            val child = getChildAt(0)
            child.layout(0, 0, srcWidth, srcHeight)
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec),
        )
        if (childCount > 0) {
            val child = getChildAt(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(srcWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(srcHeight, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val masterView = if (childCount > 0) getChildAt(0) else null
        if (masterView == null && (!isFrozen || frozenBitmap == null)) return

        val parentW = width.toFloat()
        val parentH = height.toFloat()
        if (parentW <= 0f || parentH <= 0f) return

        val drawTime = this.drawingTime
        val blendWidthDp = ScreenCaptureManager.edgeBlendWidthDp.value
        val edgeBlending = blendWidthDp > 0f
        val tolerance = MCC_TOUCH_TOLERANCE
        val blendW = (blendWidthDp * resources.displayMetrics.density).roundToInt().toFloat()

        var masterViewDrawn = false

        val overallSaveCount = canvas.save()
        try {
            val bg = bgBitmap
            if (!useAsMask && bg != null) {
                canvas.save()
                val scale = bgImageScale
                val offsetX = bgImageOffsetX * parentW
                val offsetY = bgImageOffsetY * parentH
                val cw = parentW
                val ch = parentH
                val iw = bg.width.toFloat()
                val ih = bg.height.toFloat()
                val scaleBase =
                    ViewportMath
                        .calculateAspectFillScale(cw, ch, iw, ih)
                val ws = iw * scaleBase
                val hs = ih * scaleBase

                canvas.translate(cw / 2f + offsetX, ch / 2f + offsetY)
                canvas.scale(scale, scale)

                bgSrcRect.set(0, 0, bg.width, bg.height)
                bgDestRect.set(-ws / 2f, -hs / 2f, ws / 2f, hs / 2f)
                val paint = if (bgImageDim > 0f) bgDimPaint else null
                canvas.drawBitmap(bg, bgSrcRect, bgDestRect, paint)
                canvas.restore()
            }

            var hasAnyTouchingEdge = false
            if (edgeBlending && cutouts.size > 1) {
                for (i in cutouts.indices) {
                    val c = cutouts[i]
                    if (c.destX > tolerance || c.destX + c.destWidth < 1.0f - tolerance ||
                        c.destY > tolerance || c.destY + c.destHeight < 1.0f - tolerance
                    ) {
                        hasAnyTouchingEdge = true
                        break
                    }
                }
            }

            val cutoutsLayerSaveCount =
                if (hasAnyTouchingEdge) {
                    canvas.saveLayer(0f, 0f, parentW, parentH, null)
                } else {
                    canvas.save()
                }

            for (cutout in cutouts) {
                val dw = (cutout.destWidth * parentW).roundToInt().toFloat()
                val dh = (cutout.destHeight * parentH).roundToInt().toFloat()
                val dx = (cutout.destX * parentW).roundToInt().toFloat()
                val dy = (cutout.destY * parentH).roundToInt().toFloat()

                val sw = cutout.srcWidth * srcWidth
                val sh = cutout.srcHeight * srcHeight
                val sx = cutout.srcX * srcWidth
                val sy = cutout.srcY * srcHeight

                if (dw <= 0f || dh <= 0f || sw <= 0f || sh <= 0f) continue

                val touchesLeft = edgeBlending && (cutout.destX > tolerance)
                val touchesRight = edgeBlending && (cutout.destX + cutout.destWidth < 1.0f - tolerance)
                val touchesTop = edgeBlending && (cutout.destY > tolerance)
                val touchesBottom = edgeBlending && (cutout.destY + cutout.destHeight < 1.0f - tolerance)

                val leftExt = if (touchesLeft) (blendW / 2f).roundToInt().toFloat() else 0f
                val rightExt = if (touchesRight) (blendW / 2f).roundToInt().toFloat() else 0f
                val topExt = if (touchesTop) (blendW / 2f).roundToInt().toFloat() else 0f
                val bottomExt = if (touchesBottom) (blendW / 2f).roundToInt().toFloat() else 0f
                val hasTouching = leftExt > 0f || rightExt > 0f || topExt > 0f || bottomExt > 0f

                val saveCount =
                    if (cutout.opacity < 1f || hasTouching) {
                        cutoutPaint.alpha = (cutout.opacity * 255).toInt()
                        if (hasTouching) {
                            cutoutPaint.xfermode = addXfermode
                        } else {
                            cutoutPaint.xfermode = null
                        }
                        val clipLeft = dx - leftExt
                        val clipTop = dy - topExt
                        val clipRight = dx + dw + rightExt
                        val clipBottom = dy + dh + bottomExt
                        canvas.saveLayer(clipLeft, clipTop, clipRight, clipBottom, cutoutPaint)
                    } else {
                        canvas.save()
                        canvas.clipRect(dx, dy, dx + dw, dy + dh)
                        0
                    }

                try {
                    canvas.translate(dx, dy)
                    if (cutout.shape == CutoutShape.CIRCLE) {
                        circlePath.reset()
                        val r = min(dw, dh) / 2f
                        circlePath.addCircle(dw / 2f, dh / 2f, r, Path.Direction.CW)
                        canvas.clipPath(circlePath)
                    }
                    val innerSaveCount = canvas.save()

                    val isFollowActive = ScreenCaptureManager.isFollowActive.value
                    val isUncropped = cutout.srcWidth >= MCC_UNCROPPED_THRESHOLD && cutout.srcHeight >= MCC_UNCROPPED_THRESHOLD
                    if (cutouts.size == 1 && isFollowActive && isUncropped) {
                        canvas.translate(viewportOffsetX, viewportOffsetY)
                        canvas.scale(viewportScale, viewportScale, dw / 2f, dh / 2f)

                        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
                        val destRatio = dw / dh

                        var fitW = dw
                        var fitH = dh
                        if (srcRatio > destRatio) {
                            fitH = dw / srcRatio
                        } else {
                            fitW = dh * srcRatio
                        }

                        val fitX = (dw - fitW) / 2f
                        val fitY = (dh - fitH) / 2f
                        canvas.translate(fitX, fitY)

                        val scaleX = fitW / srcWidth
                        val scaleY = fitH / srcHeight
                        canvas.scale(scaleX, scaleY)
                    } else {
                        val scaleX = dw / sw
                        val scaleY = dh / sh
                        canvas.translate(-sx * scaleX, -sy * scaleY)
                        canvas.scale(scaleX, scaleY)
                    }

                    if (isFrozen && frozenBitmap != null) {
                        canvas.drawBitmap(frozenBitmap!!, 0f, 0f, null)
                    } else if (masterView != null) {
                        drawChild(canvas, masterView, drawTime)
                        masterViewDrawn = true
                    }

                    canvas.restoreToCount(innerSaveCount)

                    if (cutout.shape == CutoutShape.CIRCLE) {
                        if (edgeBlending) {
                            val r = min(dw, dh) / 2f
                            val stop = max(0f, r - blendW) / r
                            if (r != cachedCircleRadius || stop != cachedCircleStop) {
                                circleBlendStops[1] = stop
                                cachedCircleRadius = r
                                cachedCircleStop = stop
                                cachedCircleShader =
                                    RadialGradient(dw / 2f, dh / 2f, r, circleBlendColors, circleBlendStops, Shader.TileMode.CLAMP)
                            }
                            blendPaint.shader = cachedCircleShader
                            canvas.drawRect(0f, 0f, dw, dh, blendPaint)
                            blendPaint.shader = null
                        }
                    } else if (hasTouching) {
                        if (touchesLeft) {
                            shaderMatrix.reset()
                            shaderMatrix.setScale(2f * leftExt, 1f)
                            shaderMatrix.postTranslate(-leftExt, 0f)
                            horizontalGradientShader.setLocalMatrix(shaderMatrix)
                            blendPaint.shader = horizontalGradientShader
                            canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                        }
                        if (touchesRight) {
                            shaderMatrix.reset()
                            shaderMatrix.setScale(2f * rightExt, 1f)
                            shaderMatrix.postTranslate(dw - rightExt, 0f)
                            horizontalReverseGradientShader.setLocalMatrix(shaderMatrix)
                            blendPaint.shader = horizontalReverseGradientShader
                            canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                        }
                        if (touchesTop) {
                            shaderMatrix.reset()
                            shaderMatrix.setScale(1f, 2f * topExt)
                            shaderMatrix.postTranslate(0f, -topExt)
                            verticalGradientShader.setLocalMatrix(shaderMatrix)
                            blendPaint.shader = verticalGradientShader
                            canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                        }
                        if (touchesBottom) {
                            shaderMatrix.reset()
                            shaderMatrix.setScale(1f, 2f * bottomExt)
                            shaderMatrix.postTranslate(0f, dh - bottomExt)
                            verticalReverseGradientShader.setLocalMatrix(shaderMatrix)
                            blendPaint.shader = verticalReverseGradientShader
                            canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                        }
                        blendPaint.shader = null
                    }
                } finally {
                    if (cutout.opacity < 1f || hasTouching) {
                        canvas.restoreToCount(saveCount)
                    } else {
                        canvas.restore()
                    }
                }
            }

            if (!masterViewDrawn && !isFrozen && masterView != null && cutouts.isNotEmpty()) {
                val saveCount = canvas.save()
                canvas.clipRect(0f, 0f, 1f, 1f)
                drawChild(canvas, masterView, drawTime)
                canvas.drawRect(0f, 0f, 1f, 1f, maskPaint)
                canvas.restoreToCount(saveCount)
            }

            canvas.restoreToCount(cutoutsLayerSaveCount)

            val mask = bgBitmap
            if (useAsMask && mask != null) {
                canvas.save()
                val scale = bgImageScale
                val offsetX = bgImageOffsetX * parentW
                val offsetY = bgImageOffsetY * parentH
                val cw = parentW
                val ch = parentH
                val iw = mask.width.toFloat()
                val ih = mask.height.toFloat()
                val scaleBase =
                    ViewportMath
                        .calculateAspectFillScale(cw, ch, iw, ih)
                val ws = iw * scaleBase
                val hs = ih * scaleBase

                canvas.translate(cw / 2f + offsetX, ch / 2f + offsetY)
                canvas.scale(scale, scale)

                bgSrcRect.set(0, 0, mask.width, mask.height)
                bgDestRect.set(-ws / 2f, -hs / 2f, ws / 2f, hs / 2f)
                val paint = if (bgImageDim > 0f) bgDimPaint else null
                canvas.drawBitmap(mask, bgSrcRect, bgDestRect, paint)
                canvas.restore()
            }
        } finally {
            canvas.restoreToCount(overallSaveCount)
        }
    }
}
