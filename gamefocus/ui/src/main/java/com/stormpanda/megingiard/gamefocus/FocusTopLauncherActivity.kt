package com.stormpanda.megingiard.gamefocus

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.AddRomFolderResult
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.EMULATOR_ID_RETROARCH
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SwitchEmulators
import com.stormpanda.megingiard.gamefocus.domain.GameFocusDefaultLauncherManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusPairManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusSessionTracker
import com.stormpanda.megingiard.gamefocus.domain.initGameFocusLaunchers
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_LIBRARY_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_PAIRING_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.gamefocus.viewmodel.INITIAL_LOOP_OFFSET
import com.stormpanda.megingiard.gamefocus.viewmodel.LauncherScrollDirection
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
private const val INITIAL_REPEAT_DELAY_MS = 300L
private const val REPEAT_INTERVAL_MS = 100L
private const val ARTWORK_MENU_ITEMS_COUNT = 2
private const val MAIN_MENU_UNPAIRED_ITEMS_COUNT = 4
private const val MAIN_MENU_PAIRED_ITEMS_COUNT = 5
private const val JOYSTICK_THRESHOLD = 0.5f
private const val JOYSTICK_NEGATIVE_THRESHOLD = -0.5f

internal fun getMainMenuCount(isPaired: Boolean): Int = if (isPaired) MAIN_MENU_PAIRED_ITEMS_COUNT else MAIN_MENU_UNPAIRED_ITEMS_COUNT

private const val LIBRARY_APP_ACTIONS_COUNT = 3
private const val LIBRARY_ROM_ACTIONS_COUNT = 1
private const val LIBRARY_BASE_ACTIONS_COUNT = 1

internal fun getLibraryMenuCount(
    hasApp: Boolean,
    isRom: Boolean,
    hasRomFolders: Boolean,
    isConfigurableRomSystem: Boolean,
): Int {
    val appActionCount =
        when {
            !hasApp -> 0
            isRom -> LIBRARY_ROM_ACTIONS_COUNT
            else -> LIBRARY_APP_ACTIONS_COUNT
        }
    val romFolderActionCount =
        LIBRARY_BASE_ACTIONS_COUNT +
            (if (hasRomFolders) 1 else 0) +
            (if (isConfigurableRomSystem) 1 else 0)
    return appActionCount + romFolderActionCount
}

class FocusTopLauncherActivity : ComponentActivity() {
    private val viewModel: FocusTopLauncherViewModel by viewModels()

    private var activeLibraryTabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS)
    private var currentDirection = LauncherScrollDirection.NONE
    private var repeatJob: Job? = null

    private var activeCategories: List<GameFocusCategory> = GameFocusCategory.builtIns
    private var launchedTopScreenPackage: String? = null

    private val openDocumentTreeLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    when (val result = RomManager.addRomFolder(this@FocusTopLauncherActivity, uri)) {
                        is AddRomFolderResult.Success -> {
                            if (result.folder.systemId == SYSTEM_ID_SWITCH) {
                                val emulators = SwitchEmulators.getInstalledEmulators(this@FocusTopLauncherActivity, forceRefresh = true)
                                if (emulators.size > 1) {
                                    openCoreChooserForFolder(result.folder)
                                }
                            } else {
                                viewModel.setCoreChooserDialogSelectedIndex(0)
                                viewModel.setNewlyAddedFolder(result.folder)
                            }
                        }

                        is AddRomFolderResult.Error -> {
                            val displayMessage =
                                when (result.errorCode) {
                                    AddRomFolderResult.ErrorCode.NO_SWITCH_EMULATOR -> {
                                        getString(R.string.gamefocus_error_no_switch_emulator)
                                    }

                                    AddRomFolderResult.ErrorCode.FOLDER_INACCESSIBLE -> {
                                        getString(R.string.gamefocus_error_folder_inaccessible)
                                    }

                                    AddRomFolderResult.ErrorCode.UNKNOWN_SYSTEM -> {
                                        getString(R.string.gamefocus_error_unknown_system)
                                    }

                                    AddRomFolderResult.ErrorCode.DUPLICATE_FOLDER -> {
                                        getString(R.string.gamefocus_error_duplicate_folder)
                                    }

                                    null -> {
                                        result.message
                                    }
                                }
                            Toast
                                .makeText(this@FocusTopLauncherActivity, displayMessage, Toast.LENGTH_LONG)
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
        GameFocusPairManager.loadPairs(this)
        if (savedInstanceState == null && intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            GameFocusDefaultLauncherManager.handleHomeNavigation(this)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (resetToGallery()) {
                        AppLog.i(TAG, "Back pressed -> closed sub-view, returned to main gallery")
                        return
                    }
                    if (GameFocusSessionTracker.hasActiveSession()) {
                        AppLog.i(TAG, "Back pressed on gallery -> restoring previous session")
                        lifecycleScope.launch {
                            GameFocusSessionTracker.restorePreviousSession(this@FocusTopLauncherActivity)
                        }
                    } else {
                        AppLog.d(TAG, "Back pressed on gallery with no session tracked -> staying in gallery")
                    }
                }
            },
        )

        setContent {
            val remoteThemeState by MegingiardThemeClient
                .observeTheme(
                    this,
                ).collectAsStateWithLifecycle(initialValue = Pair(ThemeMode.DARK, null))
            val (themeMode, userAccent) = remoteThemeState
            val appColors = paletteFor(themeMode, userAccent)

            val themeUpdateTrigger by MegingiardThemeClient
                .observeThemeUpdates(this)
                .collectAsStateWithLifecycle(initialValue = 0)

            val allApps by InstalledAppsManager.installedApps.collectAsStateWithLifecycle()
            val favorites by InstalledAppsManager.favorites.collectAsStateWithLifecycle()
            val hidden by InstalledAppsManager.hiddenApps.collectAsStateWithLifecycle()
            val lastUsed by InstalledAppsManager.lastUsed.collectAsStateWithLifecycle()
            val customRomFolders by RomManager.romFolders.collectAsStateWithLifecycle()
            val pairedApps by GameFocusPairManager.pairedApps.collectAsStateWithLifecycle()
            val pairingTargetApp by viewModel.pairingTargetApp.collectAsStateWithLifecycle()
            val pairingFocusedIndex by viewModel.pairingFocusedIndex.collectAsStateWithLifecycle()
            val confirmPairingTrigger by viewModel.confirmPairingTrigger.collectAsStateWithLifecycle()

            val availableAndroidApps =
                remember(allApps) {
                    allApps.filter { !it.isRom && it.packageName != packageName }.sortedBy { it.label.lowercase() }
                }

            val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
            val isLibraryOpen by viewModel.isLibraryOpen.collectAsStateWithLifecycle()
            val librarySelectedTab by viewModel.librarySelectedTab.collectAsStateWithLifecycle()
            val libraryFocusedIndex by viewModel.libraryFocusedIndex.collectAsStateWithLifecycle()
            val isMainOptionsMenuExpanded by viewModel.isMainOptionsMenuExpanded.collectAsStateWithLifecycle()
            val mainMenuSelectedIndex by viewModel.mainMenuSelectedIndex.collectAsStateWithLifecycle()
            val isOptionsMenuExpanded by viewModel.isOptionsMenuExpanded.collectAsStateWithLifecycle()
            val artworkMenuSelectedIndex by viewModel.artworkMenuSelectedIndex.collectAsStateWithLifecycle()
            val isLibraryOptionsMenuExpanded by viewModel.isLibraryOptionsMenuExpanded.collectAsStateWithLifecycle()
            val libraryMenuSelectedIndex by viewModel.libraryMenuSelectedIndex.collectAsStateWithLifecycle()
            val newlyAddedFolder by viewModel.newlyAddedFolder.collectAsStateWithLifecycle()
            val isRemoveRomFolderDialogOpen by viewModel.isRemoveRomFolderDialogOpen.collectAsStateWithLifecycle()
            val removeRomFolderDialogSelectedIndex by viewModel.removeRomFolderDialogSelectedIndex.collectAsStateWithLifecycle()
            val folderToRemove by viewModel.folderToRemove.collectAsStateWithLifecycle()
            val coreChooserDialogSelectedIndex by viewModel.coreChooserDialogSelectedIndex.collectAsStateWithLifecycle()
            val confirmCoreChooserTrigger by viewModel.confirmCoreChooserTrigger.collectAsStateWithLifecycle()
            val editingAppInfo by viewModel.editingAppInfo.collectAsStateWithLifecycle()
            val dialogVirtualIndex by viewModel.dialogVirtualIndex.collectAsStateWithLifecycle()
            val confirmDialogTrigger by viewModel.confirmDialogTrigger.collectAsStateWithLifecycle()
            val dialogL1Trigger by viewModel.dialogL1Trigger.collectAsStateWithLifecycle()
            val dialogR1Trigger by viewModel.dialogR1Trigger.collectAsStateWithLifecycle()
            val prevLetterTrigger by viewModel.prevLetterTrigger.collectAsStateWithLifecycle()
            val nextLetterTrigger by viewModel.nextLetterTrigger.collectAsStateWithLifecycle()
            val dpadUpOptionsTrigger by viewModel.dpadUpOptionsTrigger.collectAsStateWithLifecycle()
            val dpadRightOptionsTrigger by viewModel.dpadRightOptionsTrigger.collectAsStateWithLifecycle()
            val dpadLeftTrigger by viewModel.dpadLeftTrigger.collectAsStateWithLifecycle()
            val dpadStepRightTrigger by viewModel.dpadStepRightTrigger.collectAsStateWithLifecycle()
            val focusedApp by viewModel.focusedApp.collectAsStateWithLifecycle()
            val isStarted by viewModel.isStarted.collectAsStateWithLifecycle()

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

            SideEffect {
                if (selectedCategory !in categories) {
                    viewModel.setSelectedCategory(GameFocusCategory.LAST_USED)
                }
                if (librarySelectedTab !in libraryTabs) {
                    viewModel.setLibrarySelectedTab(LibraryTab.GAMES)
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
                        viewModel.setSelectedCategory(startupCat)
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
                            isLibraryOpen,
                            focusedApp,
                            libraryFocusedIndex,
                            librarySelectedTab,
                            allApps,
                        ) {
                            if (isLibraryOpen) {
                                val activeApps = librarySelectedTab.filterApps(allApps)
                                activeApps.getOrNull(libraryFocusedIndex.coerceAtLeast(0))
                            } else {
                                focusedApp
                            }
                        }

                    LaunchedEffect(currentHoveredApp, isStarted, themeUpdateTrigger) {
                        if (isStarted && launchedTopScreenPackage == null) {
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
                                launchGalleryTopApp(appInfo)
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
                                GameFocusSessionTracker.recordBottomLaunch(appInfo)
                                lifecycleScope.launch {
                                    InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, appInfo)
                                }
                            },
                            selectedCategory = selectedCategory,
                            categories = categories,
                            onCategoryUp = { viewModel.cycleCategoryUp(categories) },
                            onCategoryDown = { viewModel.cycleCategoryDown(categories) },
                            favoritesSet = favorites,
                            hiddenSet = hidden,
                            isMainOptionsMenuExpanded = isMainOptionsMenuExpanded,
                            onMainOptionsMenuExpandedChange = { viewModel.setMainOptionsMenuExpanded(it) },
                            mainMenuSelectedIndex = mainMenuSelectedIndex,
                            onToggleFavorite = { appInfo ->
                                InstalledAppsManager.toggleFavorite(this, appInfo.packageName)
                            },
                            onToggleHidden = { appInfo ->
                                InstalledAppsManager.toggleHidden(this, appInfo.packageName)
                            },
                            onEditArtwork = { appInfo -> viewModel.openArtworkDialog(appInfo) },
                            onOpenAppInfo = { appInfo ->
                                InstalledAppsManager.openAppInfo(this, appInfo.packageName)
                            },
                            onUninstallApp = { appInfo ->
                                InstalledAppsManager.uninstallApp(this, appInfo.packageName)
                            },
                            onChangeCore = { folder ->
                                openCoreChooserForFolder(folder)
                            },
                            onAddRomFolder = { openDocumentTreeLauncher.launch(null) },
                            onRemoveRomFolder = { folder -> RomManager.removeRomFolder(this, folder) },
                            editingAppInfo = editingAppInfo,
                            dialogVirtualIndex = dialogVirtualIndex,
                            onDialogVirtualIndexChange = { viewModel.setDialogVirtualIndex(it) },
                            confirmDialogTrigger = confirmDialogTrigger,
                            dialogL1Trigger = dialogL1Trigger,
                            dialogR1Trigger = dialogR1Trigger,
                            prevLetterTrigger = prevLetterTrigger,
                            nextLetterTrigger = nextLetterTrigger,
                            isOptionsMenuExpanded = isOptionsMenuExpanded,
                            onOptionsMenuExpandedChange = { viewModel.setOptionsMenuExpanded(it) },
                            artworkMenuSelectedIndex = artworkMenuSelectedIndex,
                            dpadUpTrigger = dpadUpOptionsTrigger,
                            dpadRightTrigger = dpadRightOptionsTrigger,
                            dpadLeftTrigger = dpadLeftTrigger,
                            dpadStepRightTrigger = dpadStepRightTrigger,
                            onFocusedAppChanged = { viewModel.setFocusedApp(it) },
                            onDismissEditingApp = { viewModel.setEditingAppInfo(null) },
                            newlyAddedFolder = newlyAddedFolder,
                            onDismissNewlyAddedFolder = { viewModel.setNewlyAddedFolder(null) },
                            onConfirmNewlyAddedFolderCore = { folder, coreOrPackage ->
                                if (folder.systemId == SYSTEM_ID_SWITCH) {
                                    RomManager.updateRomFolderEmulatorPackage(this, folder.uriString, coreOrPackage)
                                } else {
                                    RomManager.updateRomFolderCore(this, folder.uriString, coreOrPackage)
                                }
                                viewModel.setNewlyAddedFolder(null)
                            },
                            coreChooserDialogSelectedIndex = coreChooserDialogSelectedIndex,
                            onCoreChooserDialogSelectedIndexChange = { viewModel.setCoreChooserDialogSelectedIndex(it) },
                            confirmCoreChooserTrigger = confirmCoreChooserTrigger,
                            isRemoveRomFolderDialogOpen = isRemoveRomFolderDialogOpen,
                            onRemoveRomFolderDialogOpenChange = { viewModel.setRemoveRomFolderDialogOpen(it) },
                            removeRomFolderDialogSelectedIndex = removeRomFolderDialogSelectedIndex,
                            onRemoveRomFolderDialogSelectedIndexChange = { viewModel.setRemoveRomFolderDialogSelectedIndex(it) },
                            folderToRemove = folderToRemove,
                            onFolderToRemoveChange = { viewModel.setFolderToRemove(it) },
                            allApps = allApps,
                            lastUsed = lastUsed,
                            isLibraryOpen = isLibraryOpen,
                            librarySelectedTab = librarySelectedTab,
                            onLibraryTabSelected = { viewModel.setLibrarySelectedTab(it) },
                            libraryFocusedIndex = libraryFocusedIndex,
                            onLibraryFocusedIndexChange = { viewModel.setLibraryFocusedIndex(it) },
                            isLibraryOptionsMenuExpanded = isLibraryOptionsMenuExpanded,
                            onLibraryOptionsMenuExpandedChange = { viewModel.setLibraryOptionsMenuExpanded(it) },
                            libraryMenuSelectedIndex = libraryMenuSelectedIndex,
                            onOpenLibrary = { viewModel.setLibraryOpen(true) },
                            onCloseLibrary = { viewModel.setLibraryOpen(false) },
                            pairedApps = pairedApps,
                            availableAndroidApps = availableAndroidApps,
                            onOpenPairingDialog = { appInfo -> openPairingDialogForApp(appInfo) },
                            onRemovePair = { appInfo ->
                                GameFocusPairManager.removePair(this@FocusTopLauncherActivity, appInfo.packageName)
                            },
                            pairingTargetApp = pairingTargetApp,
                            pairingFocusedIndex = pairingFocusedIndex,
                            onPairingFocusedIndexChange = { viewModel.setPairingFocusedIndex(it) },
                            confirmPairingTrigger = confirmPairingTrigger,
                            onDismissPairing = { viewModel.dismissPairingDialog() },
                            onConfirmPairApp = { topApp, bottomApp ->
                                AppLog.i(TAG, "Pairing app ${topApp.label} with ${bottomApp.label}")
                                GameFocusPairManager.setPair(this@FocusTopLauncherActivity, topApp.packageName, bottomApp.packageName)
                                viewModel.dismissPairingDialog()
                            },
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
        GameFocusDefaultLauncherManager.handleHomeNavigation(this)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setStarted(true)
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "FocusTopLauncherActivity resumed, refreshing installed apps")
        InstalledAppsManager.loadInstalledApps(this)
        launchedTopScreenPackage = null
        viewModel.setResumed(true)
    }

    override fun onPause() {
        super.onPause()
        stopRepeat()
        viewModel.setResumed(false)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setStarted(false)
        if (launchedTopScreenPackage == null) {
            MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
    }

    private fun launchGalleryTopApp(appInfo: InstalledAppInfo) {
        AppLog.i(TAG, "Launching app from top launcher on top display: ${appInfo.label}")
        launchedTopScreenPackage = appInfo.packageName
        GameFocusSessionTracker.recordTopLaunch(appInfo)
        MegingiardSettingsClient.updateClientState(
            this,
            isActive = true,
            focusedPackage = appInfo.packageName,
        )
        lifecycleScope.launch {
            InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, appInfo)
            val pairedPackage = GameFocusPairManager.getPairedPackage(appInfo.packageName)
            if (pairedPackage != null) {
                val allApps = InstalledAppsManager.installedApps.value
                val pairedApp = allApps.find { it.packageName == pairedPackage && !it.isRom }
                if (pairedApp != null) {
                    AppLog.i(TAG, "Simultaneously launching paired companion app on bottom display: ${pairedApp.label}")
                    GameFocusSessionTracker.recordBottomLaunch(pairedApp)
                    InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, pairedApp)
                } else {
                    AppLog.w(TAG, "Paired package '$pairedPackage' not found among installed apps; skipped secondary launch")
                }
            }
        }
    }

    private fun openPairingDialogForApp(targetApp: InstalledAppInfo) {
        val allApps = InstalledAppsManager.installedApps.value
        val availableApps = allApps.filter { !it.isRom && it.packageName != packageName }.sortedBy { it.label.lowercase() }
        val currentPaired = GameFocusPairManager.getPairedPackage(targetApp.packageName)
        val initialIndex =
            if (currentPaired != null) {
                availableApps.indexOfFirst { it.packageName == currentPaired }.coerceAtLeast(0)
            } else {
                0
            }
        viewModel.openPairingDialog(targetApp, initialIndex)
    }

    private fun isCompanionApp(packageName: String): Boolean =
        packageName.startsWith("com.stormpanda.megingiard") && !packageName.contains("gamefocus")

    private fun resetToGallery(): Boolean {
        stopRepeat()
        return viewModel.resetToGallery()
    }

    private fun stepLibraryFocus(direction: LauncherScrollDirection) {
        val allApps = InstalledAppsManager.installedApps.value
        val currentTab = viewModel.librarySelectedTab.value
        val filteredApps = currentTab.filterApps(allApps)
        viewModel.stepLibraryFocus(direction, filteredApps.size, DEFAULT_LIBRARY_GRID_COLUMNS)
    }

    private fun openCoreChooserForFolder(folder: CustomRomFolder) {
        if (folder.systemId == SYSTEM_ID_SWITCH) {
            val emulators = SwitchEmulators.getInstalledEmulators(this, forceRefresh = true)
            val selectedIdx = emulators.indexOfFirst { it.packageName == folder.emulatorPackage }.coerceAtLeast(0)
            viewModel.setCoreChooserDialogSelectedIndex(selectedIdx)
            viewModel.setNewlyAddedFolder(folder)
        } else {
            val systemDef = SUPPORTED_SYSTEMS.find { it.id == folder.systemId }
            val cores = listOf(null) + (systemDef?.retroArchCoreAlternatives ?: emptyList())
            val selectedIdx = cores.indexOf(folder.retroArchCore).coerceAtLeast(0)
            viewModel.setCoreChooserDialogSelectedIndex(selectedIdx)
            viewModel.setNewlyAddedFolder(folder)
        }
    }

    private fun stepCoreChooserFocus(direction: LauncherScrollDirection) {
        val folder = viewModel.newlyAddedFolder.value ?: return
        val count =
            if (folder.systemId == SYSTEM_ID_SWITCH) {
                SwitchEmulators.getInstalledEmulators(this).size
            } else {
                val systemDef = SUPPORTED_SYSTEMS.find { it.id == folder.systemId }
                val hasCores = systemDef != null && systemDef.emulatorId == EMULATOR_ID_RETROARCH
                if (hasCores) {
                    1 + (systemDef?.retroArchCoreAlternatives?.size ?: 0)
                } else {
                    0
                }
            }
        viewModel.stepCoreChooserFocus(direction, count)
    }

    private fun stepRemoveRomFolderFocus(direction: LauncherScrollDirection) {
        val folders = RomManager.romFolders.value
        viewModel.stepRemoveRomFolderFocus(direction, folders.size)
    }

    private fun stepDirectionalAction(direction: LauncherScrollDirection) {
        if (viewModel.editingAppInfo.value != null && viewModel.isOptionsMenuExpanded.value) {
            when (direction) {
                LauncherScrollDirection.UP -> viewModel.navigateArtworkMenuUp(ARTWORK_MENU_ITEMS_COUNT)
                LauncherScrollDirection.DOWN -> viewModel.navigateArtworkMenuDown(ARTWORK_MENU_ITEMS_COUNT)
                else -> Unit
            }
            return
        }

        if (viewModel.isLibraryOptionsMenuExpanded.value) {
            val allApps = InstalledAppsManager.installedApps.value
            val currentTab = viewModel.librarySelectedTab.value
            val filteredApps = currentTab.filterApps(allApps)
            val focusedLibraryApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))
            val romFolders = RomManager.romFolders.value
            val currentSystemId = (currentTab as? LibraryTab.RomSystem)?.systemId ?: focusedLibraryApp?.systemId
            val currentRomFolder = if (currentSystemId != null) romFolders.find { it.systemId == currentSystemId } else null
            val isRetroArchRomSystem =
                currentRomFolder != null &&
                    SUPPORTED_SYSTEMS.find { it.id == currentRomFolder.systemId }?.emulatorId == EMULATOR_ID_RETROARCH
            val isSwitchRomSystem = currentRomFolder?.systemId == SYSTEM_ID_SWITCH
            val isConfigurableRomSystem = isRetroArchRomSystem || isSwitchRomSystem
            val count =
                getLibraryMenuCount(
                    hasApp = focusedLibraryApp != null,
                    isRom = focusedLibraryApp?.isRom == true,
                    hasRomFolders = romFolders.isNotEmpty(),
                    isConfigurableRomSystem = isConfigurableRomSystem,
                )
            when (direction) {
                LauncherScrollDirection.UP -> viewModel.navigateLibraryMenuUp(count)
                LauncherScrollDirection.DOWN -> viewModel.navigateLibraryMenuDown(count)
                else -> Unit
            }
            return
        }

        if (viewModel.isMainOptionsMenuExpanded.value) {
            val targetApp = viewModel.focusedApp.value
            val isPaired = targetApp != null && GameFocusPairManager.pairedApps.value.containsKey(targetApp.packageName)
            val count = getMainMenuCount(isPaired)
            when (direction) {
                LauncherScrollDirection.UP -> viewModel.navigateMainMenuUp(count)
                LauncherScrollDirection.DOWN -> viewModel.navigateMainMenuDown(count)
                else -> Unit
            }
            return
        }

        if (viewModel.pairingTargetApp.value != null) {
            val allApps = InstalledAppsManager.installedApps.value
            val availableApps = allApps.filter { !it.isRom && it.packageName != packageName }
            viewModel.stepPairingFocus(direction, availableApps.size, DEFAULT_PAIRING_GRID_COLUMNS)
            return
        }

        if (viewModel.newlyAddedFolder.value != null) {
            stepCoreChooserFocus(direction)
            return
        }
        if (viewModel.isRemoveRomFolderDialogOpen.value) {
            stepRemoveRomFolderFocus(direction)
            return
        }
        if (viewModel.isLibraryOpen.value) {
            stepLibraryFocus(direction)
            return
        }
        if (viewModel.editingAppInfo.value != null) {
            when (direction) {
                LauncherScrollDirection.LEFT -> viewModel.stepArtworkDialogVirtualIndex(-1)
                LauncherScrollDirection.RIGHT -> viewModel.stepArtworkDialogVirtualIndex(1)
                else -> Unit
            }
            return
        }

        when (direction) {
            LauncherScrollDirection.LEFT -> {
                viewModel.triggerDpadLeft()
                AppLog.d(TAG, "dpadLeftTrigger = ${viewModel.dpadLeftTrigger.value}")
            }

            LauncherScrollDirection.RIGHT -> {
                viewModel.triggerDpadStepRight()
                AppLog.d(TAG, "dpadStepRightTrigger = ${viewModel.dpadStepRightTrigger.value}")
            }

            LauncherScrollDirection.UP -> {
                viewModel.cycleCategoryUp(activeCategories)
            }

            LauncherScrollDirection.DOWN -> {
                viewModel.cycleCategoryDown(activeCategories)
            }

            LauncherScrollDirection.NONE -> {
                Unit
            }
        }
    }

    private fun startRepeat(direction: LauncherScrollDirection) {
        if (currentDirection == direction) return

        currentDirection = direction
        repeatJob?.cancel()

        if (direction == LauncherScrollDirection.NONE) return

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
        currentDirection = LauncherScrollDirection.NONE
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
            GameFocusDefaultLauncherManager.handleHomeNavigation(this)
            return true
        }

        if (viewModel.newlyAddedFolder.value != null) {
            return when {
                isUpKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Confirming core chooser dialog via gamepad A")
                    viewModel.triggerConfirmCoreChooser()
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Dismissing core chooser dialog via gamepad B")
                    viewModel.setNewlyAddedFolder(null)
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (viewModel.folderToRemove.value != null) {
            val folder = viewModel.folderToRemove.value!!
            return when {
                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Confirming removal of ROM folder via gamepad A: ${folder.systemName}")
                    RomManager.removeRomFolder(this, folder)
                    viewModel.setFolderToRemove(null)
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Cancelling folder removal via gamepad B")
                    viewModel.setFolderToRemove(null)
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (viewModel.isRemoveRomFolderDialogOpen.value) {
            val folders = RomManager.romFolders.value
            return when {
                isUpKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    if (folders.isNotEmpty()) {
                        val selectedFolder = folders.getOrNull(viewModel.removeRomFolderDialogSelectedIndex.value)
                        if (selectedFolder != null) {
                            AppLog.i(TAG, "Selected folder to remove via gamepad A: ${selectedFolder.systemName}")
                            viewModel.setFolderToRemove(selectedFolder)
                        }
                    }
                    viewModel.setRemoveRomFolderDialogOpen(false)
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Remove ROM Folder dialog via gamepad B")
                    viewModel.setRemoveRomFolderDialogOpen(false)
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (viewModel.pairingTargetApp.value != null) {
            val targetApp = viewModel.pairingTargetApp.value ?: return true
            val allApps = InstalledAppsManager.installedApps.value
            val availableApps = allApps.filter { !it.isRom && it.packageName != packageName }.sortedBy { it.label.lowercase() }

            return when {
                isUpKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.DOWN)
                    true
                }

                isLeftKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.LEFT)
                    true
                }

                isRightKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    true
                }

                isConfirmKey(keyCode) -> {
                    val focusedIdx = viewModel.pairingFocusedIndex.value
                    val chosenApp = availableApps.getOrNull(focusedIdx)
                    if (chosenApp != null) {
                        AppLog.i(TAG, "Confirming app pairing via Gamepad A: ${targetApp.label} -> ${chosenApp.label}")
                        GameFocusPairManager.setPair(this, targetApp.packageName, chosenApp.packageName)
                    }
                    viewModel.dismissPairingDialog()
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing pairing dialog via Gamepad B")
                    viewModel.dismissPairingDialog()
                    true
                }

                else -> {
                    true
                }
            }
        }

        if (viewModel.editingAppInfo.value != null) {
            // Strict Input Isolation: Traps all inputs while modal artwork dialog is open
            if (viewModel.isOptionsMenuExpanded.value) {
                return when {
                    isUpKey(keyCode) -> {
                        startRepeat(LauncherScrollDirection.UP)
                        true
                    }

                    isDownKey(keyCode) -> {
                        startRepeat(LauncherScrollDirection.DOWN)
                        true
                    }

                    isConfirmKey(keyCode) -> {
                        val selectedIndex = viewModel.artworkMenuSelectedIndex.value
                        AppLog.i(TAG, "Artwork options menu confirmed at index $selectedIndex via gamepad A")
                        when (selectedIndex) {
                            0 -> viewModel.triggerDpadUpOptions()
                            1 -> viewModel.triggerDpadRightOptions()
                        }
                        viewModel.setOptionsMenuExpanded(false)
                        true
                    }

                    isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                        AppLog.i(TAG, "Closing options menu")
                        viewModel.setOptionsMenuExpanded(false)
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
                    viewModel.setOptionsMenuExpanded(true)
                    true
                }

                isLeftKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.LEFT)
                    true
                }

                isRightKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    true
                }

                keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                    AppLog.i(TAG, "Gamepad L1 pressed inside artwork dialog")
                    viewModel.triggerDialogL1()
                    true
                }

                keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                    AppLog.i(TAG, "Gamepad R1 pressed inside artwork dialog")
                    viewModel.triggerDialogR1()
                    true
                }

                isConfirmKey(keyCode) -> {
                    AppLog.i(TAG, "Gamepad select key pressed inside artwork dialog")
                    viewModel.triggerConfirmDialog()
                    true
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Gamepad back key pressed, closing artwork dialog")
                    viewModel.setEditingAppInfo(null)
                    true
                }

                else -> {
                    true
                }
            }
        }

        // Library Navigation Mode
        if (viewModel.isLibraryOpen.value) {
            val allApps = InstalledAppsManager.installedApps.value
            val currentTab = viewModel.librarySelectedTab.value
            val filteredApps = currentTab.filterApps(allApps)
            val focusedLibraryApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))

            if (viewModel.isLibraryOptionsMenuExpanded.value) {
                return when {
                    isUpKey(keyCode) -> {
                        startRepeat(LauncherScrollDirection.UP)
                        true
                    }

                    isDownKey(keyCode) -> {
                        startRepeat(LauncherScrollDirection.DOWN)
                        true
                    }

                    isConfirmKey(keyCode) -> {
                        val selectedIndex = viewModel.libraryMenuSelectedIndex.value
                        AppLog.i(TAG, "Library options menu confirmed at index $selectedIndex via gamepad A")
                        val hasApp = focusedLibraryApp != null
                        val isRom = focusedLibraryApp?.isRom == true
                        val romFolders = RomManager.romFolders.value
                        val hasRomFolders = romFolders.isNotEmpty()
                        val currentSystemId = (currentTab as? LibraryTab.RomSystem)?.systemId ?: focusedLibraryApp?.systemId
                        val currentRomFolder = if (currentSystemId != null) romFolders.find { it.systemId == currentSystemId } else null
                        val isRetroArchRomSystem =
                            currentRomFolder != null &&
                                SUPPORTED_SYSTEMS.find { it.id == currentRomFolder.systemId }?.emulatorId == EMULATOR_ID_RETROARCH
                        val isSwitchRomSystem = currentRomFolder?.systemId == SYSTEM_ID_SWITCH
                        val isConfigurableRomSystem = isRetroArchRomSystem || isSwitchRomSystem

                        val baseIndex = if (hasApp) (if (!isRom) 3 else 1) else 0
                        if (hasApp && selectedIndex < baseIndex) {
                            if (!isRom) {
                                when (selectedIndex) {
                                    0 -> {
                                        AppLog.i(TAG, "Toggling hidden state for ${focusedLibraryApp.label}")
                                        InstalledAppsManager.toggleHidden(this, focusedLibraryApp.packageName)
                                    }

                                    1 -> {
                                        AppLog.i(TAG, "Opening App Info for ${focusedLibraryApp.label}")
                                        InstalledAppsManager.openAppInfo(this, focusedLibraryApp.packageName)
                                    }

                                    2 -> {
                                        AppLog.i(TAG, "Uninstalling app ${focusedLibraryApp.label}")
                                        InstalledAppsManager.uninstallApp(this, focusedLibraryApp.packageName)
                                    }
                                }
                            } else {
                                AppLog.i(TAG, "Toggling hidden state for ${focusedLibraryApp.label}")
                                InstalledAppsManager.toggleHidden(this, focusedLibraryApp.packageName)
                            }
                        } else {
                            val systemIndex = selectedIndex - baseIndex
                            when (systemIndex) {
                                0 -> {
                                    AppLog.i(TAG, "Adding ROM folder via DocumentTreeLauncher")
                                    openDocumentTreeLauncher.launch(null)
                                }

                                1 -> {
                                    if (hasRomFolders) {
                                        AppLog.i(TAG, "Managing ROM folders")
                                        viewModel.setRemoveRomFolderDialogSelectedIndex(0)
                                        viewModel.setRemoveRomFolderDialogOpen(true)
                                    } else if (isConfigurableRomSystem) {
                                        currentRomFolder?.let { folder ->
                                            AppLog.i(TAG, "Changing core/emulator for folder ${folder.folderPath}")
                                            openCoreChooserForFolder(folder)
                                        }
                                    }
                                }

                                2 -> {
                                    if (hasRomFolders && isConfigurableRomSystem) {
                                        currentRomFolder?.let { folder ->
                                            AppLog.i(TAG, "Changing core/emulator for folder ${folder.folderPath}")
                                            openCoreChooserForFolder(folder)
                                        }
                                    }
                                }
                            }
                        }
                        viewModel.setLibraryOptionsMenuExpanded(false)
                        true
                    }

                    isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                        AppLog.i(TAG, "Closing Library options menu")
                        viewModel.setLibraryOptionsMenuExpanded(false)
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
                        viewModel.setLibraryOptionsMenuExpanded(true)
                    }
                    true
                }

                keyCode == GamePadButton.BUTTON_R2.keyCode || isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Library section")
                    stopRepeat()
                    viewModel.setLibraryOpen(false)
                    true
                }

                keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                    viewModel.cycleLibraryTabUp(activeLibraryTabs)
                    viewModel.setLibraryFocusedIndex(0)
                    true
                }

                keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                    viewModel.cycleLibraryTabDown(activeLibraryTabs)
                    viewModel.setLibraryFocusedIndex(0)
                    true
                }

                isLeftKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.LEFT)
                    true
                }

                isRightKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    true
                }

                isUpKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    val targetApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))
                    if (targetApp != null) {
                        AppLog.i(TAG, "Library launch on top display: ${targetApp.label}")
                        launchedTopScreenPackage = targetApp.packageName
                        GameFocusSessionTracker.recordTopLaunch(targetApp)
                        MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                        lifecycleScope.launch {
                            InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, targetApp)
                        }
                    }
                    true
                }

                keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                    if (viewModel.libraryFocusedIndex.value >= 0) {
                        val targetApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value)
                        if (targetApp != null) {
                            AppLog.i(TAG, "Library launch on bottom display: ${targetApp.label}")
                            if (!isCompanionApp(targetApp.packageName)) {
                                MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                            }
                            GameFocusSessionTracker.recordBottomLaunch(targetApp)
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
        val selectedCategory = viewModel.selectedCategory.value
        val apps = selectedCategory.filterApps(allApps, favorites, hidden, lastUsed)

        if (viewModel.isMainOptionsMenuExpanded.value) {
            val targetApp = viewModel.focusedApp.value
            return when {
                isUpKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.UP)
                    true
                }

                isDownKey(keyCode) -> {
                    startRepeat(LauncherScrollDirection.DOWN)
                    true
                }

                isConfirmKey(keyCode) -> {
                    val selectedIndex = viewModel.mainMenuSelectedIndex.value
                    AppLog.i(TAG, "Main options menu confirmed at index $selectedIndex via gamepad A for ${targetApp?.label}")
                    if (targetApp != null) {
                        when (selectedIndex) {
                            0 -> InstalledAppsManager.toggleFavorite(this, targetApp.packageName)
                            1 -> viewModel.openArtworkDialog(targetApp)
                            2 -> InstalledAppsManager.toggleHidden(this, targetApp.packageName)
                            3 -> openPairingDialogForApp(targetApp)
                            4 -> GameFocusPairManager.removePair(this, targetApp.packageName)
                        }
                    }
                    viewModel.setMainOptionsMenuExpanded(false)
                    true
                }

                isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Main options menu")
                    viewModel.setMainOptionsMenuExpanded(false)
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
                    viewModel.setMainOptionsMenuExpanded(true)
                }
                return true
            }

            isUpKey(keyCode) -> {
                startRepeat(LauncherScrollDirection.UP)
                return true
            }

            isDownKey(keyCode) -> {
                startRepeat(LauncherScrollDirection.DOWN)
                return true
            }

            isLeftKey(keyCode) -> {
                if (apps.isNotEmpty()) startRepeat(LauncherScrollDirection.LEFT)
                return true
            }

            isRightKey(keyCode) -> {
                if (apps.isNotEmpty()) startRepeat(LauncherScrollDirection.RIGHT)
                return true
            }

            isConfirmKey(keyCode) -> {
                val targetApp = viewModel.focusedApp.value
                if (targetApp != null) {
                    launchGalleryTopApp(targetApp)
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                val targetApp = viewModel.focusedApp.value
                if (targetApp != null) {
                    AppLog.i(TAG, "Gamepad X button pressed for: ${targetApp.label} -> Launching on Bottom Display")
                    if (!isCompanionApp(targetApp.packageName)) {
                        MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
                    }
                    GameFocusSessionTracker.recordBottomLaunch(targetApp)
                    lifecycleScope.launch {
                        InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, targetApp)
                    }
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad L1 pressed -> skipping to previous starting letter")
                    viewModel.triggerPrevLetter()
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad R1 pressed -> skipping to next starting letter")
                    viewModel.triggerNextLetter()
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R2.keyCode -> {
                AppLog.i(TAG, "Gamepad R2 pressed -> Opening Library section")
                viewModel.setLibraryOpen(true)
                viewModel.setLibraryFocusedIndex(0)
                return true
            }

            isDismissKey(keyCode) -> {
                AppLog.i(TAG, "Dismiss/Back key pressed on main gallery (keyCode=$keyCode)")
                if (GameFocusSessionTracker.hasActiveSession()) {
                    AppLog.i(TAG, "Active session found -> restoring previous session on Back")
                    lifecycleScope.launch {
                        GameFocusSessionTracker.restorePreviousSession(this@FocusTopLauncherActivity)
                    }
                } else {
                    AppLog.d(TAG, "No previous session tracked -> consuming Back key to stay in gallery")
                }
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

        if (isDismissKey(keyCode)) {
            return true
        }

        if (viewModel.editingAppInfo.value != null ||
            viewModel.newlyAddedFolder.value != null ||
            viewModel.folderToRemove.value != null ||
            viewModel.isRemoveRomFolderDialogOpen.value ||
            viewModel.pairingTargetApp.value != null
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

            if (viewModel.pairingTargetApp.value != null) {
                if (x < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.LEFT)
                    return true
                } else if (x > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    return true
                } else if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.UP)
                    return true
                } else if (y > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection != LauncherScrollDirection.NONE) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (viewModel.newlyAddedFolder.value != null ||
                viewModel.isRemoveRomFolderDialogOpen.value
            ) {
                if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.UP)
                    return true
                } else if (y > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection == LauncherScrollDirection.UP || currentDirection == LauncherScrollDirection.DOWN) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (viewModel.editingAppInfo.value != null ||
                viewModel.folderToRemove.value != null
            ) {
                if (viewModel.editingAppInfo.value != null && viewModel.isOptionsMenuExpanded.value) {
                    if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                        startRepeat(LauncherScrollDirection.UP)
                        return true
                    } else if (y > JOYSTICK_THRESHOLD) {
                        startRepeat(LauncherScrollDirection.DOWN)
                        return true
                    } else {
                        if (currentDirection == LauncherScrollDirection.UP || currentDirection == LauncherScrollDirection.DOWN) {
                            stopRepeat()
                        }
                    }
                    return true
                }

                if (x < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.LEFT)
                    return true
                } else if (x > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    return true
                } else {
                    if (currentDirection != LauncherScrollDirection.NONE) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (viewModel.isMainOptionsMenuExpanded.value) {
                if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.UP)
                    return true
                } else if (y > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection == LauncherScrollDirection.UP || currentDirection == LauncherScrollDirection.DOWN) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (viewModel.isLibraryOptionsMenuExpanded.value) {
                if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.UP)
                    return true
                } else if (y > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection == LauncherScrollDirection.UP || currentDirection == LauncherScrollDirection.DOWN) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (viewModel.isLibraryOpen.value) {
                if (x < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.LEFT)
                    return true
                } else if (x > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.RIGHT)
                    return true
                } else if (y < JOYSTICK_NEGATIVE_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.UP)
                    return true
                } else if (y > JOYSTICK_THRESHOLD) {
                    startRepeat(LauncherScrollDirection.DOWN)
                    return true
                } else {
                    if (currentDirection != LauncherScrollDirection.NONE) {
                        stopRepeat()
                    }
                }
                return true
            }

            if (x < JOYSTICK_NEGATIVE_THRESHOLD) {
                startRepeat(LauncherScrollDirection.LEFT)
                return true
            } else if (x > JOYSTICK_THRESHOLD) {
                startRepeat(LauncherScrollDirection.RIGHT)
                return true
            } else {
                if (currentDirection != LauncherScrollDirection.NONE) {
                    stopRepeat()
                }
            }
        }
        return if (viewModel.editingAppInfo.value != null ||
            viewModel.pairingTargetApp.value != null
        ) {
            true
        } else {
            super.onGenericMotionEvent(event)
        }
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
