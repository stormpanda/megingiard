package com.stormpanda.megingiard.services

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog

/**
 * A lightweight, transparent, and animation-free activity used to briefly shift focus
 * to a specific display. Once focus is acquired, it executes a callback and finishes.
 */
class DisplayFocusActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        AppLog.d(TAG, "onCreate: DisplayFocusActivity created on display ${display?.displayId}")
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "onResume: DisplayFocusActivity acquired focus on display ${display?.displayId}")
        try {
            onFocusAcquired?.invoke()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to execute focus acquired callback", e)
        } finally {
            onFocusAcquired = null
            finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "DisplayFocusActivity"
        private var onFocusAcquired: (() -> Unit)? = null

        /**
         * Launches DisplayFocusActivity on the specified display.
         * Once the activity is resumed (gains focus), the callback is executed.
         */
        fun launch(context: Context, displayId: Int, onFocus: () -> Unit) {
            onFocusAcquired = onFocus
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(displayId)
            if (display == null) {
                AppLog.e(TAG, "launch: Display id $displayId is not valid")
                onFocusAcquired = null
                return
            }

            val displayContext = context.createDisplayContext(display)
            val intent = Intent(displayContext, DisplayFocusActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }

            try {
                AppLog.d(TAG, "launch: Launching DisplayFocusActivity on display $displayId")
                displayContext.startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                AppLog.e(TAG, "launch: Failed to start focus activity", e)
                onFocusAcquired = null
            }
        }
    }
}
