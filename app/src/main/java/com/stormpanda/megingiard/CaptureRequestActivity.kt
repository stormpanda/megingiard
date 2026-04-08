package com.stormpanda.megingiard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService

class CaptureRequestActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", result.data)
                }
                startForegroundService(serviceIntent)
                // setCapturing(true) is called by ScreenCaptureService after
                // restoreMirrorSessionState() completes, so the viewport/lock/freeze
                // values are in ScreenCaptureManager before any UI reacts to isCapturing.
                AppStateManager.setUserDeclinedCapture(false)
            } else {
                AppStateManager.setUserDeclinedCapture(true)
            }
            AppStateManager.setPromptInFlight(false)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}

