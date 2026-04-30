package com.stormpanda.megingiard

import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.view.Display
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
import androidx.lifecycle.repeatOnLifecycle
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MGRD_MIME_TYPE
import com.stormpanda.megingiard.macropad.MacroExecutor
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Suppress("unused")
private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

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

        // Handle .mgrd config files opened from a file manager or share sheet.
        handleIncomingIntent(intent)

        // Collect export/import requests posted by GlobalSettingsScreen (which may be
        // inside MirrorPresentation and thus cannot hold ActivityResultLaunchers itself).
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConfigManager.exportRequest.collect { meta ->
                    if (meta != null) {
                        pendingExportMetadata = meta
                        AppStateManager.setFilePickerOpen(true)
                        createDocumentLauncher.launch(ConfigManager.exportFilename.value)
                        ConfigManager.clearExportRequest()
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        }
        // FLAG_NOT_FOCUSABLE must only be active when the fullscreen keyboard overlay is
        // shown so that uinput key events are delivered to the focused app (the game) rather
        // than to Megingiard. Keeping it active otherwise would break in-app text fields
        // (e.g. MacroPad profile name inputs).
        lifecycleScope.launch {
            AppStateManager.isFullscreenKeyboardActive.collect { active ->
                if (active) {
                    AppLog.d(TAG, "FLAG_NOT_FOCUSABLE added (fullscreen keyboard active)")
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
}
