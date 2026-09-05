package com.stormpanda.megingiard.mirror

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.macropad.DEFAULT_HUD_DIM_OPACITY
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "PrimaryHudDimOverlayManager"
private const val CORNER_RADIUS_DP = 16f
private const val COLOR_BLACK_ARGB = 0xFF000000.toInt()
private const val MAX_ALPHA_FLOAT = 255.0f
private const val MAX_ALPHA_INT = 255

/**
 * Non-interactive ambient dimming overlay that renders on the Primary Display (Display 0).
 *
 * When HUD isolation is enabled and top screen dimming is active, this overlay draws
 * soft, translucent dark veils over the source regions of active cutouts. This guides the
 * user's visual attention down to the isolated HUD on the secondary screen while maintaining
 * zero input interference with the game on Display 0 (using [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE]
 * and [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE]).
 */
object PrimaryHudDimOverlayManager {
    private var application: Application? = null
    private var scope: CoroutineScope? = null
    private var scrimView: HudDimScrimView? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(app: Application) {
        if (application != null) return
        application = app
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = coroutineScope

        coroutineScope.launch {
            combine(
                combine(
                    ScreenCaptureManager.isCapturing,
                    ScreenCaptureManager.dimTopScreenHud,
                    ScreenCaptureManager.topScreenHudDimOpacity,
                ) { isCapturing, dimTop, opacity ->
                    CaptureConfig(isCapturing, dimTop, opacity)
                },
                ScreenCaptureManager.cutouts,
                AppStateManager.isViewportEditActive,
                HudAutoTuneCoordinator.isCalibrating,
            ) { config, cutouts, isEdit, isCalibrating ->
                DataState(
                    isCapturing = config.isCapturing,
                    dimTop = config.dimTop,
                    opacity = config.opacity,
                    cutouts = cutouts,
                    isEdit = isEdit,
                    isCalibrating = isCalibrating,
                )
            }.collect { state ->
                handleStateChange(state)
            }
        }
        AppLog.i(TAG, "PrimaryHudDimOverlayManager initialized")
    }

    private data class CaptureConfig(
        val isCapturing: Boolean,
        val dimTop: Boolean,
        val opacity: Float,
    )

    private data class DataState(
        val isCapturing: Boolean,
        val dimTop: Boolean,
        val opacity: Float,
        val cutouts: List<ScreenCutout>,
        val isEdit: Boolean,
        val isCalibrating: Boolean,
    )

    private fun handleStateChange(state: DataState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyState(state)
        } else {
            mainHandler.post { applyState(state) }
        }
    }

    private fun applyState(state: DataState) {
        val shouldShow =
            state.isCapturing &&
                state.dimTop &&
                !state.isEdit &&
                !state.isCalibrating &&
                state.cutouts.any { it.srcWidth > 0f && it.srcHeight > 0f }

        if (!shouldShow) {
            hide()
            return
        }
        showOrUpdate(state.cutouts, state.opacity)
    }

    private fun showOrUpdate(
        cutouts: List<ScreenCutout>,
        opacity: Float,
    ) {
        val app = application ?: return
        if (DisplayDetector.findSecondaryDisplay(app) == null) return

        val view = scrimView
        if (view != null) {
            view.update(cutouts, opacity)
            return
        }

        val accessibilityService = MegingiardAccessibilityService.getInstance()
        val canDrawOverlays = Settings.canDrawOverlays(app)

        val (hostContext, windowType) =
            when {
                accessibilityService != null -> {
                    accessibilityService to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                }

                canDrawOverlays -> {
                    app to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                }

                else -> {
                    AppLog.w(TAG, "Cannot show HUD dim scrim: neither accessibility nor overlay permission available")
                    return
                }
            }

        try {
            val dm = hostContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val primaryDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val windowContext = hostContext.createWindowContext(primaryDisplay, windowType, null)
            val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val params =
                WindowManager.LayoutParams().apply {
                    type = windowType
                    flags =
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    gravity = Gravity.TOP or Gravity.START
                }

            val newView =
                HudDimScrimView(windowContext).apply {
                    update(cutouts, opacity)
                }
            wm.addView(newView, params)
            scrimView = newView
            AppLog.i(TAG, "HUD dim scrim attached to Display 0 WindowManager")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to attach HUD dim scrim: ${e.message}", e)
        }
    }

    private fun hide() {
        val view = scrimView ?: return
        val wm = windowManager
        try {
            wm?.removeView(view)
            AppLog.i(TAG, "HUD dim scrim removed from Display 0 WindowManager")
        } catch (e: Exception) {
            AppLog.e(TAG, "Error removing HUD dim scrim: ${e.message}", e)
        } finally {
            scrimView = null
            windowManager = null
        }
    }

    fun destroy() {
        hide()
        scope?.cancel()
        scope = null
        application = null
    }

    private class HudDimScrimView(
        context: Context,
    ) : View(context) {
        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_BLACK_ARGB
                style = Paint.Style.FILL
            }
        private val rect = RectF()
        private val cornerRadiusPx = CORNER_RADIUS_DP * resources.displayMetrics.density

        private var activeCutouts: List<ScreenCutout> = emptyList()
        private var opacity: Float = DEFAULT_HUD_DIM_OPACITY

        fun update(
            newCutouts: List<ScreenCutout>,
            newOpacity: Float,
        ) {
            activeCutouts = newCutouts
            opacity = newOpacity
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f || activeCutouts.isEmpty()) return

            paint.alpha = (opacity * MAX_ALPHA_FLOAT).roundToInt().coerceIn(0, MAX_ALPHA_INT)

            for (cutout in activeCutouts) {
                if (cutout.srcWidth <= 0f || cutout.srcHeight <= 0f) continue
                rect.set(
                    cutout.srcX * w,
                    cutout.srcY * h,
                    (cutout.srcX + cutout.srcWidth) * w,
                    (cutout.srcY + cutout.srcHeight) * h,
                )
                if (cutout.shape == CutoutShape.CIRCLE) {
                    canvas.drawOval(rect, paint)
                } else {
                    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
                }
            }
        }
    }
}
