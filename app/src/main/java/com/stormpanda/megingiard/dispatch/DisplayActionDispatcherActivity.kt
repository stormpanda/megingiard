package com.stormpanda.megingiard.dispatch

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.stormpanda.megingiard.AppLog

private const val TAG = "DisplayActionDispatcher"
private const val EXTRA_TARGET_DISPLAY_ID = "target_display_id"
private const val EXTRA_DISPATCH_TASK_ID = "dispatch_task_id"
private const val DISPATCH_ACQUISITION_DELAY_MS = 50L

/**
 * A transparent Activity that briefly launches on a targeted display to acquire focus
 * and execute a queued display-bound action task.
 */
class DisplayActionDispatcherActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0) // Suppress window entry transitions
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        val targetDisplayId = intent.getIntExtra(EXTRA_TARGET_DISPLAY_ID, -1)
        val taskId = intent.getStringExtra(EXTRA_DISPATCH_TASK_ID)
        AppLog.d(TAG, "DisplayActionDispatcherActivity onCreate: targetDisplayId=$targetDisplayId taskId=$taskId")
    }

    override fun onResume() {
        super.onResume()
        // Wait briefly for window focus acquisition before invoking the task action
        Handler(Looper.getMainLooper()).postDelayed({
            var onCompleteCallback: (() -> Unit)? = null
            try {
                val taskId = intent.getStringExtra(EXTRA_DISPATCH_TASK_ID)
                val (action, completion) = DisplayActionDispatcher.retrieveAndRemoveTask(taskId)
                onCompleteCallback = completion
                if (action != null) {
                    action.invoke()
                    AppLog.d(TAG, "Successfully executed action task on target display context")
                } else {
                    AppLog.w(TAG, "No pending dispatch task found for taskId=$taskId")
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error executing targeted display action task", e)
            } finally {
                try {
                    onCompleteCallback?.invoke()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error executing task completion callback", e)
                }
                finish()
            }
        }, DISPATCH_ACQUISITION_DELAY_MS)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0) // Suppress window exit transitions
    }

    companion object {
        /**
         * Launches this activity on the specified display ID to dispatch a pending action task.
         */
        fun launchOnDisplay(context: Context, displayId: Int, taskId: String): Boolean {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = dm.getDisplay(displayId)
            
            if (targetDisplay == null) {
                AppLog.e(TAG, "Cannot launch dispatcher activity: displayId=$displayId is not available")
                return false
            }

            val displayContext = context.createDisplayContext(targetDisplay)
            val intent = Intent(displayContext, DisplayActionDispatcherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_TARGET_DISPLAY_ID, displayId)
                putExtra(EXTRA_DISPATCH_TASK_ID, taskId)
            }

            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }
            
            return try {
                AppLog.i(TAG, "Launching DispatcherActivity on displayId=$displayId for taskId=$taskId")
                displayContext.startActivity(intent, options.toBundle())
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to start DispatcherActivity on displayId=$displayId", e)
                false
            }
        }
    }
}
