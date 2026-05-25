package com.stormpanda.megingiard.overlay

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Region
import android.os.Build
import android.os.Vibrator
import android.view.Display
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.ButtonColorStyle
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadSurface
import com.stormpanda.megingiard.macropad.triggerHaptic
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.mirror.MirrorPresentationLifecycleOwner
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.PillMenu
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
import java.util.Locale

private const val TAG = "FloatingOverlayController"

class FloatingOverlayController(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var windowContext: Context? = null
    
    private var expandedView: ComposeView? = null
    private var bubbleView: ComposeView? = null
    
    private var lifecycleOwner: MirrorPresentationLifecycleOwner? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var isCollapsed = false
    
    // Position tracking for collapsed bubble (default to top right of bottom screen)
    private var bubbleX = 0
    private var bubbleY = 150

    fun start() {
        AppLog.i(TAG, "start")
        
        // 1. Display routing
        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
        if (secondaryDisplay == null) {
            AppLog.e(TAG, "No secondary display found. Aborting overlay start.")
            AppStateManager.setFloatingOverlayActive(false)
            return
        }
        
        // 2. Synthetic lifecycle owner
        lifecycleOwner = MirrorPresentationLifecycleOwner(context.applicationContext as Application)
        
        // 3. Window Context setup
        val displayContext = context.createDisplayContext(secondaryDisplay)
        windowContext = displayContext.createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 
            null
        )
        windowManager = windowContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 4. Reactive profile/layout layout watcher
        scope.launch {
            combine(
                MacroPadState.activeProfile,
                MacroPadState.activeLayout
            ) { profile, layout -> profile to layout }
                .distinctUntilChanged()
                .collect {
                    AppLog.d(TAG, "Active layout/profile changed -> updating overlay")
                    // Redeploy current view to apply updated button layout
                    if (isCollapsed) {
                        recreateBubble()
                    } else {
                        recreateExpanded()
                    }
                }
        }
        
        // Start in expanded state
        expand()
    }

    fun stop() {
        AppLog.i(TAG, "stop")
        scope.cancel()
        
        removeView(expandedView)
        removeView(bubbleView)
        expandedView = null
        bubbleView = null
        
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private fun expand() {
        AppLog.i(TAG, "Expanding MacroPad overlay")
        isCollapsed = false
        removeView(bubbleView)
        bubbleView = null
        
        recreateExpanded()
    }

    private fun collapse() {
        AppLog.i(TAG, "Collapsing MacroPad overlay")
        isCollapsed = true
        removeView(expandedView)
        expandedView = null
        
        recreateBubble()
    }

    private fun recreateExpanded() {
        removeView(expandedView)
        
        val wCtx = windowContext ?: return
        val wm = windowManager ?: return
        val lc = lifecycleOwner ?: return
        
        val ev = ComposeView(wCtx).apply {
            setViewTreeLifecycleOwner(lc)
            setViewTreeSavedStateRegistryOwner(lc)
            setViewTreeViewModelStoreOwner(lc)
        }
        expandedView = ev

        ev.setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, ComposeColor(userAccentArgb))

            val appLanguage by SettingsManager.appLanguage.collectAsState()
            val localeContext = remember(appLanguage) {
                val locale: Locale = when (appLanguage) {
                    AppLanguage.SYSTEM -> Locale.getDefault()
                    AppLanguage.EN     -> Locale.ENGLISH
                    AppLanguage.DE     -> Locale.GERMAN
                }
                val config = Configuration(wCtx.resources.configuration)
                config.setLocale(locale)
                wCtx.createConfigurationContext(config)
            }

            CompositionLocalProvider(
                LocalContext provides localeContext,
                LocalAppColors provides appColors,
                LocalAppDimens provides AppDimens()
            ) {
                MaterialTheme(
                    colorScheme = colorSchemeFor(appColors, themeMode),
                    typography = megingiardTypography
                ) {
                    val profile by MacroPadState.activeProfile.collectAsState()
                    val layout by MacroPadState.activeLayout.collectAsState()
                    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
                    val isPillMenuOpen by AppStateManager.isPillMenuOpen.collectAsState()
                    
                    val density = LocalDensity.current
                    
                    var swipeStartY by remember { mutableFloatStateOf(Float.NaN) }
                    var collapseTriggered by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(overlayAtBottom) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue
                                        val y = change.position.y
                                        val x = change.position.x

                                        val densityVal = density.density
                                        val pillLeft = size.width / 2f - 60f * densityVal
                                        val pillRight = size.width / 2f + 60f * densityVal
                                        val pillTop = if (overlayAtBottom) size.height - 40f * densityVal else 0f
                                        val pillBottom = if (overlayAtBottom) size.height.toFloat() else 40f * densityVal

                                        val inPillZone = x in pillLeft..pillRight && y in pillTop..pillBottom

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (inPillZone) {
                                                    swipeStartY = y
                                                    collapseTriggered = false
                                                }
                                            }
                                            PointerEventType.Move -> {
                                                if (!swipeStartY.isNaN() && !collapseTriggered) {
                                                    val delta = if (overlayAtBottom) swipeStartY - y else y - swipeStartY
                                                    val collapseThreshold = 140f * densityVal
                                                    if (delta >= collapseThreshold) {
                                                        collapseTriggered = true
                                                        AppLog.i(TAG, "Pill swipe-to-collapse triggered")
                                                        val vibrator = context.getSystemService(Vibrator::class.java)
                                                        triggerHaptic(vibrator, HapticStrength.MEDIUM)
                                                        collapse()
                                                    }
                                                }
                                            }
                                            PointerEventType.Release -> {
                                                swipeStartY = Float.NaN
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val p = profile
                        val l = layout
                        if (p != null && l != null) {
                            PadSurface(
                                profile = p,
                                layout = l,
                                accentColor = appColors.accent,
                                transparentBackground = true,
                                neutralStyle = l.buttonColorMirror == ButtonColorStyle.NEUTRAL
                            )
                        }
                        
                        IdlePill()
                        
                        PillMenu(
                            visible = isPillMenuOpen,
                            onDismiss = { AppStateManager.closePillMenu() }
                        )
                    }
                }
            }
        }

        // Setup window touchable region logic using reflection & dynamic proxy to bypass hidden API stubs
        try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val insetsClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val regionClass = Class.forName("android.graphics.Region")
            val opClass = Class.forName("android.graphics.Region\$Op")
            val unionOp = opClass.getField("UNION").get(null)
            
            val setTouchableInsetsMethod = insetsClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = insetsClass.getField("touchableRegion")
            
            val opMethod = regionClass.getMethod(
                "op", 
                Int::class.javaPrimitiveType, 
                Int::class.javaPrimitiveType, 
                Int::class.javaPrimitiveType, 
                Int::class.javaPrimitiveType, 
                opClass
            )
            val setEmptyMethod = regionClass.getMethod("setEmpty")

            val listener = java.lang.reflect.Proxy.newProxyInstance(
                wCtx.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.size == 1) {
                    val info = args[0]
                    // TOUCHABLE_INSETS_REGION = 3
                    setTouchableInsetsMethod.invoke(info, 3)
                    
                    val touchableRegion = touchableRegionField.get(info)
                    setEmptyMethod.invoke(touchableRegion)
                    
                    val density = ev.resources.displayMetrics.density
                    val w = ev.width.toFloat()
                    val h = ev.height.toFloat()
                    
                    val padLayout = MacroPadState.activeLayout.value
                    padLayout?.buttons?.forEach { btn ->
                        val btnW = btn.buttonSize.cols * 60f * density
                        val btnH = btn.buttonSize.rows * 60f * density
                        val btnX = btn.posX * w
                        val btnY = btn.posY * h
                        
                        val left = (btnX - btnW / 2f).toInt()
                        val top = (btnY - btnH / 2f).toInt()
                        val right = (btnX + btnW / 2f).toInt()
                        val bottom = (btnY + btnH / 2f).toInt()
                        
                        opMethod.invoke(touchableRegion, left, top, right, bottom, unionOp)
                    }
                    
                    // IdlePill region (centered, 120dp wide, 40dp high)
                    val pillW = 120f * density
                    val pillH = 40f * density
                    val pillX = w / 2f
                    val pillLeft = (pillX - pillW / 2f).toInt()
                    val pillRight = (pillX + pillW / 2f).toInt()
                    
                    val overlayAtBottom = SettingsManager.overlayAtBottom.value
                    val pillTop = if (overlayAtBottom) (h - pillH).toInt() else 0
                    val pillBottom = if (overlayAtBottom) h.toInt() else pillH.toInt()
                    
                    opMethod.invoke(touchableRegion, pillLeft, pillTop, pillRight, pillBottom, unionOp)
                    
                    // Full screen if Pill Menu or modals are showing
                    if (AppStateManager.isPillMenuOpen.value || AppStateManager.isAnyModalActive.value) {
                        opMethod.invoke(touchableRegion, 0, 0, w.toInt(), h.toInt(), unionOp)
                    }
                }
                null
            }

            val addListenerMethod = ViewTreeObserver::class.java.getMethod(
                "addOnComputeInternalInsetsListener", 
                listenerClass
            )
            addListenerMethod.invoke(ev.viewTreeObserver, listener)
            AppLog.i(TAG, "Touch pass-through listener registered successfully via reflection")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to register touchable region listener via reflection", e)
        }

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }
        
        wm.addView(ev, params)
    }

    private fun recreateBubble() {
        removeView(bubbleView)
        
        val wCtx = windowContext ?: return
        val wm = windowManager ?: return
        val lc = lifecycleOwner ?: return
        
        val displayMetrics = wCtx.resources.displayMetrics
        val density = displayMetrics.density
        val sizePx = (56f * density).toInt()

        val bv = ComposeView(wCtx).apply {
            setViewTreeLifecycleOwner(lc)
            setViewTreeSavedStateRegistryOwner(lc)
            setViewTreeViewModelStoreOwner(lc)
        }
        bubbleView = bv

        bv.setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, ComposeColor(userAccentArgb))

            CompositionLocalProvider(
                LocalAppColors provides appColors
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(appColors.controlOverlay.copy(alpha = 0.9f))
                        .border(1.5.dp, appColors.accent, CircleShape)
                        .clickable { expand() }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                bubbleX = (bubbleX + dragAmount.x).toInt()
                                bubbleY = (bubbleY + dragAmount.y).toInt()
                                
                                val layoutParams = bv.layoutParams as? WindowManager.LayoutParams
                                if (layoutParams != null) {
                                    layoutParams.x = bubbleX
                                    layoutParams.y = bubbleY
                                    wm.updateViewLayout(bv, layoutParams)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Widgets,
                        contentDescription = "Expand MacroPad",
                        tint = appColors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Draggable bubble LayoutParams
        val params = WindowManager.LayoutParams().apply {
            width = sizePx
            height = sizePx
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
        
        wm.addView(bv, params)
    }

    private fun removeView(view: ComposeView?) {
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error removing overlay view", e)
            }
        }
    }
}
