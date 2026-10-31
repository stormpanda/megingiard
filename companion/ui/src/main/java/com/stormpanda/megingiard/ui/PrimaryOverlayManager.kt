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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "PrimaryOverlayManager"

internal fun syncPrimaryOverlayMirrorFreeze(
    modal: PrimaryModalConfig?,
    wasFrozenForModal: Boolean,
): Boolean {
    if (modal == null) return wasFrozenForModal
    return if (modal.type != PrimaryModalType.MIRROR_VIEWPORT_EDITOR) {
        if (!wasFrozenForModal && ScreenCaptureManager.isCapturing.value && !ScreenCaptureManager.isFrozen.value) {
            AppLog.i(TAG, "Freezing mirror capture for primary modal: ${modal.type}")
            ScreenCaptureManager.setFrozen(true)
            true
        } else {
            wasFrozenForModal
        }
    } else {
        if (wasFrozenForModal) {
            AppLog.i(TAG, "Resuming live mirror capture for viewport editor")
            if (ScreenCaptureManager.isCapturing.value) {
                ScreenCaptureManager.setFrozen(false)
            }
        }
        false
    }
}

internal fun releasePrimaryOverlayMirrorFreeze(wasFrozenForModal: Boolean): Boolean {
    if (wasFrozenForModal && ScreenCaptureManager.isCapturing.value) {
        AppLog.i(TAG, "Resuming live mirror capture after closing primary modal")
        ScreenCaptureManager.setFrozen(false)
    }
    return false
}

internal fun isDirectionalDpadKey(keyCode: Int): Boolean =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        -> true

        else -> false
    }

internal fun isGamepadNavigationKey(keyCode: Int): Boolean =
    isDirectionalDpadKey(keyCode) ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        KeyEvent.isGamepadButton(keyCode)

internal fun notifyPrimaryOverlayTouchEvent(event: MotionEvent) {
    val isGamepadOrJoystick =
        event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)
    if (!isGamepadOrJoystick &&
        (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN)
    ) {
        PrimaryOverlayInputBridge.notifyTouchInput()
    }
}

internal fun dispatchPrimaryOverlayGenericMotionEvent(
    motionEvent: MotionEvent,
    dispatchKey: (KeyEvent) -> Boolean,
): Boolean =
    PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, dpadKeyCode ->
        val keyEvent = KeyEvent(action, dpadKeyCode)
        val handled = dispatchKey(keyEvent)
        if (!handled && action == KeyEvent.ACTION_DOWN) {
            PrimaryOverlayInputBridge.sendFocusRecovery(dpadKeyCode)
        }
    }

internal fun dispatchPrimaryOverlayKeyEvent(
    action: Int,
    keyCode: Int,
    backDispatcherOwner: OnBackPressedDispatcherOwner,
    dispatchKey: (KeyEvent) -> Boolean,
): Boolean =
    when {
        action == KeyEvent.ACTION_DOWN && isBackKey(keyCode) -> {
            PrimaryOverlayInputBridge.notifyGamepadInput()
            AppLog.i(TAG, "Back / B-Button pressed in PrimaryOverlay -> handling back")
            if (backDispatcherOwner.onBackPressedDispatcher.hasEnabledCallbacks()) {
                backDispatcherOwner.onBackPressedDispatcher.onBackPressed()
            } else {
                AppStateManager.closePrimaryModal()
                AppStateManager.setActiveCropCutoutId(null)
                AppStateManager.setSelectedCutoutId(null)
            }
            true
        }

        action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_L1 -> {
            AppLog.d(TAG, "L1 pressed -> Bumper PREV")
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
            true
        }

        action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> {
            AppLog.d(TAG, "R1 pressed -> Bumper NEXT")
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
            true
        }

        action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_A -> {
            PrimaryOverlayInputBridge.notifyGamepadInput()
            AppLog.d(TAG, "Button A down -> Forwarding as DPAD_CENTER down")
            dispatchKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            true
        }

        action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BUTTON_A -> {
            AppLog.d(TAG, "Button A up -> Forwarding as DPAD_CENTER up")
            val handled = dispatchKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            if (!handled) {
                PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_BUTTON_A)
            }
            true
        }

        else -> {
            false
        }
    }

@Composable
internal fun PrimaryOverlayRootContent(
    hostContext: Context,
    backDispatcherOwner: OnBackPressedDispatcherOwner,
) {
    val themeMode by SettingsManager.themeMode.collectAsStateWithLifecycle()
    val userAccentArgb by SettingsManager.accentColor.collectAsStateWithLifecycle()
    val appColors = paletteFor(themeMode, Color(userAccentArgb))
    val activeModal by AppStateManager.activePrimaryModal.collectAsStateWithLifecycle()
    val appLanguage by SettingsManager.appLanguage.collectAsStateWithLifecycle()

    val localeContext =
        remember(hostContext, appLanguage) {
            val locale: Locale =
                when (appLanguage) {
                    AppLanguage.SYSTEM -> Locale.getDefault()
                    AppLanguage.EN -> Locale.ENGLISH
                    AppLanguage.DE -> Locale.GERMAN
                    AppLanguage.ZH_TW -> Locale.TRADITIONAL_CHINESE
                }
            val config = Configuration(hostContext.resources.configuration)
            config.setLocale(locale)
            hostContext.createConfigurationContext(config)
        }

    CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
        LocalAppColors provides appColors,
        LocalFullscreenKeyboardRequester provides { active -> AppStateManager.setFullscreenKeyboardActive(active) },
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(appColors),
            typography = megingiardTypography,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {
                activeModal?.let { modal ->
                    PrimaryModalHost(
                        config = modal,
                        onDismiss = {
                            AppLog.d(TAG, "Dismissing primary modal: ${modal.type}")
                            AppStateManager.closePrimaryModal()
                        },
                    )
                }
            }
        }
    }
}

private class PrimaryOverlayHostView(
    context: Context,
    private val backDispatcherOwner: OnBackPressedDispatcherOwner,
) : FrameLayout(context) {
    private var isForwardingTranslatedKey = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        notifyPrimaryOverlayTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (dispatchPrimaryOverlayGenericMotionEvent(event, ::dispatchKeyEvent)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && isGamepadNavigationKey(event.keyCode)) {
            PrimaryOverlayInputBridge.notifyGamepadInput()
        }
        if (super.dispatchKeyEvent(event)) {
            return true
        }
        if (!isForwardingTranslatedKey) {
            isForwardingTranslatedKey = true
            try {
                if (dispatchPrimaryOverlayKeyEvent(
                        action = event.action,
                        keyCode = event.keyCode,
                        backDispatcherOwner = backDispatcherOwner,
                        dispatchKey = ::dispatchKeyEvent,
                    )
                ) {
                    return true
                }
            } finally {
                isForwardingTranslatedKey = false
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && isDirectionalDpadKey(event.keyCode)) {
            PrimaryOverlayInputBridge.sendFocusRecovery(event.keyCode)
            return true
        }
        return false
    }
}

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
    private var overlayView: View? = null
    private var overlayWindowManager: WindowManager? = null
    private var lifecycleOwner: WindowOverlayLifecycleOwner? = null
    private var wasFrozenForModal = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(app: Application) {
        if (application != null) return
        application = app
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = coroutineScope

        coroutineScope.launch {
            AppStateManager.activePrimaryModal.collect { modal ->
                handleOverlayState(modal)
            }
        }
        AppLog.i(TAG, "PrimaryOverlayManager initialized")
    }

    private fun handleOverlayState(modal: PrimaryModalConfig?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleOverlayStateOnMainThread(modal)
        } else {
            mainHandler.post { handleOverlayStateOnMainThread(modal) }
        }
    }

    private fun handleOverlayStateOnMainThread(modal: PrimaryModalConfig?) {
        if (modal == null) {
            hideOverlayOnMainThread()
            return
        }
        wasFrozenForModal = syncPrimaryOverlayMirrorFreeze(modal, wasFrozenForModal)
        showOverlayOnMainThread()
    }

    private fun showOverlayOnMainThread() {
        val app = application ?: return
        val isDual = DisplayDetector.findSecondaryDisplay(app) != null
        if (!isDual) {
            AppLog.w(TAG, "No secondary display found; Megingiard requires a dual-screen device.")
            return
        }

        if (overlayView != null) {
            // Overlay already attached to WindowManager; Compose state flow will re-compose content
            AppLog.d(TAG, "Overlay window already attached — updating content via state flow")
            overlayView?.post {
                overlayView?.requestFocus()
            }
            if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_DOWN)
            }
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
                WindowOverlayLifecycleOwner(app) {
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

            val composeView =
                ComposeView(windowContext).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    isFocusable = true
                    isFocusableInTouchMode = true

                    setContent {
                        PrimaryOverlayRootContent(
                            hostContext = windowContext,
                            backDispatcherOwner = owner,
                        )
                    }
                }

            val hostView =
                PrimaryOverlayHostView(windowContext, owner).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                    addView(composeView)
                }

            wm.addView(hostView, params)
            composeView.post {
                composeView.requestFocus()
            }
            overlayView = hostView
            AppLog.i(TAG, "Primary overlay window successfully attached to Display 0 WindowManager (non-activity)")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to attach primary overlay window: ${e.message}", e)
            lifecycleOwner?.destroy()
            lifecycleOwner = null
            overlayWindowManager = null
            overlayView = null
            wasFrozenForModal = releasePrimaryOverlayMirrorFreeze(wasFrozenForModal)
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
            wasFrozenForModal = releasePrimaryOverlayMirrorFreeze(wasFrozenForModal)
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
