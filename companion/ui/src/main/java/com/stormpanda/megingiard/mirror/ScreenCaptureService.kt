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
import android.view.Surface
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CaptureRequestActivity
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
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
    private var mediaProjection: MediaProjection? = null
    private var mirrorVirtualDisplay: VirtualDisplay? = null
    private var directPrivdActiveSurface: Surface? = null
    private var directPrivdSession: DirectPrivdMirrorSession? = null
    private var isPrivilegedMode = false
    private var capturedSrcWidth: Int = 0
    private var capturedSrcHeight: Int = 0
    private var capturedDpi: Int = 0
    private var consentFallbackInFlight = false
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
                } else if (wasCapturing && !consentFallbackInFlight) {
                    AppLog.i(TAG, "isCapturing transitioned to false → stopping service")
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
            combine(
                MasterSurfaceRegistry.masterSurface,
                ScreenCaptureManager.isFrozen,
                ScreenCaptureManager.isCapturing,
            ) { surface, isFrozen, isCapturing ->
                Triple(surface, isFrozen, isCapturing)
            }.distinctUntilChanged().collect { (surface, isFrozen, isCapturing) ->
                if (!isCapturing) return@collect

                if (isPrivilegedMode) {
                    if (surface != null && !isFrozen) {
                        updateDirectServerSurfaces()
                    } else {
                        directPrivdActiveSurface = null
                        DirectMirrorSurfaceBridge.clearDirectSurfaces()
                        directPrivdSession?.release()
                        directPrivdSession = null
                    }
                } else {
                    val activeSurface = if (isFrozen) null else surface
                    val existingVd = mirrorVirtualDisplay
                    if (existingVd != null) {
                        existingVd.setSurface(activeSurface)
                        AppLog.d(TAG, "VirtualDisplay surface updated: surface=${activeSurface != null}")
                    } else if (activeSurface != null && mediaProjection != null && capturedSrcWidth > 0 && capturedSrcHeight > 0) {
                        try {
                            val vd =
                                mediaProjection?.createVirtualDisplay(
                                    "ScreenCapture-Master",
                                    capturedSrcWidth,
                                    capturedSrcHeight,
                                    capturedDpi,
                                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                    activeSurface,
                                    null,
                                    null,
                                )
                            mirrorVirtualDisplay = vd
                            AppLog.i(TAG, "VirtualDisplay created for master ${capturedSrcWidth}x$capturedSrcHeight dpi=$capturedDpi")
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Exception creating VirtualDisplay for master", e)
                        }
                    }
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
            capturedDpi = dpi
            ScreenCaptureManager.setCaptureSourceSize(srcWidth, srcHeight)

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
                    AppLog.i(TAG, "onStartCommand: cutout list is empty, creating default cutout size=${secWidth}x$secHeight")
                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(defaultCutout)))
                }
                MirrorViewportController.restoreFromLayout()
                AppLog.i(TAG, "session state restored → setCapturing(true)")
                ScreenCaptureManager.setCapturing(true)
                AppStateManager.setPromptInFlight(false)
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
        ScreenCaptureManager.setCaptureSourceSize(srcWidth, srcHeight)

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
        }
        return START_NOT_STICKY
    }

    private fun updateDirectServerSurfaces() {
        if (!isPrivilegedMode) return
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

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: cleanup sequence")
        scope.cancel()
        ScreenCaptureManager.setPrivilegedMirror(false)
        if (ScreenCaptureManager.isCapturing.value) ScreenCaptureManager.setCapturing(false)
        if (!consentFallbackInFlight) AppStateManager.setPromptInFlight(false)
        mirrorVirtualDisplay?.release()
        mirrorVirtualDisplay = null
        directPrivdActiveSurface = null
        mediaProjection?.stop()
        if (isPrivilegedMode) {
            DirectMirrorSurfaceBridge.clearDirectSurfaces()
        }
        directPrivdSession?.release()
        directPrivdSession = null
    }
}
