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
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.stormpanda.megingiard.AppMode
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
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra("DATA", Intent::class.java)

        startForegroundNotification()

        if (resultCode == Activity.RESULT_OK && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val secondaryDisplay = displayManager.getDisplays()
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

            if (secondaryDisplay == null) {
                Log.e(TAG, "No secondary display found!")
                stopSelf()
                return START_NOT_STICKY
            }

            val primaryDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val windowContext = createWindowContext(primaryDisplay, WindowManager.LayoutParams.TYPE_APPLICATION, null)
            val windowMetrics = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
            val bounds = windowMetrics.bounds
            val srcWidth = bounds.width()
            val srcHeight = bounds.height()
            val dpi = windowContext.resources.configuration.densityDpi

            var currentSurface: Surface? = null
            scope.launch {
                ScreenCaptureManager.isFrozen.collect { frozen ->
                    virtualDisplay?.surface = if (frozen) null else currentSurface
                }
            }

            val presentation = MirrorPresentation(this, secondaryDisplay, srcWidth, srcHeight)
            mirrorPresentation = presentation

            presentation.onSurfaceDestroyed = {
                virtualDisplay?.release()
                virtualDisplay = null
            }

            presentation.onSurfaceReady = { surface ->
                currentSurface = surface
                virtualDisplay?.release()
                val mode = AppStateManager.currentMode.value
                val ambientEnabled = SettingsManager.macropadAmbientEnabled.value
                val shouldCreateVd = mode == AppMode.MIRROR ||
                    (mode == AppMode.MACROPAD && ambientEnabled)
                if (shouldCreateVd) {
                    try {
                        val isFrozen = ScreenCaptureManager.isFrozen.value
                        virtualDisplay = mediaProjection?.createVirtualDisplay(
                            "ScreenCapture",
                            srcWidth, srcHeight, dpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            if (isFrozen) null else surface, null, null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception creating VirtualDisplay", e)
                    }
                }
            }

            // Restore saved viewport/lock/freeze into ScreenCaptureManager BEFORE
            // presentation.show() so MirrorPresentation's StateFlow collectors receive
            // the correct values on their very first emission instead of the defaults.
            // setCapturing(true) is called afterwards so MirrorScreen's LaunchedEffect
            // reads already-restored values — no DataStore I/O needed in the UI layer.
            // setPromptInFlight(false) is called here (not in CaptureRequestActivity)
            // so there is no window where !isCapturing && !promptInFlight is true,
            // which would cause MainActivity to re-trigger the capture prompt.
            scope.launch {
                SettingsManager.restoreMirrorSessionState()
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
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        mirrorPresentation?.dismiss()
    }
}
