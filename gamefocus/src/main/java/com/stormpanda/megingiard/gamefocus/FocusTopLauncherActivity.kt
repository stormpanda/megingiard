package com.stormpanda.megingiard.gamefocus

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.InstalledAppInfo
import com.stormpanda.megingiard.focus.InstalledAppsManager
import com.stormpanda.megingiard.focus.LibraryTab
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.GamePadButton
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

private enum class ScrollDirection { NONE, LEFT, RIGHT, UP, DOWN }

class FocusTopLauncherActivity : ComponentActivity() {
    private val dialogVirtualIndexState = mutableIntStateOf(INITIAL_LOOP_OFFSET)
    private val confirmDialogTriggerState = mutableIntStateOf(0)
    private val dialogL1TriggerState = mutableIntStateOf(0)
    private val dialogR1TriggerState = mutableIntStateOf(0)
    private val prevLetterTriggerState = mutableIntStateOf(0)
    private val nextLetterTriggerState = mutableIntStateOf(0)

    private val selectedCategoryState = mutableStateOf(GameFocusCategory.GAMES)
    private val isMainOptionsMenuExpandedState = mutableStateOf(false)

    private val isOptionsMenuExpandedState = mutableStateOf(false)
    private val dpadUpOptionsTriggerState = mutableIntStateOf(0)
    private val dpadRightOptionsTriggerState = mutableIntStateOf(0)

    private val editingAppInfoState = mutableStateOf<InstalledAppInfo?>(null)

    private val isLibraryOpenState = mutableStateOf(false)
    private val librarySelectedTabState = mutableStateOf(LibraryTab.ALL)
    private val libraryFocusedIndexState = mutableIntStateOf(0)

    private var currentDirection = ScrollDirection.NONE
    private var repeatJob: Job? = null

    private val dpadLeftTriggerState = mutableIntStateOf(0)
    private val dpadStepRightTriggerState = mutableIntStateOf(0)
    private val focusedAppState = mutableStateOf<InstalledAppInfo?>(null)

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
            val remoteThemeState by MegingiardThemeClient
                .observeTheme(
                    this,
                ).collectAsState(initial = Pair(com.stormpanda.megingiard.settings.ThemeMode.DARK, null))
            val (themeMode, userAccent) = remoteThemeState
            val appColors = paletteFor(themeMode, userAccent)

            val allApps by InstalledAppsManager.installedApps.collectAsState()
            val favorites by InstalledAppsManager.favorites.collectAsState()
            val lastUsed by InstalledAppsManager.lastUsed.collectAsState()

            val selectedCategory = selectedCategoryState.value
            val displayedApps =
                remember(allApps, favorites, lastUsed, selectedCategory) {
                    when (selectedCategory) {
                        GameFocusCategory.GAMES -> allApps.filter { it.isGame }
                        GameFocusCategory.APPS -> allApps.filter { !it.isGame }
                        GameFocusCategory.ALL_APPS -> allApps
                        GameFocusCategory.FAVORITES -> allApps.filter { favorites.contains(it.packageName) }
                        GameFocusCategory.LAST_USED -> lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
                    }
                }

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
                            apps = displayedApps,
                            onAppClickTop = { appInfo ->
                                AppLog.i(TAG, "Launching app from top launcher on top display: ${appInfo.label}")
                                InstalledAppsManager.launchAppOnPrimaryDisplay(this, appInfo)
                            },
                            onAppClickBottom = { appInfo ->
                                AppLog.i(TAG, "Launching app from top launcher on bottom display: ${appInfo.label}")
                                InstalledAppsManager.launchAppOnSecondaryDisplay(this, appInfo)
                            },
                            selectedCategory = selectedCategory,
                            favoritesSet = favorites,
                            isMainOptionsMenuExpanded = isMainOptionsMenuExpandedState.value,
                            onMainOptionsMenuExpandedChange = { isMainOptionsMenuExpandedState.value = it },
                            onToggleFavorite = { appInfo ->
                                InstalledAppsManager.toggleFavorite(this, appInfo.packageName)
                            },
                            onEditArtwork = { appInfo ->
                                AppLog.i(TAG, "Opening artwork edit dialog for ${appInfo.label}")
                                dialogVirtualIndexState.intValue = INITIAL_LOOP_OFFSET
                                confirmDialogTriggerState.intValue = 0
                                dialogL1TriggerState.intValue = 0
                                dialogR1TriggerState.intValue = 0
                                isOptionsMenuExpandedState.value = false
                                dpadUpOptionsTriggerState.intValue = 0
                                dpadRightOptionsTriggerState.intValue = 0
                                editingAppInfoState.value = appInfo
                            },
                            onOpenAppInfo = { appInfo ->
                                InstalledAppsManager.openAppInfo(this, appInfo.packageName)
                            },
                            editingAppInfo = editingApp,
                            dialogVirtualIndex = dialogVirtualIndexState.intValue,
                            onDialogVirtualIndexChange = { dialogVirtualIndexState.intValue = it },
                            confirmDialogTrigger = confirmDialogTriggerState.intValue,
                            dialogL1Trigger = dialogL1TriggerState.intValue,
                            dialogR1Trigger = dialogR1TriggerState.intValue,
                            prevLetterTrigger = prevLetterTriggerState.intValue,
                            nextLetterTrigger = nextLetterTriggerState.intValue,
                            isOptionsMenuExpanded = isOptionsMenuExpandedState.value,
                            onOptionsMenuExpandedChange = { isOptionsMenuExpandedState.value = it },
                            dpadUpTrigger = dpadUpOptionsTriggerState.intValue,
                            dpadRightTrigger = dpadRightOptionsTriggerState.intValue,
                            dpadLeftTrigger = dpadLeftTriggerState.intValue,
                            dpadStepRightTrigger = dpadStepRightTriggerState.intValue,
                            onFocusedAppChanged = { focusedAppState.value = it },
                            onDismissEditingApp = { editingAppInfoState.value = null },
                            allApps = allApps,
                            isLibraryOpen = isLibraryOpenState.value,
                            librarySelectedTab = librarySelectedTabState.value,
                            onLibraryTabSelected = { librarySelectedTabState.value = it },
                            libraryFocusedIndex = libraryFocusedIndexState.intValue,
                            onLibraryFocusedIndexChange = { libraryFocusedIndexState.intValue = it },
                            onOpenLibrary = { isLibraryOpenState.value = true },
                            onCloseLibrary = { isLibraryOpenState.value = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLog.i(TAG, "onNewIntent received -> resetting view to main gallery")
        resetToGallery()
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

    private fun resetToGallery(): Boolean {
        val wasNotInGallery =
            isLibraryOpenState.value ||
                editingAppInfoState.value != null ||
                isMainOptionsMenuExpandedState.value ||
                isOptionsMenuExpandedState.value

        if (wasNotInGallery) {
            AppLog.i(TAG, "Resetting view state to main gallery")
            stopRepeat()
            isLibraryOpenState.value = false
            editingAppInfoState.value = null
            isMainOptionsMenuExpandedState.value = false
            isOptionsMenuExpandedState.value = false
            return true
        }
        return false
    }

    private fun stepLibraryFocus(direction: ScrollDirection) {
        val allApps = InstalledAppsManager.installedApps.value
        val currentTab = librarySelectedTabState.value
        val filteredApps = currentTab.filterApps(allApps)
        val total = filteredApps.size
        val current = libraryFocusedIndexState.intValue.coerceAtLeast(0)

        when (direction) {
            ScrollDirection.LEFT -> {
                if (current > 0) {
                    libraryFocusedIndexState.intValue = current - 1
                }
            }

            ScrollDirection.RIGHT -> {
                if (total > 0 && current < total - 1) {
                    libraryFocusedIndexState.intValue = current + 1
                }
            }

            ScrollDirection.UP -> {
                if (current >= FLS_GRID_COLUMNS) {
                    libraryFocusedIndexState.intValue = current - FLS_GRID_COLUMNS
                }
            }

            ScrollDirection.DOWN -> {
                if (total > 0) {
                    if (current + FLS_GRID_COLUMNS < total) {
                        libraryFocusedIndexState.intValue = current + FLS_GRID_COLUMNS
                    } else if (current < total - 1) {
                        libraryFocusedIndexState.intValue = total - 1
                    }
                }
            }

            ScrollDirection.NONE -> {}
        }
    }

    private fun startRepeat(direction: ScrollDirection) {
        if (currentDirection == direction) return

        currentDirection = direction
        repeatJob?.cancel()

        if (direction == ScrollDirection.NONE) return

        if (isLibraryOpenState.value) {
            stepLibraryFocus(direction)
            repeatJob =
                lifecycleScope.launch {
                    delay(INITIAL_REPEAT_DELAY_MS)
                    while (isActive && currentDirection == direction) {
                        stepLibraryFocus(direction)
                        delay(REPEAT_INTERVAL_MS)
                    }
                }
            return
        }

        if (editingAppInfoState.value != null) {
            if (direction == ScrollDirection.LEFT) {
                dialogVirtualIndexState.intValue--
            } else if (direction == ScrollDirection.RIGHT) {
                dialogVirtualIndexState.intValue++
            }
            repeatJob =
                lifecycleScope.launch {
                    delay(INITIAL_REPEAT_DELAY_MS)
                    while (isActive && currentDirection == direction) {
                        if (direction == ScrollDirection.LEFT) {
                            dialogVirtualIndexState.intValue--
                        } else if (direction == ScrollDirection.RIGHT) {
                            dialogVirtualIndexState.intValue++
                        }
                        delay(REPEAT_INTERVAL_MS)
                    }
                }
            return
        }

        AppLog.d(TAG, "startRepeat: direction=$direction")
        if (direction == ScrollDirection.LEFT) {
            dpadLeftTriggerState.intValue++
            AppLog.d(TAG, "Incremented dpadLeftTriggerState to ${dpadLeftTriggerState.intValue}")
        } else if (direction == ScrollDirection.RIGHT) {
            dpadStepRightTriggerState.intValue++
            AppLog.d(TAG, "Incremented dpadStepRightTriggerState to ${dpadStepRightTriggerState.intValue}")
        }

        repeatJob =
            lifecycleScope.launch {
                delay(INITIAL_REPEAT_DELAY_MS)
                while (isActive && currentDirection == direction) {
                    if (direction == ScrollDirection.LEFT) {
                        dpadLeftTriggerState.intValue++
                        AppLog.d(TAG, "Repeat tick: dpadLeftTriggerState = ${dpadLeftTriggerState.intValue}")
                    } else if (direction == ScrollDirection.RIGHT) {
                        dpadStepRightTriggerState.intValue++
                        AppLog.d(TAG, "Repeat tick: dpadStepRightTriggerState = ${dpadStepRightTriggerState.intValue}")
                    }
                    delay(REPEAT_INTERVAL_MS)
                }
            }
    }

    private fun stopRepeat() {
        AppLog.d(TAG, "stopRepeat: currentDirection was $currentDirection")
        currentDirection = ScrollDirection.NONE
        repeatJob?.cancel()
        repeatJob = null
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            AppLog.i(TAG, "Home key pressed (keyCode=$keyCode) -> returning to main gallery")
            resetToGallery()
            return true
        }

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

                    GamePadButton.BUTTON_Y.keyCode,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    GamePadButton.BUTTON_B.keyCode,
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
                GamePadButton.BUTTON_Y.keyCode,
                KeyEvent.KEYCODE_MENU,
                -> {
                    AppLog.i(TAG, "Gamepad Y/Menu pressed -> Opening options menu")
                    isOptionsMenuExpandedState.value = true
                    return true
                }

                GamePadButton.DPAD_LEFT.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                -> {
                    startRepeat(ScrollDirection.LEFT)
                    return true
                }

                GamePadButton.DPAD_RIGHT.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                -> {
                    startRepeat(ScrollDirection.RIGHT)
                    return true
                }

                GamePadButton.BUTTON_L1.keyCode -> {
                    AppLog.i(TAG, "Gamepad L1 pressed inside artwork dialog")
                    dialogL1TriggerState.intValue++
                    return true
                }

                GamePadButton.BUTTON_R1.keyCode -> {
                    AppLog.i(TAG, "Gamepad R1 pressed inside artwork dialog")
                    dialogR1TriggerState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                GamePadButton.BUTTON_A.keyCode,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    AppLog.i(TAG, "Gamepad select key pressed inside artwork dialog")
                    confirmDialogTriggerState.intValue++
                    return true
                }

                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                GamePadButton.BUTTON_B.keyCode,
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

        // Library Navigation Mode
        if (isLibraryOpenState.value) {
            val allApps = InstalledAppsManager.installedApps.value
            val currentTab = librarySelectedTabState.value
            val filteredApps = currentTab.filterApps(allApps)

            return when (keyCode) {
                GamePadButton.BUTTON_R2.keyCode,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                GamePadButton.BUTTON_B.keyCode,
                -> {
                    AppLog.i(TAG, "Closing Library section")
                    stopRepeat()
                    isLibraryOpenState.value = false
                    true
                }

                GamePadButton.BUTTON_L1.keyCode -> {
                    val prevTab = currentTab.previous()
                    AppLog.i(TAG, "Library L1 pressed -> switching tab to ${prevTab.name}")
                    librarySelectedTabState.value = prevTab
                    libraryFocusedIndexState.intValue = 0
                    true
                }

                GamePadButton.BUTTON_R1.keyCode -> {
                    val nextTab = currentTab.next()
                    AppLog.i(TAG, "Library R1 pressed -> switching tab to ${nextTab.name}")
                    librarySelectedTabState.value = nextTab
                    libraryFocusedIndexState.intValue = 0
                    true
                }

                GamePadButton.DPAD_LEFT.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                -> {
                    startRepeat(ScrollDirection.LEFT)
                    true
                }

                GamePadButton.DPAD_RIGHT.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                -> {
                    startRepeat(ScrollDirection.RIGHT)
                    true
                }

                GamePadButton.DPAD_UP.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
                -> {
                    startRepeat(ScrollDirection.UP)
                    true
                }

                GamePadButton.DPAD_DOWN.keyCode,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
                -> {
                    startRepeat(ScrollDirection.DOWN)
                    true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                GamePadButton.BUTTON_A.keyCode,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    val targetApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue.coerceAtLeast(0))
                    if (targetApp != null) {
                        AppLog.i(TAG, "Library launch on top display: ${targetApp.label}")
                        InstalledAppsManager.launchAppOnPrimaryDisplay(this, targetApp)
                    }
                    true
                }

                GamePadButton.BUTTON_X.keyCode,
                KeyEvent.KEYCODE_X,
                -> {
                    if (libraryFocusedIndexState.intValue >= 0) {
                        val targetApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue)
                        if (targetApp != null) {
                            AppLog.i(TAG, "Library launch on bottom display: ${targetApp.label}")
                            InstalledAppsManager.launchAppOnSecondaryDisplay(this, targetApp)
                        }
                    }
                    true
                }

                else -> {
                    true
                }
            }
        }

        // Navigation when Main Launcher is active
        val allApps = InstalledAppsManager.installedApps.value
        val favorites = InstalledAppsManager.favorites.value
        val lastUsed = InstalledAppsManager.lastUsed.value
        val selectedCategory = selectedCategoryState.value
        val apps =
            when (selectedCategory) {
                GameFocusCategory.GAMES -> allApps.filter { it.isGame }
                GameFocusCategory.APPS -> allApps.filter { !it.isGame }
                GameFocusCategory.ALL_APPS -> allApps
                GameFocusCategory.FAVORITES -> allApps.filter { favorites.contains(it.packageName) }
                GameFocusCategory.LAST_USED -> lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
            }

        if (isMainOptionsMenuExpandedState.value) {
            stopRepeat()
            val targetApp = focusedAppState.value
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (targetApp != null) {
                        InstalledAppsManager.toggleFavorite(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (targetApp != null) {
                        AppLog.i(TAG, "D-pad RIGHT pressed while options menu expanded -> Editing artwork for ${targetApp.label}")
                        dialogVirtualIndexState.intValue = INITIAL_LOOP_OFFSET
                        confirmDialogTriggerState.intValue = 0
                        dialogL1TriggerState.intValue = 0
                        dialogR1TriggerState.intValue = 0
                        isOptionsMenuExpandedState.value = false
                        dpadUpOptionsTriggerState.intValue = 0
                        dpadRightOptionsTriggerState.intValue = 0
                        editingAppInfoState.value = targetApp
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (targetApp != null) {
                        AppLog.i(TAG, "D-pad DOWN pressed while options menu expanded -> Opening native app info for ${targetApp.label}")
                        InstalledAppsManager.openAppInfo(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                GamePadButton.BUTTON_Y.keyCode,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                GamePadButton.BUTTON_B.keyCode,
                -> {
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                else -> {
                    true
                }
            }
        }

        when (keyCode) {
            GamePadButton.BUTTON_Y.keyCode,
            KeyEvent.KEYCODE_MENU,
            -> {
                if (apps.isNotEmpty()) {
                    stopRepeat()
                    isMainOptionsMenuExpandedState.value = true
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
            -> {
                val prevCategory = selectedCategoryState.value.previous()
                AppLog.i(TAG, "D-pad UP pressed -> switching launcher category to ${prevCategory.name}")
                selectedCategoryState.value = prevCategory
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
            -> {
                val nextCategory = selectedCategoryState.value.next()
                AppLog.i(TAG, "D-pad DOWN pressed -> switching launcher category to ${nextCategory.name}")
                selectedCategoryState.value = nextCategory
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
            -> {
                if (apps.isNotEmpty()) startRepeat(ScrollDirection.LEFT)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
            -> {
                if (apps.isNotEmpty()) startRepeat(ScrollDirection.RIGHT)
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            GamePadButton.BUTTON_A.keyCode,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                val targetApp = focusedAppState.value
                if (targetApp != null) {
                    AppLog.i(TAG, "Gamepad A button / launch key pressed for: ${targetApp.label} -> Launching on Top Display")
                    InstalledAppsManager.launchAppOnPrimaryDisplay(this, targetApp)
                    return true
                }
            }

            GamePadButton.BUTTON_X.keyCode,
            KeyEvent.KEYCODE_X,
            -> {
                val targetApp = focusedAppState.value
                if (targetApp != null) {
                    AppLog.i(TAG, "Gamepad X button pressed for: ${targetApp.label} -> Launching on Bottom Display")
                    InstalledAppsManager.launchAppOnSecondaryDisplay(this, targetApp)
                    return true
                }
            }

            GamePadButton.BUTTON_L1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad L1 pressed -> skipping to previous starting letter")
                    prevLetterTriggerState.intValue++
                }
                return true
            }

            GamePadButton.BUTTON_R1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad R1 pressed -> skipping to next starting letter")
                    nextLetterTriggerState.intValue++
                }
                return true
            }

            GamePadButton.BUTTON_R2.keyCode -> {
                AppLog.i(TAG, "Gamepad R2 pressed -> Opening Library section")
                isLibraryOpenState.value = true
                libraryFocusedIndexState.intValue = 0
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            return true
        }

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
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
            -> {
                stopRepeat()
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

            if (isMainOptionsMenuExpandedState.value) {
                stopRepeat()
                val allApps = InstalledAppsManager.installedApps.value
                val favorites = InstalledAppsManager.favorites.value
                val lastUsed = InstalledAppsManager.lastUsed.value
                val selectedCategory = selectedCategoryState.value
                val apps =
                    when (selectedCategory) {
                        GameFocusCategory.GAMES -> allApps.filter { it.isGame }
                        GameFocusCategory.APPS -> allApps.filter { !it.isGame }
                        GameFocusCategory.ALL_APPS -> allApps
                        GameFocusCategory.FAVORITES -> allApps.filter { favorites.contains(it.packageName) }
                        GameFocusCategory.LAST_USED -> lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
                    }

                if (y < -0.5f) {
                    val targetApp = focusedAppState.value
                    if (targetApp != null) {
                        InstalledAppsManager.toggleFavorite(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    return true
                } else if (x > 0.5f) {
                    val targetApp = focusedAppState.value
                    if (targetApp != null) {
                        AppLog.i(TAG, "Joystick RIGHT pressed while options menu expanded -> Edit artwork for ${targetApp.label}")
                        dialogVirtualIndexState.intValue = INITIAL_LOOP_OFFSET
                        confirmDialogTriggerState.intValue = 0
                        dialogL1TriggerState.intValue = 0
                        dialogR1TriggerState.intValue = 0
                        isOptionsMenuExpandedState.value = false
                        dpadUpOptionsTriggerState.intValue = 0
                        dpadRightOptionsTriggerState.intValue = 0
                        editingAppInfoState.value = targetApp
                    }
                    isMainOptionsMenuExpandedState.value = false
                    return true
                } else if (y > 0.5f) {
                    val targetApp = focusedAppState.value
                    if (targetApp != null) {
                        AppLog.i(TAG, "Joystick DOWN pressed while options menu expanded -> Opening native app info for ${targetApp.label}")
                        InstalledAppsManager.openAppInfo(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    return true
                }
                return true
            }

            if (isLibraryOpenState.value) {
                if (x < -0.5f) {
                    startRepeat(ScrollDirection.LEFT)
                    return true
                } else if (x > 0.5f) {
                    startRepeat(ScrollDirection.RIGHT)
                    return true
                } else if (y < -0.5f) {
                    startRepeat(ScrollDirection.UP)
                    return true
                } else if (y > 0.5f) {
                    startRepeat(ScrollDirection.DOWN)
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
