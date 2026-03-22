package com.stormpanda.megingiard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCaptureService

class CaptureRequestActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            android.util.Log.d("MegingiardMirror", "CaptureRequestActivity returned with target code: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                android.util.Log.d("MegingiardMirror", "Starting ScreenCaptureService")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                ScreenCaptureManager.isCapturing.value = true
                com.stormpanda.megingiard.AppStateManager.userDeclinedCapture.value = false
            } else {
                com.stormpanda.megingiard.AppStateManager.userDeclinedCapture.value = true
            }
            com.stormpanda.megingiard.AppStateManager.promptInFlight.value = false
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
