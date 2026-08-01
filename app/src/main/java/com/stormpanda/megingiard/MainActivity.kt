package com.stormpanda.megingiard

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.os.Process
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MGRD_MIME_TYPE
import com.stormpanda.megingiard.log.LogReportManager
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ACTION_START_PRIVD
import com.stormpanda.megingiard.mirror.ACTION_STOP
import com.stormpanda.megingiard.mirror.CropSelectorActivity
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.mirror.MirrorRuntimeAction
import com.stormpanda.megingiard.mirror.MirrorRuntimePolicyState
import com.stormpanda.megingiard.mirror.MirrorStrategy
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService
import com.stormpanda.megingiard.mirror.decideMirrorRuntimeAction
import com.stormpanda.megingiard.mirror.isPrivdMirrorConnecting
import com.stormpanda.megingiard.mirror.selectMirrorStrategy
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.provider.MegingiardSettingsProvider
import com.stormpanda.megingiard.security.SignatureGuard
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.ScreenshotPreviewOverlay
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    // ── File picker launchers ─────────────────────────────────────────────────
    // Registered here because ActivityResultLaunchers require an Activity context.
    // GlobalSettingsScreen (which may be inside MirrorPresentation) posts requests
    // to ConfigManager and these launchers pick them up.

    private var pendingExportKind: ConfigManager.ExportKind? = null
    private var pendingInAppImportMode = ConfigManager.ImportMode.BACKUP_RESTORE

    private val createDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(MGRD_MIME_TYPE),
        ) { uri ->
            AppStateManager.setFilePickerOpen(false)
            val kind = pendingExportKind ?: return@registerForActivityResult
            pendingExportKind = null
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val export =
                        when (kind) {
                            is ConfigManager.ExportKind.Backup -> {
                                ConfigManager.buildExport(
                                    kind.metadata,
                                    this@MainActivity,
                                    kind.includeBackgrounds,
                                )
                            }

                            is ConfigManager.ExportKind.ProfileShare -> {
                                ConfigManager.buildProfileExport(
                                    kind.metadata,
                                    kind.profile,
                                    this@MainActivity,
                                    kind.includeBackgrounds,
                                )
                            }
                        }
                    ConfigManager.writeToUri(this@MainActivity, uri, export, kind.includeBackgrounds)
                }.onSuccess {
                    AppLog.i(TAG, "Export written to $uri")
                    ConfigManager.setExportResult(ConfigManager.ExportResult.Success)
                }.onFailure { e ->
                    AppLog.e(TAG, "Export failed", e)
                    ConfigManager.setExportResult(ConfigManager.ExportResult.Failure(e.message))
                }
            }
        }

    private val openDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            AppStateManager.setFilePickerOpen(false)
            if (uri == null) {
                return@registerForActivityResult
            }
            ConfigManager.setPendingInAppUri(uri, pendingInAppImportMode)
        }

    private val createLogDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            AppStateManager.setFilePickerOpen(false)
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                LogReportManager.writeReportToUri(
                    context = applicationContext,
                    uri = uri,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceModel = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    pid = Process.myPid(),
                )
            }
        }

    // The manifest declares configChanges that prevent activity recreation when the app
    // is moved between displays. Without this override, Compose never recomposes and
    // context.display retains the old display ID — the wrong-screen overlay would stay
    // visible even after moving to the correct display.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        DisplayDetector.updateDisplayValidity(displayId)
    }

    /**
     * Called when the user explicitly navigates away from the app (Home button,
     * Recents). NOT called when a Presentation or other same-process window covers
     * the Activity. This is the correct signal for hiding mirror presentations
     * without triggering the show/hide feedback loop that [isActivityResumed] causes.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        AppLog.i(TAG, "onUserLeaveHint → user navigating away, hiding presentations")
        AppStateManager.setUserLeaving(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        super.onCreate(savedInstanceState)

        // Init settings first so the persisted log level is active before anything
        // else runs (including SignatureGuard below). SettingsManager.init() reads
        // just the log level synchronously from DataStore then continues async.
        SettingsManager.init(this)

        // Load the per-install Privd pair key before any connect() attempt.
        // The Keystore decrypt is a short hardware-backed operation (~10 ms);
        // loading it here (before setContent) ensures the key is in place
        // before the auto-connect collector in GlobalSettingsViewModel fires.
        PrivdClient.loadKey(this)

        AppLog.i(TAG, "onCreate")

        // APK signature pinning — abort on tampered/re-signed release builds,
        // and refuse to run a release build that ships without a pinned hash
        // (fail-closed; this is the second line of defence behind the Gradle
        // guard in app/build.gradle.kts).
        when (val res = SignatureGuard.verify(this)) {
            is SignatureGuard.Result.Tampered -> {
                if (!BuildConfig.DEBUG) {
                    AppLog.e(TAG, "Aborting: APK signature does not match pinned hash")
                    finishAffinity()
                    Process.killProcess(Process.myPid())
                    return
                } else {
                    AppLog.w(TAG, "Debug build: signature mismatch ignored ($res)")
                }
            }

            is SignatureGuard.Result.Error -> {
                if (!BuildConfig.DEBUG) {
                    AppLog.e(TAG, "Aborting: signature verification failed (${res.message})")
                    finishAffinity()
                    Process.killProcess(Process.myPid())
                    return
                }
            }

            SignatureGuard.Result.Skipped -> {
                if (!BuildConfig.DEBUG) {
                    AppLog.e(TAG, "Aborting: release build ships without a pinned signing hash")
                    finishAffinity()
                    Process.killProcess(Process.myPid())
                    return
                }
            }

            SignatureGuard.Result.Ok -> {
                Unit
            }
        }

        // Provide a stable applicationContext to MacroExecutor so that TouchTap macro
        // steps can start TouchInjector without needing the caller to supply a Context.
        MacroExecutor.init(this)

        SettingsManager.onThemeChangedListener = {
            MegingiardSettingsProvider.notifyThemeChanged(this)
            MegingiardSettingsProvider.notifySettingsChanged(this)
        }

        val hasCreds =
            File(noBackupFilesDir, "privd_adb_key.bin").exists() &&
                File(noBackupFilesDir, "privd_adb_cert.bin").exists()
        AppStateManager.setHasAdbCredentials(hasCreds)
        AppStateManager.setAccessibilityActive(MegingiardAccessibilityService.isEnabled(this))
        AppStateManager.resetPrivdPromptState()

        // Handle .mgrd config files opened from a file manager or share sheet.
        handleIncomingIntent(intent)

        // Collect export/import requests posted by GlobalSettingsScreen (which may be
        // inside MirrorPresentation and thus cannot hold ActivityResultLaunchers itself).
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConfigManager.exportRequest.collect { kind ->
                    pendingExportKind = kind
                    AppStateManager.setFilePickerOpen(true)
                    createDocumentLauncher.launch(ConfigManager.exportFilename.value)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConfigManager.importRequest.collect { mode ->
                    pendingInAppImportMode = mode
                    AppStateManager.setFilePickerOpen(true)
                    // Use "*/*" instead of the custom MGRD MIME type: the Android file
                    // picker (DocumentsUI) does not know the custom MIME type and would
                    // show an empty list. With "*/*" all files are visible and the user
                    // can navigate to their .mgrd file.
                    openDocumentLauncher.launch(arrayOf("*/*"))
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LogReportManager.saveRequest.collect {
                    val timestamp =
                        LocalDateTime
                            .now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                    val filename = LogReportManager.buildReportFilename(timestamp)
                    AppStateManager.setFilePickerOpen(true)
                    createLogDocumentLauncher.launch(filename)
                }
            }
        }
        lifecycleScope.launch {
            var lastLaunchedId: String? = null
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateManager.activeCropCutoutId.collect { id ->
                    if (id != null && id != lastLaunchedId) {
                        lastLaunchedId = id
                        AppLog.i(TAG, "activeCropCutoutId=$id -> launching CropSelectorActivity on primary display")
                        val options = ActivityOptions.makeBasic()
                        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                        val intent =
                            Intent(this@MainActivity, CropSelectorActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            }
                        startActivity(intent, options.toBundle())
                    } else if (id == null) {
                        lastLaunchedId = null
                    }
                }
            }
        }
        // Keep the normal MacroPad use surface non-focusable on the secondary display so
        // touch input does not steal focus from a primary-display game that owns pointer
        // capture. Focus is restored for app overlays that need text/input focus.
        lifecycleScope.launch {
            combine(
                AppStateManager.isFullscreenKeyboardActive,
                AppStateManager.isOnValidScreen,
                AppStateManager.isQuickMenuOpen,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
                AppStateManager.isGlobalSettingsOpen,
                AppStateManager.isKeyboardSettingsOpen,
                AppStateManager.isTouchpadSettingsOpen,
            ) { values ->
                val fullscreenKeyboard = values[0] as Boolean
                val onValidScreen = values[1] as Boolean
                val quickMenuOpen = values[2] as Boolean
                val filePickerOpen = values[3] as Boolean
                val editorActive = values[4] as Boolean
                val ambientSettingsActive = values[5] as Boolean
                val globalSettingsOpen = values[6] as Boolean
                val keyboardSettingsOpen = values[7] as Boolean
                val touchpadSettingsOpen = values[8] as Boolean
                shouldKeepPrimaryGameFocus(
                    MacroPadFocusPolicyState(
                        isMacroPadSurfaceActive = onValidScreen,
                        isFullscreenKeyboardActive = fullscreenKeyboard,
                        isQuickMenuOpen = quickMenuOpen,
                        isFilePickerOpen = filePickerOpen,
                        isEditorActive = editorActive,
                        isBackgroundSettingsActive = ambientSettingsActive,
                        isGlobalSettingsOpen = globalSettingsOpen,
                        isKeyboardSettingsOpen = keyboardSettingsOpen,
                        isTouchpadSettingsOpen = touchpadSettingsOpen,
                    ),
                )
            }.distinctUntilChanged()
                .collect { keepPrimaryFocus -> setActivityFocusMode(keepPrimaryFocus) }
        }
        // Auto-connect Privileged Mode if the user previously bootstrapped the daemon.
        // The daemon survives app restarts (it's a separate shell-UID process); we just
        // re-open the abstract socket. Failure is silent: the user can re-bootstrap from
        // Settings if needed.
        lifecycleScope.launch {
            var triggered = false
            PrivdManager.state.collect { state ->
                when {
                    state == PrivdState.RUNNING -> {
                        triggered = false
                    }

                    (state == PrivdState.OFF || state == PrivdState.FAILED) && !triggered && !PrivdManager.isManuallyDisconnected -> {
                        triggered = true
                        AppLog.i(TAG, "Auto-connecting Privileged Mode")
                        withContext(Dispatchers.IO) { PrivdManager.connect(applicationContext) }
                    }
                }
            }
        }
        lifecycleScope.launch {
            SettingsManager.appLanguage.drop(1).collect { lang ->
                AppLog.d(TAG, "appLanguage changed to $lang → applying locales")
                val desired =
                    when (lang) {
                        AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                        AppLanguage.EN -> LocaleList(Locale.ENGLISH)
                        AppLanguage.DE -> LocaleList(Locale.GERMAN)
                    }
                val localeManager = getSystemService(LocaleManager::class.java)
                if (localeManager.applicationLocales != desired) {
                    localeManager.applicationLocales = desired
                }
            }
        }
        lifecycleScope.launch {
            SettingsManager.excludeFromRecents.collect { exclude ->
                AppLog.d(TAG, "excludeFromRecents changed to $exclude → updating task")
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.firstOrNull()?.setExcludeFromRecents(exclude)
            }
        }
        enableEdgeToEdge()
        setContent {
            val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                AppLog.i(TAG, "ON_RESUME isValid=${AppStateManager.isOnValidScreen.value}")
                                AppStateManager.setActivityResumed(true)
                                // Clear the user-leaving flag: the user has returned to the app.
                                AppStateManager.setUserLeaving(false)
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

            val isOnValidScreen by AppStateManager.isOnValidScreen.collectAsState()

            val activeLayout by MacroPadState.activeLayout.collectAsState()

            // Reconcile the running mirror session with the active layout's persisted
            // desired state. PadLayout.mirrorAutoStart is the single source of truth:
            // true means this layout should mirror (subject to the global auto-start
            // gate when not already running), false means this layout should not mirror.
            LaunchedEffect(isOnValidScreen) {
                var lastPolicyLayoutId: String? = null
                // True while privd mirror daemon is still connecting
                // (CONNECTING, BOOTSTRAPPING, or OFF-but-auto-connect-pending). Blocks
                // auto-start so the strategy decision waits for the daemon to settle.
                val privdMirrorConnectingFlow =
                    combine(
                        PrivdManager.state,
                        AppStateManager.isPrivdPromptActive,
                        AppStateManager.hasAdbCredentials,
                        MacroPadSettings.privdShowAdbPrompt,
                        AppStateManager.isPrivdPromptDismissed,
                    ) { privdState, promptActive, hasCreds, showPromptPref, dismissed ->
                        isPrivdMirrorConnecting(
                            privdState = privdState,
                            promptActive = promptActive,
                            hasCreds = hasCreds,
                            showPromptPref = showPromptPref,
                            dismissed = dismissed,
                            isManuallyDisconnected = PrivdManager.isManuallyDisconnected,
                        )
                    }
                combine(
                    AppStateManager.promptInFlight,
                    AppStateManager.mirrorAutoStartSuppressedLayoutId,
                    ScreenCaptureManager.isCapturing,
                    MacroPadState.activeLayout,
                    AppStateManager.isOnValidScreen,
                    OnboardingWizardManager.isWizardActive,
                    AppStateManager.isExternalClientActive,
                    AppStateManager.focusedAppPackageName,
                    MacroPadState.activeProfile,
                ) { values ->
                    val promptInFlight = values[0] as Boolean
                    val suppressedLayoutId = values[1] as? String
                    val capturing = values[2] as Boolean
                    val currentLayout = values[3] as? PadLayout
                    val onValidScreen = values[4] as Boolean
                    val wizardActive = values[5] as Boolean
                    val externalClientActive = values[6] as Boolean
                    val focusedPackage = values[7] as? String
                    val activeProfile = values[8] as? PadProfile

                    val showIntegrationHome =
                        externalClientActive &&
                            (focusedPackage == null || activeProfile?.associatedPackage != focusedPackage)

                    MirrorRuntimePolicyState(
                        promptInFlight = promptInFlight,
                        isOnValidScreen = onValidScreen,
                        isCapturing = capturing,
                        layoutId = currentLayout?.id,
                        layoutWantsMirror = currentLayout?.mirrorAutoStart == true,
                        autoStartSuppressed = currentLayout?.id == suppressedLayoutId,
                        tutorialsActive = wizardActive,
                        showIntegrationHome = showIntegrationHome,
                    )
                }.combine(privdMirrorConnectingFlow) { policy, connecting ->
                    policy.copy(privdMirrorConnecting = connecting)
                }.distinctUntilChanged()
                    .collect { policy ->
                        if (policy.layoutId != lastPolicyLayoutId) {
                            AppLog.i(
                                TAG,
                                "mirror policy: active layout changed ${lastPolicyLayoutId ?: "<none>"} -> ${policy.layoutId ?: "<none>"} wantsMirror=${policy.layoutWantsMirror} isCapturing=${policy.isCapturing}",
                            )
                            lastPolicyLayoutId = policy.layoutId
                        }
                        when (decideMirrorRuntimeAction(policy)) {
                            MirrorRuntimeAction.START -> {
                                // Re-read promptInFlight from the live StateFlow before
                                // acting. The combine() snapshot may have captured a stale
                                // promptInFlight=false if the manual-start handler set it to
                                // true in the same scheduler turn — causing a double launch.
                                if (AppStateManager.promptInFlight.value) {
                                    AppLog.d(
                                        TAG,
                                        "mirror policy: layout=${policy.layoutId} wants ON but prompt already in flight — skipping",
                                    )
                                } else {
                                    AppLog.i(TAG, "mirror policy: layout=${policy.layoutId} wants ON → start")
                                    startMirrorByPolicy()
                                }
                            }

                            MirrorRuntimeAction.STOP -> {
                                AppLog.i(TAG, "mirror policy: layout=${policy.layoutId} wants OFF → stop")
                                stopMirrorService()
                            }

                            MirrorRuntimeAction.NONE -> {
                                Unit
                            }
                        }
                    }
            }

            // ── Mirror button signals from MacroPad ───────────────────────────────
            // MirrorPlayStop button sets these flags in AppStateManager; MainActivity
            // handles them here because sending intents or launching Activities requires
            LaunchedEffect(Unit) {
                AppStateManager.mirrorStartRequested.collect { requested ->
                    if (!requested) return@collect
                    AppLog.i(TAG, "mirrorStartRequested → triggering capture flow")
                    AppStateManager.consumeMirrorStartRequest()
                    val alreadyPrompting = AppStateManager.promptInFlight.value
                    if (!alreadyPrompting) {
                        AppStateManager.setPromptInFlight(true)
                    }
                    val requestedLayoutId = activeLayout?.id
                    if (requestedLayoutId != null) {
                        val layoutId = requestedLayoutId
                        AppStateManager.clearMirrorAutoStartSuppression(layoutId)
                        MacroPadState.setLayoutMirrorAutoStart(layoutId, true)
                        MacroPadState.activeLayout
                            .map { layout -> layout?.id == layoutId && layout?.mirrorAutoStart == true }
                            .first { it }
                    }
                    // Manual start bypasses the global auto-start gate — launch directly.
                    if (isOnValidScreen &&
                        !ScreenCaptureManager.isCapturing.value &&
                        !alreadyPrompting
                    ) {
                        startMirrorByPolicy()
                    } else if (!alreadyPrompting) {
                        AppStateManager.setPromptInFlight(false)
                    }
                }
            }

            LaunchedEffect(Unit) {
                AppStateManager.mirrorStopRequested.collect { requested ->
                    if (!requested) return@collect
                    AppLog.i(TAG, "mirrorStopRequested → sending STOP to ScreenCaptureService")
                    AppStateManager.consumeMirrorStopRequest()
                    activeLayout?.id?.let { layoutId ->
                        AppStateManager.suppressMirrorAutoStart(layoutId)
                        MacroPadState.setLayoutMirrorAutoStart(layoutId, false)
                    }
                    if (isCapturing) stopMirrorService()
                }
            }

            LaunchedEffect(Unit) {
                AppStateManager.shutOffRequested.collect { requested ->
                    if (!requested) return@collect
                    AppLog.i(TAG, "shutOffRequested → performing graceful shutdown")
                    AppStateManager.consumeShutOffRequest()
                    if (ScreenCaptureManager.isCapturing.value) {
                        stopMirrorService()
                    }
                    PrivdClient.disconnect()
                    finishAndRemoveTask()
                }
            }

            LaunchedEffect(Unit) {
                ScreenCaptureManager.screenshotRequested.collect { requested ->
                    if (!requested) return@collect
                    if (!ScreenCaptureManager.isCapturing.value) {
                        if (PrivdClient.isConnected) {
                            AppLog.i(TAG, "screenshotRequested → handling via privileged mode (mirroring not running)")
                            launch(Dispatchers.IO) {
                                try {
                                    val filename = "Megingiard_Screenshot_${System.currentTimeMillis()}.png"
                                    val picturesDir =
                                        File(Environment.getExternalStorageDirectory(), ScreenCaptureManager.SCREENSHOT_SUBDIR)
                                    if (!picturesDir.exists()) {
                                        picturesDir.mkdirs()
                                    }
                                    val filepath = File(picturesDir, filename).absolutePath
                                    val ok = PrivdClient.takeScreenshot(filepath)
                                    if (ok) {
                                        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(filepath), null, null)
                                        val bitmap = BitmapFactory.decodeFile(filepath)
                                        if (bitmap != null) {
                                            ScreenCaptureManager.showScreenshotPreview(bitmap)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@MainActivity, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            AppLog.e(TAG, "Failed to decode screenshot file $filepath")
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@MainActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        AppLog.e(TAG, "Privileged screenshot failed via privd client")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    AppLog.e(TAG, "Exception during privileged screenshot", t)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    ScreenCaptureManager.consumeScreenshotRequest()
                                }
                            }
                        } else {
                            AppLog.w(TAG, "Screenshot requested but mirroring is not active and privd is not connected. Consuming request.")
                            ScreenCaptureManager.consumeScreenshotRequest()
                        }
                    }
                }
            }

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
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.appBackground,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainAppScreen()

                            ScreenshotPreviewOverlay(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }

    private fun setActivityFocusMode(keepPrimaryFocus: Boolean) {
        if (keepPrimaryFocus) {
            AppLog.d(TAG, "FLAG_NOT_FOCUSABLE added (macropad use surface)")
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            AppLog.d(TAG, "FLAG_NOT_FOCUSABLE cleared (interactive app overlay)")
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    /**
     * Decides whether to start the privileged mirror path (no consent dialog,
     * direct SurfaceControl output) or the standard MediaProjection path. The
     * privileged path requires the per-feature flag to be enabled and a RUNNING
     * privd connection.
     */
    private fun startMirrorByPolicy() {
        val privdRunning = PrivdManager.state.value == PrivdState.RUNNING
        val strategy = selectMirrorStrategy(privdRunning)
        when (strategy) {
            MirrorStrategy.PRIVILEGED -> {
                AppLog.i(TAG, "startMirrorByPolicy: privd path")
                AppStateManager.setPromptInFlight(true)
                val intent =
                    Intent(this, ScreenCaptureService::class.java).apply {
                        action = ACTION_START_PRIVD
                    }
                startForegroundService(intent)
            }

            MirrorStrategy.MEDIA_PROJECTION -> {
                AppLog.i(TAG, "startMirrorByPolicy: MediaProjection path")
                launchCaptureRequest()
            }
        }
    }

    /**
     * Launches [CaptureRequestActivity] on the primary display so the system MediaProjection
     * consent dialog appears on the correct screen. Used by both the auto-start path
     * and the manual "Start mirroring" button. Sets `promptInFlight` to suppress
     * concurrent launches.
     */
    private fun launchCaptureRequest() {
        AppStateManager.setPromptInFlight(true)
        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        val intent =
            Intent(this, CaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        startActivity(intent, options.toBundle())
    }

    private fun stopMirrorService() {
        val stopIntent =
            Intent(this, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        startService(stopIntent)
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
