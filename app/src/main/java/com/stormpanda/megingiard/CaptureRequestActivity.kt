package com.stormpanda.megingiard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService

class CaptureRequestActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // TEMP DEBUG
            Log.d("MG_CAPTURE", "[CaptureReq] result=${result.resultCode}  thread=${Thread.currentThread().name}  " +
                "isCapturing=${com.stormpanda.megingiard.mirror.ScreenCaptureManager.isCapturing.value}  " +
                "promptInFlight=${AppStateManager.promptInFlight.value}  " +
                "userDeclined=${AppStateManager.userDeclinedCapture.value}")
            if (result.resultCode == Activity.RESULT_OK) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", result.data)
                }
                Log.d("MG_CAPTURE", "[CaptureReq] OK -> startForegroundService  promptInFlight stays true")
                startForegroundService(serviceIntent)
                // setCapturing(true) and setPromptInFlight(false) are called by
                // ScreenCaptureService after restoreMirrorSessionState() completes,
                // so promptInFlight stays true during the gap — preventing MainActivity
                // from re-triggering the capture prompt before isCapturing becomes true.
                AppStateManager.setUserDeclinedCapture(false)
            } else {
                Log.d("MG_CAPTURE", "[CaptureReq] DECLINED/CANCELLED -> setUserDeclined=true; setPromptInFlight=false")
                AppStateManager.setUserDeclinedCapture(true)
                AppStateManager.setPromptInFlight(false)
            }
            Log.d("MG_CAPTURE", "[CaptureReq] calling finish()  " +
                "isCapturing=${com.stormpanda.megingiard.mirror.ScreenCaptureManager.isCapturing.value}  " +
                "promptInFlight=${AppStateManager.promptInFlight.value}  " +
                "userDeclined=${AppStateManager.userDeclinedCapture.value}")
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}

