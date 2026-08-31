package com.stormpanda.megingiard.gamefocus

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.AddRomFolderResult
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.gamefocus.domain.initGameFocusLaunchers
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.settings.ThemeMode
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

    private val selectedCategoryState = mutableStateOf<GameFocusCategory>(GameFocusCategory.LAST_USED)
    private val isMainOptionsMenuExpandedState = mutableStateOf(false)
    private val newlyAddedFolderState = mutableStateOf<CustomRomFolder?>(null)
    private val isRemoveRomFolderDialogOpenState = mutableStateOf(false)
    private val removeRomFolderDialogSelectedIndexState = mutableIntStateOf(0)
    private val folderToRemoveState = mutableStateOf<CustomRomFolder?>(null)
    private val coreChooserDialogSelectedIndexState = mutableIntStateOf(0)
    private val confirmCoreChooserTriggerState = mutableIntStateOf(0)

    private val isOptionsMenuExpandedState = mutableStateOf(false)
    private val dpadUpOptionsTriggerState = mutableIntStateOf(0)
    private val dpadRightOptionsTriggerState = mutableIntStateOf(0)

    private val editingAppInfoState = mutableStateOf<InstalledAppInfo?>(null)

    private val isLibraryOpenState = mutableStateOf(false)
    private val librarySelectedTabState = mutableStateOf<LibraryTab>(LibraryTab.GAMES)
    private var activeLibraryTabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS)
    private val libraryFocusedIndexState = mutableIntStateOf(0)
    private val isLibraryOptionsMenuExpandedState = mutableStateOf(false)

    private var currentDirection = ScrollDirection.NONE
    private var repeatJob: Job? = null

    private val dpadLeftTriggerState = mutableIntStateOf(0)
    private val dpadStepRightTriggerState = mutableIntStateOf(0)
    private val focusedAppState = mutableStateOf<InstalledAppInfo?>(null)

    private var activeCategories: List<GameFocusCategory> = GameFocusCategory.builtIns
    private var launchedTopScreenPackage: String? = null
    private val isResumedState = mutableStateOf(false)
    private val isStartedState = mutableStateOf(false)

    private val openDocumentTreeLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    when (val result = RomManager.addRomFolder(this@FocusTopLauncherActivity, uri)) {
                        is AddRomFolderResult.Success -> {
                            coreChooserDialogSelectedIndexState.intValue = 0
                            confirmCoreChooserTriggerState.intValue = 0
                            newlyAddedFolderState.value = result.folder
                        }

                        is AddRomFolderResult.Error -> {
                            Toast
                                .makeText(this@FocusTopLauncherActivity, result.message, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        }

    private var frozenHiddenSetForInput: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide top system status bar for immersive fullscreen gamepad browsing
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())

        AppLog.i(TAG, "FocusTopLauncherActivity created on primary display (fullscreen)")

        initGameFocusLaunchers()
        InstalledAppsManager.loadInstalledApps(this)

        setContent {
            val remoteThemeState by MegingiardThemeClient
                .observeTheme(
                    this,
                ).collectAsState(initial = Pair(ThemeMode.DARK, null))
            val (themeMode, userAccent) = remoteThemeState
            val appColors = paletteFor(themeMode, userAccent)

            val themeUpdateTrigger by MegingiardThemeClient
                .observeThemeUpdates(this)
                .collectAsState(initial = 0)

            val allApps by InstalledAppsManager.installedApps.collectAsState()
            val favorites by InstalledAppsManager.favorites.collectAsState()
            val hidden by InstalledAppsManager.hiddenApps.collectAsState()
            val lastUsed by InstalledAppsManager.lastUsed.collectAsState()
            val customRomFolders by RomManager.romFolders.collectAsState()

            val categories =
                remember(customRomFolders) {
                    GameFocusCategory.builtIns +
                        customRomFolders
                            .map { folder ->
                                GameFocusCategory.RomSystem(
                                    id = "rom_${folder.systemId}_${folder.uriString.hashCode()}",
                                    systemId = folder.systemId,
                                    displayName = folder.systemName,
                                    folderUri = folder.uriString,
                                )
                            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                }

            val libraryTabs =
                remember(customRomFolders) {
                    listOf(LibraryTab.GAMES, LibraryTab.APPS) +
                        customRomFolders
                            .map { folder ->
                                LibraryTab.RomSystem(
                                    id = "rom_${folder.systemId}",
                                    systemId = folder.systemId,
                                    displayName = SUPPORTED_SYSTEMS.find { it.id == folder.systemId }?.displayName ?: folder.systemName,
                                )
                            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                }

            SideEffect {
                activeCategories = categories
                activeLibraryTabs = libraryTabs
            }

            val selectedCategory = selectedCategoryState.value
            val isLibraryOpen = isLibraryOpenState.value

            SideEffect {
                if (!categories.contains(selectedCategory)) {
                    selectedCategoryState.value = GameFocusCategory.LAST_USED
                }
                if (!libraryTabs.contains(librarySelectedTabState.value)) {
                    librarySelectedTabState.value = LibraryTab.GAMES
                }
            }

            var hasSetStartupCategory by remember { mutableStateOf(false) }
            val currentHidden = hidden

            LaunchedEffect(categories, allApps, favorites, lastUsed, currentHidden) {
                if (!hasSetStartupCategory && allApps.isNotEmpty()) {
                    val startupCat =
                        categories.firstOrNull { cat ->
                            cat.filterApps(allApps, favorites, currentHidden, lastUsed).isNotEmpty()
                        }
                    if (startupCat != null) {
                        selectedCategoryState.value = startupCat
                        hasSetStartupCategory = true
                    }
                }
            }

            val frozenHidden = remember(allApps, selectedCategory, isLibraryOpen) { hidden }
            SideEffect {
                frozenHiddenSetForInput = frozenHidden
            }

            val displayedApps =
                remember(allApps, favorites, frozenHidden, lastUsed, selectedCategory) {
                    selectedCategory.filterApps(allApps, favorites, frozenHidden, lastUsed)
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
                    val currentHoveredApp =
                        remember(
                            isLibraryOpenState.value,
                            focusedAppState.value,
                            libraryFocusedIndexState.intValue,
                            librarySelectedTabState.value,
                            allApps,
                        ) {
                            if (isLibraryOpenState.value) {
                                val activeApps = librarySelectedTabState.value.filterApps(allApps)
                                activeApps.getOrNull(libraryFocusedIndexState.intValue.coerceAtLeast(0))
                            } else {
                                focusedAppState.value
                            }
                        }

                    LaunchedEffect(currentHoveredApp, isStartedState.value, themeUpdateTrigger) {
                        if (isStartedState.value && launchedTopScreenPackage == null) {
                            val palette =
                                currentHoveredApp?.let { app ->
                                    AppPaletteExtractor.extractColorsAsync(app, appColors.accent, appColors.appBackground)
                                }
                            val primaryArgb = palette?.primaryColor?.toArgb()
                            val secondaryArgb = palette?.secondaryColor?.toArgb()

                            MegingiardSettingsClient.updateClientState(
                                context = this@FocusTopLauncherActivity,
                                isActive = true,
                                focusedPackage = null,
                                hoveredPackage = currentHoveredApp?.packageName,
                                hoveredLabel = currentHoveredApp?.label,
                                hoveredPrimaryColor = primaryArgb,
                                hoveredSecondaryColor = secondaryArgb,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.appBackground,
                    ) {
                        FocusTopLauncherScreen(
                            apps = displayedApps,
                            onAppClickTop = { appInfo ->
                                AppLog.i(TAG, "Launching app from top launcher on top display: ${appInfo.label}")
                                launchedTopScreenPackage = appInfo.packageName
                                MegingiardSettingsClient.updateClientState(
                                    this@FocusTopLauncherActivity,
                                    isActive = true,
                                    focusedPackage = appInfo.packageName,
                                )
                                lifecycleScope.launch {
                                    InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, appInfo)
                                }
                            },
                            onAppClickBottom = { appInfo ->
                                AppLog.i(TAG, "Launching app from top launcher on bottom display: ${appInfo.label}")
                                if (!isCompanionApp(appInfo.packageName)) {
                                    MegingiardSettingsClient.updateClientState(
                                        this@FocusTopLauncherActivity,
                                        isActive = true,
                                        focusedPackage = appInfo.packageName,
                                    )
                                }
                                lifecycleScope.launch {
                                    InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, appInfo)
                                }
                            },
                            selectedCategory = selectedCategory,
                            categories = categories,
                            onCategoryUp = {
                                val prevCategory = selectedCategoryState.value.previous(categories)
                                AppLog.i(TAG, "Category UP button clicked -> switching launcher category to ${prevCategory.id}")
                                selectedCategoryState.value = prevCategory
                            },
                            onCategoryDown = {
                                val nextCategory = selectedCategoryState.value.next(categories)
                                AppLog.i(TAG, "Category DOWN button clicked -> switching launcher category to ${nextCategory.id}")
                                selectedCategoryState.value = nextCategory
                            },
                            favoritesSet = favorites,
                            hiddenSet = hidden,
                            isMainOptionsMenuExpanded = isMainOptionsMenuExpandedState.value,
                            onMainOptionsMenuExpandedChange = { isMainOptionsMenuExpandedState.value = it },
                            onToggleFavorite = { appInfo ->
                                InstalledAppsManager.toggleFavorite(this, appInfo.packageName)
                            },
                            onToggleHidden = { appInfo ->
                                InstalledAppsManager.toggleHidden(this, appInfo.packageName)
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
                            onAddRomFolder = { openDocumentTreeLauncher.launch(null) },
                            onRemoveRomFolder = { folder -> RomManager.removeRomFolder(this, folder) },
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
                            newlyAddedFolder = newlyAddedFolderState.value,
                            onDismissNewlyAddedFolder = { newlyAddedFolderState.value = null },
                            onConfirmNewlyAddedFolderCore = { folder, core ->
                                RomManager.updateRomFolderCore(this, folder.uriString, core)
                                newlyAddedFolderState.value = null
                            },
                            coreChooserDialogSelectedIndex = coreChooserDialogSelectedIndexState.intValue,
                            onCoreChooserDialogSelectedIndexChange = { coreChooserDialogSelectedIndexState.intValue = it },
                            confirmCoreChooserTrigger = confirmCoreChooserTriggerState.intValue,
                            isRemoveRomFolderDialogOpen = isRemoveRomFolderDialogOpenState.value,
                            onRemoveRomFolderDialogOpenChange = { isRemoveRomFolderDialogOpenState.value = it },
                            removeRomFolderDialogSelectedIndex = removeRomFolderDialogSelectedIndexState.intValue,
                            onRemoveRomFolderDialogSelectedIndexChange = { removeRomFolderDialogSelectedIndexState.intValue = it },
                            folderToRemove = folderToRemoveState.value,
                            onFolderToRemoveChange = { folderToRemoveState.value = it },
                            allApps = allApps,
                            lastUsed = lastUsed,
                            isLibraryOpen = isLibraryOpenState.value,
                            librarySelectedTab = librarySelectedTabState.value,
                            onLibraryTabSelected = { librarySelectedTabState.value = it },
                            libraryFocusedIndex = libraryFocusedIndexState.intValue,
                            onLibraryFocusedIndexChange = { libraryFocusedIndexState.intValue = it },
                            isLibraryOptionsMenuExpanded = isLibraryOptionsMenuExpandedState.value,
                            onLibraryOptionsMenuExpandedChange = { isLibraryOptionsMenuExpandedState.value = it },
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

    override fun onStart() {
        super.onStart()
        isStartedState.value = true
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "FocusTopLauncherActivity resumed, refreshing installed apps")
        InstalledAppsManager.loadInstalledApps(this)
        launchedTopScreenPackage = null
        isResumedState.value = true
    }

    override fun onPause() {
        super.onPause()
        stopRepeat()
        isResumedState.value = false
    }

    override fun onStop() {
        super.onStop()
        isStartedState.value = false
        if (launchedTopScreenPackage == null) {
            MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
    }

    private fun isCompanionApp(packageName: String): Boolean =
        packageName.startsWith("com.stormpanda.megingiard") && !packageName.contains("gamefocus")

    private fun resetToGallery(): Boolean {
        val wasNotInGallery =
            isLibraryOpenState.value ||
                editingAppInfoState.value != null ||
                isMainOptionsMenuExpandedState.value ||
                isOptionsMenuExpandedState.value ||
                isLibraryOptionsMenuExpandedState.value ||
                isRemoveRomFolderDialogOpenState.value ||
                folderToRemoveState.value != null ||
                newlyAddedFolderState.value != null

        if (wasNotInGallery) {
            AppLog.i(TAG, "Resetting view state to main gallery")
            stopRepeat()
            isLibraryOpenState.value = false
            editingAppInfoState.value = null
            isMainOptionsMenuExpandedState.value = false
            isOptionsMenuExpandedState.value = false
            isLibraryOptionsMenuExpandedState.value = false
            isRemoveRomFolderDialogOpenState.value = false
            folderToRemoveState.value = null
            newlyAddedFolderState.value = null
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

    private fun stepCoreChooserFocus(direction: ScrollDirection) {
        val folder = newlyAddedFolderState.value ?: return
        val systemDef = SUPPORTED_SYSTEMS.find { it.id == folder.systemId }
        val hasCores = systemDef != null && systemDef.emulatorId == "retroarch"
        val coreCount =
            if (hasCores) {
                1 + (systemDef?.retroArchCoreAlternatives?.size ?: 0)
            } else {
                0
            }

        if (hasCores && coreCount > 0) {
            val current = coreChooserDialogSelectedIndexState.intValue
            if (direction == ScrollDirection.UP) {
                coreChooserDialogSelectedIndexState.intValue = (current - 1).floorMod(coreCount)
            } else if (direction == ScrollDirection.DOWN) {
                coreChooserDialogSelectedIndexState.intValue = (current + 1).floorMod(coreCount)
            }
        }
    }

    private fun stepRemoveRomFolderFocus(direction: ScrollDirection) {
        val folders = RomManager.romFolders.value
        val foldersCount = folders.size
        if (foldersCount > 0) {
            val current = removeRomFolderDialogSelectedIndexState.intValue
            if (direction == ScrollDirection.UP) {
                removeRomFolderDialogSelectedIndexState.intValue = (current - 1).floorMod(foldersCount)
            } else if (direction == ScrollDirection.DOWN) {
                removeRomFolderDialogSelectedIndexState.intValue = (current + 1).floorMod(foldersCount)
            }
        }
    }

    private fun stepDirectionalAction(direction: ScrollDirection) {
        if (newlyAddedFolderState.value != null) {
            stepCoreChooserFocus(direction)
            return
        }
        if (isRemoveRomFolderDialogOpenState.value) {
            stepRemoveRomFolderFocus(direction)
            return
        }
        if (isLibraryOpenState.value) {
            stepLibraryFocus(direction)
            return
        }
        if (editingAppInfoState.value != null) {
            when (direction) {
                ScrollDirection.LEFT -> dialogVirtualIndexState.intValue--
                ScrollDirection.RIGHT -> dialogVirtualIndexState.intValue++
                else -> Unit
            }
            return
        }

        when (direction) {
            ScrollDirection.LEFT -> {
                dpadLeftTriggerState.intValue++
                AppLog.d(TAG, "dpadLeftTriggerState = ${dpadLeftTriggerState.intValue}")
            }

            ScrollDirection.RIGHT -> {
                dpadStepRightTriggerState.intValue++
                AppLog.d(TAG, "dpadStepRightTriggerState = ${dpadStepRightTriggerState.intValue}")
            }

            ScrollDirection.UP -> {
                val prevCategory = selectedCategoryState.value.previous(activeCategories)
                AppLog.i(TAG, "Category UP -> switching launcher category to ${prevCategory.id}")
                selectedCategoryState.value = prevCategory
            }

            ScrollDirection.DOWN -> {
                val nextCategory = selectedCategoryState.value.next(activeCategories)
                AppLog.i(TAG, "Category DOWN -> switching launcher category to ${nextCategory.id}")
                selectedCategoryState.value = nextCategory
            }

            ScrollDirection.NONE -> {
                Unit
            }
        }
    }

    private fun startRepeat(direction: ScrollDirection) {
        if (currentDirection == direction) return

        currentDirection = direction
        repeatJob?.cancel()

        if (direction == ScrollDirection.NONE) return

        stepDirectionalAction(direction)
        repeatJob =
            lifecycleScope.launch {
                delay(INITIAL_REPEAT_DELAY_MS)
                while (isActive && currentDirection == direction) {
                    stepDirectionalAction(direction)
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

        if (newlyAddedFolderState.value != null) {
            return when {
                isUpKey(keyCode) -> {
                    startRepeat(ScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(ScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Confirming core chooser dialog via gamepad A")
                    confirmCoreChooserTriggerState.intValue++
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Dismissing core chooser dialog via gamepad B")
                    newlyAddedFolderState.value = null
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (folderToRemoveState.value != null) {
            val folder = folderToRemoveState.value!!
            return when {
                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Confirming removal of ROM folder via gamepad A: ${folder.systemName}")
                    RomManager.removeRomFolder(this, folder)
                    folderToRemoveState.value = null
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Cancelling folder removal via gamepad B")
                    folderToRemoveState.value = null
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (isRemoveRomFolderDialogOpenState.value) {
            val folders = RomManager.romFolders.value
            return when {
                isUpKey(keyCode) -> {
                    startRepeat(ScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(ScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    if (folders.isNotEmpty()) {
                        val selectedFolder = folders.getOrNull(removeRomFolderDialogSelectedIndexState.intValue)
                        if (selectedFolder != null) {
                            AppLog.i(TAG, "Selected folder to remove via gamepad A: ${selectedFolder.systemName}")
                            folderToRemoveState.value = selectedFolder
                        }
                    }
                    isRemoveRomFolderDialogOpenState.value = false
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Remove ROM Folder dialog via gamepad B")
                    isRemoveRomFolderDialogOpenState.value = false
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (editingAppInfoState.value != null) {
            // Strict Input Isolation: Traps all inputs while modal artwork dialog is open
            if (isOptionsMenuExpandedState.value) {
                return when {
                    keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        AppLog.i(TAG, "Dpad UP pressed while options menu expanded -> Change Search Term")
                        dpadUpOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        AppLog.i(TAG, "Dpad RIGHT pressed while options menu expanded -> Use App Icon")
                        dpadRightOptionsTriggerState.intValue++
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                        AppLog.i(TAG, "Closing options menu")
                        isOptionsMenuExpandedState.value = false
                        true
                    }

                    else -> {
                        true
                    }
                }
            }

            // Options menu is collapsed - handle artwork chooser dialog controls
            return when {
                isMenuKey(keyCode) -> {
                    AppLog.i(TAG, "Gamepad Y/Menu pressed -> Opening options menu")
                    isOptionsMenuExpandedState.value = true
                    true
                }

                isLeftKey(keyCode) -> {
                    startRepeat(ScrollDirection.LEFT)
                    true
                }

                isRightKey(keyCode) -> {
                    startRepeat(ScrollDirection.RIGHT)
                    true
                }

                keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                    AppLog.i(TAG, "Gamepad L1 pressed inside artwork dialog")
                    dialogL1TriggerState.intValue++
                    true
                }

                keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                    AppLog.i(TAG, "Gamepad R1 pressed inside artwork dialog")
                    dialogR1TriggerState.intValue++
                    true
                }

                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Gamepad select key pressed inside artwork dialog")
                    confirmDialogTriggerState.intValue++
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Gamepad back key pressed, closing artwork dialog")
                    editingAppInfoState.value = null
                    true
                }

                else -> {
                    true
                }
            }
        }

        // Library Navigation Mode
        if (isLibraryOpenState.value) {
            val allApps = InstalledAppsManager.installedApps.value
            val currentTab = librarySelectedTabState.value
            val filteredApps = currentTab.filterApps(allApps)
            val focusedLibraryApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue.coerceAtLeast(0))

            if (isLibraryOptionsMenuExpandedState.value) {
                stopRepeat()
                return when {
                    isLeftKey(keyCode) -> {
                        if (focusedLibraryApp != null) {
                            AppLog.i(
                                TAG,
                                "D-pad LEFT pressed while Library options menu expanded -> Toggling hidden state for ${focusedLibraryApp.label}",
                            )
                            InstalledAppsManager.toggleHidden(this, focusedLibraryApp.packageName)
                        }
                        isLibraryOptionsMenuExpandedState.value = false
                        true
                    }

                    isUpKey(keyCode) -> {
                        AppLog.i(TAG, "D-pad UP pressed while Library options menu expanded -> Adding ROM folder")
                        openDocumentTreeLauncher.launch(null)
                        isLibraryOptionsMenuExpandedState.value = false
                        true
                    }

                    isDownKey(keyCode) -> {
                        val folders = RomManager.romFolders.value
                        if (folders.isNotEmpty()) {
                            AppLog.i(TAG, "D-pad DOWN pressed while Library options menu expanded -> Manage ROM folders")
                            removeRomFolderDialogSelectedIndexState.intValue = 0
                            isRemoveRomFolderDialogOpenState.value = true
                        }
                        isLibraryOptionsMenuExpandedState.value = false
                        true
                    }

                    isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                        AppLog.i(TAG, "Closing Library options menu")
                        isLibraryOptionsMenuExpandedState.value = false
                        true
                    }

                    else -> {
                        true
                    }
                }
            }

            return when {
                isMenuKey(keyCode) -> {
                    if (focusedLibraryApp != null) {
                        AppLog.i(TAG, "Gamepad Y/Menu pressed in Library -> Opening Library options menu for ${focusedLibraryApp.label}")
                        isLibraryOptionsMenuExpandedState.value = true
                    }
                    true
                }

                keyCode == GamePadButton.BUTTON_R2.keyCode || isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Library section")
                    stopRepeat()
                    isLibraryOpenState.value = false
                    true
                }

                keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                    val prevTab = currentTab.previous(activeLibraryTabs)
                    AppLog.i(TAG, "Library L1 pressed -> switching tab to ${prevTab.id}")
                    librarySelectedTabState.value = prevTab
                    libraryFocusedIndexState.intValue = 0
                    true
                }

                keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                    val nextTab = currentTab.next(activeLibraryTabs)
                    AppLog.i(TAG, "Library R1 pressed -> switching tab to ${nextTab.id}")
                    librarySelectedTabState.value = nextTab
                    libraryFocusedIndexState.intValue = 0
                    true
                }

                isLeftKey(keyCode) -> {
                    startRepeat(ScrollDirection.LEFT)
                    true
                }

                isRightKey(keyCode) -> {
                    startRepeat(ScrollDirection.RIGHT)
                    true
                }

                isUpKey(keyCode) -> {
                    startRepeat(ScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(ScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    val targetApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue.coerceAtLeast(0))
                    if (targetApp != null) {
                        AppLog.i(TAG, "Library launch on top display: ${targetApp.label}")
                        launchedTopScreenPackage = targetApp.packageName
                        MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                        lifecycleScope.launch {
                            InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, targetApp)
                        }
                    }
                    true
                }

                keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                    if (libraryFocusedIndexState.intValue >= 0) {
                        val targetApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue)
                        if (targetApp != null) {
                            AppLog.i(TAG, "Library launch on bottom display: ${targetApp.label}")
                            if (!isCompanionApp(targetApp.packageName)) {
                                MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                            }
                            lifecycleScope.launch {
                                InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, targetApp)
                            }
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
        val hidden = frozenHiddenSetForInput
        val lastUsed = InstalledAppsManager.lastUsed.value
        val selectedCategory = selectedCategoryState.value
        val apps = selectedCategory.filterApps(allApps, favorites, hidden, lastUsed)

        if (isMainOptionsMenuExpandedState.value) {
            stopRepeat()
            val targetApp = focusedAppState.value
            return when {
                isUpKey(keyCode) -> {
                    if (targetApp != null) {
                        InstalledAppsManager.toggleFavorite(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                isRightKey(keyCode) -> {
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

                isDownKey(keyCode) -> {
                    if (targetApp != null) {
                        AppLog.i(TAG, "D-pad DOWN pressed while options menu expanded -> Opening native app info for ${targetApp.label}")
                        InstalledAppsManager.openAppInfo(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                isLeftKey(keyCode) -> {
                    if (targetApp != null) {
                        AppLog.i(TAG, "D-pad LEFT pressed while options menu expanded -> Toggling hidden state for ${targetApp.label}")
                        InstalledAppsManager.toggleHidden(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                    isMainOptionsMenuExpandedState.value = false
                    true
                }

                else -> {
                    true
                }
            }
        }

        when {
            isMenuKey(keyCode) -> {
                if (apps.isNotEmpty()) {
                    stopRepeat()
                    isMainOptionsMenuExpandedState.value = true
                }
                return true
            }

            isUpKey(keyCode) -> {
                startRepeat(ScrollDirection.UP)
                return true
            }

            isDownKey(keyCode) -> {
                startRepeat(ScrollDirection.DOWN)
                return true
            }

            isLeftKey(keyCode) -> {
                if (apps.isNotEmpty()) startRepeat(ScrollDirection.LEFT)
                return true
            }

            isRightKey(keyCode) -> {
                if (apps.isNotEmpty()) startRepeat(ScrollDirection.RIGHT)
                return true
            }

            isConfirmKey(keyCode) -> {
                val targetApp = focusedAppState.value
                if (targetApp != null) {
                    AppLog.i(TAG, "Gamepad A button / launch key pressed for: ${targetApp.label} -> Launching on Top Display")
                    launchedTopScreenPackage = targetApp.packageName
                    MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                    lifecycleScope.launch {
                        InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, targetApp)
                    }
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                val targetApp = focusedAppState.value
                if (targetApp != null) {
                    AppLog.i(TAG, "Gamepad X button pressed for: ${targetApp.label} -> Launching on Bottom Display")
                    if (!isCompanionApp(targetApp.packageName)) {
                        MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                    }
                    lifecycleScope.launch {
                        InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, targetApp)
                    }
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad L1 pressed -> skipping to previous starting letter")
                    prevLetterTriggerState.intValue++
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad R1 pressed -> skipping to next starting letter")
                    nextLetterTriggerState.intValue++
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R2.keyCode -> {
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

        if (isDirectionalKey(keyCode)) {
            stopRepeat()
            return true
        }

        if (editingAppInfoState.value != null ||
            newlyAddedFolderState.value != null ||
            folderToRemoveState.value != null ||
            isRemoveRomFolderDialogOpenState.value
        ) {
            return true
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

            if (newlyAddedFolderState.value != null ||
                isRemoveRomFolderDialogOpenState.value
            ) {
                if (y < -0.5f) {
                    startRepeat(ScrollDirection.UP)
                    return true
                } else if (y > 0.5f) {
                    startRepeat(ScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection == ScrollDirection.UP || currentDirection == ScrollDirection.DOWN) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (editingAppInfoState.value != null ||
                folderToRemoveState.value != null
            ) {
                if (editingAppInfoState.value != null && isOptionsMenuExpandedState.value) {
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
                } else if (x < -0.5f) {
                    val targetApp = focusedAppState.value
                    if (targetApp != null) {
                        AppLog.i(TAG, "Joystick LEFT pressed while options menu expanded -> Toggling hidden for ${targetApp.label}")
                        InstalledAppsManager.toggleHidden(this, targetApp.packageName)
                    }
                    isMainOptionsMenuExpandedState.value = false
                    return true
                }
                return true
            }

            if (isLibraryOptionsMenuExpandedState.value) {
                stopRepeat()
                if (x < -0.5f) {
                    val allApps = InstalledAppsManager.installedApps.value
                    val currentTab = librarySelectedTabState.value
                    val filteredApps = currentTab.filterApps(allApps)
                    val focusedLibraryApp = filteredApps.getOrNull(libraryFocusedIndexState.intValue.coerceAtLeast(0))
                    if (focusedLibraryApp != null) {
                        AppLog.i(
                            TAG,
                            "Joystick LEFT pressed while Library options menu expanded -> Toggling hidden for ${focusedLibraryApp.label}",
                        )
                        InstalledAppsManager.toggleHidden(this, focusedLibraryApp.packageName)
                    }
                    isLibraryOptionsMenuExpandedState.value = false
                    return true
                } else if (y < -0.5f) {
                    AppLog.i(TAG, "Joystick UP pressed while Library options menu expanded -> Adding ROM folder")
                    openDocumentTreeLauncher.launch(null)
                    isLibraryOptionsMenuExpandedState.value = false
                    return true
                } else if (y > 0.5f) {
                    val folders = RomManager.romFolders.value
                    if (folders.isNotEmpty()) {
                        AppLog.i(TAG, "Joystick DOWN pressed while Library options menu expanded -> Manage ROM folders")
                        removeRomFolderDialogSelectedIndexState.intValue = 0
                        isRemoveRomFolderDialogOpenState.value = true
                    }
                    isLibraryOptionsMenuExpandedState.value = false
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

private fun isUpKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP ||
        keyCode == GamePadButton.DPAD_UP.keyCode

private fun isDownKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN ||
        keyCode == GamePadButton.DPAD_DOWN.keyCode

private fun isLeftKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT ||
        keyCode == GamePadButton.DPAD_LEFT.keyCode

private fun isRightKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT ||
        keyCode == GamePadButton.DPAD_RIGHT.keyCode

private fun isDirectionalKey(keyCode: Int): Boolean = isUpKey(keyCode) || isDownKey(keyCode) || isLeftKey(keyCode) || isRightKey(keyCode)

private fun isConfirmKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == GamePadButton.BUTTON_A.keyCode ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

private fun isDismissKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE ||
        keyCode == GamePadButton.BUTTON_B.keyCode

private fun isMenuKey(keyCode: Int): Boolean = keyCode == GamePadButton.BUTTON_Y.keyCode || keyCode == KeyEvent.KEYCODE_MENU
