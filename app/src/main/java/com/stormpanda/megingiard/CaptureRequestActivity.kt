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
                // ScreenCaptureService restores viewport state first, then calls
                // setCapturing(true) and setPromptInFlight(false), so promptInFlight
                // stays true throughout the restore — preventing MainActivity from
                // re-triggering the capture prompt before isCapturing becomes true.
                AppStateManager.setUserDeclinedCapture(false)
            } else {
                AppStateManager.setUserDeclinedCapture(true)
                AppStateManager.setPromptInFlight(false)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}

