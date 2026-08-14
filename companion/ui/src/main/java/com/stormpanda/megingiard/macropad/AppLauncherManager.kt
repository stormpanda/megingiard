package com.stormpanda.megingiard.macropad

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.Display
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.MainActivity

private const val TAG = "AppLauncherManager"

object AppLauncherManager {
    fun launchApp(
        context: Context,
        packageName: String,
        touchX: Float = -1f,
        touchY: Float = -1f,
    ) {
        if (packageName.isBlank()) {
            AppLog.w(TAG, "Cannot launch app: packageName is blank")
            return
        }

        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                AppLog.w(TAG, "No launch intent found for package: $packageName")
                return
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val displayId =
                if (context is Activity) {
                    context.display?.displayId ?: Display.DEFAULT_DISPLAY
                } else {
                    Display.DEFAULT_DISPLAY
                }

            val options =
                ActivityOptions.makeBasic().apply {
                    this.launchDisplayId = displayId
                }

            AppLog.i(TAG, "Launching package $packageName on display $displayId touch=($touchX, $touchY)")
            context.startActivity(launchIntent, options.toBundle())

            // Display floating bubble overlay above the target application at pressed location
            FloatingBubbleOverlay.show(
                packageName = packageName,
                touchX = touchX,
                touchY = touchY,
            )

            // Minimize Megingiard to the background
            if (context is Activity) {
                AppLog.d(TAG, "Moving Megingiard task to back")
                context.moveTaskToBack(true)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch app $packageName: ${e.message}")
        }
    }

    fun restoreMegingiard(context: Context) {
        try {
            AppLog.i(TAG, "Restoring Megingiard activity to foreground")
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            context.startActivity(intent)
            FloatingBubbleOverlay.hide()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to restore Megingiard: ${e.message}")
        }
    }

    fun dismissBubble() {
        AppLog.d(TAG, "Dismissing floating bubble")
        FloatingBubbleOverlay.hide()
    }
}
