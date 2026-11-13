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
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.BackgroundScaleMode
import com.stormpanda.megingiard.macropad.CutoutLostAnchorEffect
import com.stormpanda.megingiard.macropad.LayoutCanvasSpec
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.math.DesignViewport
import com.stormpanda.megingiard.math.ViewportMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "MultiCutoutContainer"

private const val MCC_TOUCH_TOLERANCE = 0.005f
private const val MCC_MAX_ALPHA_FLOAT = 255f
private const val MCC_MAX_ALPHA_INT = 255
private const val TARGET_BLUR_RADIUS = 8f
private const val BLUR_TRANSITION_DURATION_MS = 300L
private const val MIN_ALPHA_THRESHOLD = 0.005f
private const val FULL_ALPHA_FLOAT = 1.0f
private const val FROZEN_INACTIVE_SATURATION = 0.6f
private const val FROZEN_INACTIVE_BRIGHTNESS = 0.65f
private const val MCC_ROTATION_90 = 90
private const val MCC_ROTATION_270 = 270

internal class MultiCutoutContainer(
    context: Context,
    private val srcWidth: Int,
    private val srcHeight: Int,
) : FrameLayout(context) {
    private val bgDimPaint = Paint()
    private val maskDimPaint = Paint()
    private val ambientDimPaint =
        Paint().apply {
            style = Paint.Style.FILL
        }
    private val bgSrcRect = Rect()
    private val bgDestRect = RectF()
    private val overlayMaskSrcRect = Rect()
    private val overlayMaskDestRect = RectF()
    private var aboveMaskCutouts: List<ScreenCutout> = emptyList()
    private var belowMaskCutouts: List<ScreenCutout> = emptyList()
    private var designCanvasSpec: LayoutCanvasSpec? = null

    fun setDesignCanvas(spec: LayoutCanvasSpec?) {
        if (designCanvasSpec != spec) {
            AppLog.d(TAG, "setDesignCanvas: $designCanvasSpec -> $spec")
            designCanvasSpec = spec
            requestLayout()
            invalidate()
        }
    }

    internal fun resolveViewport(
        hostW: Float = width.toFloat(),
        hostH: Float = height.toFloat(),
    ): DesignViewport {
        val spec = designCanvasSpec
        return if (spec != null) {
            ViewportMath.calculateDesignViewport(
                hostWidthPx = hostW,
                hostHeightPx = hostH,
                designWidthPx = spec.widthPx.toFloat(),
                designHeightPx = spec.heightPx.toFloat(),
            )
        } else {
            ViewportMath.calculateDesignViewport(
                hostWidthPx = hostW,
                hostHeightPx = hostH,
                designWidthPx = hostW,
                designHeightPx = hostH,
            )
        }
    }

    var cutouts: List<ScreenCutout> = emptyList()
        set(value) {
            field = value
            val (above, below) = value.partition { it.renderAboveMask }
            aboveMaskCutouts = above
            belowMaskCutouts = below
            pruneStaleCutoutResources(value)
            CutoutMaskManager.preloadCutoutsAsync(context, value, ::postInvalidateOnAnimation)
            invalidate()
        }
    var isFrozen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isViewportEditActive: Boolean = false
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
    var maskBitmap: Bitmap? = null
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
    var maskImageScale: Float = 1f
        set(value) {
            field = value
            invalidate()
        }
    var maskImageOffsetX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var maskImageOffsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var maskImageDim: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateMaskDimPaint()
                invalidate()
            }
        }
    var maskScaleMode: BackgroundScaleMode = BackgroundScaleMode.FILL
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

    private fun updateMaskDimPaint() {
        val dim = maskImageDim
        if (dim > 0f) {
            val scale = 1f - dim
            val matrix =
                ColorMatrix().apply {
                    setScale(scale, scale, scale, 1f)
                }
            maskDimPaint.colorFilter = ColorMatrixColorFilter(matrix)
        } else {
            maskDimPaint.colorFilter = null
        }
    }

    private fun drawBackgroundBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        viewport: DesignViewport,
    ) {
        val viewportW = viewport.viewportWidthPx
        val viewportH = viewport.viewportHeightPx
        if (viewportW <= 0f || viewportH <= 0f) return

        val saveCount = canvas.save()
        try {
            canvas.clipRect(
                viewport.leftPx,
                viewport.topPx,
                viewport.leftPx + viewportW,
                viewport.topPx + viewportH,
            )
            canvas.translate(viewport.leftPx, viewport.topPx)

            val paint = if (bgImageDim > 0f) bgDimPaint else null
            if (bgScaleMode == BackgroundScaleMode.STRETCH) {
                bgSrcRect.set(0, 0, bitmap.width, bitmap.height)
                bgDestRect.set(0f, 0f, viewportW, viewportH)
                canvas.drawBitmap(bitmap, bgSrcRect, bgDestRect, paint)
            } else {
                val iw = bitmap.width.toFloat()
                val ih = bitmap.height.toFloat()
                val scaleBase =
                    if (bgScaleMode == BackgroundScaleMode.FIT) {
                        ViewportMath.calculateAspectFitScale(viewportW, viewportH, iw, ih)
                    } else {
                        ViewportMath.calculateAspectFillScale(viewportW, viewportH, iw, ih)
                    }
                val ws = iw * scaleBase
                val hs = ih * scaleBase

                canvas.translate(
                    viewportW / 2f + bgImageOffsetX * viewportW,
                    viewportH / 2f + bgImageOffsetY * viewportH,
                )
                canvas.scale(bgImageScale, bgImageScale)

                bgSrcRect.set(0, 0, bitmap.width, bitmap.height)
                bgDestRect.set(-ws / 2f, -hs / 2f, ws / 2f, hs / 2f)
                canvas.drawBitmap(bitmap, bgSrcRect, bgDestRect, paint)
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun drawMaskBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        viewport: DesignViewport,
    ) {
        val viewportW = viewport.viewportWidthPx
        val viewportH = viewport.viewportHeightPx
        if (viewportW <= 0f || viewportH <= 0f) return

        val saveCount = canvas.save()
        try {
            canvas.clipRect(
                viewport.leftPx,
                viewport.topPx,
                viewport.leftPx + viewportW,
                viewport.topPx + viewportH,
            )
            canvas.translate(viewport.leftPx, viewport.topPx)

            val paint = if (maskImageDim > 0f) maskDimPaint else null
            if (maskScaleMode == BackgroundScaleMode.STRETCH) {
                overlayMaskSrcRect.set(0, 0, bitmap.width, bitmap.height)
                overlayMaskDestRect.set(0f, 0f, viewportW, viewportH)
                canvas.drawBitmap(bitmap, overlayMaskSrcRect, overlayMaskDestRect, paint)
            } else {
                val iw = bitmap.width.toFloat()
                val ih = bitmap.height.toFloat()
                val scaleBase =
                    if (maskScaleMode == BackgroundScaleMode.FIT) {
                        ViewportMath.calculateAspectFitScale(viewportW, viewportH, iw, ih)
                    } else {
                        ViewportMath.calculateAspectFillScale(viewportW, viewportH, iw, ih)
                    }
                val ws = iw * scaleBase
                val hs = ih * scaleBase

                canvas.translate(
                    viewportW / 2f + maskImageOffsetX * viewportW,
                    viewportH / 2f + maskImageOffsetY * viewportH,
                )
                canvas.scale(maskImageScale, maskImageScale)

                overlayMaskSrcRect.set(0, 0, bitmap.width, bitmap.height)
                overlayMaskDestRect.set(-ws / 2f, -hs / 2f, ws / 2f, hs / 2f)
                canvas.drawBitmap(bitmap, overlayMaskSrcRect, overlayMaskDestRect, paint)
            }
        } finally {
            canvas.restoreToCount(saveCount)
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

    private val addXfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val transparentToBlackColors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
    private val blackToTransparentColors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
    private val circleBlendColors = intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT)
    private val circleBlendStops = floatArrayOf(0f, 0f, 1f)

    private val horizontalGradientShader =
        LinearGradient(0f, 0f, 1f, 0f, transparentToBlackColors, null, Shader.TileMode.CLAMP)
    private val horizontalReverseGradientShader =
        LinearGradient(0f, 0f, 1f, 0f, blackToTransparentColors, null, Shader.TileMode.CLAMP)
    private val verticalGradientShader =
        LinearGradient(0f, 0f, 0f, 1f, transparentToBlackColors, null, Shader.TileMode.CLAMP)
    private val verticalReverseGradientShader =
        LinearGradient(0f, 0f, 0f, 1f, blackToTransparentColors, null, Shader.TileMode.CLAMP)
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
    private val frozenInactiveColorFilter =
        run {
            val satMatrix = ColorMatrix().apply { setSaturation(FROZEN_INACTIVE_SATURATION) }
            val scaleMatrix =
                ColorMatrix().apply {
                    setScale(FROZEN_INACTIVE_BRIGHTNESS, FROZEN_INACTIVE_BRIGHTNESS, FROZEN_INACTIVE_BRIGHTNESS, 1f)
                }
            val combined = ColorMatrix(satMatrix).apply { postConcat(scaleMatrix) }
            ColorMatrixColorFilter(combined)
        }
    private val cutoutBlurAlphas = mutableMapOf<String, Float>()
    private val cutoutTransitionAnimators = mutableMapOf<String, ValueAnimator>()
    private val cutoutWasFrozen = mutableMapOf<String, Boolean>()
    private val cutoutRenderNodes = mutableMapOf<String, RenderNode>()
    private val cutoutRenderNodeBitmaps = mutableMapOf<String, Bitmap>()
    private val cutoutRenderNodeWidths = mutableMapOf<String, Int>()
    private val cutoutRenderNodeHeights = mutableMapOf<String, Int>()
    private val layerBoundsRect = RectF()

    private fun pruneStaleCutoutResources(activeCutouts: List<ScreenCutout>) {
        if (cutoutRenderNodes.isNotEmpty()) {
            val iterator = cutoutRenderNodes.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (activeCutouts.none { it.id == entry.key }) {
                    entry.value.discardDisplayList()
                    iterator.remove()
                    cutoutRenderNodeBitmaps.remove(entry.key)
                    cutoutRenderNodeWidths.remove(entry.key)
                    cutoutRenderNodeHeights.remove(entry.key)
                }
            }
        }

        if (cutoutWasFrozen.isNotEmpty()) {
            val iterator = cutoutWasFrozen.keys.iterator()
            while (iterator.hasNext()) {
                val id = iterator.next()
                if (activeCutouts.none { it.id == id }) {
                    cutoutTransitionAnimators.remove(id)?.cancel()
                    cutoutBlurAlphas.remove(id)
                    iterator.remove()
                }
            }
        }
    }

    private fun computeCutoutsBounds(
        cutouts: List<ScreenCutout>,
        viewport: DesignViewport,
        parentW: Float,
        parentH: Float,
        blendW: Float,
        outRect: RectF,
    ) {
        var minX = parentW
        var minY = parentH
        var maxX = 0f
        var maxY = 0f
        for (i in cutouts.indices) {
            val c = cutouts[i]
            val dx = viewport.normToScreenX(c.destX).roundToInt().toFloat()
            val dy = viewport.normToScreenY(c.destY).roundToInt().toFloat()
            val dw = (c.destWidth * viewport.viewportWidthPx).roundToInt().toFloat()
            val dh = (c.destHeight * viewport.viewportHeightPx).roundToInt().toFloat()
            if (dx - blendW < minX) minX = dx - blendW
            if (dy - blendW < minY) minY = dy - blendW
            if (dx + dw + blendW > maxX) maxX = dx + dw + blendW
            if (dy + dh + blendW > maxY) maxY = dy + dh + blendW
        }
        val left = minX.coerceIn(0f, parentW)
        val top = minY.coerceIn(0f, parentH)
        val right = maxX.coerceIn(left, parentW)
        val bottom = maxY.coerceIn(top, parentH)
        outRect.set(left, top, right, bottom)
    }

    private fun updateCutoutTransitions() {
        val isEditing =
            isViewportEditActive ||
                AppStateManager.isViewportEditActive.value ||
                VisualAutoTuneCoordinator.uiState.value.isCalibrating ||
                AnchorTestCoordinator.uiState.value.isTesting

        val activeLayout = MacroPadState.activeLayout.value
        val isLayoutAnchorActive = activeLayout?.visualAnchor?.enabled == true
        val isLayoutAnchorLost =
            !isEditing && activeLayout != null && isLayoutAnchorActive &&
                AnchorPresenceManager.isLayoutAnchorLost(activeLayout.id)

        val anchorHasFreeze = activeLayout?.visualAnchor?.hasEffect(CutoutLostAnchorEffect.FREEZE) == true
        val anchorHasBlur = activeLayout?.visualAnchor?.hasEffect(CutoutLostAnchorEffect.BLUR) == true

        val shouldFreeze = isLayoutAnchorLost && anchorHasFreeze
        val shouldBlur = isLayoutAnchorLost && anchorHasBlur

        val effectiveManualFrozen = !isEditing && isFrozen
        val isTargetFrozen = effectiveManualFrozen || shouldFreeze
        val targetAlpha = if (shouldBlur) FULL_ALPHA_FLOAT else 0f

        for (cutout in cutouts) {
            val wasTargetFrozen = cutoutWasFrozen[cutout.id]
            val isFrozenChanged = wasTargetFrozen == null || isTargetFrozen != wasTargetFrozen
            if (isFrozenChanged) {
                cutoutWasFrozen[cutout.id] = isTargetFrozen
            }

            val currentAlpha = cutoutBlurAlphas[cutout.id] ?: 0f
            val isAlphaChanged = abs(targetAlpha - currentAlpha) > MIN_ALPHA_THRESHOLD
            val isAnimating = cutoutTransitionAnimators.containsKey(cutout.id)

            if (isFrozenChanged || (isAlphaChanged && !isAnimating)) {
                cutoutBlurAlphas[cutout.id] = currentAlpha
                cutoutTransitionAnimators.remove(cutout.id)?.cancel()

                if (isAlphaChanged) {
                    val animator =
                        ValueAnimator.ofFloat(currentAlpha, targetAlpha).apply {
                            duration = BLUR_TRANSITION_DURATION_MS
                            interpolator = AccelerateDecelerateInterpolator()
                            addUpdateListener { anim ->
                                cutoutBlurAlphas[cutout.id] = anim.animatedValue as Float
                                invalidate()
                            }
                            addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        cutoutTransitionAnimators.remove(cutout.id)
                                        cutoutBlurAlphas[cutout.id] = targetAlpha
                                        invalidate()
                                    }

                                    override fun onAnimationCancel(animation: Animator) {
                                        cutoutTransitionAnimators.remove(cutout.id)
                                    }
                                },
                            )
                        }
                    cutoutTransitionAnimators[cutout.id] = animator
                    animator.start()
                } else {
                    cutoutBlurAlphas[cutout.id] = targetAlpha
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cutoutTransitionAnimators.values.forEach { it.cancel() }
        cutoutTransitionAnimators.clear()
        cutoutBlurAlphas.clear()
        cutoutWasFrozen.clear()
        cutoutRenderNodes.values.forEach { it.discardDisplayList() }
        cutoutRenderNodes.clear()
        cutoutRenderNodeBitmaps.clear()
        cutoutRenderNodeWidths.clear()
        cutoutRenderNodeHeights.clear()
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
        AnchorPresenceManager.initialize(context)
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

    private fun drawSingleCutout(
        canvas: Canvas,
        cutout: ScreenCutout,
        viewport: DesignViewport,
        edgeBlending: Boolean,
        tolerance: Float,
        blendW: Float,
        isTargetFrozen: Boolean,
        effectiveManualFrozen: Boolean,
        shouldBlur: Boolean,
        drawTime: Long,
        masterView: View?,
    ): Boolean {
        var masterViewDrawn = false
        val effectiveCrop = InteractiveCutoutController.getEffectiveCrop(cutout)
        val dx = viewport.normToScreenX(cutout.destX).roundToInt().toFloat()
        val dy = viewport.normToScreenY(cutout.destY).roundToInt().toFloat()
        val dw = (cutout.destWidth * viewport.viewportWidthPx).roundToInt().toFloat()
        val dh = (cutout.destHeight * viewport.viewportHeightPx).roundToInt().toFloat()

        val sw = effectiveCrop.srcWidth * srcWidth
        val sh = effectiveCrop.srcHeight * srcHeight
        val sx = effectiveCrop.srcX * srcWidth
        val sy = effectiveCrop.srcY * srcHeight

        if (dw <= 0f || dh <= 0f || sw <= 0f || sh <= 0f) return false

        val touchesLeft = edgeBlending && (cutout.destX > tolerance)
        val touchesRight = edgeBlending && (cutout.destX + cutout.destWidth < 1.0f - tolerance)
        val touchesTop = edgeBlending && (cutout.destY > tolerance)
        val touchesBottom = edgeBlending && (cutout.destY + cutout.destHeight < 1.0f - tolerance)

        val leftExt = if (touchesLeft) (blendW / 2f).roundToInt().toFloat() else 0f
        val rightExt = if (touchesRight) (blendW / 2f).roundToInt().toFloat() else 0f
        val topExt = if (touchesTop) (blendW / 2f).roundToInt().toFloat() else 0f
        val bottomExt = if (touchesBottom) (blendW / 2f).roundToInt().toFloat() else 0f
        val hasTouching = leftExt > 0f || rightExt > 0f || topExt > 0f || bottomExt > 0f
        val hasTransparencyMask =
            cutout.hasTransparencyMask &&
                CutoutMaskManager.hasCachedMaskOrLoadAsync(context, cutout.id, ::postInvalidateOnAnimation)

        val saveCount =
            if (cutout.opacity < 1f || hasTouching || hasTransparencyMask) {
                cutoutPaint.alpha = (cutout.opacity * MCC_MAX_ALPHA_FLOAT).roundToInt().coerceIn(0, MCC_MAX_ALPHA_INT)
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

            val blurAlpha =
                cutoutBlurAlphas[cutout.id] ?: (if (shouldBlur) FULL_ALPHA_FLOAT else 0f)

            val cachedFrozenFrame =
                if (isTargetFrozen && !effectiveManualFrozen) {
                    AnchorPresenceManager.getCachedFrozenFrameOrLoadAsync(
                        context = context,
                        cutoutId = cutout.id,
                        onLoaded = ::postInvalidateOnAnimation,
                    )
                } else {
                    null
                }
            val fullFrozenBitmap = if (cachedFrozenFrame == null && effectiveManualFrozen) frozenBitmap else null
            val frozenBitmapToDraw = cachedFrozenFrame ?: fullFrozenBitmap
            val isCropped = cachedFrozenFrame != null
            val hasFrozenBitmap = frozenBitmapToDraw != null && !frozenBitmapToDraw.isRecycled

            val staticAssetBitmap =
                if (cutout.renderAsStaticAsset && hasTransparencyMask) {
                    CutoutMaskManager.getCachedStaticAssetOrLoadAsync(
                        context = context,
                        cutoutId = cutout.id,
                        translucency = cutout.maskTranslucency,
                        sensitivity = cutout.maskSensitivity,
                        cavityHealing = cutout.maskCavityHealing,
                        onLoaded = ::postInvalidateOnAnimation,
                    )
                } else {
                    null
                }
            val isStaticAssetDrawn = staticAssetBitmap != null && !staticAssetBitmap.isRecycled

            val isQuarterTurn = (cutout.rotation == MCC_ROTATION_90 || cutout.rotation == MCC_ROTATION_270)
            val contentW = if (isQuarterTurn) dh else dw
            val contentH = if (isQuarterTurn) dw else dh

            val contentSaveCount = canvas.save()
            try {
                canvas.translate(dw / 2f, dh / 2f)
                if (cutout.rotation != 0) {
                    canvas.rotate(cutout.rotation.toFloat())
                }
                canvas.translate(-contentW / 2f, -contentH / 2f)

                if (cutout.flipHorizontal) {
                    canvas.scale(-1f, 1f, contentW / 2f, contentH / 2f)
                }
                if (cutout.flipVertical) {
                    canvas.scale(1f, -1f, contentW / 2f, contentH / 2f)
                }

                // 1. Base Layer
                if (isStaticAssetDrawn) {
                    cutoutDestRect.set(0f, 0f, contentW, contentH)
                    canvas.drawBitmap(staticAssetBitmap, null, cutoutDestRect, frozenFramePaint)
                } else if (isTargetFrozen && hasFrozenBitmap) {
                    // Frozen state (anchor lost or manual freeze): render sharp frozen frame base layer
                    if (blurAlpha < FULL_ALPHA_FLOAT) {
                        frozenFramePaint.alpha = MCC_MAX_ALPHA_INT
                        drawFrozenBitmapToCanvas(
                            canvas,
                            frozenBitmapToDraw,
                            isCropped,
                            contentW,
                            contentH,
                            sw,
                            sh,
                            sx,
                            sy,
                            frozenFramePaint,
                        )
                    }
                } else if (!isTargetFrozen || blurAlpha < FULL_ALPHA_FLOAT) {
                    // Live stream fallback
                    val liveSaveCount = canvas.save()
                    try {
                        val scaleX = contentW / sw
                        val scaleY = contentH / sh
                        canvas.translate(-sx * scaleX, -sy * scaleY)
                        canvas.scale(scaleX, scaleY)

                        if (masterView != null) {
                            drawChild(canvas, masterView, drawTime)
                            masterViewDrawn = true
                        }
                    } finally {
                        canvas.restoreToCount(liveSaveCount)
                    }
                }

                // 2. Top Frosted Blur Layer (8px blur, opacity = blurAlpha)
                if (!isStaticAssetDrawn && blurAlpha > MIN_ALPHA_THRESHOLD && (hasFrozenBitmap || masterView != null)) {
                    val bitmapToBlur = if (hasFrozenBitmap) frozenBitmapToDraw else null
                    val intContentW = contentW.roundToInt().coerceAtLeast(1)
                    val intContentH = contentH.roundToInt().coerceAtLeast(1)
                    val renderNode =
                        try {
                            val node =
                                cutoutRenderNodes.getOrPut(cutout.id) { RenderNode("CutoutBlur_${cutout.id}") }
                            val needsRecord =
                                bitmapToBlur == null ||
                                    cutoutRenderNodeBitmaps[cutout.id] !== bitmapToBlur ||
                                    cutoutRenderNodeWidths[cutout.id] != intContentW ||
                                    cutoutRenderNodeHeights[cutout.id] != intContentH

                            if (needsRecord) {
                                node.setPosition(0, 0, intContentW, intContentH)
                                val blurEffect =
                                    RenderEffect.createBlurEffect(
                                        TARGET_BLUR_RADIUS,
                                        TARGET_BLUR_RADIUS,
                                        Shader.TileMode.CLAMP,
                                    )
                                node.setRenderEffect(
                                    if (bitmapToBlur != null) {
                                        blurEffect
                                    } else {
                                        RenderEffect.createChainEffect(
                                            blurEffect,
                                            RenderEffect.createColorFilterEffect(frozenInactiveColorFilter),
                                        )
                                    },
                                )
                                val recCanvas = node.beginRecording()
                                try {
                                    if (bitmapToBlur != null) {
                                        frozenFramePaint.alpha = MCC_MAX_ALPHA_INT
                                        frozenFramePaint.colorFilter = frozenInactiveColorFilter
                                        drawFrozenBitmapToCanvas(
                                            recCanvas,
                                            bitmapToBlur,
                                            isCropped,
                                            contentW,
                                            contentH,
                                            sw,
                                            sh,
                                            sx,
                                            sy,
                                            frozenFramePaint,
                                        )
                                    } else if (masterView != null) {
                                        val scaleX = contentW / sw
                                        val scaleY = contentH / sh
                                        recCanvas.translate(-sx * scaleX, -sy * scaleY)
                                        recCanvas.scale(scaleX, scaleY)
                                        drawChild(recCanvas, masterView, drawTime)
                                        masterViewDrawn = true
                                    }
                                } finally {
                                    frozenFramePaint.colorFilter = null
                                    node.endRecording()
                                }
                                if (bitmapToBlur != null) {
                                    cutoutRenderNodeBitmaps[cutout.id] = bitmapToBlur
                                } else {
                                    cutoutRenderNodeBitmaps.remove(cutout.id)
                                }
                                cutoutRenderNodeWidths[cutout.id] = intContentW
                                cutoutRenderNodeHeights[cutout.id] = intContentH
                            }
                            node.setAlpha(blurAlpha.coerceIn(0f, FULL_ALPHA_FLOAT))
                            node
                        } catch (e: Throwable) {
                            AppLog.w(TAG, "RenderNode blur setup failed: ${e.message}")
                            null
                        }

                    if (renderNode != null) {
                        canvas.drawRenderNode(renderNode)
                    } else if (bitmapToBlur != null) {
                        frozenFramePaint.alpha =
                            (blurAlpha * MCC_MAX_ALPHA_FLOAT).roundToInt().coerceIn(0, MCC_MAX_ALPHA_INT)
                        frozenFramePaint.colorFilter = frozenInactiveColorFilter
                        drawFrozenBitmapToCanvas(
                            canvas,
                            bitmapToBlur,
                            isCropped,
                            contentW,
                            contentH,
                            sw,
                            sh,
                            sx,
                            sy,
                            frozenFramePaint,
                        )
                        frozenFramePaint.colorFilter = null
                        frozenFramePaint.alpha = MCC_MAX_ALPHA_INT
                    }
                }

                // 3. Transparency Mask
                if (hasTransparencyMask && !isStaticAssetDrawn) {
                    val maskBitmap =
                        CutoutMaskManager.getCachedMaskOrLoadAsync(
                            context = context,
                            cutoutId = cutout.id,
                            translucency = cutout.maskTranslucency,
                            sensitivity = cutout.maskSensitivity,
                            cavityHealing = cutout.maskCavityHealing,
                            onLoaded = ::postInvalidateOnAnimation,
                        )
                    if (maskBitmap != null && !maskBitmap.isRecycled) {
                        maskDestRect.set(0f, 0f, contentW, contentH)
                        canvas.drawBitmap(maskBitmap, null, maskDestRect, transparencyMaskPaint)
                    }
                }
            } finally {
                canvas.restoreToCount(contentSaveCount)
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
                            RadialGradient(
                                dw / 2f,
                                dh / 2f,
                                r,
                                circleBlendColors,
                                circleBlendStops,
                                Shader.TileMode.CLAMP,
                            )
                    }
                    blendPaint.shader = cachedCircleShader
                    canvas.drawRect(0f, 0f, dw, dh, blendPaint)
                    blendPaint.shader = null
                }
            } else if (hasTouching) {
                val rLeft = -leftExt
                val rTop = -topExt
                val rRight = dw + rightExt
                val rBottom = dh + bottomExt
                if (touchesLeft) {
                    renderEdgeBlend(canvas, horizontalGradientShader, 2f * leftExt, 1f, -leftExt, 0f, rLeft, rTop, rRight, rBottom)
                }
                if (touchesRight) {
                    renderEdgeBlend(
                        canvas,
                        horizontalReverseGradientShader,
                        2f * rightExt,
                        1f,
                        dw - rightExt,
                        0f,
                        rLeft,
                        rTop,
                        rRight,
                        rBottom,
                    )
                }
                if (touchesTop) {
                    renderEdgeBlend(canvas, verticalGradientShader, 1f, 2f * topExt, 0f, -topExt, rLeft, rTop, rRight, rBottom)
                }
                if (touchesBottom) {
                    renderEdgeBlend(
                        canvas,
                        verticalReverseGradientShader,
                        1f,
                        2f * bottomExt,
                        0f,
                        dh - bottomExt,
                        rLeft,
                        rTop,
                        rRight,
                        rBottom,
                    )
                }
                blendPaint.shader = null
            }
        } finally {
            if (cutout.opacity < 1f || hasTouching || hasTransparencyMask) {
                canvas.restoreToCount(saveCount)
            } else {
                canvas.restore()
            }
        }
        return masterViewDrawn
    }

    private fun renderEdgeBlend(
        canvas: Canvas,
        shader: LinearGradient,
        scaleX: Float,
        scaleY: Float,
        transX: Float,
        transY: Float,
        rectLeft: Float,
        rectTop: Float,
        rectRight: Float,
        rectBottom: Float,
    ) {
        shaderMatrix.reset()
        shaderMatrix.setScale(scaleX, scaleY)
        shaderMatrix.postTranslate(transX, transY)
        shader.setLocalMatrix(shaderMatrix)
        blendPaint.shader = shader
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, blendPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val masterView = if (childCount > 0) getChildAt(0) else null
        if (masterView == null && (!isFrozen || frozenBitmap == null)) return

        val parentW = width.toFloat()
        val parentH = height.toFloat()
        if (parentW <= 0f || parentH <= 0f) return

        val viewport = resolveViewport(parentW, parentH)

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
            if (bg != null) {
                drawBackgroundBitmap(canvas, bg, viewport)
            }

            val isEditing =
                isViewportEditActive ||
                    AppStateManager.isViewportEditActive.value ||
                    VisualAutoTuneCoordinator.uiState.value.isCalibrating ||
                    AnchorTestCoordinator.uiState.value.isTesting
            val activeLayout = MacroPadState.activeLayout.value
            val isLayoutAnchorActive = activeLayout?.visualAnchor?.enabled == true
            val isLayoutAnchorLost =
                !isEditing && activeLayout != null && isLayoutAnchorActive &&
                    AnchorPresenceManager.isLayoutAnchorLost(activeLayout.id)

            val anchorHasFreeze = activeLayout?.visualAnchor?.hasEffect(CutoutLostAnchorEffect.FREEZE) == true
            val anchorHasBlur = activeLayout?.visualAnchor?.hasEffect(CutoutLostAnchorEffect.BLUR) == true

            val shouldFreeze = isLayoutAnchorLost && anchorHasFreeze
            val shouldBlur = isLayoutAnchorLost && anchorHasBlur

            val effectiveManualFrozen = !isEditing && isFrozen
            val isTargetFrozen = effectiveManualFrozen || shouldFreeze

            // Pass 1: Cutouts rendered below background mask
            var hasAnyBelowTouchingEdge = false
            if (edgeBlending && belowMaskCutouts.size > 1) {
                for (i in belowMaskCutouts.indices) {
                    val c = belowMaskCutouts[i]
                    if (c.destX > tolerance || c.destX + c.destWidth < 1.0f - tolerance ||
                        c.destY > tolerance || c.destY + c.destHeight < 1.0f - tolerance
                    ) {
                        hasAnyBelowTouchingEdge = true
                        break
                    }
                }
            }

            val belowLayerSaveCount =
                if (hasAnyBelowTouchingEdge) {
                    computeCutoutsBounds(belowMaskCutouts, viewport, parentW, parentH, blendW, layerBoundsRect)
                    canvas.saveLayer(layerBoundsRect, null)
                } else {
                    canvas.save()
                }

            try {
                for (cutout in belowMaskCutouts) {
                    val drew =
                        drawSingleCutout(
                            canvas = canvas,
                            cutout = cutout,
                            viewport = viewport,
                            edgeBlending = edgeBlending,
                            tolerance = tolerance,
                            blendW = blendW,
                            isTargetFrozen = isTargetFrozen,
                            effectiveManualFrozen = effectiveManualFrozen,
                            shouldBlur = shouldBlur,
                            drawTime = drawTime,
                            masterView = masterView,
                        )
                    if (drew) {
                        masterViewDrawn = true
                    }
                }
            } finally {
                canvas.restoreToCount(belowLayerSaveCount)
            }

            // Overlay mask pass: if maskBitmap is present, draw mask image above Pass 1 cutouts
            val mask = maskBitmap
            if (mask != null) {
                drawMaskBitmap(canvas, mask, viewport)
            }

            // Pass 2: Cutouts rendered above background mask (below Compose MacroPad buttons)
            if (aboveMaskCutouts.isNotEmpty()) {
                var hasAnyAboveTouchingEdge = false
                if (edgeBlending && aboveMaskCutouts.size > 1) {
                    for (i in aboveMaskCutouts.indices) {
                        val c = aboveMaskCutouts[i]
                        if (c.destX > tolerance || c.destX + c.destWidth < 1.0f - tolerance ||
                            c.destY > tolerance || c.destY + c.destHeight < 1.0f - tolerance
                        ) {
                            hasAnyAboveTouchingEdge = true
                            break
                        }
                    }
                }

                val aboveLayerSaveCount =
                    if (hasAnyAboveTouchingEdge) {
                        computeCutoutsBounds(aboveMaskCutouts, viewport, parentW, parentH, blendW, layerBoundsRect)
                        canvas.saveLayer(layerBoundsRect, null)
                    } else {
                        canvas.save()
                    }

                try {
                    for (cutout in aboveMaskCutouts) {
                        val drew =
                            drawSingleCutout(
                                canvas = canvas,
                                cutout = cutout,
                                viewport = viewport,
                                edgeBlending = edgeBlending,
                                tolerance = tolerance,
                                blendW = blendW,
                                isTargetFrozen = isTargetFrozen,
                                effectiveManualFrozen = effectiveManualFrozen,
                                shouldBlur = shouldBlur,
                                drawTime = drawTime,
                                masterView = masterView,
                            )
                        if (drew) {
                            masterViewDrawn = true
                        }
                    }
                } finally {
                    canvas.restoreToCount(aboveLayerSaveCount)
                }
            }

            if (!masterViewDrawn && !isFrozen && masterView != null) {
                val saveCount = canvas.save()
                canvas.clipRect(0f, 0f, 1f, 1f)
                drawChild(canvas, masterView, drawTime)
                canvas.drawRect(0f, 0f, 1f, 1f, maskPaint)
                canvas.restoreToCount(saveCount)
                masterViewDrawn = true
            }
        } finally {
            canvas.restoreToCount(overallSaveCount)
        }
    }
}
