package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.MirrorPresentationLifecycleOwner
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "FloatingBubbleOverlay"

private val FBO_BUBBLE_SIZE: Dp = 56.dp
private val FBO_BORDER_WIDTH: Dp = 2.dp
private val FBO_ICON_SIZE: Dp = 36.dp
private const val FBO_INITIAL_X_DP = 32
private const val FBO_INITIAL_Y_DP = 200

object FloatingBubbleOverlay {
    private var bubbleView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: MirrorPresentationLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        packageName: String,
        touchX: Float = -1f,
        touchY: Float = -1f,
    ) {
        mainHandler.post {
            showOnMainThread(packageName, touchX, touchY)
        }
    }

    fun hide() {
        mainHandler.post {
            hideOnMainThread()
        }
    }

    private fun showOnMainThread(
        packageName: String,
        touchX: Float = -1f,
        touchY: Float = -1f,
    ) {
        val service = MegingiardAccessibilityService.getInstance()
        if (service == null) {
            AppLog.w(TAG, "Cannot show floating bubble: MegingiardAccessibilityService is not active")
            return
        }

        if (bubbleView != null) {
            AppLog.d(TAG, "Bubble overlay already active — keeping existing overlay")
            return
        }

        try {
            // Target secondary display (bottom screen) using a dedicated TYPE_ACCESSIBILITY_OVERLAY WindowContext
            val dm = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: service.display
            val windowContext = service.createWindowContext(targetDisplay, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)

            val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val owner = MirrorPresentationLifecycleOwner(service.application)
            lifecycleOwner = owner

            val density = windowContext.resources.displayMetrics.density
            val bubbleSizePx = (FBO_BUBBLE_SIZE.value * density).toInt()

            val initialX =
                if (touchX >= 0f) {
                    (touchX - bubbleSizePx / 2f).coerceAtLeast(0f).toInt()
                } else {
                    (FBO_INITIAL_X_DP * density).toInt()
                }
            val initialY =
                if (touchY >= 0f) {
                    (touchY - bubbleSizePx / 2f).coerceAtLeast(0f).toInt()
                } else {
                    (FBO_INITIAL_Y_DP * density).toInt()
                }

            val params =
                WindowManager.LayoutParams().apply {
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    format = PixelFormat.TRANSLUCENT
                    width = bubbleSizePx
                    height = bubbleSizePx
                    gravity = Gravity.TOP or Gravity.START
                    x = initialX
                    y = initialY
                }
            layoutParams = params

            val view =
                ComposeView(windowContext).apply {
                    layoutParams = ViewGroup.LayoutParams(bubbleSizePx, bubbleSizePx)
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                }

            var dragStartX = 0
            var dragStartY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = params.x
                        dragStartY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x = (dragStartX + dx).toInt()
                            params.y = (dragStartY + dy).toInt()
                            wm.updateViewLayout(view, params)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            AppLog.i(TAG, "Bubble tapped -> restoring Megingiard")
                            AppLauncherManager.restoreMegingiard(service)
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }

            view.setContent {
                val themeMode by SettingsManager.themeMode.collectAsState()
                val userAccentArgb by SettingsManager.accentColor.collectAsState()
                val appColors = paletteFor(themeMode, Color(userAccentArgb))

                MaterialTheme(
                    colorScheme = colorSchemeFor(appColors, themeMode),
                    typography = megingiardTypography,
                ) {
                    CompositionLocalProvider(
                        LocalAppColors provides appColors,
                        LocalAppDimens provides AppDimens(),
                    ) {
                        FloatingBubbleContent(
                            packageName = packageName,
                        )
                    }
                }
            }

            wm.addView(view, params)
            bubbleView = view
            AppStateManager.setFloatingBubbleActive(true)
            AppLog.i(
                TAG,
                "Floating bubble overlay displayed on display ${targetDisplay.displayId} via TYPE_ACCESSIBILITY_OVERLAY (size=${bubbleSizePx}px)",
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to display floating bubble overlay: ${e.message}", e)
        }
    }

    private fun hideOnMainThread() {
        val view = bubbleView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
                AppLog.i(TAG, "Floating bubble view removed from WindowManager")
            } catch (e: Exception) {
                AppLog.w(TAG, "Error removing floating bubble view: ${e.message}")
            }
        }
        lifecycleOwner?.destroy()
        bubbleView = null
        windowManager = null
        lifecycleOwner = null
        layoutParams = null
        AppStateManager.setFloatingBubbleActive(false)
    }
}

@Composable
private fun FloatingBubbleContent(packageName: String) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var megingiardIconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        visible = true
        megingiardIconBitmap =
            withContext(Dispatchers.IO) {
                try {
                    val iconDrawable = context.packageManager.getApplicationIcon(context.packageName)
                    iconDrawable.toImageBitmap()
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to load Megingiard app icon: ${e.message}")
                    null
                }
            }
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1.0f else 0.0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "bubbleScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = scale.coerceIn(0f, 1f)
                }.size(FBO_BUBBLE_SIZE)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = 0.95f))
                .border(FBO_BORDER_WIDTH, colors.accent, CircleShape)
                .padding(6.dp),
    ) {
        val bitmap = megingiardIconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.floating_bubble_cd),
                modifier = Modifier.size(FBO_ICON_SIZE),
            )
        } else {
            MaterialSymbol(
                name = "apps",
                size = FBO_ICON_SIZE,
                tint = colors.accent,
            )
        }
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap? =
    try {
        val width = if (intrinsicWidth > 0) intrinsicWidth else 48
        val height = if (intrinsicHeight > 0) intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
