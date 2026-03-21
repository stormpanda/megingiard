package com.stormpanda.megingiard

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
            var isAccessibilityEnabled by remember { mutableStateOf(com.stormpanda.megingiard.mirror.MegingiardAccessibilityService.isServiceConnected) }
            
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasNotificationAccess = isNotificationListenerEnabled(this@MainActivity)
                        isAccessibilityEnabled = com.stormpanda.megingiard.mirror.MegingiardAccessibilityService.isServiceConnected
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }

            LaunchedEffect(Unit) {
                while(true) {
                    delay(500)
                    if (isAccessibilityEnabled != com.stormpanda.megingiard.mirror.MegingiardAccessibilityService.isServiceConnected) {
                        isAccessibilityEnabled = com.stormpanda.megingiard.mirror.MegingiardAccessibilityService.isServiceConnected
                    }
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
                                Text("Notification Access is required for Media Control", color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    } else if (!isAccessibilityEnabled) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Accessibility Service is required for Screen Mirroring", color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }) {
                                    Text("Grant Permission")
                                }
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
