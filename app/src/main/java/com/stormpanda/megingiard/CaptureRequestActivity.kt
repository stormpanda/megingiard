package com.stormpanda.megingiard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService

@Suppressprivate const val TAG = "CaptureRequestActivity"

class CaptureRequestActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                AppLog.i(TAG, "RESULT_OK → starting ScreenCaptureService")
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
                AppLog.i(TAG, "RESULT_CANCELED → user declined capture, setUserDeclinedCapture(true)")
                AppStateManager.setUserDeclinedCapture(true)
                AppStateManager.setPromptInFlight(false)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // The activity was recreated by a configuration change (e.g. keyboard/display
            // config triggered by the system permission dialog appearing).  The permission
            // dialog is still showing; the ActivityResult API will deliver the result to
            // the re-registered launcher automatically.  Do NOT launch a second intent.
            AppLog.d(TAG, "onCreate recreated (savedInstanceState != null) — skipping launch")
            return
        }
        AppLog.d(TAG, "onCreate → launching screen capture intent")
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}

