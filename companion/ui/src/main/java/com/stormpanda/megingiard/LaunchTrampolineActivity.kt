package com.stormpanda.megingiard

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display
import com.stormpanda.megingiard.catalog.DisplayDetector

private const val TAG = "LaunchTrampolineActivity"

/**
 * Invisible entry point Activity that routes launches of Megingiard to the secondary
 * (bottom) display on dual-screen devices such as the AYN Thor.
 */
class LaunchTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val secondaryDisplay = DisplayDetector.findSecondaryDisplay(this)
            val targetDisplayId = secondaryDisplay?.displayId ?: Display.DEFAULT_DISPLAY

            AppLog.i(
                TAG,
                "Routing launch to display $targetDisplayId (action=${intent?.action}, data=${intent?.data}, secondary=${secondaryDisplay?.displayId})",
            )

            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(targetDisplayId)
                }
            val targetIntent =
                Intent(this, MainActivity::class.java).apply {
                    action = intent?.action
                    data = intent?.data
                    type = intent?.type
                    intent?.clipData?.let { clipData = it }
                    intent?.extras?.let { putExtras(it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    intent?.flags?.let { originalFlags ->
                        if ((originalFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        if ((originalFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    }
                }
            startActivity(targetIntent, options.toBundle())
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to route launch intent to MainActivity", e)
        } finally {
            finish()
        }
    }
}
