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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "FocusTopLauncherActivity"
private const val INITIAL_LOOP_OFFSET = 10_000
private const val INITIAL_REPEAT_DELAY_MS = 300L
private const val REPEAT_INTERVAL_MS = 100L

private enum class ScrollDirection { NONE, LEFT, RIGHT }

class FocusTopLauncherActivity : ComponentActivity() {
    private val virtualIndexState = mutableIntStateOf(INITIAL_LOOP_OFFSET)
    private val editingAppInfoState = mutableStateOf<InstalledAppInfo?>(null)
    private var currentDirection = ScrollDirection.NONE
    private var repeatJob: Job? = null

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
            val editingApp = editingAppInfoState.value

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
                            editingAppInfo = editingApp,
                            onDismissEditingApp = { editingAppInfoState.value = null },
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

    override fun onPause() {
        super.onPause()
        stopRepeat()
    }

    private fun startRepeat(direction: ScrollDirection) {
        if (currentDirection == direction) return

        currentDirection = direction
        repeatJob?.cancel()

        if (direction == ScrollDirection.NONE) return

        if (direction == ScrollDirection.LEFT) {
            virtualIndexState.intValue--
        } else if (direction == ScrollDirection.RIGHT) {
            virtualIndexState.intValue++
        }

        repeatJob =
            lifecycleScope.launch {
                delay(INITIAL_REPEAT_DELAY_MS)
                while (isActive && currentDirection == direction) {
                    if (direction == ScrollDirection.LEFT) {
                        virtualIndexState.intValue--
                    } else if (direction == ScrollDirection.RIGHT) {
                        virtualIndexState.intValue++
                    }
                    delay(REPEAT_INTERVAL_MS)
                }
            }
    }

    private fun stopRepeat() {
        currentDirection = ScrollDirection.NONE
        repeatJob?.cancel()
        repeatJob = null
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // If an editing dialog is open, let the dialog handle inputs
        if (editingAppInfoState.value != null) {
            return super.onKeyDown(keyCode, event)
        }

        val apps = InstalledAppsManager.installedApps.value
        if (apps.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                -> {
                    startRepeat(ScrollDirection.LEFT)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                -> {
                    startRepeat(ScrollDirection.RIGHT)
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

                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_Y,
                -> {
                    val actualIndex = Math.floorMod(virtualIndexState.intValue, apps.size)
                    val targetApp = apps.getOrNull(actualIndex)
                    if (targetApp != null) {
                        AppLog.i(TAG, "Gamepad Y button pressed to edit artwork for: ${targetApp.label}")
                        editingAppInfoState.value = targetApp
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
            -> {
                if (currentDirection == ScrollDirection.LEFT) {
                    stopRepeat()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
            -> {
                if (currentDirection == ScrollDirection.RIGHT) {
                    stopRepeat()
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (editingAppInfoState.value != null) {
            return super.onGenericMotionEvent(event)
        }

        if (event != null && (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val axisHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val axisX = event.getAxisValue(MotionEvent.AXIS_X)
            val x = if (axisHatX != 0f) axisHatX else axisX

            if (x < -0.5f) {
                startRepeat(ScrollDirection.LEFT)
                return true
            } else if (x > 0.5f) {
                startRepeat(ScrollDirection.RIGHT)
                return true
            } else {
                if (currentDirection != ScrollDirection.NONE) {
                    stopRepeat()
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
