package com.stormpanda.megingiard

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService

class CaptureRequestActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                ScreenCaptureManager.setCapturing(true)
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

