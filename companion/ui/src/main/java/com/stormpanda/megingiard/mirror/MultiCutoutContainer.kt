package com.stormpanda.megingiard.mirror

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.BackgroundScaleMode
import com.stormpanda.megingiard.math.ViewportMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "MultiCutoutContainer"

private const val MCC_TOUCH_TOLERANCE = 0.005f
private const val MCC_UNCROPPED_THRESHOLD = 0.999f
private const val MCC_MAX_ALPHA_FLOAT = 255f
private const val MCC_MAX_ALPHA_INT = 255
private const val TARGET_BLUR_RADIUS = 8f
private const val BLUR_IN_DURATION_MS = 350L
private const val CROSSFADE_OUT_DURATION_MS = 300L
private const val MIN_RENDER_EFFECT_RADIUS = 0.5f
private const val MIN_ANIMATION_DIFF = 0.1f
private const val MIN_ALPHA_THRESHOLD = 0.005f
private const val FULL_ALPHA_FLOAT = 1.0f

internal class MultiCutoutContainer(
    context: Context,
    private val srcWidth: Int,
    private val srcHeight: Int,
) : FrameLayout(context) {
    private val bgDimPaint = Paint()
    private val ambientDimPaint =
        Paint().apply {
            style = Paint.Style.FILL
        }
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
    var bgScaleMode: BackgroundScaleMode = BackgroundScaleMode.FILL
        set(value) {
            if (field != value) {
                field = value
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

    private fun drawBackgroundBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        parentW: Float,
        parentH: Float,
    ) {
        val paint = if (bgImageDim > 0f) bgDimPaint else null
        if (bgScaleMode == BackgroundScaleMode.STRETCH) {
            bgSrcRect.set(0, 0, bitmap.width, bitmap.height)
            bgDestRect.set(0f, 0f, parentW, parentH)
            canvas.drawBitmap(bitmap, bgSrcRect, bgDestRect, paint)
        } else {
            canvas.save()
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val scaleBase =
                if (bgScaleMode == BackgroundScaleMode.FIT) {
                    ViewportMath.calculateAspectFitScale(parentW, parentH, iw, ih)
                } else {
                    ViewportMath.calculateAspectFillScale(parentW, parentH, iw, ih)
                }
            val ws = iw * scaleBase
            val hs = ih * scaleBase

            canvas.translate(parentW / 2f + bgImageOffsetX * parentW, parentH / 2f + bgImageOffsetY * parentH)
            canvas.scale(bgImageScale, bgImageScale)

            bgSrcRect.set(0, 0, bitmap.width, bitmap.height)
            bgDestRect.set(-ws / 2f, -hs / 2f, ws / 2f, hs / 2f)
            canvas.drawBitmap(bitmap, bgSrcRect, bgDestRect, paint)
            canvas.restore()
        }
    }

    var ambientDim: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                updateAmbientDimPaint()
                invalidate()
            }
        }

    private fun updateAmbientDimPaint() {
        if (ambientDim > 0f) {
            val alpha = (ambientDim * MCC_MAX_ALPHA_FLOAT).roundToInt().coerceIn(0, MCC_MAX_ALPHA_INT)
            ambientDimPaint.color = Color.argb(alpha, 0, 0, 0)
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
    private val transparencyMaskPaint =
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    private val maskDestRect = RectF()
    private val cutoutDestRect = RectF()
    private val frozenFramePaint =
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    private val delayedFramePaint =
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    private val cutoutBlurRadii = mutableMapOf<String, Float>()
    private val cutoutFadeAlphas = mutableMapOf<String, Float>()
    private val cutoutBlurAnimators = mutableMapOf<String, ValueAnimator>()
    private val cutoutFadeAnimators = mutableMapOf<String, ValueAnimator>()
    private val cutoutWasFrozen = mutableMapOf<String, Boolean>()
    private val cutoutRenderNodes = mutableMapOf<String, RenderNode>()

    private fun updateCutoutTransitions() {
        val activeCutoutIds = cutouts.map { it.id }.toSet()

        val trackedIds = cutoutBlurAnimators.keys + cutoutFadeAnimators.keys + cutoutWasFrozen.keys
        for (id in trackedIds.toSet()) {
            if (id !in activeCutoutIds) {
                cutoutBlurAnimators.remove(id)?.cancel()
                cutoutFadeAnimators.remove(id)?.cancel()
                cutoutBlurRadii.remove(id)
                cutoutFadeAlphas.remove(id)
                cutoutWasFrozen.remove(id)
                cutoutRenderNodes.remove(id)
            }
        }

        for (cutout in cutouts) {
            val isCutoutHudLost = cutout.freezeOnHudLoss && HudPresenceManager.isCutoutHudLost(cutout.id)
            val isTargetFrozen = isFrozen || isCutoutHudLost
            val wasTargetFrozen = cutoutWasFrozen[cutout.id] ?: false

            if (isTargetFrozen != wasTargetFrozen) {
                cutoutWasFrozen[cutout.id] = isTargetFrozen

                if (isTargetFrozen) {
                    cutoutFadeAnimators.remove(cutout.id)?.cancel()
                    cutoutFadeAlphas[cutout.id] = FULL_ALPHA_FLOAT

                    val currentBlur = cutoutBlurRadii[cutout.id] ?: 0f
                    cutoutBlurAnimators[cutout.id]?.cancel()

                    if (TARGET_BLUR_RADIUS - currentBlur > MIN_ANIMATION_DIFF) {
                        val animator =
                            ValueAnimator.ofFloat(currentBlur, TARGET_BLUR_RADIUS).apply {
                                duration = BLUR_IN_DURATION_MS
                                interpolator = AccelerateDecelerateInterpolator()
                                addUpdateListener { anim ->
                                    cutoutBlurRadii[cutout.id] = anim.animatedValue as Float
                                    invalidate()
                                }
                                addListener(
                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            cutoutBlurAnimators.remove(cutout.id)
                                            cutoutBlurRadii[cutout.id] = TARGET_BLUR_RADIUS
                                            invalidate()
                                        }

                                        override fun onAnimationCancel(animation: Animator) {
                                            cutoutBlurAnimators.remove(cutout.id)
                                        }
                                    },
                                )
                            }
                        cutoutBlurAnimators[cutout.id] = animator
                        animator.start()
                    } else {
                        cutoutBlurRadii[cutout.id] = TARGET_BLUR_RADIUS
                    }
                } else {
                    cutoutBlurAnimators.remove(cutout.id)?.cancel()
                    cutoutBlurRadii[cutout.id] = TARGET_BLUR_RADIUS

                    val currentAlpha = cutoutFadeAlphas[cutout.id] ?: FULL_ALPHA_FLOAT
                    cutoutFadeAnimators[cutout.id]?.cancel()

                    if (currentAlpha > MIN_ALPHA_THRESHOLD) {
                        val animator =
                            ValueAnimator.ofFloat(currentAlpha, 0f).apply {
                                duration = CROSSFADE_OUT_DURATION_MS
                                interpolator = AccelerateDecelerateInterpolator()
                                addUpdateListener { anim ->
                                    cutoutFadeAlphas[cutout.id] = anim.animatedValue as Float
                                    invalidate()
                                }
                                addListener(
                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            cutoutFadeAnimators.remove(cutout.id)
                                            cutoutFadeAlphas[cutout.id] = 0f
                                            cutoutBlurRadii[cutout.id] = 0f
                                            invalidate()
                                        }

                                        override fun onAnimationCancel(animation: Animator) {
                                            cutoutFadeAnimators.remove(cutout.id)
                                        }
                                    },
                                )
                            }
                        cutoutFadeAnimators[cutout.id] = animator
                        animator.start()
                    } else {
                        cutoutFadeAlphas[cutout.id] = 0f
                        cutoutBlurRadii[cutout.id] = 0f
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cutoutBlurAnimators.values.forEach { it.cancel() }
        cutoutBlurAnimators.clear()
        cutoutFadeAnimators.values.forEach { it.cancel() }
        cutoutFadeAnimators.clear()
        cutoutBlurRadii.clear()
        cutoutFadeAlphas.clear()
        cutoutWasFrozen.clear()
        cutoutRenderNodes.clear()
    }

    private fun drawFrozenBitmapToCanvas(
        targetCanvas: Canvas,
        frozenBitmapToDraw: Bitmap,
        isCroppedCutoutBitmap: Boolean,
        dw: Float,
        dh: Float,
        sw: Float,
        sh: Float,
        sx: Float,
        sy: Float,
        paint: Paint,
    ) {
        if (isCroppedCutoutBitmap) {
            cutoutDestRect.set(0f, 0f, dw, dh)
            targetCanvas.drawBitmap(frozenBitmapToDraw, null, cutoutDestRect, paint)
        } else {
            val save = targetCanvas.save()
            try {
                val scaleX = dw / sw
                val scaleY = dh / sh
                targetCanvas.translate(-sx * scaleX, -sy * scaleY)
                targetCanvas.scale(scaleX, scaleY)
                targetCanvas.drawBitmap(frozenBitmapToDraw, 0f, 0f, paint)
            } finally {
                targetCanvas.restoreToCount(save)
            }
        }
    }

    init {
        HudPresenceManager.initialize(context)
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

        updateCutoutTransitions()

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
                drawBackgroundBitmap(canvas, bg, parentW, parentH)
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
                val hasTransparencyMask = cutout.hasTransparencyMask && CutoutMaskManager.hasMask(context, cutout.id)

                val saveCount =
                    if (cutout.opacity < 1f || hasTouching || hasTransparencyMask) {
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

                    val isCutoutHudLost = cutout.freezeOnHudLoss && HudPresenceManager.isCutoutHudLost(cutout.id)
                    val isTargetFrozen = isFrozen || isCutoutHudLost
                    val fadeAlpha = cutoutFadeAlphas[cutout.id] ?: (if (isTargetFrozen) FULL_ALPHA_FLOAT else 0f)
                    val isFrozenOverlayActive = fadeAlpha > MIN_ALPHA_THRESHOLD

                    val cachedFrozenFrame = if (isFrozenOverlayActive) HudPresenceManager.getFrozenFrame(context, cutout.id) else null
                    val fullFrozenBitmap = if (isFrozenOverlayActive && cachedFrozenFrame == null && isFrozen) frozenBitmap else null
                    val hasFrozenBitmap =
                        (cachedFrozenFrame != null && !cachedFrozenFrame.isRecycled) ||
                            (fullFrozenBitmap != null && !fullFrozenBitmap.isRecycled)
                    val showFrozenOverlay = isFrozenOverlayActive && hasFrozenBitmap

                    if (fadeAlpha < FULL_ALPHA_FLOAT || !showFrozenOverlay) {
                        val delayedFrame =
                            if (cutout.streamDelayFrames > 0) {
                                HudPresenceManager.getDelayedFrame(cutout.id, cutout.streamDelayFrames)
                            } else {
                                null
                            }

                        if (delayedFrame != null && !delayedFrame.isRecycled) {
                            cutoutDestRect.set(0f, 0f, dw, dh)
                            canvas.drawBitmap(delayedFrame, null, cutoutDestRect, delayedFramePaint)
                        } else {
                            val isFollowActive = ScreenCaptureManager.isFollowActive.value
                            val isUncropped = cutout.srcWidth >= MCC_UNCROPPED_THRESHOLD && cutout.srcHeight >= MCC_UNCROPPED_THRESHOLD
                            val liveSaveCount = canvas.save()
                            try {
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

                                if (masterView != null) {
                                    drawChild(canvas, masterView, drawTime)
                                    masterViewDrawn = true
                                }
                            } finally {
                                canvas.restoreToCount(liveSaveCount)
                            }
                        }
                    }

                    if (showFrozenOverlay) {
                        val currentBlur = cutoutBlurRadii[cutout.id] ?: TARGET_BLUR_RADIUS
                        val isBlurActive = currentBlur > MIN_RENDER_EFFECT_RADIUS
                        val renderNode =
                            if (isBlurActive) {
                                try {
                                    val node = cutoutRenderNodes.getOrPut(cutout.id) { RenderNode("CutoutBlur_${cutout.id}") }
                                    val intDw = dw.roundToInt().coerceAtLeast(1)
                                    val intDh = dh.roundToInt().coerceAtLeast(1)
                                    node.setPosition(0, 0, intDw, intDh)
                                    val safeRadius = currentBlur.coerceAtLeast(MIN_RENDER_EFFECT_RADIUS)
                                    node.setRenderEffect(RenderEffect.createBlurEffect(safeRadius, safeRadius, Shader.TileMode.CLAMP))
                                    node.setAlpha(fadeAlpha.coerceIn(0f, FULL_ALPHA_FLOAT))
                                    node
                                } catch (e: Throwable) {
                                    AppLog.w(TAG, "RenderNode blur setup failed: ${e.message}")
                                    null
                                }
                            } else {
                                null
                            }

                        val bitmapToDraw = cachedFrozenFrame ?: fullFrozenBitmap!!
                        val isCropped = cachedFrozenFrame != null

                        if (renderNode != null) {
                            val recCanvas = renderNode.beginRecording()
                            try {
                                frozenFramePaint.alpha = MCC_MAX_ALPHA_INT
                                drawFrozenBitmapToCanvas(recCanvas, bitmapToDraw, isCropped, dw, dh, sw, sh, sx, sy, frozenFramePaint)
                            } finally {
                                renderNode.endRecording()
                            }
                            canvas.drawRenderNode(renderNode)
                        } else {
                            frozenFramePaint.alpha = (fadeAlpha * MCC_MAX_ALPHA_FLOAT).roundToInt().coerceIn(0, MCC_MAX_ALPHA_INT)
                            drawFrozenBitmapToCanvas(canvas, bitmapToDraw, isCropped, dw, dh, sw, sh, sx, sy, frozenFramePaint)
                            frozenFramePaint.alpha = MCC_MAX_ALPHA_INT
                        }
                    }

                    if (ambientDim > 0f) {
                        canvas.drawRect(0f, 0f, dw, dh, ambientDimPaint)
                    }

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
                        fun drawEdgeBlend(
                            shader: LinearGradient,
                            scaleX: Float,
                            scaleY: Float,
                            transX: Float,
                            transY: Float,
                        ) {
                            shaderMatrix.reset()
                            shaderMatrix.setScale(scaleX, scaleY)
                            shaderMatrix.postTranslate(transX, transY)
                            shader.setLocalMatrix(shaderMatrix)
                            blendPaint.shader = shader
                            canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                        }
                        if (touchesLeft) drawEdgeBlend(horizontalGradientShader, 2f * leftExt, 1f, -leftExt, 0f)
                        if (touchesRight) drawEdgeBlend(horizontalReverseGradientShader, 2f * rightExt, 1f, dw - rightExt, 0f)
                        if (touchesTop) drawEdgeBlend(verticalGradientShader, 1f, 2f * topExt, 0f, -topExt)
                        if (touchesBottom) drawEdgeBlend(verticalReverseGradientShader, 1f, 2f * bottomExt, 0f, dh - bottomExt)
                        blendPaint.shader = null
                    }

                    if (hasTransparencyMask) {
                        val maskBitmap =
                            CutoutMaskManager.getMask(
                                context = context,
                                cutoutId = cutout.id,
                                translucency = cutout.maskTranslucency,
                                featheringPx = cutout.maskFeathering,
                            )
                        if (maskBitmap != null && !maskBitmap.isRecycled) {
                            maskDestRect.set(0f, 0f, dw, dh)
                            canvas.drawBitmap(maskBitmap, null, maskDestRect, transparencyMaskPaint)
                        }
                    }
                } finally {
                    if (cutout.opacity < 1f || hasTouching || hasTransparencyMask) {
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
                drawBackgroundBitmap(canvas, mask, parentW, parentH)
            }
        } finally {
            canvas.restoreToCount(overallSaveCount)
        }
    }
}
