package com.stormpanda.megingiard.mirror

import android.app.Activity
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
    private var privdSession: PrivdMirrorSession? = null
    private var capturedSrcWidth: Int = 0
    private var capturedSrcHeight: Int = 0
    private var capturedSecondaryDisplay: Display? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

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
                virtualDisplay?.release()
                virtualDisplay = null
            }

            presentation.onSurfaceReady = { surface ->
                virtualDisplay?.release()
                val ambientEnabled = AmbientSettings.macropadAmbientEnabled.value
                if (ambientEnabled) {
                    try {
                        val isFrozen = ScreenCaptureManager.isFrozen.value
                        virtualDisplay = mediaProjection?.createVirtualDisplay(
                            "ScreenCapture",
                            srcWidth, srcHeight, dpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            if (isFrozen) null else surface, null, null
                        )
                        AppLog.i(TAG, "VirtualDisplay created ${srcWidth}x${srcHeight} dpi=$dpi")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Exception creating VirtualDisplay", e)
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
     * Starts the privileged mirror path: no MediaProjection consent, no
     * VirtualDisplay. The MirrorPresentation's SurfaceView is fed by a
     * MediaCodec H.264 decoder whose input is a NAL stream from the
     * privd-spawned mirror server child.
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
            DirectMirrorSurfaceRegistry.clear()
            directPrivdSession?.stop()
            privdSession?.stop()
        }
        presentation.onSurfaceReady = { surface ->
            // Tear down any existing session and start a fresh one bound to the new surface.
            DirectMirrorSurfaceRegistry.publish(surface)
            directPrivdSession?.release()
            directPrivdSession = null
            privdSession?.release()
            privdSession = null
            scope.launch {
                val directSession = DirectPrivdMirrorSession(surface, srcWidth, srcHeight)
                directPrivdSession = directSession
                if (directSession.start()) {
                    AppLog.i(TAG, "direct privileged mirror session started")
                    return@launch
                }
                directSession.release()
                directPrivdSession = null
                DirectMirrorSurfaceRegistry.clear(surface)

                AppLog.w(TAG, "direct privileged mirror unavailable — falling back to H.264 stream")
                val session = PrivdMirrorSession(surface, srcWidth, srcHeight)
                privdSession = session
                if (!session.start()) {
                    AppLog.e(TAG, "H.264 PrivdMirrorSession failed to start")
                    stopSelf()
                }
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

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        // Safety net: if the service is killed unexpectedly (system, crash) after
        // setCapturing(true) was called but before the user could press Stop, ensure
        // the UI state is cleaned up so the app doesn't get stuck.
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        AppStateManager.setPromptInFlight(false)
        virtualDisplay?.release()
        mediaProjection?.stop()
        recordingPresentation?.dismiss()
        mirrorPresentation?.dismiss()
        DirectMirrorSurfaceRegistry.clear()
        directPrivdSession?.release()
        directPrivdSession = null
        privdSession?.release()
        privdSession = null
    }
}
