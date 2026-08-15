package com.stormpanda.megingiard.mirror

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CaptureRequestActivity
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.AmbientPreviewManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.MirrorSettings
import com.stormpanda.megingiard.shouldShowIntegrationHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "ScreenCaptureService"
private const val DIRECT_MIRROR_MAX_RETRIES = 3
private const val DIRECT_MIRROR_RETRY_DELAY_MS = 200L

const val ACTION_START_PRIVD = "START_PRIVD"
const val ACTION_STOP = "STOP"

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var mirrorVirtualDisplay: VirtualDisplay? = null
    private var mirrorSurface: Surface? = null
    private var directPrivdActiveSurface: Surface? = null
    private var mirrorPresentation: MirrorPresentation? = null
    private var recordingPresentation: RecordingMirrorPresentation? = null
    private var directPrivdSession: DirectPrivdMirrorSession? = null
    private var recordingPrivdSession: DirectPrivdMirrorSession? = null
    private var isPrivilegedMode = false
    private var capturedSrcWidth: Int = 0
    private var capturedSrcHeight: Int = 0
    private var capturedSecondaryDisplay: Display? = null
    private var consentFallbackInFlight = false
    private var directPrivdStartGeneration = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        scope.launch {
            combine(
                ScreenCaptureManager.isCapturing,
                AppStateManager.isOnValidScreen,
                AppStateManager.isFilePickerOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isBackgroundSettingsActive,
                AmbientPreviewManager.isActive,
                TouchRecordingManager.recordingRequested,
                AppStateManager.isPrivdPromptActive,
                AppStateManager.isPrivdSetupWizardActive,
                AppStateManager.showIntegrationHome,
                AppStateManager.isFullscreenMouseActive,
                AppStateManager.isFullscreenKeyboardActive,
            ) { values ->
                val capturing = values[0] as Boolean
                val validScreen = values[1] as Boolean
                val filePickerOpen = values[2] as Boolean
                val editorActive = values[3] as Boolean
                val ambientActive = values[4] as Boolean
                val ambientPreviewActive = values[5] as Boolean
                val recordingRequested = values[6] as Boolean
                val isPrivdPromptActive = values[7] as Boolean
                val isPrivdSetupWizardActive = values[8] as Boolean
                val showIntegrationHome = values[9] as Boolean
                val isFullscreenMouseActive = values[10] as Boolean
                val isFullscreenKeyboardActive = values[11] as Boolean

                capturing && validScreen &&
                    !filePickerOpen && !editorActive &&
                    (!ambientActive || ambientPreviewActive) &&
                    !recordingRequested &&
                    !isPrivdPromptActive &&
                    !isPrivdSetupWizardActive &&
                    (!showIntegrationHome || isFullscreenMouseActive || isFullscreenKeyboardActive)
            }.distinctUntilChanged()
                .collect { shouldShow ->
                    AppLog.d(TAG, "shouldShowMirrorPresentation changed → $shouldShow")
                    if (shouldShow) {
                        mirrorPresentation?.show()
                    } else {
                        mirrorPresentation?.hide()
                    }
                }
        }

        scope.launch {
            var wasCapturing = false
            ScreenCaptureManager.isCapturing.collect { capturing ->
                if (capturing) {
                    wasCapturing = true
                } else if (wasCapturing && !consentFallbackInFlight) {
                    AppLog.i(TAG, "isCapturing transitioned to false → hiding mirror presentation and stopping service")
                    mirrorPresentation?.hide()
                    mirrorPresentation?.dismiss()
                    mirrorPresentation = null
                    stopSelf()
                }
            }
        }

        scope.launch {
            PrivdClient.state.collect { state ->
                if (state == PrivdConnectionState.CONNECTED && isPrivilegedMode) {
                    AppLog.i(TAG, "Privd reconnected while mirror active -> updating direct server surfaces")
                    directPrivdSession?.release()
                    directPrivdSession = null
                    updateDirectServerSurfaces()
                }
            }
        }

        scope.launch {
            TouchRecordingManager.recordingRequested.collect { requested ->
                if (requested) {
                    val sd =
                        capturedSecondaryDisplay ?: run {
                            AppLog.w(TAG, "recording requested but secondary display is null — ignoring")
                            return@collect
                        }
                    if (isPrivilegedMode) {
                        AppLog.i(TAG, "recording requested in privileged mode → starting session first")
                        scope.launch {
                            val session = DirectPrivdMirrorSession(capturedSrcWidth, capturedSrcHeight)
                            recordingPrivdSession = session
                            val started = session.start()
                            if (started) {
                                AppLog.i(TAG, "Recording direct privileged mirror session running → showing presentation")
                                recordingPresentation?.dismiss()

                                // Tear down direct privileged mirror session first to avoid AYN Thor display conflict
                                directPrivdSession?.release()
                                directPrivdSession = null
                                // Hide the main presentation window
                                mirrorPresentation?.hide()

                                val rp =
                                    RecordingMirrorPresentation(
                                        this@ScreenCaptureService,
                                        sd,
                                        capturedSrcWidth,
                                        capturedSrcHeight,
                                        mediaProjection,
                                    )
                                recordingPresentation = rp
                                rp.show()
                            } else {
                                AppLog.e(TAG, "Failed to start recording privileged mirror session")
                                session.release()
                                recordingPrivdSession = null
                            }
                        }
                    } else {
                        if (mediaProjection == null) {
                            AppLog.w(TAG, "recording requested in non-privileged mode but mediaProjection is null — ignoring")
                            return@collect
                        }
                        AppLog.i(TAG, "recording requested → creating RecordingMirrorPresentation")
                        recordingPresentation?.dismiss()

                        // Release master virtualDisplay to avoid AYN Thor display conflict
                        mirrorVirtualDisplay?.release()
                        mirrorVirtualDisplay = null
                        mirrorSurface = null
                        // Hide the main presentation window
                        mirrorPresentation?.hide()

                        val rp =
                            RecordingMirrorPresentation(
                                this@ScreenCaptureService,
                                sd,
                                capturedSrcWidth,
                                capturedSrcHeight,
                                mediaProjection,
                            )
                        recordingPresentation = rp
                        rp.show()
                    }
                } else {
                    recordingPresentation?.dismiss()
                    recordingPresentation = null

                    if (isPrivilegedMode) {
                        AppLog.i(TAG, "recording stopped in privileged mode → releasing recording session and restoring main mirror")
                        recordingPrivdSession?.release()
                        recordingPrivdSession = null

                        if (shouldShowMirrorPresentation()) {
                            mirrorPresentation?.show()
                        }
                        val surface = mirrorPresentation?.getSurface()
                        if (surface != null && surface.isValid) {
                            mirrorSurface = surface
                        }
                        if (mirrorSurface != null && directPrivdSession == null) {
                            AppLog.i(TAG, "surface is already valid in privileged mode → manually recreating directPrivdSession")
                            updateDirectServerSurfaces()
                        }
                    } else {
                        AppLog.i(TAG, "recording stopped in non-privileged mode → restoring main mirror")
                        if (shouldShowMirrorPresentation()) {
                            mirrorPresentation?.show()
                        }
                        val surface = mirrorPresentation?.getSurface()
                        if (surface != null && surface.isValid && mirrorVirtualDisplay == null) {
                            val dpi = resources.displayMetrics.densityDpi
                            try {
                                val vd =
                                    mediaProjection?.createVirtualDisplay(
                                        "ScreenCapture-Master",
                                        capturedSrcWidth,
                                        capturedSrcHeight,
                                        dpi,
                                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                        surface,
                                        null,
                                        null,
                                    )
                                mirrorVirtualDisplay = vd
                                mirrorSurface = surface
                                AppLog.i(
                                    TAG,
                                    "VirtualDisplay recreated manually for master ${capturedSrcWidth}x$capturedSrcHeight dpi=$dpi",
                                )
                            } catch (e: Exception) {
                                AppLog.e(TAG, "Exception recreating VirtualDisplay manually for master", e)
                            }
                        }
                    }
                }
            }
        }

        scope.launch {
            ScreenCaptureManager.screenshotRequested.collect { requested ->
                if (requested) {
                    val presentation = mirrorPresentation
                    if (presentation != null) {
                        val bitmap = presentation.captureScreenshot()
                        if (bitmap != null) {
                            try {
                                val previewBitmap = Bitmap.createBitmap(bitmap)
                                ScreenCaptureManager.showScreenshotPreview(previewBitmap)
                            } catch (t: Throwable) {
                                AppLog.e(TAG, "Failed to create preview bitmap", t)
                            }

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val savedUri = saveScreenshotToGallery(this@ScreenCaptureService, bitmap)
                                    if (savedUri != null) {
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(this@ScreenCaptureService, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(this@ScreenCaptureService, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    AppLog.e(TAG, "Failed to save screenshot", t)
                                    launch(Dispatchers.Main) {
                                        Toast.makeText(this@ScreenCaptureService, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    launch(Dispatchers.Main) {
                                        bitmap.recycle()
                                    }
                                }
                            }
                        } else {
                            AppLog.e(TAG, "Screenshot capture returned null bitmap")
                            Toast.makeText(this@ScreenCaptureService, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    ScreenCaptureManager.consumeScreenshotRequest()
                }
            }
        }

        scope.launch {
            AppStateManager.showIntegrationHome.collect { showIntegrationHome ->
                if (showIntegrationHome && ScreenCaptureManager.isCapturing.value &&
                    !AppStateManager.isFullscreenMouseActive.value &&
                    !AppStateManager.isFullscreenKeyboardActive.value &&
                    !AppStateManager.wasMirroringStartedByTouchpad.value
                ) {
                    AppLog.i(TAG, "Companion Hub screen became active -> stopping screen capture to conserve resources")
                    stopSelf()
                }
            }
        }

        scope.launch {
            AppStateManager.isFloatingBubbleActive.collect { bubbleActive ->
                if (bubbleActive && ScreenCaptureManager.isCapturing.value) {
                    AppLog.i(TAG, "Floating bubble overlay became active -> stopping screen capture to conserve resources")
                    stopSelf()
                }
            }
        }
    }

    private fun saveScreenshotToGallery(
        context: Context,
        bitmap: Bitmap,
    ): Uri? {
        val filename = "Megingiard_Screenshot_${System.currentTimeMillis()}.png"
        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, ScreenCaptureManager.SCREENSHOT_SUBDIR)
            }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw Exception("Failed to compress bitmap")
                }
            }
            AppLog.i(TAG, "Screenshot saved to $uri")
            uri
        } catch (t: Throwable) {
            AppLog.e(TAG, "Failed to write screenshot to MediaStore", t)
            resolver.delete(uri, null, null)
            null
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            AppLog.i(TAG, "onStartCommand STOP → stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START_PRIVD) {
            return startPrivdPath()
        }

        val resultCode =
            intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra("DATA", Intent::class.java)
        AppLog.i(TAG, "onStartCommand resultCode=$resultCode")

        startForegroundNotification()

        if (resultCode == Activity.RESULT_OK && data != null) {
            // Guard against double-starts (e.g. CaptureRequestActivity recreated by a
            // config change and delivering the result a second time).
            if (ScreenCaptureManager.isCapturing.value) {
                AppLog.w(TAG, "onStartCommand: already capturing — ignoring duplicate start")
                return START_NOT_STICKY
            }

            isPrivilegedMode = false
            ScreenCaptureManager.setPrivilegedMirror(false)
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val secondaryDisplay =
                displayManager
                    .getDisplays()
                    .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

            if (secondaryDisplay == null) {
                AppLog.e(TAG, "No secondary display found!")
                AppStateManager.setPromptInFlight(false)
                stopSelf()
                return START_NOT_STICKY
            }
            AppLog.i(TAG, "secondary display found: id=${secondaryDisplay.displayId}")

            val primaryDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val windowContext = createWindowContext(primaryDisplay, WindowManager.LayoutParams.TYPE_APPLICATION, null)
            val windowMetrics = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
            val bounds = windowMetrics.bounds
            val srcWidth = bounds.width()
            val srcHeight = bounds.height()
            val dpi = windowContext.resources.configuration.densityDpi

            capturedSrcWidth = srcWidth
            capturedSrcHeight = srcHeight
            capturedSecondaryDisplay = secondaryDisplay
            ScreenCaptureManager.setCaptureSourceSize(srcWidth, srcHeight)

            // NOTE: freeze is handled entirely by MirrorPresentation.bindStateFlows:
            // PixelCopy captures the last frame, then sv.visibility = INVISIBLE hides the
            // SurfaceView overlay so the ComposeView frozen-bitmap Image is visible.
            // Setting virtualDisplay.surface = null *before* PixelCopy completes would
            // make the SurfaceView go black before the bitmap is captured.

            val presentation = MirrorPresentation(this, secondaryDisplay, srcWidth, srcHeight)
            mirrorPresentation = presentation

            presentation.onSurfaceReady = { surface ->
                mirrorSurface = surface
                val isFrozen = ScreenCaptureManager.isFrozen.value
                val activeSurface = if (isFrozen) null else surface
                val existingVd = mirrorVirtualDisplay
                if (existingVd != null) {
                    existingVd.setSurface(activeSurface)
                    AppLog.d(TAG, "VirtualDisplay surface reattached for master after show()")
                } else {
                    try {
                        val vd =
                            mediaProjection?.createVirtualDisplay(
                                "ScreenCapture-Master",
                                srcWidth,
                                srcHeight,
                                dpi,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                activeSurface,
                                null,
                                null,
                            )
                        mirrorVirtualDisplay = vd
                        AppLog.i(TAG, "VirtualDisplay created for master ${srcWidth}x$srcHeight dpi=$dpi")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Exception creating VirtualDisplay for master", e)
                    }
                }
            }
            presentation.onSurfaceDestroyed = {
                mirrorSurface = null
                mirrorVirtualDisplay?.setSurface(null)
                AppLog.d(TAG, "VirtualDisplay surface detached after surface destroyed (hide())")
            }

            // Start viewport persistence coroutines in the service scope so they are
            // alive for the entire capture session regardless of which UI composable is
            // active (MirrorScreen on primary or MirrorPresentation on secondary display).
            MirrorViewportController.startPersistence(scope)

            // Restore viewport/lock/freeze FIRST so MirrorScreen's LaunchedEffect(isCapturing)
            // observes the correct values the moment isCapturing becomes true.
            // promptInFlight remains true throughout the restore, so there is no window where
            // isCapturing=false && promptInFlight=false could re-trigger the capture prompt.
            scope.launch {
                MirrorSettings.restoreMirrorSessionState()
                val layout = MacroPadState.activeLayout.value
                if (layout != null && layout.mirrorCutouts.isEmpty()) {
                    val secWindowContext = createWindowContext(secondaryDisplay, WindowManager.LayoutParams.TYPE_APPLICATION, null)
                    val secWindowMetrics = secWindowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
                    val secBounds = secWindowMetrics.bounds
                    val secWidth = secBounds.width().toFloat()
                    val secHeight = secBounds.height().toFloat()
                    val defaultCutout =
                        ScreenCutout.createDefault(
                            srcPixelWidth = srcWidth.toFloat(),
                            srcPixelHeight = srcHeight.toFloat(),
                            bottomPixelWidth = secWidth,
                            bottomPixelHeight = secHeight,
                        )
                    AppLog.i(TAG, "onStartCommand: cutout list is empty, creating default cutout size=${secWidth}x$secHeight")
                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(defaultCutout)))
                }
                MirrorViewportController.restoreFromLayout()
                AppLog.i(TAG, "session state restored → setCapturing(true)")
                ScreenCaptureManager.setCapturing(true)
                AppStateManager.setPromptInFlight(false)
                if (shouldShowMirrorPresentation()) {
                    presentation.show()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screen_capture_channel"
        val channel = NotificationChannel(channelId, "Screen Mirroring", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification =
            Notification
                .Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_mirroring_active))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun startForegroundNotificationConnectedDevice() {
        val channelId = "screen_capture_channel"
        val channel = NotificationChannel(channelId, "Screen Mirroring", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification =
            Notification
                .Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_mirroring_active))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    /**
     * Starts the privileged direct-Surface mirror path. If direct setup fails,
     * the service launches the normal MediaProjection consent flow.
     */
    private fun startPrivdPath(): Int {
        if (ScreenCaptureManager.isCapturing.value) {
            AppLog.w(TAG, "startPrivdPath: already capturing — ignoring duplicate start")
            return START_NOT_STICKY
        }
        isPrivilegedMode = true
        ScreenCaptureManager.setPrivilegedMirror(true)
        startForegroundNotificationConnectedDevice()

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondaryDisplay =
            displayManager
                .getDisplays()
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (secondaryDisplay == null) {
            AppLog.e(TAG, "startPrivdPath: no secondary display")
            AppStateManager.setPromptInFlight(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val primaryDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val windowContext = createWindowContext(primaryDisplay, WindowManager.LayoutParams.TYPE_APPLICATION, null)
        val windowMetrics = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val bounds = windowMetrics.bounds
        val srcWidth = bounds.width()
        val srcHeight = bounds.height()

        capturedSrcWidth = srcWidth
        capturedSrcHeight = srcHeight
        capturedSecondaryDisplay = secondaryDisplay
        ScreenCaptureManager.setCaptureSourceSize(srcWidth, srcHeight)

        val presentation = MirrorPresentation(this, secondaryDisplay, srcWidth, srcHeight)
        mirrorPresentation = presentation

        presentation.onSurfaceDestroyed = {
            AppLog.i(TAG, "presentation surface destroyed -> tearing down direct privileged session")
            mirrorSurface = null
            directPrivdActiveSurface = null
            DirectMirrorSurfaceBridge.clearDirectSurfaces()
            directPrivdSession?.release()
            directPrivdSession = null
        }
        presentation.onSurfaceReady = { surface ->
            mirrorSurface = surface
            updateDirectServerSurfaces()
        }

        MirrorViewportController.startPersistence(scope)
        scope.launch {
            MirrorSettings.restoreMirrorSessionState()
            val layout = MacroPadState.activeLayout.value
            if (layout != null && layout.mirrorCutouts.isEmpty()) {
                val secWindowContext = createWindowContext(secondaryDisplay, WindowManager.LayoutParams.TYPE_APPLICATION, null)
                val secWindowMetrics = secWindowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
                val secBounds = secWindowMetrics.bounds
                val secWidth = secBounds.width().toFloat()
                val secHeight = secBounds.height().toFloat()
                val defaultCutout =
                    ScreenCutout.createDefault(
                        srcPixelWidth = srcWidth.toFloat(),
                        srcPixelHeight = srcHeight.toFloat(),
                        bottomPixelWidth = secWidth,
                        bottomPixelHeight = secHeight,
                    )
                AppLog.i(TAG, "startPrivdPath: cutout list is empty, creating default cutout size=${secWidth}x$secHeight")
                MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(defaultCutout)))
            }
            MirrorViewportController.restoreFromLayout()
            AppLog.i(TAG, "privd session state restored → setCapturing(true)")
            ScreenCaptureManager.setCapturing(true)
            AppStateManager.setPromptInFlight(false)
            if (shouldShowMirrorPresentation()) {
                presentation.show()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateDirectServerSurfaces() {
        if (!isPrivilegedMode) return
        directPrivdStartGeneration += 1L
        val startGeneration = directPrivdStartGeneration
        scope.launch {
            if (startGeneration != directPrivdStartGeneration) return@launch
            val surface = mirrorSurface
            if (surface == null || !surface.isValid) {
                directPrivdActiveSurface = null
                DirectMirrorSurfaceBridge.clearDirectSurfaces()
                directPrivdSession?.release()
                directPrivdSession = null
                return@launch
            }

            if (directPrivdActiveSurface != surface && directPrivdSession != null) {
                AppLog.i(TAG, "target surface changed -> restarting direct privileged mirror session")
                directPrivdSession?.release()
                directPrivdSession = null
                directPrivdActiveSurface = null
            }

            var directSession = directPrivdSession
            if (directSession == null) {
                directSession = DirectPrivdMirrorSession(capturedSrcWidth, capturedSrcHeight)
                directPrivdSession = directSession
                val directStarted = directSession.start()
                if (startGeneration != directPrivdStartGeneration || directPrivdSession !== directSession) {
                    directSession.release()
                    return@launch
                }
                if (!directStarted) {
                    directSession.release()
                    directPrivdSession = null
                    directPrivdActiveSurface = null
                    launchConsentFallback("direct privileged mirror unavailable")
                    return@launch
                }
            }

            if (startGeneration == directPrivdStartGeneration) {
                var success = false
                for (attempt in 1..DIRECT_MIRROR_MAX_RETRIES) {
                    if (startGeneration != directPrivdStartGeneration) return@launch
                    if (DirectMirrorSurfaceBridge.sendToDirectServer(surface, capturedSrcWidth, capturedSrcHeight)) {
                        directPrivdActiveSurface = surface
                        AppLog.i(
                            TAG,
                            "direct privileged mirror session updated with master surface (attempt $attempt/$DIRECT_MIRROR_MAX_RETRIES)",
                        )
                        success = true
                        break
                    }
                    if (attempt < DIRECT_MIRROR_MAX_RETRIES) {
                        AppLog.w(
                            TAG,
                            "direct privileged mirror send attempt $attempt failed — retrying in ${DIRECT_MIRROR_RETRY_DELAY_MS}ms",
                        )
                        delay(DIRECT_MIRROR_RETRY_DELAY_MS)
                    }
                }

                if (!success && startGeneration == directPrivdStartGeneration) {
                    directSession.release()
                    directPrivdSession = null
                    launchConsentFallback("direct privileged mirror send failed after $DIRECT_MIRROR_MAX_RETRIES attempts")
                }
            }
        }
    }

    private fun launchConsentFallback(reason: String) {
        if (consentFallbackInFlight) return
        if (PrivdManager.state.value == PrivdState.RUNNING) {
            AppLog.w(
                TAG,
                "$reason — Privd is RUNNING, skipping MediaProjection fallback to prevent permission prompt popup while privileged mode is active",
            )
            return
        }
        AppLog.w(TAG, "$reason — falling back to MediaProjection consent")
        consentFallbackInFlight = true
        directPrivdStartGeneration += 1L
        directPrivdSession?.release()
        directPrivdSession = null
        recordingPresentation?.dismiss()
        recordingPresentation = null
        mirrorPresentation?.hide()
        mirrorPresentation?.dismiss()
        mirrorPresentation = null
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        AppStateManager.setPromptInFlight(true)

        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        val intent =
            Intent(this, CaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        startActivity(intent, options.toBundle())
        stopSelf()
    }

    private fun shouldShowMirrorPresentation(): Boolean {
        val capturing = ScreenCaptureManager.isCapturing.value
        val validScreen = AppStateManager.isOnValidScreen.value
        val filePickerOpen = AppStateManager.isFilePickerOpen.value
        val editorActive = AppStateManager.isEditorActive.value
        val ambientActive = AppStateManager.isBackgroundSettingsActive.value
        val ambientPreviewActive = AmbientPreviewManager.isActive.value
        val recordingRequested = TouchRecordingManager.recordingRequested.value
        val isPrivdPromptActive = AppStateManager.isPrivdPromptActive.value
        val isPrivdSetupWizardActive = AppStateManager.isPrivdSetupWizardActive.value
        val showIntegrationHome = AppStateManager.showIntegrationHome.value
        val isFloatingBubbleActive = AppStateManager.isFloatingBubbleActive.value
        val isFullscreenMouseActive = AppStateManager.isFullscreenMouseActive.value
        val isFullscreenKeyboardActive = AppStateManager.isFullscreenKeyboardActive.value

        val shouldShow =
            capturing && validScreen &&
                !filePickerOpen && !editorActive &&
                (!ambientActive || ambientPreviewActive) &&
                !recordingRequested &&
                !isPrivdPromptActive &&
                !isPrivdSetupWizardActive &&
                !isFloatingBubbleActive &&
                (!showIntegrationHome || isFullscreenMouseActive || isFullscreenKeyboardActive)

        AppLog.d(
            TAG,
            "shouldShowMirrorPresentation evaluated to $shouldShow (capturing=$capturing, validScreen=$validScreen, filePickerOpen=$filePickerOpen, editorActive=$editorActive, ambientActive=$ambientActive, ambientPreviewActive=$ambientPreviewActive, recordingRequested=$recordingRequested, isPrivdPromptActive=$isPrivdPromptActive, isPrivdSetupWizardActive=$isPrivdSetupWizardActive, showIntegrationHome=$showIntegrationHome, isFloatingBubbleActive=$isFloatingBubbleActive, isFullscreenMouseActive=$isFullscreenMouseActive, isFullscreenKeyboardActive=$isFullscreenKeyboardActive)",
        )
        return shouldShow
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        // Safety net: if the service is killed unexpectedly (system, crash) after
        // setCapturing(true) was called but before the user could press Stop, ensure
        // the UI state is cleaned up so the app doesn't get stuck.
        ScreenCaptureManager.setPrivilegedMirror(false)
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        if (!consentFallbackInFlight) AppStateManager.setPromptInFlight(false)
        mirrorVirtualDisplay?.release()
        mirrorVirtualDisplay = null
        mirrorSurface = null
        directPrivdActiveSurface = null
        mediaProjection?.stop()
        recordingPresentation?.dismiss()
        recordingPresentation = null
        mirrorPresentation?.hide()
        mirrorPresentation?.dismiss()
        mirrorPresentation = null
        if (isPrivilegedMode) {
            DirectMirrorSurfaceBridge.clearDirectSurfaces()
        }
        directPrivdSession?.release()
        directPrivdSession = null
        recordingPrivdSession?.release()
        recordingPrivdSession = null
    }
}
