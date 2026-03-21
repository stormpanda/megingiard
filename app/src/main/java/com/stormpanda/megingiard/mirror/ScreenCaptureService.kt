package com.stormpanda.megingiard.mirror

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
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mirrorPresentation: MirrorPresentation? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MegingiardMirror", "ScreenCaptureService onStartCommand triggered")
        if (intent?.action == "STOP") {
            Log.d("MegingiardMirror", "ScreenCaptureService STOP intent received")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", android.app.Activity.RESULT_CANCELED) ?: android.app.Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            intent?.getParcelableExtra("DATA")
        }

        startForegroundService()

        Log.d("MegingiardMirror", "ScreenCaptureService checking resultCode=$resultCode, data=$data")
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            Log.d("MegingiardMirror", "MediaProjection acquired: $mediaProjection")

            // Find the secondary (bottom) display — display ID != DEFAULT_DISPLAY
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.getDisplays()
            val secondaryDisplay = displays.firstOrNull { it.displayId != android.view.Display.DEFAULT_DISPLAY }

            if (secondaryDisplay == null) {
                Log.e("ScreenCaptureService", "No secondary display found!")
                stopSelf()
                return START_NOT_STICKY
            }

            // Pick the primary display for capture dimensions, accounting for real-time rotation
            val primaryDisplay = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val metrics = android.util.DisplayMetrics()
            primaryDisplay.getRealMetrics(metrics)
            val srcWidth = metrics.widthPixels
            val srcHeight = metrics.heightPixels
            val dpi = metrics.densityDpi
            Log.d("MegingiardMirror", "Primary display mapped: ${srcWidth}x${srcHeight} at ${dpi}dpi. Secondary display: ${secondaryDisplay.displayId}")

            var currentSurface: android.view.Surface? = null
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
            scope.launch {
                com.stormpanda.megingiard.mirror.ScreenCaptureManager.isFrozen.collect { frozen ->
                    if (frozen) {
                        virtualDisplay?.surface = null
                    } else {
                        virtualDisplay?.surface = currentSurface
                    }
                }
            }

            // Create a Presentation on the secondary display, strongly typed to main display bounds
            val presentation = MirrorPresentation(this, secondaryDisplay, srcWidth, srcHeight)
            mirrorPresentation = presentation

            presentation.onSurfaceDestroyed = {
                Log.d("MegingiardMirror", "Presentation Surface destroyed, releasing VirtualDisplay")
                virtualDisplay?.release()
                virtualDisplay = null
            }

            // Wait for the Presentation's SurfaceView to be ready, then hook up VirtualDisplay
            presentation.onSurfaceReady = { surface ->
                Log.d("MegingiardMirror", "MirrorPresentation onSurfaceReady callback fired! Surface=$surface")
                currentSurface = surface
                virtualDisplay?.release()
                if (com.stormpanda.megingiard.AppStateManager.currentMode.value == com.stormpanda.megingiard.AppMode.MIRROR) {
                    try {
                        virtualDisplay = mediaProjection?.createVirtualDisplay(
                            "ScreenCapture",
                            srcWidth, srcHeight, dpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            surface, null, null
                        )
                        Log.d("MegingiardMirror", "VirtualDisplay created successfully: $virtualDisplay")
                    } catch (e: Exception) {
                        Log.e("MegingiardMirror", "Exception creating VirtualDisplay: ", e)
                    }
                }
            }

            Log.d("MegingiardMirror", "Executing presentation.show()")
            presentation.show()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "screen_capture_channel"
        val channelName = "Screen Mirroring"
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Megingiard")
            .setContentText("Mirroring active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection?.stop()
        mirrorPresentation?.dismiss()
    }
}
