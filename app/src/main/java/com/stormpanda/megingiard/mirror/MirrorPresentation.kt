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
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import com.stormpanda.megingiard.MacroPadFocusPolicyState
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.shouldKeepPrimaryGameFocus
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.BackgroundMacroPadOverlay
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.MirrorSettings
import com.stormpanda.megingiard.settings.SettingsManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
private val MP_SWIPE_PILL_ZONE_WIDTH = 120.dp
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

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

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
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                st.setDefaultBufferSize(srcWidth, srcHeight)
                val surface = Surface(st)
                masterSurface = surface
                try {
                    val fps = MirrorSettings.maxFps.value
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
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        scope.launch {
            ScreenCaptureManager.cutouts.collect { cutouts ->
                mcc.cutouts = cutouts
            }
        }

        scope.launch {
            MirrorSettings.crossfadeBlendWidthDp.collect {
                mcc.invalidate()
            }
        }

        scope.launch {
            MirrorSettings.maxFps.collect { fps ->
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
            .collect { (scale, offsetX, offsetY) ->
                mcc.viewportScale = scale
                mcc.viewportOffsetX = offsetX
                mcc.viewportOffsetY = offsetY
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
                        val pillZoneWidthPx = with(density) { MP_SWIPE_PILL_ZONE_WIDTH.toPx() }
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
                                .pointerInput(isFullscreenMouseActive, isFullscreenKeyboardActive, overlayAtBottom, pillZoneWidthPx) {
                                    if (!isFullscreenMouseActive && !isFullscreenKeyboardActive) return@pointerInput
                                    val swipe = SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom, pillZoneWidthPx)
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
                            // so IdlePill remains visible in all modes. Internally hides
                            // buttons/dim/vignette during touch projection, freeze, and
                            // viewport edit.
                            if (capturing) {
                                BackgroundMacroPadOverlay(showIdlePill = false)
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

                            // Layer 6: IdlePill — always the topmost layer so the swipe
                            // affordance and PillMenu are never covered by fullscreen
                            // overlays (keyboard / mouse). Suppressed inside
                            // BackgroundMacroPadOverlay (showIdlePill = false) to ensure
                            // only one IdlePill instance exists at a time.
                            if (capturing) {
                                IdlePill()
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

        bindStateFlows()
    }

    private fun bindStateFlows() {
        scope.launch {
            combine(
                AppStateManager.isFullscreenKeyboardActive,
                AppStateManager.isPillMenuOpen,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
            ) { fullscreenKeyboard, pillMenuOpen, filePickerOpen, editorActive, ambientSettingsActive ->
                shouldKeepPrimaryGameFocus(
                    MacroPadFocusPolicyState(
                        isMacroPadSurfaceActive = true,
                        isFullscreenKeyboardActive = fullscreenKeyboard,
                        isPillMenuOpen = pillMenuOpen,
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
                // NOTE: isPillMenuOpen intentionally excluded — PillMenu renders inside
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
}

class MultiCutoutContainer(
    context: Context,
    private val srcWidth: Int,
    private val srcHeight: Int
) : FrameLayout(context) {
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

    private var accumulatedMasterBitmap: Bitmap? = null
    private var tempMasterBitmap: Bitmap? = null
    private var isAccumulatorInitialized = false

    init {
        clipChildren = true
        setWillNotDraw(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseAccumulator()
    }

    private fun releaseAccumulator() {
        accumulatedMasterBitmap?.recycle()
        accumulatedMasterBitmap = null
        tempMasterBitmap?.recycle()
        tempMasterBitmap = null
        isAccumulatorInitialized = false
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

        val hasSmoothing = !isFrozen && cutouts.any { it.motionSmoothing }
        if (hasSmoothing && masterView is TextureView && srcWidth > 0 && srcHeight > 0) {
            val curAccum = accumulatedMasterBitmap
            val curTemp = tempMasterBitmap
            if (curAccum == null || curAccum.width != srcWidth || curAccum.height != srcHeight) {
                curAccum?.recycle()
                accumulatedMasterBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
                isAccumulatorInitialized = false
            }
            if (curTemp == null || curTemp.width != srcWidth || curTemp.height != srcHeight) {
                curTemp?.recycle()
                tempMasterBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
            }

            val accum = accumulatedMasterBitmap
            val temp = tempMasterBitmap
            if (accum != null && temp != null) {
                try {
                    masterView.getBitmap(temp)
                    val accumCanvas = Canvas(accum)
                    if (!isAccumulatorInitialized) {
                        accumCanvas.drawBitmap(temp, 0f, 0f, null)
                        isAccumulatorInitialized = true
                    } else {
                        val blendPaintForAccum = Paint().apply {
                            alpha = 38 // 15% opacity
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                        }
                        accumCanvas.drawBitmap(temp, 0f, 0f, blendPaintForAccum)
                    }
                } catch (e: Exception) {
                    AppLog.e("MultiCutoutContainer", "Error updating motion smoothing accumulator", e)
                }
            }
        } else {
            releaseAccumulator()
        }

        val drawingTime = drawingTime
        val blendWidthDp = MirrorSettings.crossfadeBlendWidthDp.value
        val isMultiMode = AppStateManager.isMultiCutoutEditMode.value || cutouts.size > 1
        val crossfade = blendWidthDp > 0f && isMultiMode
        val tolerance = TOUCH_TOLERANCE
        val blendW = (blendWidthDp * resources.displayMetrics.density).roundToInt().toFloat()

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

            var touchesOtherLeft = false
            var touchesOtherRight = false
            var touchesOtherTop = false
            var touchesOtherBottom = false

            if (crossfade && cutouts.size > 1) {
                for (other in cutouts) {
                    if (other == cutout) continue
                    
                    // Left-right touches
                    val overlapsY = maxOf(cutout.destY, other.destY) < minOf(cutout.destY + cutout.destHeight, other.destY + other.destHeight) - tolerance
                    if (overlapsY) {
                        if (abs(cutout.destX - (other.destX + other.destWidth)) < tolerance) {
                            touchesOtherLeft = true
                        }
                        if (abs((cutout.destX + cutout.destWidth) - other.destX) < tolerance) {
                            touchesOtherRight = true
                        }
                    }
                    
                    // Top-bottom touches
                    val overlapsX = maxOf(cutout.destX, other.destX) < minOf(cutout.destX + cutout.destWidth, other.destX + other.destWidth) - tolerance
                    if (overlapsX) {
                        if (abs(cutout.destY - (other.destY + other.destHeight)) < tolerance) {
                            touchesOtherTop = true
                        }
                        if (abs((cutout.destY + cutout.destHeight) - other.destY) < tolerance) {
                            touchesOtherBottom = true
                        }
                    }
                }
            }

            val touchesLeft = crossfade
            val touchesRight = crossfade
            val touchesTop = crossfade
            val touchesBottom = crossfade

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
                val innerSaveCount = canvas.save()
                
                if (cutouts.size == 1 && !AppStateManager.isMultiCutoutEditMode.value) {
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
                } else if (cutout.motionSmoothing && accumulatedMasterBitmap != null) {
                    canvas.drawBitmap(accumulatedMasterBitmap!!, 0f, 0f, null)
                } else if (masterView != null) {
                    drawChild(canvas, masterView, drawingTime)
                }

                canvas.restoreToCount(innerSaveCount)

                if (hasTouching) {
                    if (touchesLeft) {
                        val colors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
                        val endX = if (touchesOtherLeft) leftExt else 0f
                        val shader = LinearGradient(-leftExt, 0f, endX, 0f, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesRight) {
                        val colors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
                        val startX = if (touchesOtherRight) dw - rightExt else dw
                        val shader = LinearGradient(startX, 0f, dw + rightExt, 0f, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesTop) {
                        val colors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
                        val endY = if (touchesOtherTop) topExt else 0f
                        val shader = LinearGradient(0f, -topExt, 0f, endY, colors, null, Shader.TileMode.CLAMP)
                        blendPaint.shader = shader
                        canvas.drawRect(-leftExt, -topExt, dw + rightExt, dh + bottomExt, blendPaint)
                    }
                    if (touchesBottom) {
                        val colors = intArrayOf(Color.BLACK, Color.TRANSPARENT)
                        val startY = if (touchesOtherBottom) dh - bottomExt else dh
                        val shader = LinearGradient(0f, startY, 0f, dh + bottomExt, colors, null, Shader.TileMode.CLAMP)
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
        val interval = if (maxFps >= 60) 0L else (1000L / maxFps)
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





