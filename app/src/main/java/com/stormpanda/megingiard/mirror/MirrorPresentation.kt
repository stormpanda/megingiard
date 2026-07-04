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
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import java.io.File
import com.stormpanda.megingiard.macropad.MacroPadState
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
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
import com.stormpanda.megingiard.MacroPadFocusPolicyState
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.shouldKeepPrimaryGameFocus
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.BackgroundMacroPadOverlay
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.ui.QuickMenuBar
import com.stormpanda.megingiard.ui.ScreenshotPreviewOverlay
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
private val MP_SWIPE_QM_BAR_ZONE_WIDTH = 120.dp
private const val TAG = "MirrorPresentation"
private const val TOUCH_TOLERANCE = 0.005f

class MirrorPresentation(
    context: Context, 
    private val display: Display, 
    private val srcWidth: Int, 
    private val srcHeight: Int
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {
    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null
    
    private var masterTextureView: TextureView? = null
    private var masterSurface: Surface? = null
    private var multiCutoutContainer: MultiCutoutContainer? = null
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
        window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        setPresentationFocusMode(
            keepPrimaryFocus = shouldKeepPrimaryGameFocus(
                MacroPadFocusPolicyState(isMacroPadSurfaceActive = true)
            )
        )
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

        ScreenCaptureManager.setSurfaceSize(targetWidth.toFloat(), targetHeight.toFloat())

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val mcc = MultiCutoutContainer(context, srcWidth, srcHeight).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        multiCutoutContainer = mcc
        container.addView(mcc)

        val tv = ThrottledTextureView(context)
        masterTextureView = tv
        mcc.addView(tv)

        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            private var lastUpdateTime = 0L

            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                st.setDefaultBufferSize(srcWidth, srcHeight)
                val surface = Surface(st)
                masterSurface = surface
                try {
                    val fps = ScreenCaptureManager.maxFps.value
                    AppLog.i(TAG, "Setting initial surface frame rate to $fps FPS")
                    surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error setting initial surface frame rate", e)
                }
                AppLog.d(TAG, "master TextureView surface available")
                onSurfaceReady?.invoke(surface)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                AppLog.d(TAG, "master TextureView surface destroyed")
                onSurfaceDestroyed?.invoke()
                masterSurface?.release()
                masterSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                val now = System.currentTimeMillis()
                val fps = ScreenCaptureManager.maxFps.value.coerceIn(10, 60)
                val interval = 1000L / fps
                if (now - lastUpdateTime >= interval) {
                    mcc.updateAccumulator(tv)
                    lastUpdateTime = now
                }
            }
        }

        scope.launch {
            ScreenCaptureManager.cutouts.collect { cutouts ->
                mcc.cutouts = cutouts
            }
        }

        scope.launch {
            ScreenCaptureManager.edgeBlendWidthDp.collect {
                mcc.invalidate()
            }
        }

        scope.launch {
            ScreenCaptureManager.maxFps.collect { fps ->
                tv.maxFps = fps
                masterSurface?.let { surface ->
                    if (surface.isValid) {
                        try {
                            AppLog.i(TAG, "Setting surface frame rate to $fps FPS")
                            surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Error setting surface frame rate", e)
                        }
                    }
                }
            }
        }

        scope.launch {
            combine(
                ScreenCaptureManager.scale,
                ScreenCaptureManager.offsetX,
                ScreenCaptureManager.offsetY,
            ) { s, ox, oy -> Triple(s, ox, oy) }
            .collect { triple ->
                mcc.viewportScale = triple.first
                mcc.viewportOffsetX = triple.second
                mcc.viewportOffsetY = triple.third
            }
        }

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
                    LocalAppColors provides appColors,
                    LocalAppDimens provides AppDimens()
                ) {
                    MaterialTheme(
                        colorScheme = colorSchemeFor(appColors, themeMode),
                        typography = megingiardTypography
                    ) {
                        val capturing by ScreenCaptureManager.isCapturing.collectAsState()
                        val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
                        val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
                        val scale by ScreenCaptureManager.scale.collectAsState()
                        val offsetX by ScreenCaptureManager.offsetX.collectAsState()
                        val offsetY by ScreenCaptureManager.offsetY.collectAsState()
                        val isTouchProjectionActive by ScreenCaptureManager.isTouchProjectionActive.collectAsState()
                        val isFollowActive by ScreenCaptureManager.isFollowActive.collectAsState()
                        val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
                        val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
                        val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
                        val fullscreenKeyboardLayout by AppStateManager.fullscreenKeyboardLayout.collectAsState()
                        val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
                        val density = LocalDensity.current
                        val edgeZonePx = with(density) { MP_EDGE_ZONE.toPx() }
                        val swipeThresholdPx = with(density) { MP_SWIPE_THRESHOLD.toPx() }
                        val quickMenuBarZoneWidthPx = with(density) { MP_SWIPE_QM_BAR_ZONE_WIDTH.toPx() }
                        val projectionController = remember(edgeZonePx, overlayAtBottom) {
                            TouchProjectionController(edgeZonePx, overlayAtBottom)
                        }

                        LaunchedEffect(isTouchProjectionActive) {
                            if (isTouchProjectionActive) {
                                TouchInjector.start(localeContext, "MirrorPresentation")
                            } else {
                                TouchInjector.stop("MirrorPresentation")
                            }
                        }
                        LaunchedEffect(isFollowActive, capturing) {
                            if (isFollowActive && capturing) {
                                TouchScreenObserver.onTouchNormalized = { nx, ny ->
                                    ScreenCaptureManager.onTouchReceived(nx, ny)
                                }
                                TouchScreenObserver.start()
                            } else {
                                TouchScreenObserver.stop()
                                TouchScreenObserver.onTouchNormalized = null
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose {
                                TouchInjector.stop("MirrorPresentation")
                                TouchScreenObserver.stop()
                            }
                        }

                        var gestureBoxSize by remember { mutableStateOf(IntSize.Zero) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords -> gestureBoxSize = coords.size }
                                // Parent-level swipe handler (PointerEventPass.Initial).
                                // Fires BEFORE any child regardless of z-order, so the
                                // dismiss-swipe works even when FullscreenMouseOverlay or
                                // KeyboardScreen is the hit-test target.
                                // Only active while a fullscreen overlay is shown.
                                .pointerInput(isFullscreenMouseActive, isFullscreenKeyboardActive, overlayAtBottom, quickMenuBarZoneWidthPx) {
                                    if (!isFullscreenMouseActive && !isFullscreenKeyboardActive) return@pointerInput
                                    val swipe = SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom, quickMenuBarZoneWidthPx)
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val firstChange = event.changes.firstOrNull()
                                            val x = firstChange?.position?.x ?: 0f
                                            val y = firstChange?.position?.y ?: 0f
                                            when (event.type) {
                                                PointerEventType.Press -> {
                                                    swipe.onPress(
                                                        pointerY = y,
                                                        containerHeight = size.height.toFloat(),
                                                        pointerX = x,
                                                        containerWidth = size.width.toFloat(),
                                                    )
                                                    if (swipe.isNearEdge) {
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                                PointerEventType.Move  -> {
                                                    swipe.onMove(y)
                                                    if (swipe.isNearEdge) {
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                                PointerEventType.Release -> {
                                                    swipe.onRelease(!event.changes.any { it.pressed })
                                                    if (swipe.isNearEdge) {
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
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


                            // Layer 2: BackgroundMacroPadOverlay — always rendered when active
                             // so QuickMenuBar remains visible in all modes. Internally dims
                            // buttons during viewport edit.
                            if (capturing) {
                                BackgroundMacroPadOverlay(showQuickMenuBar = false)
                            }

                            // Layer 3: Viewport edit gesture overlay — transparent fullscreen
                            // pinch/pan surface for adjusting the mirror viewport.
                            // Exit via edge-swipe (isViewportEditActive is part of
                            // isAnyModalActive → closeActiveModal() in SwipeGestureProcessor).
                            if (isViewportEditActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    CutoutLayoutEditor(overlayAtBottom = overlayAtBottom)
                                }
                            }

                            // Layer 4: Fullscreen Mouse Overlay — rendered above background
                            // content when triggered from BackgroundMacroPadOverlay buttons.
                            // Dismissed via edge-swipe → BackgroundMacroPadOverlay's
                            // SwipeGestureProcessor → AppStateManager.closeActiveModal().
                            if (capturing && isFullscreenMouseActive) {
                                FullscreenMouseOverlay()
                            }

                            // Layer 5: Fullscreen Keyboard Overlay — rendered above background
                            // content when triggered from BackgroundMacroPadOverlay buttons.
                            // Dismissed via edge-swipe → AppStateManager.closeActiveModal().
                            if (capturing && isFullscreenKeyboardActive) {
                                KeyboardScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    forcedLayout = fullscreenKeyboardLayout,
                                )
                            }

                            // Layer 6: QuickMenuBar — always the topmost layer so the swipe
                            // affordance and QuickMenu are never covered by fullscreen
                            // overlays (keyboard / mouse). Suppressed inside
                            // BackgroundMacroPadOverlay (showQuickMenuBar = false) to ensure
                            // only one QuickMenuBar instance exists at a time.
                            if (capturing) {
                                QuickMenuBar()
                            }

                            ScreenshotPreviewOverlay(modifier = Modifier.align(Alignment.Center))
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

        bindStateFlows(container)
    }

    private fun bindStateFlows(container: FrameLayout) {
        scope.launch {
            combine(
                AppStateManager.isFullscreenKeyboardActive,
                AppStateManager.isQuickMenuOpen,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
            ) { fullscreenKeyboard, quickMenuOpen, filePickerOpen, editorActive, ambientSettingsActive ->
                shouldKeepPrimaryGameFocus(
                    MacroPadFocusPolicyState(
                        isMacroPadSurfaceActive = true,
                        isFullscreenKeyboardActive = fullscreenKeyboard,
                        isQuickMenuOpen = quickMenuOpen,
                        isFilePickerOpen = filePickerOpen,
                        isEditorActive = editorActive,
                        isBackgroundSettingsActive = ambientSettingsActive,
                    )
                )
            }
                .distinctUntilChanged()
                .collect { keepPrimaryFocus -> setPresentationFocusMode(keepPrimaryFocus) }
        }
        scope.launch {
            combine(
                AppStateManager.isOnValidScreen,
                ScreenCaptureManager.isCapturing,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
                AppStateManager.isAmbientPreviewActive,
                AppStateManager.isUserLeaving,
                TouchRecordingManager.recordingRequested,
            ) { values ->
                val isValid = values[0] as Boolean
                val capturing = values[1] as Boolean
                val filePickerOpen = values[2] as Boolean
                val editorActive = values[3] as Boolean
                val ambientSettingsActive = values[4] as Boolean
                val ambientPreviewActive = values[5] as Boolean
                val userLeaving = values[6] as Boolean
                val recordingRequested = values[7] as Boolean
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
                // NOTE: isQuickMenuOpen intentionally excluded — QuickMenu renders inside
                // the Presentation's own ComposeView; hiding would pause mirroring.
                //
                // ambientPreviewActive: during preview mode the Presentation stays visible
                // so the user can see the live mirror + dimmed buttons behind the
                // transparent BackgroundSettingsOverlay on the primary screen (same visual
                // as viewport edit mode). Primary-screen input is unaffected because the
                // Presentation sits on the secondary display.
                capturing && isValid &&
                    !filePickerOpen && !editorActive &&
                    (!ambientSettingsActive || ambientPreviewActive) &&
                    !userLeaving &&
                    !recordingRequested
            }.collect { shouldShow ->
                if (shouldShow) show() else hide()
            }
        }
        scope.launch {
            ScreenCaptureManager.isFrozen.collect { frozen ->
                val tv = masterTextureView
                if (frozen && tv != null && tv.width > 0 && tv.height > 0) {
                    try {
                        val bitmap = tv.getBitmap()
                        if (bitmap != null) {
                            ScreenCaptureManager.setFrozenBitmap(bitmap)
                            multiCutoutContainer?.isFrozen = true
                            multiCutoutContainer?.frozenBitmap = bitmap
                        } else {
                            AppLog.e(TAG, "masterTextureView.getBitmap() returned null")
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "masterTextureView.getBitmap() exception", e)
                    }
                } else if (!frozen) {
                    multiCutoutContainer?.isFrozen = false
                    multiCutoutContainer?.frozenBitmap = null
                    ScreenCaptureManager.setFrozenBitmap(null)
                }
            }
        }
        scope.launch {
            MacroPadState.activeLayout.collect { layout ->
                val path = layout?.backgroundImagePath
                if (path != null) {
                    val file = File(context.filesDir, path)
                    withContext(Dispatchers.IO) {
                        try {
                            if (file.exists()) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                withContext(Dispatchers.Main) {
                                    if (bitmap != null) {
                                        container.background = BitmapDrawable(context.resources, bitmap)
                                    } else {
                                        container.setBackgroundColor(Color.BLACK)
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    container.setBackgroundColor(Color.BLACK)
                                }
                            }
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Failed to load background image for MirrorPresentation", e)
                            withContext(Dispatchers.Main) {
                                container.setBackgroundColor(Color.BLACK)
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        container.setBackgroundColor(Color.BLACK)
                    }
                }
            }
        }
    }

    private fun setPresentationFocusMode(keepPrimaryFocus: Boolean) {
        if (keepPrimaryFocus) {
            AppLog.d(TAG, "FLAG_NOT_FOCUSABLE added (ambient presentation surface)")
            window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            AppLog.d(TAG, "FLAG_NOT_FOCUSABLE cleared (interactive presentation overlay)")
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    fun getSurface(): Surface? = masterSurface

    fun captureScreenshot(): Bitmap? {
        val frozen = ScreenCaptureManager.isFrozen.value
        if (frozen) {
            val bitmap = ScreenCaptureManager.frozenBitmap.value
            if (bitmap != null) {
                return try {
                    Bitmap.createBitmap(bitmap)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to copy frozen bitmap for screenshot", e)
                    null
                }
            }
        }
        val tv = masterTextureView ?: return null
        if (tv.width <= 0 || tv.height <= 0) return null
        return try {
            tv.getBitmap()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to capture TextureView bitmap for screenshot", e)
            null
        }
    }
}

class MultiCutoutContainer(
    context: Context,
    private val srcWidth: Int,
    private val srcHeight: Int
) : FrameLayout(context) {
    var cutouts: List<ScreenCutout> = emptyList()
        set(value) {
            field = value
            if (isFrozen || !value.any { it.motionSmoothing }) {
                releaseAccumulator()
            }
            invalidate()
        }
    var isFrozen: Boolean = false
        set(value) {
            field = value
            if (value) {
                releaseAccumulator()
            }
            invalidate()
        }
    var frozenBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
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

    private val cutoutPaint = Paint()
    private val blendPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val circlePath = Path()
    private val maskPaint = Paint().apply {
        color = Color.BLACK
    }

    private var scratchBitmap: Bitmap? = null

    private class Accumulator(val strength: Int, width: Int, height: Int) {
        var accumulated: Bitmap? = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var initialized: Boolean = false

        val blendPaint = Paint().apply {
            val alphaPercent = (100 - strength).coerceAtLeast(1) / 100f
            alpha = (alphaPercent * 255f).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        fun recycle() {
            accumulated?.recycle()
            accumulated = null
        }
    }

    private val accumulators = mutableMapOf<Int, Accumulator>()

    init {
        clipChildren = true
        setWillNotDraw(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseAccumulator()
    }

    fun releaseAccumulator() {
        accumulators.values.forEach { it.recycle() }
        accumulators.clear()
        scratchBitmap?.recycle()
        scratchBitmap = null
    }

    fun updateAccumulator(textureView: TextureView) {
        val activeStrengths = if (isFrozen) emptySet() else cutouts.filter { it.motionSmoothing }.map { it.motionSmoothingStrength }.toSet()
        if (activeStrengths.isNotEmpty() && srcWidth > 0 && srcHeight > 0) {
            // 1. Recycle accumulators for strengths that are no longer active
            val iterator = accumulators.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in activeStrengths) {
                    entry.value.recycle()
                    iterator.remove()
                }
            }

            // 2. Ensure accumulators exist and have correct sizes
            for (strength in activeStrengths) {
                val existing = accumulators[strength]
                if (existing != null) {
                    val acc = existing.accumulated
                    if (acc == null || acc.width != srcWidth || acc.height != srcHeight) {
                        existing.recycle()
                        accumulators[strength] = Accumulator(strength, srcWidth, srcHeight)
                    }
                } else {
                    accumulators[strength] = Accumulator(strength, srcWidth, srcHeight)
                }
            }

            // 3. Ensure a valid single scratch bitmap exists
            val currentScratch = scratchBitmap
            val scratch = if (currentScratch == null || currentScratch.width != srcWidth || currentScratch.height != srcHeight) {
                currentScratch?.recycle()
                Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888).also { scratchBitmap = it }
            } else {
                currentScratch
            }

            // 4. Capture TextureView frame once
            try {
                textureView.getBitmap(scratch)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error getting TextureView bitmap for motion smoothing", e)
                return
            }

            // 5. Update each active accumulator with the captured frame
            for (strength in activeStrengths) {
                val acc = accumulators[strength] ?: continue
                val accum = acc.accumulated
                if (accum != null) {
                    try {
                        val accumCanvas = Canvas(accum)
                        if (!acc.initialized) {
                            accumCanvas.drawBitmap(scratch, 0f, 0f, null)
                            acc.initialized = true
                        } else {
                            accumCanvas.drawBitmap(scratch, 0f, 0f, acc.blendPaint)
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error updating motion smoothing accumulator for strength $strength", e)
                    }
                }
            }
            invalidate()
        } else {
            releaseAccumulator()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (childCount > 0) {
            val child = getChildAt(0)
            child.layout(0, 0, srcWidth, srcHeight)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        )
        if (childCount > 0) {
            val child = getChildAt(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(srcWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(srcHeight, MeasureSpec.EXACTLY)
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
        val tolerance = TOUCH_TOLERANCE
        val blendW = (blendWidthDp * resources.displayMetrics.density).roundToInt().toFloat()

        var masterViewDrawn = false

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

            val saveCount = if (cutout.opacity < 1f || hasTouching) {
                cutoutPaint.alpha = (cutout.opacity * 255).toInt()
                if (hasTouching) {
                    cutoutPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
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
                val isUncropped = cutout.srcWidth >= 0.999f && cutout.srcHeight >= 0.999f
                if (cutouts.size == 1 && isFollowActive && isUncropped) {
                    canvas.translate(viewportOffsetX, viewportOffsetY)
                    canvas.scale(viewportScale, viewportScale, dw / 2f, dh / 2f)

                    // Fit srcWidth x srcHeight into dw x dh preserving aspect ratio
                    val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
                    val destRatio = dw / dh
                    
                    var fitW = dw
                    var fitH = dh
                    if (srcRatio > destRatio) {
                        fitH = dw / srcRatio
                    } else {
                        fitW = dh * srcRatio
                    }
                    
                    // Center the fitted rectangle within dw x dh
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
                } else if (cutout.motionSmoothing && accumulators[cutout.motionSmoothingStrength]?.accumulated != null) {
                    canvas.drawBitmap(accumulators[cutout.motionSmoothingStrength]!!.accumulated!!, 0f, 0f, null)
                } else if (masterView != null) {
                    drawChild(canvas, masterView, drawTime)
                    masterViewDrawn = true
                }

                canvas.restoreToCount(innerSaveCount)

                if (cutout.shape == CutoutShape.CIRCLE) {
                    if (edgeBlending) {
                        val r = min(dw, dh) / 2f
                        val stop = maxOf(0f, r - blendW) / r
                        val colors = intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT)
                        val stops = floatArrayOf(0f, stop, 1f)
                        val shader = RadialGradient(dw / 2f, dh / 2f, r, colors, stops, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(0f, 0f, dw, dh, blendPaint)
                        blendPaint.shader = null
                    }
                } else if (hasTouching) {
                    if (touchesLeft) {
                        val colors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
                        val shader = LinearGradient(-leftExt, 0f, leftExt, 0f, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesRight) {
                        val colors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
                        val shader = LinearGradient(dw - rightExt, 0f, dw + rightExt, 0f, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesTop) {
                        val colors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
                        val shader = LinearGradient(0f, -topExt, 0f, topExt, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesBottom) {
                        val colors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
                        val shader = LinearGradient(0f, dh - bottomExt, 0f, dh + bottomExt, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
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
    }
}

private class ThrottledTextureView(context: Context) : TextureView(context) {
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
    private val invalidateRunnable = Runnable {
        isScheduled = false
        invalidate()
    }

    override fun invalidate() {
        val now = System.currentTimeMillis()
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

    @Deprecated("Deprecated in parent class")
    override fun invalidate(dirty: android.graphics.Rect?) {
        invalidate()
    }

    @Deprecated("Deprecated in parent class")
    override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
        invalidate()
    }
}





