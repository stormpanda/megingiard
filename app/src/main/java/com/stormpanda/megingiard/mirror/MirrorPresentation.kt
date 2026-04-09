package com.stormpanda.megingiard.mirror

import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppMode
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
    // Compose (Dialog dismiss, ToolSettingsPanel, etc.) fire correctly. Only if no
    // Compose callback is enabled do we fall back to switching mode.
    private val onBackCallback = OnBackInvokedCallback {
        if (backDispatcher.hasEnabledCallbacks()) {
            backDispatcher.onBackPressed()
        } else {
            AppStateManager.setMode(AppMode.TOUCHPAD)
        }
    }

    override fun cancel() {
        AppStateManager.setMode(AppMode.TOUCHPAD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            onBackCallback
        )
        val lifecycleOwner = MirrorPresentationLifecycleOwner()
        window?.decorView?.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        setOnDismissListener {
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

        // BackHandler (used in ToolSettingsPanel and Compose Dialog) requires
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
                val userAccent by SettingsManager.accentColor.collectAsState()
                val appColors = paletteFor(themeMode, userAccent)

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
                        val mode by AppStateManager.currentMode.collectAsState()
                        val ambientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()
                        val capturing by ScreenCaptureManager.isCapturing.collectAsState()
                        when {
                            mode == AppMode.MIRROR -> MirrorScreen()
                            mode == AppMode.MACROPAD && ambientEnabled && capturing -> AmbientMacroPadOverlay()
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
                AppStateManager.currentMode,
                AppStateManager.isOnValidScreen,
                SettingsManager.macropadAmbientEnabled,
                ScreenCaptureManager.isCapturing
            ) { mode, isValid, ambientEnabled, capturing ->
                // Show based on capturing state, not on whether MainActivity is in the
                // foreground. Using isActivityResumed here caused a feedback loop: each
                // time the user opened the app while mirroring, show() covered the screen,
                // pushing MainActivity to background (ON_PAUSE ~70 ms). ON_STOP then set
                // isResumed=false → hide(), and the cycle repeated indefinitely.
                capturing && isValid && (
                    mode == AppMode.MIRROR ||
                    (mode == AppMode.MACROPAD && ambientEnabled)
                )
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
                                    Log.e(TAG, "PixelCopy failed with result code: $result")
                                    bitmap.recycle()
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "PixelCopy exception", e)
                    }
                } else if (!frozen) {
                    sv.visibility = View.VISIBLE
                    ScreenCaptureManager.setFrozenBitmap(null)
                }
            }
        }
    }
}
