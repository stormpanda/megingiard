package com.stormpanda.megingiard

import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.view.Display
import android.view.InputEvent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MGRD_MIME_TYPE
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadHitTestEngine
import com.stormpanda.megingiard.macropad.GamepadRecordingManager
import com.stormpanda.megingiard.macropad.GamepadKeycodes
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.settings.AppLanguage
import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
private const val VIRTUAL_GAMEPAD_DEVICE_NAME = "Megingiard Virtual Gamepad"

class MainActivity : ComponentActivity() {

    private var fallbackDpadX = 0
    private var fallbackDpadY = 0

    // ── File picker launchers ─────────────────────────────────────────────────
    // Registered here because ActivityResultLaunchers require an Activity context.
    // GlobalSettingsScreen (which may be inside MirrorPresentation) posts requests
    // to ConfigManager and these launchers pick them up.

    private var pendingExportMetadata: ExportMetadata? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MGRD_MIME_TYPE)
    ) { uri ->
        AppStateManager.setFilePickerOpen(false)
        val meta = pendingExportMetadata ?: return@registerForActivityResult
        pendingExportMetadata = null
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ConfigManager.writeToUri(this@MainActivity, uri, ConfigManager.buildExport(meta)) }
                .onSuccess {
                    AppLog.i(TAG, "Export written to $uri")
                    ConfigManager.setExportResult(ConfigManager.ExportResult.Success)
                }
                .onFailure { e ->
                    AppLog.e(TAG, "Export failed", e)
                    ConfigManager.setExportResult(ConfigManager.ExportResult.Failure(e.message))
                }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        AppStateManager.setFilePickerOpen(false)
        if (uri == null) {
            ConfigManager.clearImportRequest()
            return@registerForActivityResult
        }
        ConfigManager.setPendingInAppUri(uri)
    }

    // The manifest declares configChanges that prevent activity recreation when the app
    // is moved between displays. Without this override, Compose never recomposes and
    // context.display retains the old display ID — the wrong-screen overlay would stay
    // visible even after moving to the correct display.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        DisplayDetector.updateDisplayValidity(displayId)
        // Mirror the ON_RESUME auto-start behaviour: if the app just moved onto the
        // valid screen, let the capture prompt fire (if the user enabled auto-start).
        if (AppStateManager.isOnValidScreen.value && SettingsManager.autoStartCapture.value) {
            AppStateManager.setUserDeclinedCapture(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate")

        // Provide a stable applicationContext to MacroExecutor so that TouchTap macro
        // steps can start TouchInjector without needing the caller to supply a Context.
        MacroExecutor.init(this)

        // Initialise R.string resource IDs for the domain-layer MacroPadHitTestEngine.
        MacroPadHitTestEngine.MACROPAD_DEVICE_DISABLED_KEYBOARD = R.string.macropad_device_disabled_keyboard
        MacroPadHitTestEngine.MACROPAD_DEVICE_DISABLED_GAMEPAD = R.string.macropad_device_disabled_gamepad
        MacroPadHitTestEngine.MACROPAD_DEVICE_DISABLED_MOUSE = R.string.macropad_device_disabled_mouse

        // Handle .mgrd config files opened from a file manager or share sheet.
        handleIncomingIntent(intent)

        // Collect export/import requests posted by GlobalSettingsScreen (which may be
        // inside MirrorPresentation and thus cannot hold ActivityResultLaunchers itself).
        lifecycleScope.launch {
            ConfigManager.exportRequest.collect { meta ->
                if (meta != null) {
                    pendingExportMetadata = meta
                    AppStateManager.setFilePickerOpen(true)
                    createDocumentLauncher.launch(ConfigManager.exportFilename.value)
                    ConfigManager.clearExportRequest()
                }
            }
        }
        lifecycleScope.launch {
            ConfigManager.importRequested.collect { requested ->
                if (requested) {
                    AppStateManager.setFilePickerOpen(true)
                    // Use "*/*" instead of the custom MGRD MIME type: the Android file
                    // picker (DocumentsUI) does not know the custom MIME type and would
                    // show an empty list. With "*/*" all files are visible and the user
                    // can navigate to their .mgrd file.
                    openDocumentLauncher.launch(arrayOf("*/*"))
                    ConfigManager.clearImportRequest()
                }
            }
        }
        // FLAG_NOT_FOCUSABLE must only be active for focus-forwarding overlays/sessions:
        // fullscreen keyboard and gamepad recording passthrough. This keeps injected input
        // targeted at the currently focused game window instead of Megingiard.
        lifecycleScope.launch {
            combine(
                AppStateManager.isFullscreenKeyboardActive,
                AppStateManager.isGamepadRecordingPassthroughActive,
            ) { keyboardActive, recordingPassthroughActive ->
                keyboardActive || recordingPassthroughActive
            }.collect { active ->
                if (active) {
                    AppLog.d(TAG, "FLAG_NOT_FOCUSABLE added (keyboard/recording passthrough active)")
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                } else {
                    AppLog.d(TAG, "FLAG_NOT_FOCUSABLE cleared")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                }
            }
        }
        SettingsManager.init(this)
        lifecycleScope.launch {
            SettingsManager.appLanguage.drop(1).collect { lang ->
                AppLog.d(TAG, "appLanguage changed to $lang → applying locales")
                val desired = when (lang) {
                    AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                    AppLanguage.EN     -> LocaleList(Locale.ENGLISH)
                    AppLanguage.DE     -> LocaleList(Locale.GERMAN)
                }
                val localeManager = getSystemService(LocaleManager::class.java)
                if (localeManager.applicationLocales != desired) {
                    localeManager.applicationLocales = desired
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
            val promptInFlight by AppStateManager.promptInFlight.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            AppLog.i(TAG, "ON_RESUME isValid=${AppStateManager.isOnValidScreen.value} autoStart=${SettingsManager.autoStartCapture.value}")
                            AppStateManager.setActivityResumed(true)
                            // Auto-start: treat each app resume as a fresh opportunity.
                            // Only clear the decline flag here — not in the LaunchedEffect,
                            // so a decline within a session is respected until the next resume.
                            // Guard: only on the secondary (valid) screen — prevents a
                            // capture prompt from firing if the app is on the primary display.
                            if (SettingsManager.autoStartCapture.value
                                && AppStateManager.isOnValidScreen.value
                            ) {
                                AppStateManager.setUserDeclinedCapture(false)
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            AppLog.i(TAG, "ON_STOP")
                            AppStateManager.setActivityResumed(false)
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }

            // Synchronous display evaluation gets correct value on frame 0
            val context = LocalContext.current
            val currentDisplayId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
            val isOnValidScreenLocal = currentDisplayId != Display.DEFAULT_DISPLAY

            // Update global state for other components
            LaunchedEffect(isOnValidScreenLocal) {
                AppStateManager.setOnValidScreen(isOnValidScreenLocal)
            }

            val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()

            val macropadAmbientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()

            // Auto-start capture when Ambient Display is enabled.
            // Declining within a session is respected until the next mode entry.
            // Guard: skip when on the primary display — no capture should start there.
            LaunchedEffect(macropadAmbientEnabled, isOnValidScreenLocal) {
                if (macropadAmbientEnabled
                    && isOnValidScreenLocal && !isCapturing
                ) {
                    AppStateManager.setUserDeclinedCapture(false)
                }
            }

            // Launch capture proxy on the main screen when conditions are met.
            // With autoStartCapture=false (default) the user must tap "Start mirroring" manually.
            // With autoStartCapture=true the prompt fires once per app session (on resume);
            // declining within a session is respected — the dialog will not re-appear until the next resume.
            LaunchedEffect(isCapturing, promptInFlight, isOnValidScreenLocal, userDeclinedCapture) {
                if (!promptInFlight && isOnValidScreenLocal && !isCapturing && !userDeclinedCapture) {
                    AppLog.i(TAG, "launching CaptureRequestActivity on display DEFAULT")
                    AppStateManager.setPromptInFlight(true)
                    val options = ActivityOptions.makeBasic()
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    val intent = Intent(this@MainActivity, CaptureRequestActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                    startActivity(intent, options.toBundle())
                }
            }

            // ── Mirror button signals from MacroPad ───────────────────────────────
            // MirrorPlayStop button sets these flags in AppStateManager; MainActivity
            // handles them here because sending intents or launching Activities requires
            // a Context only available in the Activity layer.
            val mirrorStartRequested by AppStateManager.mirrorStartRequested.collectAsState()
            val mirrorStopRequested by AppStateManager.mirrorStopRequested.collectAsState()

            LaunchedEffect(mirrorStartRequested) {
                if (!mirrorStartRequested) return@LaunchedEffect
                AppLog.i(TAG, "mirrorStartRequested → triggering capture flow")
                AppStateManager.consumeMirrorStartRequest()
                // Override any in-session decline so the capture prompt fires.
                AppStateManager.setUserDeclinedCapture(false)
            }

            LaunchedEffect(mirrorStopRequested) {
                if (!mirrorStopRequested) return@LaunchedEffect
                AppLog.i(TAG, "mirrorStopRequested → sending STOP to ScreenCaptureService")
                AppStateManager.consumeMirrorStopRequest()
                val stopIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                    action = "STOP"
                }
                startService(stopIntent)
            }

            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, Color(userAccentArgb))

            MaterialTheme(
                colorScheme = colorSchemeFor(appColors, themeMode),
                typography = megingiardTypography
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
                    LocalAppDimens provides AppDimens()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.appBackground
                    ) {
                        MainAppScreen()
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadRecordingManager.isFrameworkCaptureActive() && !isFromVirtualGamepad(event)) {
            val action = event.action
            if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
                val isPressed = action == KeyEvent.ACTION_DOWN
                val mappedButton = mapAndroidGamepadButton(event.keyCode)
                if (mappedButton != null) {
                    if (GamepadRecordingManager.onFrameworkButtonEvent(mappedButton, isPressed)) {
                        return true
                    }
                }
                if (handleDpadKeyForFallback(event.keyCode, isPressed)) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (GamepadRecordingManager.isFrameworkCaptureActive()
            && !isFromVirtualGamepad(event)
            && event.action == MotionEvent.ACTION_MOVE
            && (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK))
        ) {
            val handled = GamepadRecordingManager.onFrameworkAxisSnapshot(
                leftX = event.getAxisValue(MotionEvent.AXIS_X),
                leftY = event.getAxisValue(MotionEvent.AXIS_Y),
                rightX = event.getAxisValue(MotionEvent.AXIS_Z),
                rightY = event.getAxisValue(MotionEvent.AXIS_RZ),
                hatX = axisToHatDirection(event.getAxisValue(MotionEvent.AXIS_HAT_X)),
                hatY = axisToHatDirection(event.getAxisValue(MotionEvent.AXIS_HAT_Y)),
            )
            if (handled) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun isFromVirtualGamepad(event: InputEvent): Boolean {
        val deviceName = event.device?.name ?: return false
        return deviceName.contains(VIRTUAL_GAMEPAD_DEVICE_NAME, ignoreCase = true)
    }

    /** Called when the app is already running and receives a new ACTION_VIEW intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLog.i(TAG, "onNewIntent")
        handleIncomingIntent(intent)
    }

    /**
     * Checks whether [intent] is an ACTION_VIEW intent carrying a `.mgrd` URI and, if so,
     * notifies [ConfigManager] so the Compose UI can show the import preview dialog.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                AppLog.i(TAG, "handleIncomingIntent: .mgrd URI received: $uri")
                ConfigManager.setPendingUri(uri)
            }
        }
    }

    private fun handleDpadKeyForFallback(keyCode: Int, isPressed: Boolean): Boolean {
        var changed = false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val next = if (isPressed) -1 else if (fallbackDpadX < 0) 0 else fallbackDpadX
                if (next != fallbackDpadX) {
                    fallbackDpadX = next
                    changed = true
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val next = if (isPressed) 1 else if (fallbackDpadX > 0) 0 else fallbackDpadX
                if (next != fallbackDpadX) {
                    fallbackDpadX = next
                    changed = true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                val next = if (isPressed) -1 else if (fallbackDpadY < 0) 0 else fallbackDpadY
                if (next != fallbackDpadY) {
                    fallbackDpadY = next
                    changed = true
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val next = if (isPressed) 1 else if (fallbackDpadY > 0) 0 else fallbackDpadY
                if (next != fallbackDpadY) {
                    fallbackDpadY = next
                    changed = true
                }
            }
        }

        if (!changed) {
            return false
        }
        return GamepadRecordingManager.onFrameworkDpadState(fallbackDpadX, fallbackDpadY)
    }

    private fun axisToHatDirection(value: Float): Int = when {
        value > 0.5f -> 1
        value < -0.5f -> -1
        else -> 0
    }

    private fun mapAndroidGamepadButton(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GamepadKeycodes.BTN_SOUTH
        KeyEvent.KEYCODE_BUTTON_B -> GamepadKeycodes.BTN_EAST
        KeyEvent.KEYCODE_BUTTON_X -> GamepadKeycodes.BTN_WEST
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadKeycodes.BTN_NORTH
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadKeycodes.BTN_TL
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadKeycodes.BTN_TR
        KeyEvent.KEYCODE_BUTTON_L2 -> GamepadKeycodes.BTN_TL2
        KeyEvent.KEYCODE_BUTTON_R2 -> GamepadKeycodes.BTN_TR2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadKeycodes.BTN_THUMBL
        KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadKeycodes.BTN_THUMBR
        KeyEvent.KEYCODE_BUTTON_START -> GamepadKeycodes.BTN_START
        KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadKeycodes.BTN_SELECT
        KeyEvent.KEYCODE_BUTTON_MODE -> GamepadKeycodes.BTN_MODE
        else -> null
    }
}
