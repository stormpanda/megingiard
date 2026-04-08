package com.stormpanda.megingiard

import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.paletteFor
import java.util.Locale
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
        // FLAG_NOT_FOCUSABLE must only be active in KEYBOARD mode so that uinput key events
        // are delivered to the focused app (the game) rather than to Megingiard.
        // Keeping it active in other modes would break in-app text fields (e.g. MacroPad
        // profile name inputs).
        lifecycleScope.launch {
            AppStateManager.currentMode.collect { mode ->
                if (mode == AppMode.KEYBOARD) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                }
            }
        }
        SettingsManager.init(this)
        lifecycleScope.launch {
            SettingsManager.appLanguage.drop(1).collect { lang ->
                val desired = when (lang) {
                    AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                    AppLanguage.EN     -> LocaleList(Locale.ENGLISH)
                    AppLanguage.DE     -> LocaleList(Locale.GERMAN)
                }
                val localeManager = getSystemService(LocaleManager::class.java)
                if (localeManager.applicationLocales != desired) {
                    localeManager.applicationLocales = desired
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            var hasNotificationAccess by remember { mutableStateOf(isNotificationListenerEnabled(this@MainActivity)) }
            val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
            val promptInFlight by AppStateManager.promptInFlight.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    // TEMP DEBUG
                    Log.d("MG_CAPTURE", "[MA] lifecycle event=$event  isCapturing=${ScreenCaptureManager.isCapturing.value}  " +
                        "promptInFlight=${AppStateManager.promptInFlight.value}  " +
                        "userDeclined=${AppStateManager.userDeclinedCapture.value}  " +
                        "thread=${Thread.currentThread().name}")
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            hasNotificationAccess = isNotificationListenerEnabled(this@MainActivity)
                            AppStateManager.setActivityResumed(true)
                            // Auto-start: treat each app resume as a fresh opportunity.
                            // Only clear the decline flag here — not in the LaunchedEffect,
                            // so a decline within a session is respected until the next resume.
                            if (SettingsManager.autoStartCapture.value) {
                                Log.d("MG_CAPTURE", "[MA] ON_RESUME: autoStartCapture=true -> setUserDeclinedCapture(false)")
                                AppStateManager.setUserDeclinedCapture(false)
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            AppStateManager.setActivityResumed(false)
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }

            // Synchronous display evaluation gets correct value on frame 0
            val context = LocalContext.current
            val currentDisplayId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
            val isOnValidScreenLocal = currentDisplayId != Display.DEFAULT_DISPLAY

            // Update global state for other components
            LaunchedEffect(isOnValidScreenLocal) {
                AppStateManager.setOnValidScreen(isOnValidScreenLocal)
            }

            val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()

            val currentMode by AppStateManager.currentMode.collectAsState()
            val macropadAmbientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()

            // Auto-start capture when entering MacroPad mode with Ambient Display enabled,
            // mirroring the autoStartCapture behaviour for Mirror mode.
            // Declining within a session is respected until the next mode entry.
            LaunchedEffect(currentMode, macropadAmbientEnabled) {
                if (currentMode == AppMode.MACROPAD && macropadAmbientEnabled && !isCapturing) {
                    AppStateManager.setUserDeclinedCapture(false)
                }
            }

            // Once notification access is granted and we're not yet capturing, launch proxy on main screen.
            // With autoStartCapture=false (default) the user must tap "Start mirroring" manually.
            // With autoStartCapture=true the prompt fires once per app session (on resume);
            // declining within a session is respected — the dialog will not re-appear until the next resume.
            LaunchedEffect(hasNotificationAccess, isCapturing, promptInFlight, isOnValidScreenLocal, userDeclinedCapture) {
                // TEMP DEBUG
                val shouldTrigger = !isCapturing && !userDeclinedCapture
                Log.d("MG_CAPTURE", "[MA] captureLaunchedEffect fired: " +
                    "notif=$hasNotificationAccess isCapturing=$isCapturing promptInFlight=$promptInFlight " +
                    "validScreen=$isOnValidScreenLocal declined=$userDeclinedCapture " +
                    "shouldTrigger=$shouldTrigger  WILL_LAUNCH=${hasNotificationAccess && !promptInFlight && isOnValidScreenLocal && shouldTrigger}  " +
                    "thread=${Thread.currentThread().name}")
                if (hasNotificationAccess && !promptInFlight && isOnValidScreenLocal && shouldTrigger) {
                    AppStateManager.setPromptInFlight(true)
                    val options = ActivityOptions.makeBasic()
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    val intent = Intent(this@MainActivity, CaptureRequestActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                    startActivity(intent, options.toBundle())
                }
            }

            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccent by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, userAccent)

            MaterialTheme(colorScheme = colorSchemeFor(themeMode)) {
                CompositionLocalProvider(LocalAppColors provides appColors) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.appBackground
                    ) {
                        if (!hasNotificationAccess) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.notification_listener_required), color = appColors.onSurface)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = {
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }) { Text(stringResource(R.string.grant_permission)) }
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
}
