package com.stormpanda.megingiard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stormpanda.megingiard.mirror.ScreenCaptureManager

class MainActivity : ComponentActivity() {

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                if (componentName != null && componentName.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var hasNotificationAccess by remember { mutableStateOf(isNotificationListenerEnabled(this@MainActivity)) }
            var promptInFlight by remember { mutableStateOf(false) }
            val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasNotificationAccess = isNotificationListenerEnabled(this@MainActivity)
                        // If user cancelled the dialog, allow retrying
                        if (!isCapturing) promptInFlight = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }

            // Once notification access is granted and we're not yet capturing, launch proxy on main screen
            LaunchedEffect(hasNotificationAccess, isCapturing, promptInFlight) {
                if (hasNotificationAccess && !isCapturing && !promptInFlight) {
                    promptInFlight = true
                    val options = android.app.ActivityOptions.makeBasic()
                    options.setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY)
                    val intent = Intent(this@MainActivity, CaptureRequestActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                    startActivity(intent, options.toBundle())
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (!hasNotificationAccess) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Notification Access required for Media Control", color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }) { Text("Grant Permission") }
                            }
                        }
                    } else {
                        MainAppScreen()
                    }
                }
            }
        }
    }
}
