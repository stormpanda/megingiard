package com.stormpanda.megingiard

import android.app.ActivityOptions
import android.app.LocaleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    // The manifest declares configChanges that prevent activity recreation when the app
    // is moved between displays. Without this override, Compose never recomposes and
    // context.display retains the old display ID — the wrong-screen overlay would stay
    // visible even after moving to the correct display.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val isValid = displayId != Display.DEFAULT_DISPLAY
        AppStateManager.setOnValidScreen(isValid)
        // Mirror the ON_RESUME auto-start behaviour: if the app just moved onto the
        // valid screen, let the capture prompt fire (if the user enabled auto-start).
        if (isValid && SettingsManager.autoStartCapture.value) {
            AppStateManager.setUserDeclinedCapture(false)
        }
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
            val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
            val promptInFlight by AppStateManager.promptInFlight.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            AppStateManager.setActivityResumed(true)
                            // Auto-start: treat each app resume as a fresh opportunity.
                            // Only clear the decline flag here — not in the LaunchedEffect,
                            // so a decline within a session is respected until the next resume.
                            // Guard: only on the secondary (valid) screen — prevents a
                            // capture prompt from firing if the app is on the primary display.
                            if (SettingsManager.autoStartCapture.value
                                && AppStateManager.isOnValidScreen.value
                            ) {
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
            // Guard: skip when on the primary display — no capture should start there.
            LaunchedEffect(currentMode, macropadAmbientEnabled, isOnValidScreenLocal) {
                if (currentMode == AppMode.MACROPAD && macropadAmbientEnabled
                    && isOnValidScreenLocal && !isCapturing
                ) {
                    AppStateManager.setUserDeclinedCapture(false)
                }
            }

            // Launch capture proxy on the main screen when conditions are met.
            // With autoStartCapture=false (default) the user must tap "Start mirroring" manually.
            // With autoStartCapture=true the prompt fires once per app session (on resume);
            // declining within a session is respected — the dialog will not re-appear until the next resume.
            LaunchedEffect(isCapturing, promptInFlight, isOnValidScreenLocal, userDeclinedCapture) {
                if (!promptInFlight && isOnValidScreenLocal && !isCapturing && !userDeclinedCapture) {
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
                        MainAppScreen()
                    }
                }
            }
        }
    }
}
