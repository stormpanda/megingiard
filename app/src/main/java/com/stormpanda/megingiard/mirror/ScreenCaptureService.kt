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
import android.view.Surface
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "ScreenCaptureService"

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mirrorPresentation: MirrorPresentation? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            AppLog.i(TAG, "onStartCommand STOP → stopping self")
            stopSelf()
            return START_NOT_STICKY
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

            var currentSurface: Surface? = null
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
                currentSurface = surface
                virtualDisplay?.release()
                val ambientEnabled = SettingsManager.macropadAmbientEnabled.value
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

            // Restore viewport/lock/freeze FIRST so MirrorScreen's LaunchedEffect(isCapturing)
            // observes the correct values the moment isCapturing becomes true.
            // promptInFlight remains true throughout the restore, so there is no window where
            // isCapturing=false && promptInFlight=false could re-trigger the capture prompt.
            scope.launch {
                SettingsManager.restoreMirrorSessionState()
                AppLog.i(TAG, "session state restored → setCapturing(true)")
                ScreenCaptureManager.setCapturing(true)
                AppStateManager.setPromptInFlight(false)
                presentation.show()
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

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        // Safety net: if the service is killed unexpectedly (system, crash) after
        // setCapturing(true) was called but before the user could press Stop, ensure
        // the UI state is cleaned up so the app doesn't get stuck.
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        AppStateManager.setPromptInFlight(false)
        // Prevent the auto-start LaunchedEffect in MainActivity from re-triggering capture
        // as soon as isCapturing transitions to false. The user must explicitly press Start again.
        AppStateManager.setUserDeclinedCapture(true)
        virtualDisplay?.release()
        mediaProjection?.stop()
        mirrorPresentation?.dismiss()
    }
}
