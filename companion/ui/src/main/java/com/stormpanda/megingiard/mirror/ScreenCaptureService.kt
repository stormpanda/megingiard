package com.stormpanda.megingiard.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.settings.MirrorSettings
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
    private var directPrivdActiveSurface: Surface? = null
    private var directPrivdSession: DirectPrivdMirrorSession? = null
    private var capturedSrcWidth: Int = 0
    private var capturedSrcHeight: Int = 0
    private var directPrivdStartGeneration = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        scope.launch {
            var wasCapturing = false
            ScreenCaptureManager.isCapturing.collect { capturing ->
                if (capturing) {
                    wasCapturing = true
                } else if (wasCapturing) {
                    AppLog.i(TAG, "isCapturing transitioned to false → stopping service")
                    stopSelf()
                }
            }
        }

        scope.launch {
            PrivdClient.state.collect { state ->
                if (state == PrivdConnectionState.CONNECTED) {
                    AppLog.i(TAG, "Privd reconnected while mirror active -> updating direct server surfaces")
                    directPrivdSession?.release()
                    directPrivdSession = null
                    updateDirectServerSurfaces()
                }
            }
        }

        scope.launch {
            combine(
                MasterSurfaceRegistry.masterSurface,
                ScreenCaptureManager.isFrozen,
                ScreenCaptureManager.isCapturing,
            ) { surface, isFrozen, isCapturing ->
                Triple(surface, isFrozen, isCapturing)
            }.distinctUntilChanged().collect { (surface, isFrozen, isCapturing) ->
                if (!isCapturing) return@collect

                if (surface != null && !isFrozen) {
                    updateDirectServerSurfaces()
                } else {
                    directPrivdActiveSurface = null
                    DirectMirrorSurfaceBridge.clearDirectSurfaces()
                    directPrivdSession?.release()
                    directPrivdSession = null
                }
            }
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

        return startPrivdPath()
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

    private fun startPrivdPath(): Int {
        if (ScreenCaptureManager.isCapturing.value) {
            AppLog.w(TAG, "startPrivdPath: already capturing — ignoring duplicate start")
            return START_NOT_STICKY
        }
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
        ScreenCaptureManager.setCaptureSourceSize(srcWidth, srcHeight)

        MirrorViewportController.startPersistence(scope)
        scope.launch {
            MirrorSettings.restoreMirrorSessionState()
            MirrorViewportController.restoreFromLayout()
            AppLog.i(TAG, "privd session state restored → setCapturing(true)")
            ScreenCaptureManager.setCapturing(true)
            AppStateManager.setPromptInFlight(false)
        }
        return START_NOT_STICKY
    }

    private fun updateDirectServerSurfaces() {
        directPrivdStartGeneration += 1L
        val startGeneration = directPrivdStartGeneration
        scope.launch {
            if (startGeneration != directPrivdStartGeneration) return@launch
            val surface = MasterSurfaceRegistry.masterSurface.value
            if (surface == null || !surface.isValid) {
                directPrivdActiveSurface = null
                DirectMirrorSurfaceBridge.clearDirectSurfaces()
                directPrivdSession?.release()
                directPrivdSession = null
                return@launch
            }

            if (directPrivdActiveSurface == surface) {
                return@launch
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
                    AppLog.w(TAG, "updateDirectServerSurfaces: direct privileged mirror session start failed")
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
                    AppLog.w(TAG, "direct privileged mirror send failed — attempting session recreation")
                    directSession.release()
                    directPrivdSession = null
                    directPrivdActiveSurface = null

                    val freshSession = DirectPrivdMirrorSession(capturedSrcWidth, capturedSrcHeight)
                    directPrivdSession = freshSession
                    val restarted = freshSession.start()
                    if (restarted && startGeneration == directPrivdStartGeneration) {
                        if (DirectMirrorSurfaceBridge.sendToDirectServer(surface, capturedSrcWidth, capturedSrcHeight)) {
                            directPrivdActiveSurface = surface
                            AppLog.i(TAG, "direct privileged mirror recovered after session recreation")
                            return@launch
                        }
                    }
                    freshSession.release()
                    directPrivdSession = null
                    directPrivdActiveSurface = null
                    AppLog.e(TAG, "direct privileged mirror send failed after retry and recovery")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        AppStateManager.setPromptInFlight(false)
        directPrivdActiveSurface = null
        DirectMirrorSurfaceBridge.clearDirectSurfaces()
        directPrivdSession?.release()
        directPrivdSession = null
    }
}
