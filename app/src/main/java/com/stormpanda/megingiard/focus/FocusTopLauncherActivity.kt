package com.stormpanda.megingiard.focus

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor

private const val TAG = "FocusTopLauncherActivity"
private const val INITIAL_LOOP_OFFSET = 10_000

class FocusTopLauncherActivity : ComponentActivity() {
    private val virtualIndexState = mutableIntStateOf(INITIAL_LOOP_OFFSET)
    private var lastStickLeft = false
    private var lastStickRight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide top system status bar for immersive fullscreen gamepad browsing
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())

        AppLog.i(TAG, "FocusTopLauncherActivity created on primary display (fullscreen)")

        InstalledAppsManager.loadInstalledApps(this)

        setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, Color(userAccentArgb))

            val apps by InstalledAppsManager.installedApps.collectAsState()

            MaterialTheme(
                colorScheme = colorSchemeFor(appColors, themeMode),
                typography = megingiardTypography,
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
                    LocalAppDimens provides AppDimens(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.appBackground,
                    ) {
                        FocusTopLauncherScreen(
                            apps = apps,
                            virtualIndex = virtualIndexState.intValue,
                            onVirtualIndexChange = { virtualIndexState.intValue = it },
                            onAppClick = { appInfo ->
                                AppLog.i(TAG, "Launching app from top launcher: ${appInfo.label}")
                                InstalledAppsManager.launchAppOnPrimaryDisplay(this, appInfo)
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "FocusTopLauncherActivity resumed, refreshing installed apps")
        InstalledAppsManager.loadInstalledApps(this)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        val apps = InstalledAppsManager.installedApps.value
        if (apps.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                -> {
                    virtualIndexState.intValue--
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                -> {
                    virtualIndexState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    val actualIndex = Math.floorMod(virtualIndexState.intValue, apps.size)
                    val targetApp = apps.getOrNull(actualIndex)
                    if (targetApp != null) {
                        AppLog.i(TAG, "Gamepad launch key pressed for: ${targetApp.label}")
                        InstalledAppsManager.launchAppOnPrimaryDisplay(this, targetApp)
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val axisHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val axisX = event.getAxisValue(MotionEvent.AXIS_X)
            val x = if (axisHatX != 0f) axisHatX else axisX

            if (x < -0.5f) {
                if (!lastStickLeft) {
                    lastStickLeft = true
                    virtualIndexState.intValue--
                }
                return true
            } else {
                lastStickLeft = false
            }

            if (x > 0.5f) {
                if (!lastStickRight) {
                    lastStickRight = true
                    virtualIndexState.intValue++
                }
                return true
            } else {
                lastStickRight = false
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
