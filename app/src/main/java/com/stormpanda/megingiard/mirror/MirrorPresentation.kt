package com.stormpanda.megingiard.mirror

import android.app.Application
import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.compose.ui.graphics.Color as ComposeColor
import com.stormpanda.megingiard.AppLog
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.AmbientMacroPadOverlay
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import java.util.Locale
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTransformGestures
import com.stormpanda.megingiard.input.TouchInjector

private val MP_EDGE_ZONE = 40.dp
private val MP_SWIPE_THRESHOLD = 25.dp
private const val TAG = "MirrorPresentation"

class MirrorPresentation(
    context: Context, 
    private val display: Display, 
    private val srcWidth: Int, 
    private val srcHeight: Int
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {
    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null
    private var surfaceView: SurfaceView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // OnBackPressedDispatcher provided to the Compose tree. Needs to be a class
    // property so onBackCallback can delegate to it (see below).
    private val backDispatcher = OnBackPressedDispatcher(null)

    // System back events arrive here via the Presentation's OnBackInvokedDispatcher.
    // We forward them to backDispatcher first so that BackHandlers registered by
    // Compose (Dialog dismiss, etc.) fire correctly. Only if no
    // Compose callback is enabled do we fall back to switching mode.
    private val onBackCallback = OnBackInvokedCallback {
        if (backDispatcher.hasEnabledCallbacks()) {
            AppLog.d(TAG, "back pressed: delegating to Compose")
            backDispatcher.onBackPressed()
        } else {
            AppLog.d(TAG, "back pressed: no Compose handler → ignoring")
        }
    }

    override fun cancel() {
        AppLog.d(TAG, "cancel → ignoring")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate display=${display.displayId} src=${srcWidth}x${srcHeight}")
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            onBackCallback
        )
        val lifecycleOwner = MirrorPresentationLifecycleOwner(context.applicationContext as Application)
        window?.decorView?.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
        }

        setOnDismissListener {
            AppLog.i(TAG, "dismissed → scope cancelled, lifecycle destroyed")
            scope.cancel()
            lifecycleOwner.destroy()
        }

        val windowContext = context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION, null)
        val windowMetrics = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val targetBounds = windowMetrics.bounds
        val targetWidth = targetBounds.width()
        val targetHeight = targetBounds.height()

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

        var finalWidth = targetWidth
        var finalHeight = targetHeight

        if (srcRatio > targetRatio) {
            // Source is wider than target. Fit width, calculate height to maintain ratio.
            finalHeight = (targetWidth / srcRatio).toInt()
        } else {
            // Source is taller than target. Fit height, calculate width.
            finalWidth = (targetHeight * srcRatio).toInt()
        }

        ScreenCaptureManager.setSurfaceSize(finalWidth.toFloat(), finalHeight.toFloat())

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val sv = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(finalWidth, finalHeight, Gravity.CENTER)
            setZOrderMediaOverlay(true)
        }
        // Force the hardware buffer memory allocation to match the raw screen pixel coordinates
        sv.holder.setFixedSize(srcWidth, srcHeight)
        surfaceView = sv

        container.addView(sv)

        // BackHandler (used in Compose Dialog) requires
        // LocalOnBackPressedDispatcherOwner. backDispatcher is a class property so that
        // onBackCallback (the system back receiver) can delegate into it, making all
        // BackHandlers inside this ComposeView fire correctly before falling back to
        // the Presentation-level mode switch.
        val backDispatcherOwner = object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
            override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
        }

        // Compose's Dialog() composable creates android.app.Dialog using
        // LocalView.current.context — i.e. the ComposeView's own context.
        // If that context is the Presentation's ContextThemeWrapper (window type
        // TYPE_PRIVATE_PRESENTATION = 2037), Dialog.show() throws:
        //   "Window type mismatch: context type 2037 vs LayoutParams type 2 (TYPE_APPLICATION)"
        // Fix: create a TYPE_APPLICATION window context on the same secondary display.
        // This context is used for the ComposeView so that LocalView.current.context
        // (which Compose Dialog reads) carries no window-type restriction, and Dialog
        // sub-windows appear on the correct secondary display.
        val composeViewContext = context.createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            null
        )
        val composeView = ComposeView(composeViewContext).apply {
            setContent {
                val themeMode by SettingsManager.themeMode.collectAsState()
                val userAccentArgb by SettingsManager.accentColor.collectAsState()
                val appColors = paletteFor(themeMode, ComposeColor(userAccentArgb))

                // The Presentation window has its own Context that is never updated when
                // LocaleManager.applicationLocales changes (only the Activity recreates).
                // We derive a locale-aware context here so all stringResource() calls inside
                // this Compose tree use the correct locale after a language switch.
                val appLanguage by SettingsManager.appLanguage.collectAsState()
                val localeContext = remember(appLanguage) {
                    val locale: Locale = when (appLanguage) {
                        AppLanguage.SYSTEM -> Locale.getDefault()
                        AppLanguage.EN     -> Locale.ENGLISH
                        AppLanguage.DE     -> Locale.GERMAN
                    }
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(locale)
                    composeViewContext.createConfigurationContext(config)
                }

                CompositionLocalProvider(
                    LocalContext provides localeContext,
                    LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                    LocalAppColors provides appColors
                ) {
                    MaterialTheme(colorScheme = colorSchemeFor(themeMode)) {
                        val ambientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()
                        val capturing by ScreenCaptureManager.isCapturing.collectAsState()
                        val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
                        val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
                        val scale by ScreenCaptureManager.scale.collectAsState()
                        val offsetX by ScreenCaptureManager.offsetX.collectAsState()
                        val offsetY by ScreenCaptureManager.offsetY.collectAsState()
                        val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
                        val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
                        val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
                        val density = LocalDensity.current
                        val edgeZonePx = with(density) { MP_EDGE_ZONE.toPx() }
                        val swipeThresholdPx = with(density) { MP_SWIPE_THRESHOLD.toPx() }
                        val projectionController = remember(edgeZonePx, overlayAtBottom) {
                            TouchProjectionController(edgeZonePx, overlayAtBottom)
                        }

                        LaunchedEffect(isTouchProjectionActive) {
                            if (isTouchProjectionActive) {
                                TouchInjector.start(localeContext)
                            } else {
                                TouchInjector.stop()
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose { TouchInjector.stop() }
                        }

                        var gestureBoxSize by remember { mutableStateOf(IntSize.Zero) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords -> gestureBoxSize = coords.size }
                                .pointerInput(isTouchProjectionActive, overlayAtBottom) {
                                    if (!isTouchProjectionActive) return@pointerInput
                                    projectionController.reset()
                                    var swipeStartY = Float.NaN
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (gestureBoxSize == IntSize.Zero) continue
                                            val scW = gestureBoxSize.width.toFloat()
                                            val scH = gestureBoxSize.height.toFloat()
                                            when (event.type) {
                                                PointerEventType.Press -> {
                                                    val change = event.changes.firstOrNull() ?: continue
                                                    val y = change.position.y
                                                    val nearEdge = if (overlayAtBottom) {
                                                        y >= scH - edgeZonePx
                                                    } else {
                                                        y <= edgeZonePx
                                                    }
                                                    swipeStartY = if (nearEdge) y else Float.NaN
                                                    if (!nearEdge) {
                                                        projectionController.onPress(
                                                            pointerId = change.id.value,
                                                            x = change.position.x,
                                                            y = y,
                                                            boxW = scW,
                                                            boxH = scH,
                                                            isConsumed = change.isConsumed,
                                                            pointerCount = event.changes.size,
                                                        )
                                                    }
                                                }
                                                PointerEventType.Move -> {
                                                    val change = event.changes.firstOrNull() ?: continue
                                                    val y = change.position.y
                                                    if (!swipeStartY.isNaN()) {
                                                        val delta = if (overlayAtBottom) {
                                                            swipeStartY - y
                                                        } else {
                                                            y - swipeStartY
                                                        }
                                                        if (delta >= swipeThresholdPx) {
                                                            ScreenCaptureManager.setTouchProjectionActive(false)
                                                            swipeStartY = Float.NaN
                                                        }
                                                    } else {
                                                        projectionController.onMove(
                                                            pointerId = change.id.value,
                                                            x = change.position.x,
                                                            y = y,
                                                            boxW = scW,
                                                            boxH = scH,
                                                            isConsumed = change.isConsumed,
                                                        )
                                                    }
                                                }
                                                PointerEventType.Release -> {
                                                    val change = event.changes.firstOrNull()
                                                    swipeStartY = Float.NaN
                                                    projectionController.onRelease(
                                                        pointerId = change?.id?.value ?: -1L,
                                                        x = change?.position?.x,
                                                        y = change?.position?.y,
                                                        boxW = scW,
                                                        boxH = scH,
                                                    )
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                },
                        ) {
                            // Layer 1: Frozen bitmap — rendered with the same viewport
                            // transform (graphicsLayer) that was applied to the SurfaceView,
                            // so it looks identical to what was visible before freeze.
                            // PixelCopy captures without View transforms, so we re-apply
                            // scale/offsetX/offsetY here. Collecting via StateFlow also
                            // makes it reactive during viewport-edit pan/zoom.
                            val bitmap = frozenBitmap
                            if (isFrozen && bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY,
                                        ),
                                    contentScale = ContentScale.Fit,
                                )
                            }

                            // Layer 2: AmbientMacroPadOverlay — always rendered when active
                            // so IdlePill remains visible in all modes. Internally hides
                            // buttons/dim/vignette during touch projection, freeze, and
                            // viewport edit.
                            if (ambientEnabled && capturing) {
                                AmbientMacroPadOverlay()
                            }

                            // Layer 3: Viewport edit gesture overlay — transparent fullscreen
                            // pinch/pan surface for adjusting the mirror viewport.
                            // Exit via edge-swipe (isViewportEditActive is part of
                            // isAnyModalActive → closeActiveModal() in SwipeGestureProcessor).
                            if (isViewportEditActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val sw = ScreenCaptureManager.surfaceWidth.value
                                                val sh = ScreenCaptureManager.surfaceHeight.value
                                                val newScale =
                                                    (ScreenCaptureManager.scale.value * zoom)
                                                        .coerceIn(1f, 5f)
                                                ScreenCaptureManager.setScale(newScale)
                                                val maxX = (sw * (newScale - 1f)) / 2f
                                                val maxY = (sh * (newScale - 1f)) / 2f
                                                ScreenCaptureManager.setOffsetX(
                                                    (ScreenCaptureManager.offsetX.value + pan.x)
                                                        .coerceIn(-maxX, maxX)
                                                )
                                                ScreenCaptureManager.setOffsetY(
                                                    (ScreenCaptureManager.offsetY.value + pan.y)
                                                        .coerceIn(-maxY, maxY)
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        container.addView(composeView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(container)

        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceReady?.invoke(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // no-op
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed?.invoke()
            }
        })

        bindStateFlows(sv)
    }

    private fun bindStateFlows(sv: SurfaceView) {
        scope.launch {
            combine(
                AppStateManager.isOnValidScreen,
                SettingsManager.macropadAmbientEnabled,
                ScreenCaptureManager.isCapturing,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isAmbientSettingsActive,
            ) { values ->
                val isValid = values[0] as Boolean
                val ambientEnabled = values[1] as Boolean
                val capturing = values[2] as Boolean
                val filePickerOpen = values[3] as Boolean
                val editorActive = values[4] as Boolean
                val ambientSettingsActive = values[5] as Boolean
                // Show based on capturing state, not on whether MainActivity is in the
                // foreground. Using isActivityResumed here caused a feedback loop: each
                // time the user opened the app while mirroring, show() covered the screen,
                // pushing MainActivity to background (ON_PAUSE ~70 ms). ON_STOP then set
                // isResumed=false → hide(), and the cycle repeated indefinitely.
                //
                // filePickerOpen / editorActive / ambientSettingsActive: while any of these
                // Activity-level modals are visible we hide the Presentation so the user
                // can interact with them.  Without this the Presentation window
                // (TYPE_PRIVATE_PRESENTATION), which sits above regular Activities, would
                // block input entirely.
                // NOTE: isPillMenuOpen intentionally excluded — PillMenu renders inside
                // the Presentation's own ComposeView; hiding would pause mirroring.
                capturing && isValid && ambientEnabled &&
                    !filePickerOpen && !editorActive && !ambientSettingsActive
            }.collect { shouldShow ->
                if (shouldShow) show() else hide()
            }
        }
        scope.launch {
            ScreenCaptureManager.scale.collect { 
                sv.scaleX = it
                sv.scaleY = it
            }
        }
        scope.launch {
            ScreenCaptureManager.offsetX.collect { sv.translationX = it }
        }
        scope.launch {
            ScreenCaptureManager.offsetY.collect { sv.translationY = it }
        }
        scope.launch {
            ScreenCaptureManager.isFrozen.collect { frozen ->
                if (frozen && sv.width > 0 && sv.height > 0) {
                    try {
                        val bitmap = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
                        PixelCopy.request(
                            sv,
                            bitmap,
                            { result ->
                                if (result == PixelCopy.SUCCESS) {
                                    ScreenCaptureManager.setFrozenBitmap(bitmap)
                                    sv.visibility = View.INVISIBLE
                                } else {
                                    AppLog.e(TAG, "PixelCopy failed with result code: $result")
                                    bitmap.recycle()
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    } catch (e: Exception) {
                        AppLog.e(TAG, "PixelCopy exception", e)
                    }
                } else if (!frozen) {
                    sv.visibility = View.VISIBLE
                    ScreenCaptureManager.setFrozenBitmap(null)
                }
            }
        }
    }
}
