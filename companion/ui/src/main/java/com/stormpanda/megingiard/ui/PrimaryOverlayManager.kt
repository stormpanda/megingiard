package com.stormpanda.megingiard.ui

import android.app.ActivityOptions
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.mirror.MirrorPresentationLifecycleOwner
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "PrimaryOverlayManager"

/**
 * Manages rendering of deep configuration dialogs, inspectors, setup wizards, and tutorials
 * on the Primary Display (Display 0) as non-Activity WindowManager overlays.
 *
 * By attaching directly to the WindowManager with [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * or [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY], the overlay does NOT push a new Activity
 * onto the task stack. This guarantees that the background game or emulator on Display 0 remains in the
 * [android.app.Activity.RESUMED] state and is NEVER paused while dialogs are open.
 */
object PrimaryOverlayManager {
    private var application: Application? = null
    private var scope: CoroutineScope? = null
    private var overlayView: ComposeView? = null
    private var overlayWindowManager: WindowManager? = null
    private var lifecycleOwner: MirrorPresentationLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(app: Application) {
        if (application != null) return
        application = app
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = coroutineScope

        coroutineScope.launch {
            combine(
                AppStateManager.activePrimaryModal,
                AppStateManager.activeCropCutoutId,
            ) { modal, cropId ->
                modal to cropId
            }.collect { (modal, cropId) ->
                val shouldShow = modal != null || cropId != null
                if (shouldShow) {
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }
        AppLog.i(TAG, "PrimaryOverlayManager initialized")
    }

    private fun showOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOverlayOnMainThread()
        } else {
            mainHandler.post { showOverlayOnMainThread() }
        }
    }

    private fun hideOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideOverlayOnMainThread()
        } else {
            mainHandler.post { hideOverlayOnMainThread() }
        }
    }

    private fun showOverlayOnMainThread() {
        val app = application ?: return
        val isDual = DisplayDetector.findSecondaryDisplay(app) != null
        if (!isDual) {
            // Single-screen devices handle modals in-tree in MainAppScreen
            return
        }

        if (overlayView != null) {
            // Overlay already attached to WindowManager; Compose state flow will re-compose content
            AppLog.d(TAG, "Overlay window already attached — updating content via state flow")
            return
        }

        val accessibilityService = MegingiardAccessibilityService.getInstance()
        val canDrawOverlays = Settings.canDrawOverlays(app)

        val (hostContext, windowType) =
            when {
                accessibilityService != null -> {
                    AppLog.i(TAG, "Using MegingiardAccessibilityService for TYPE_ACCESSIBILITY_OVERLAY on Display 0")
                    accessibilityService to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                }

                canDrawOverlays -> {
                    AppLog.i(TAG, "Using Application context for TYPE_APPLICATION_OVERLAY on Display 0")
                    app to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                }

                else -> {
                    AppLog.w(
                        TAG,
                        "Neither accessibility service nor overlay permission available; falling back to Activity on Display 0",
                    )
                    launchFallbackActivity(app)
                    return
                }
            }

        try {
            val dm = hostContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val primaryDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
            if (primaryDisplay == null) {
                AppLog.e(TAG, "Primary display (DEFAULT_DISPLAY) not found")
                return
            }

            val windowContext = hostContext.createWindowContext(primaryDisplay, windowType, null)
            val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayWindowManager = wm

            val owner =
                MirrorPresentationLifecycleOwner(app) {
                    AppStateManager.closePrimaryModal()
                    AppStateManager.setActiveCropCutoutId(null)
                }
            lifecycleOwner = owner

            val params =
                WindowManager.LayoutParams().apply {
                    type = windowType
                    flags =
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    gravity = Gravity.CENTER
                }

            val view =
                ComposeView(windowContext).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                    isFocusable = true
                    isFocusableInTouchMode = true

                    setOnGenericMotionListener { _, motionEvent ->
                        PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { dpadKeyCode ->
                            val down = KeyEvent(KeyEvent.ACTION_DOWN, dpadKeyCode)
                            val up = KeyEvent(KeyEvent.ACTION_UP, dpadKeyCode)
                            val downHandled = dispatchKeyEvent(down)
                            val upHandled = dispatchKeyEvent(up)
                            if (!downHandled && !upHandled) {
                                PrimaryOverlayInputBridge.sendFocusRecovery(dpadKeyCode)
                            }
                        }
                    }

                    setOnKeyListener { _, keyCode, event ->
                        when {
                            event.action == KeyEvent.ACTION_DOWN &&
                                (
                                    keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK ||
                                        keyCode == KeyEvent.KEYCODE_ESCAPE
                                ) -> {
                                AppLog.i(TAG, "Back / B-Button pressed in PrimaryOverlay -> handling back")
                                if (owner.onBackPressedDispatcher.hasEnabledCallbacks()) {
                                    owner.onBackPressedDispatcher.onBackPressed()
                                } else {
                                    AppStateManager.closePrimaryModal()
                                    AppStateManager.setActiveCropCutoutId(null)
                                }
                                true
                            }

                            event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_L1 -> {
                                AppLog.d(TAG, "L1 pressed -> Bumper PREV")
                                PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
                                true
                            }

                            event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> {
                                AppLog.d(TAG, "R1 pressed -> Bumper NEXT")
                                PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
                                true
                            }

                            event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_A -> {
                                AppLog.d(TAG, "Button A down -> Forwarding as DPAD_CENTER down")
                                val dpadCenterDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                                dispatchKeyEvent(dpadCenterDown)
                                true
                            }

                            event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BUTTON_A -> {
                                AppLog.d(TAG, "Button A up -> Forwarding as DPAD_CENTER up")
                                val dpadCenterUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
                                val handled = dispatchKeyEvent(dpadCenterUp)
                                if (!handled) {
                                    PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_BUTTON_A)
                                }
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }

                    setContent {
                        val themeMode by SettingsManager.themeMode.collectAsState()
                        val userAccentArgb by SettingsManager.accentColor.collectAsState()
                        val appColors = paletteFor(themeMode, Color(userAccentArgb))
                        val activeModal by AppStateManager.activePrimaryModal.collectAsState()
                        val activeCropCutoutId by AppStateManager.activeCropCutoutId.collectAsState()

                        val appLanguage by SettingsManager.appLanguage.collectAsState()
                        val localeContext =
                            remember(appLanguage) {
                                val locale: Locale =
                                    when (appLanguage) {
                                        AppLanguage.SYSTEM -> Locale.getDefault()
                                        AppLanguage.EN -> Locale.ENGLISH
                                        AppLanguage.DE -> Locale.GERMAN
                                        AppLanguage.ZH_TW -> Locale.TRADITIONAL_CHINESE
                                    }
                                val config = Configuration(windowContext.resources.configuration)
                                config.setLocale(locale)
                                windowContext.createConfigurationContext(config)
                            }

                        CompositionLocalProvider(
                            LocalContext provides localeContext,
                            LocalOnBackPressedDispatcherOwner provides owner,
                            LocalAppColors provides appColors,
                            LocalAppDimens provides AppDimens(),
                        ) {
                            MaterialTheme(
                                colorScheme = colorSchemeFor(appColors, themeMode),
                                typography = megingiardTypography,
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Transparent,
                                ) {
                                    when {
                                        activeCropCutoutId != null -> {
                                            CropSelectorOverlay(
                                                cutoutId = activeCropCutoutId!!,
                                                onDismiss = {
                                                    AppLog.d(TAG, "Dismissing crop selector")
                                                    AppStateManager.setActiveCropCutoutId(null)
                                                    AppStateManager.closePrimaryModal()
                                                },
                                            )
                                        }

                                        activeModal != null -> {
                                            PrimaryModalHost(
                                                config = activeModal!!,
                                                onDismiss = {
                                                    AppLog.d(TAG, "Dismissing primary modal: ${activeModal?.type}")
                                                    AppStateManager.closePrimaryModal()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            wm.addView(view, params)
            view.post {
                view.requestFocus()
            }
            overlayView = view
            AppLog.i(TAG, "Primary overlay window successfully attached to Display 0 WindowManager (non-activity)")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to attach primary overlay window: ${e.message}", e)
            launchFallbackActivity(app)
        }
    }

    private fun hideOverlayOnMainThread() {
        val view = overlayView ?: return
        val wm = overlayWindowManager
        try {
            wm?.removeView(view)
            AppLog.i(TAG, "Primary overlay window removed from Display 0 WindowManager")
        } catch (e: Exception) {
            AppLog.e(TAG, "Error removing primary overlay view: ${e.message}", e)
        } finally {
            PrimaryOverlayInputBridge.resetJoystickState()
            overlayView = null
            overlayWindowManager = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
        }
    }

    private fun launchFallbackActivity(context: Context) {
        try {
            val options = ActivityOptions.makeBasic()
            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            val intent =
                Intent(context, PrimaryOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            context.startActivity(intent, options.toBundle())
            AppLog.i(TAG, "Launched fallback PrimaryOverlayActivity on Display.DEFAULT_DISPLAY")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch fallback PrimaryOverlayActivity: ${e.message}", e)
        }
    }
}
