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
import androidx.compose.runtime.MutableIntState
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
    private val dialogVirtualIndexState = mutableIntStateOf(INITIAL_LOOP_OFFSET)
    private val confirmDialogTriggerState = mutableIntStateOf(0)
    private val l1TriggerState = mutableIntStateOf(0)
    private val r1TriggerState = mutableIntStateOf(0)

    private val isOptionsMenuExpandedState = mutableStateOf(false)
    private val dpadUpOptionsTriggerState = mutableIntStateOf(0)
    private val dpadRightOptionsTriggerState = mutableIntStateOf(0)

    private val editingAppInfoState = mutableStateOf<InstalledAppInfo?>(null)

    private var currentDirection = ScrollDirection.NONE
    private var repeatJob: Job? = null

    private fun getActiveVirtualIndexState(): MutableIntState =
        if (editingAppInfoState.value != null) {
            dialogVirtualIndexState
        } else {
            virtualIndexState
        }

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
                            dialogVirtualIndex = dialogVirtualIndexState.intValue,
                            onDialogVirtualIndexChange = { dialogVirtualIndexState.intValue = it },
                            confirmDialogTrigger = confirmDialogTriggerState.intValue,
                            l1Trigger = l1TriggerState.intValue,
                            r1Trigger = r1TriggerState.intValue,
                            isOptionsMenuExpanded = isOptionsMenuExpandedState.value,
                            onOptionsMenuExpandedChange = { isOptionsMenuExpandedState.value = it },
                            dpadUpTrigger = dpadUpOptionsTriggerState.intValue,
                            dpadRightTrigger = dpadRightOptionsTriggerState.intValue,
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

        val activeIndexState = getActiveVirtualIndexState()
        if (direction == ScrollDirection.LEFT) {
            activeIndexState.intValue--
        } else if (direction == ScrollDirection.RIGHT) {
            activeIndexState.intValue++
        }

        repeatJob =
            lifecycleScope.launch {
                delay(INITIAL_REPEAT_DELAY_MS)
                while (isActive && currentDirection == direction) {
                    if (direction == ScrollDirection.LEFT) {
                        activeIndexState.intValue--
                    } else if (direction == ScrollDirection.RIGHT) {
                        activeIndexState.intValue++
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
        if (editingAppInfoState.value != null) {
            // Strict Input Isolation: Traps all inputs while modal artwork dialog is open
            if (isOptionsMenuExpandedState.value) {
                return when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        AppLog.i(TAG, "Dpad UP pressed while options menu expanded -> Change Search Term")
                        dpadUpOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        AppLog.i(TAG, "Dpad RIGHT pressed while options menu expanded -> Use App Icon")
                        dpadRightOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B,
                    -> {
                        AppLog.i(TAG, "Closing options menu")
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    else -> {
                        // Suppress any other button/D-pad event while options menu is open
                        true
                    }
                }
            }

            // Options menu is collapsed - handle artwork chooser dialog controls
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_MENU,
                -> {
                    AppLog.i(TAG, "Gamepad Select/Menu pressed -> Opening options menu")
                    isOptionsMenuExpandedState.value = true
                    return true
                }

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

                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    AppLog.i(TAG, "Gamepad L1 pressed inside artwork dialog")
                    l1TriggerState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    AppLog.i(TAG, "Gamepad R1 pressed inside artwork dialog")
                    r1TriggerState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    AppLog.i(TAG, "Gamepad select key pressed inside artwork dialog")
                    confirmDialogTriggerState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_B,
                -> {
                    AppLog.i(TAG, "Gamepad back key pressed, closing artwork dialog")
                    editingAppInfoState.value = null
                    return true
                }

                else -> {
                    // Suppress unhandled D-pad keys (e.g. Up/Down) from affecting background launcher
                    return true
                }
            }
        }

        // Navigation when Main Launcher is active
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
                        dialogVirtualIndexState.intValue = INITIAL_LOOP_OFFSET
                        confirmDialogTriggerState.intValue = 0
                        l1TriggerState.intValue = 0
                        r1TriggerState.intValue = 0
                        isOptionsMenuExpandedState.value = false
                        dpadUpOptionsTriggerState.intValue = 0
                        dpadRightOptionsTriggerState.intValue = 0
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
        if (editingAppInfoState.value != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                -> {
                    stopRepeat()
                    return true
                }
            }
            return true
        }

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
        if (event != null && (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val axisHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val axisHatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val axisX = event.getAxisValue(MotionEvent.AXIS_X)
            val axisY = event.getAxisValue(MotionEvent.AXIS_Y)

            val x = if (axisHatX != 0f) axisHatX else axisX
            val y = if (axisHatY != 0f) axisHatY else axisY

            if (editingAppInfoState.value != null) {
                if (isOptionsMenuExpandedState.value) {
                    if (y < -0.5f) {
                        AppLog.i(TAG, "Joystick Hat/Stick UP pressed while options expanded -> Change Search Term")
                        dpadUpOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        return true
                    } else if (x > 0.5f) {
                        AppLog.i(TAG, "Joystick Hat/Stick RIGHT pressed while options expanded -> Use App Icon")
                        dpadRightOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        return true
                    }
                    return true
                }

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
                return true
            }

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
        return if (editingAppInfoState.value != null) true else super.onGenericMotionEvent(event)
    }
}
