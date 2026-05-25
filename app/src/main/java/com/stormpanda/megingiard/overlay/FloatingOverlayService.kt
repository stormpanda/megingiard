package com.stormpanda.megingiard.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.MainActivity
import com.stormpanda.megingiard.R

private const val TAG = "FloatingOverlayService"
private const val NOTIFICATION_ID = 99
private const val CHANNEL_ID = "floating_overlay_channel"

const val ACTION_START_OVERLAY = "START_OVERLAY"
const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"

class FloatingOverlayService : Service() {
    private var controller: FloatingOverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.i(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_OVERLAY
        AppLog.i(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Permission validation
        if (!Settings.canDrawOverlays(this)) {
            AppLog.e(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot start overlay.")
            AppStateManager.setFloatingOverlayActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Start Foreground Notification
        startForegroundNotification()

        // 3. Initialize Controller
        if (controller == null) {
            controller = FloatingOverlayController(this)
        }
        controller?.start()
        AppStateManager.setFloatingOverlayActive(true)

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Overlay Pad",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Floating MacroPad overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy")
        controller?.stop()
        controller = null
        AppStateManager.setFloatingOverlayActive(false)

        // Bring MainActivity back to front!
        val restoreIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(restoreIntent)
    }
}
