package com.stormpanda.megingiard.mirror

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.Display
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CaptureRequestActivity
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.settings.AmbientSettings
import com.stormpanda.megingiard.settings.MirrorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "ScreenCaptureService"

const val ACTION_START_PRIVD = "START_PRIVD"
const val ACTION_STOP = "STOP"

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mirrorPresentation: MirrorPresentation? = null
    private var recordingPresentation: RecordingMirrorPresentation? = null
    private var directPrivdSession: DirectPrivdMirrorSession? = null
    private var capturedSrcWidth: Int = 0
    private var capturedSrcHeight: Int = 0
    private var capturedSecondaryDisplay: Display? = null
    private var consentFallbackInFlight = false
    private var directPrivdStartGeneration = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Hide the mirror / recording presentations when the user explicitly navigates
        // away (Home button, Recents). Show them again when the user returns.
        //
        // We use isUserLeaving (set in onUserLeaveHint / cleared on ON_RESUME) rather
        // than isActivityResumed to avoid a feedback loop: the Presentation window sits
        // above the Activity on the secondary display, which causes ON_PAUSE/ON_STOP to
        // fire immediately after show() — isActivityResumed would then toggle hide() and
        // trigger an indefinite cycle. onUserLeaveHint is NOT called for Presentation
        // coverage, only for genuine user navigation.
        scope.launch {
            AppStateManager.isUserLeaving.collect { leaving ->
                AppLog.d(TAG, "isUserLeaving=$leaving → ${if (leaving) "hide" else "show"} presentations")
                if (leaving) {
                    mirrorPresentation?.hide()
                    recordingPresentation?.hide()
                } else {
                    mirrorPresentation?.show()
                    recordingPresentation?.show()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppLog.i(TAG, "onStartCommand STOP → stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START_PRIVD) {
            return startPrivdPath()
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
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

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val secondaryDisplay = displayManager.getDisplays()
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

            presentation.onSurfaceDestroyed = {
                // Detach the surface but keep the VirtualDisplay alive. Releasing here
                // would remove a virtual display from the system, causing the launcher on
                // the secondary display to react (detect display change) and push
                // Megingiard off the secondary screen the next time show() creates a NEW
                // VirtualDisplay. By keeping it alive with surface=null we avoid any
                // system display lifecycle event. The VirtualDisplay is properly released
                // in onDestroy().
                virtualDisplay?.setSurface(null)
            }

            presentation.onSurfaceReady = { surface ->
                val isFrozen = ScreenCaptureManager.isFrozen.value
                val activeSurface = if (isFrozen) null else surface
                if (virtualDisplay != null) {
                    // Reattach surface after a hide/show cycle — VirtualDisplay was kept
                    // alive with surface=null while the Presentation window was hidden.
                    virtualDisplay?.setSurface(activeSurface)
                    AppLog.d(TAG, "VirtualDisplay surface reattached after show()")
                } else {
                    val ambientEnabled = AmbientSettings.macropadAmbientEnabled.value
                    if (ambientEnabled) {
                        try {
                            virtualDisplay = mediaProjection?.createVirtualDisplay(
                                "ScreenCapture",
                                srcWidth, srcHeight, dpi,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                activeSurface, null, null
                            )
                            AppLog.i(TAG, "VirtualDisplay created ${srcWidth}x${srcHeight} dpi=$dpi")
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Exception creating VirtualDisplay", e)
                        }
                    }
                }
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
                MirrorViewportController.restoreFromLayout()
                AppLog.i(TAG, "session state restored → setCapturing(true)")
                ScreenCaptureManager.setCapturing(true)
                AppStateManager.setPromptInFlight(false)
                presentation.show()
            }

            scope.launch {
                TouchRecordingManager.recordingRequested.collect { requested ->
                    if (requested) {
                        val mp = mediaProjection ?: run {
                            AppLog.w(TAG, "recording requested but mediaProjection is null — ignoring")
                            return@collect
                        }
                        val sd = capturedSecondaryDisplay ?: run {
                            AppLog.w(TAG, "recording requested but secondary display is null — ignoring")
                            return@collect
                        }
                        AppLog.i(TAG, "recording requested → creating RecordingMirrorPresentation")
                        recordingPresentation?.dismiss()
                        val rp = RecordingMirrorPresentation(
                            this@ScreenCaptureService,
                            sd,
                            capturedSrcWidth,
                            capturedSrcHeight,
                            mp,
                        )
                        recordingPresentation = rp
                        rp.show()
                    } else {
                        recordingPresentation?.dismiss()
                        recordingPresentation = null
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screen_capture_channel"
        val channel = NotificationChannel(channelId, "Screen Mirroring", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
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

        val notification = Notification.Builder(this, channelId)
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
        startForegroundNotificationConnectedDevice()

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondaryDisplay = displayManager.getDisplays()
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (secondaryDisplay == null) {
            AppLog.e(TAG, "startPrivdPath: no secondary display")
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
            directPrivdStartGeneration += 1L
            directPrivdSession?.stop()
        }
        presentation.onSurfaceReady = { surface ->
            // Tear down any existing session and start a fresh one bound to the new surface.
            directPrivdStartGeneration += 1L
            val startGeneration = directPrivdStartGeneration
            directPrivdSession?.release()
            directPrivdSession = null
            scope.launch {
                if (startGeneration != directPrivdStartGeneration) {
                    AppLog.d(TAG, "stale privileged mirror launch ignored")
                    return@launch
                }
                val directSession = DirectPrivdMirrorSession(
                    srcWidth,
                    srcHeight,
                )
                directPrivdSession = directSession
                val directStarted = directSession.start()
                if (startGeneration != directPrivdStartGeneration || directPrivdSession !== directSession) {
                    AppLog.d(TAG, "stale privileged mirror start ignored")
                    directSession.release()
                    return@launch
                }
                if (directStarted && DirectMirrorSurfaceBridge.sendToDirectServer(surface)) {
                    AppLog.i(TAG, "direct privileged mirror session started")
                    return@launch
                }
                if (startGeneration != directPrivdStartGeneration || directPrivdSession !== directSession) {
                    AppLog.d(TAG, "stale privileged mirror fallback ignored")
                    directSession.release()
                    return@launch
                }
                directSession.release()
                directPrivdSession = null
                launchConsentFallback("direct privileged mirror unavailable")
            }
        }

        MirrorViewportController.startPersistence(scope)
        scope.launch {
            MirrorSettings.restoreMirrorSessionState()
            MirrorViewportController.restoreFromLayout()
            AppLog.i(TAG, "privd session state restored → setCapturing(true)")
            ScreenCaptureManager.setCapturing(true)
            AppStateManager.setPromptInFlight(false)
            presentation.show()
        }
        return START_NOT_STICKY
    }

    private fun launchConsentFallback(reason: String) {
        if (consentFallbackInFlight) return
        AppLog.w(TAG, "$reason — falling back to MediaProjection consent")
        consentFallbackInFlight = true
        directPrivdStartGeneration += 1L
        directPrivdSession?.release()
        directPrivdSession = null
        recordingPresentation?.dismiss()
        recordingPresentation = null
        mirrorPresentation?.dismiss()
        mirrorPresentation = null
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        AppStateManager.setPromptInFlight(true)

        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        val intent = Intent(this, CaptureRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent, options.toBundle())
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        // Safety net: if the service is killed unexpectedly (system, crash) after
        // setCapturing(true) was called but before the user could press Stop, ensure
        // the UI state is cleaned up so the app doesn't get stuck.
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        if (!consentFallbackInFlight) AppStateManager.setPromptInFlight(false)
        virtualDisplay?.release()
        mediaProjection?.stop()
        recordingPresentation?.dismiss()
        mirrorPresentation?.dismiss()
        directPrivdSession?.release()
        directPrivdSession = null
    }
}
