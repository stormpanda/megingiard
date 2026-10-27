package com.stormpanda.megingiard.gamefocus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
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
import com.stormpanda.megingiard.catalog.InstalledEmulatorRegistry
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.gamefocus.domain.GameFocusDefaultLauncherManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusGalleryManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusPairManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusPreferences
import com.stormpanda.megingiard.gamefocus.domain.GameFocusSessionTracker
import com.stormpanda.megingiard.gamefocus.domain.initGameFocusLaunchers
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_LIBRARY_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_PAIRING_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.gamefocus.viewmodel.LauncherScrollDirection
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.ExpandableActionItem
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "FocusTopLauncherAct"
private const val INITIAL_REPEAT_DELAY_MS = 220L
private const val REPEAT_INTERVAL_MS = 70L
private const val JOYSTICK_THRESHOLD = 0.5f
private const val JOYSTICK_NEGATIVE_THRESHOLD = -0.5f
private const val PACKAGE_DATA_SCHEME = "package"

internal const val LIBRARY_APP_ACTIONS_COUNT = 4
internal const val LIBRARY_ROM_ACTIONS_COUNT = 2

internal fun shouldHandlePackageBroadcast(
    action: String?,
    isReplacing: Boolean,
): Boolean =
    when (action) {
        Intent.ACTION_PACKAGE_ADDED,
        Intent.ACTION_PACKAGE_REMOVED,
        -> !isReplacing

        Intent.ACTION_PACKAGE_REPLACED,
        Intent.ACTION_PACKAGE_CHANGED,
        -> true

        else -> false
    }

private fun Context?.resolveMenuString(resId: Int): String = this?.getString(resId) ?: ""

internal fun buildGalleryMenuActions(
    context: Context? = null,
    currentApp: InstalledAppInfo?,
    isFavorite: Boolean = false,
    isHidden: Boolean = false,
    isPaired: Boolean = false,
    onToggleFavorite: (InstalledAppInfo) -> Unit = {},
    onEditGame: (InstalledAppInfo) -> Unit = {},
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onOpenPairingDialog: (InstalledAppInfo) -> Unit = {},
    onRemovePair: (InstalledAppInfo) -> Unit = {},
    onOpenManageGalleries: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
): List<ExpandableActionItem> =
    buildList {
        if (currentApp != null) {
            add(
                ExpandableActionItem(
                    label =
                        if (isFavorite) {
                            context.resolveMenuString(R.string.gamefocus_option_remove_favorite)
                        } else {
                            context.resolveMenuString(R.string.gamefocus_option_add_favorite)
                        },
                    iconSymbol = "star",
                    onClick = {
                        onToggleFavorite(currentApp)
                        onDismissMenu()
                    },
                ),
            )
            add(
                ExpandableActionItem(
                    label = context.resolveMenuString(R.string.gamefocus_option_edit),
                    iconSymbol = "palette",
                    onClick = {
                        onEditGame(currentApp)
                        onDismissMenu()
                    },
                ),
            )
            add(
                ExpandableActionItem(
                    label =
                        if (isHidden) {
                            context.resolveMenuString(R.string.gamefocus_option_unhide)
                        } else {
                            context.resolveMenuString(R.string.gamefocus_option_hide)
                        },
                    iconSymbol = if (isHidden) "visibility" else "visibility_off",
                    onClick = {
                        onToggleHidden(currentApp)
                        onDismissMenu()
                    },
                ),
            )
            add(
                ExpandableActionItem(
                    label =
                        if (isPaired) {
                            context.resolveMenuString(R.string.gamefocus_option_change_pair)
                        } else {
                            context.resolveMenuString(R.string.gamefocus_option_pair_bottom)
                        },
                    iconSymbol = "splitscreen",
                    onClick = {
                        onOpenPairingDialog(currentApp)
                        onDismissMenu()
                    },
                ),
            )
            if (isPaired) {
                add(
                    ExpandableActionItem(
                        label = context.resolveMenuString(R.string.gamefocus_option_remove_pair),
                        iconSymbol = "delete",
                        isDestructive = true,
                        onClick = {
                            onRemovePair(currentApp)
                            onDismissMenu()
                        },
                    ),
                )
            }
        }
        add(
            ExpandableActionItem(
                label = context.resolveMenuString(R.string.gamefocus_manage_galleries_title),
                iconSymbol = "view_carousel",
                onClick = {
                    onOpenManageGalleries()
                    onDismissMenu()
                },
            ),
        )
    }

internal fun buildLibraryMenuActions(
    context: Context? = null,
    focusedApp: InstalledAppInfo?,
    isHidden: Boolean = false,
    selectedTab: LibraryTab = LibraryTab.GAMES,
    romFolders: List<CustomRomFolder> = emptyList(),
    onEditArtwork: (InstalledAppInfo) -> Unit = {},
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onUninstallApp: (InstalledAppInfo) -> Unit = {},
    onAddRomFolder: () -> Unit = {},
    onManageRomFolders: () -> Unit = {},
    onChangeCore: (CustomRomFolder) -> Unit = {},
    onOpenManageGalleries: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
): List<ExpandableActionItem> {
    val currentSystemId = (selectedTab as? LibraryTab.RomSystem)?.systemId ?: focusedApp?.systemId
    val currentRomFolder = if (currentSystemId != null) romFolders.find { it.systemId == currentSystemId } else null
    val isRetroArchSystem =
        currentRomFolder != null &&
            SUPPORTED_SYSTEMS.find { it.id == currentRomFolder.systemId }?.emulatorId == EMULATOR_ID_RETROARCH
    val isMultiEmulatorSystem = InstalledEmulatorRegistry.isMultiEmulatorSystem(currentRomFolder?.systemId)

    return buildList {
        if (focusedApp != null) {
            add(
                ExpandableActionItem(
                    label = context.resolveMenuString(R.string.gamefocus_option_edit),
                    iconSymbol = "palette",
                    onClick = {
                        onEditArtwork(focusedApp)
                        onDismissMenu()
                    },
                ),
            )
            add(
                ExpandableActionItem(
                    label =
                        if (isHidden) {
                            context.resolveMenuString(R.string.gamefocus_option_unhide)
                        } else {
                            context.resolveMenuString(R.string.gamefocus_option_hide)
                        },
                    iconSymbol = if (isHidden) "visibility" else "visibility_off",
                    onClick = {
                        onToggleHidden(focusedApp)
                        onDismissMenu()
                    },
                ),
            )
            if (!focusedApp.isRom) {
                add(
                    ExpandableActionItem(
                        label = context.resolveMenuString(R.string.gamefocus_option_app_info),
                        iconSymbol = "info",
                        onClick = {
                            onOpenAppInfo(focusedApp)
                            onDismissMenu()
                        },
                    ),
                )
                add(
                    ExpandableActionItem(
                        label = context.resolveMenuString(R.string.gamefocus_option_uninstall),
                        iconSymbol = "delete",
                        isDestructive = true,
                        onClick = {
                            onUninstallApp(focusedApp)
                            onDismissMenu()
                        },
                    ),
                )
            }
        }
        add(
            ExpandableActionItem(
                label = context.resolveMenuString(R.string.gamefocus_option_add_rom_folder),
                iconSymbol = "create_new_folder",
                onClick = {
                    onAddRomFolder()
                    onDismissMenu()
                },
            ),
        )
        if (romFolders.isNotEmpty()) {
            add(
                ExpandableActionItem(
                    label = context.resolveMenuString(R.string.gamefocus_option_manage_rom_folders),
                    iconSymbol = "folder",
                    onClick = {
                        onManageRomFolders()
                        onDismissMenu()
                    },
                ),
            )
        }
        if (isRetroArchSystem && currentRomFolder != null) {
            add(
                ExpandableActionItem(
                    label = context.resolveMenuString(R.string.gamefocus_option_change_core),
                    iconSymbol = "tune",
                    onClick = {
                        onChangeCore(currentRomFolder)
                        onDismissMenu()
                    },
                ),
            )
        } else if (isMultiEmulatorSystem && currentRomFolder != null) {
            add(
                ExpandableActionItem(
                    label = context.resolveMenuString(R.string.gamefocus_option_change_emulator),
                    iconSymbol = "tune",
                    onClick = {
                        onChangeCore(currentRomFolder)
                        onDismissMenu()
                    },
                ),
            )
        }
        add(
            ExpandableActionItem(
                label = context.resolveMenuString(R.string.gamefocus_manage_galleries_title),
                iconSymbol = "view_carousel",
                onClick = {
                    onOpenManageGalleries()
                    onDismissMenu()
                },
            ),
        )
    }
}

class FocusTopLauncherActivity : ComponentActivity() {
    private val viewModel: FocusTopLauncherViewModel by viewModels()

    private var activeLibraryTabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS)
    private var currentDirection = LauncherScrollDirection.NONE
    private var repeatJob: Job? = null

    private var activeCategories: List<GameFocusCategory> = GameFocusCategory.builtIns
    private var launchedTopScreenPackage: String? = null
    private var isPackageReceiverRegistered = false

    private val packageChangeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val action = intent.action
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!shouldHandlePackageBroadcast(action, isReplacing)) return
                val pkg = intent.data?.schemeSpecificPart
                AppLog.i(TAG, "Package broadcast handled: action=$action, package=$pkg, isReplacing=$isReplacing")
                if (!pkg.isNullOrBlank()) {
                    FocusImageCache.invalidateIcon(pkg)
                    AppPaletteExtractor.invalidatePalette(pkg)
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!pkg.isNullOrBlank()) {
                        FocusImageCache.deleteDiskCachedIcon(this@FocusTopLauncherActivity, pkg)
                    }
                    InstalledAppsManager.loadInstalledApps(this@FocusTopLauncherActivity)
                }
            }
        }

    private val openDocumentTreeLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    when (val result = RomManager.addRomFolder(this@FocusTopLauncherActivity, uri)) {
                        is AddRomFolderResult.Success -> {
                            val registry = InstalledEmulatorRegistry.forSystem(result.folder.systemId)
                            if (registry != null) {
                                val emulators =
                                    registry.getInstalledEmulators(this@FocusTopLauncherActivity, forceRefresh = true)
                                if (emulators.size > 1) {
                                    openCoreChooserForFolder(result.folder)
                                }
                            } else {
                                val systemDef = SUPPORTED_SYSTEMS.find { it.id == result.folder.systemId }
                                if (systemDef?.emulatorId == EMULATOR_ID_RETROARCH) {
                                    openCoreChooserForFolder(result.folder)
                                }
                            }
                        }

                        is AddRomFolderResult.Error -> {
                            val displayMessage =
                                when (result.errorCode) {
                                    AddRomFolderResult.ErrorCode.NO_SWITCH_EMULATOR -> {
                                        getString(R.string.gamefocus_error_no_switch_emulator)
                                    }

                                    AddRomFolderResult.ErrorCode.NO_PC_EMULATOR -> {
                                        getString(R.string.gamefocus_error_no_pc_emulator)
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
        AppPaletteExtractor.init(applicationContext)
        GameFocusPairManager.loadPairs(this)
        GameFocusGalleryManager.load(this)
        viewModel.setButtonPromptsVisible(GameFocusPreferences.areButtonPromptsVisible(this))
        if (savedInstanceState == null && intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            GameFocusDefaultLauncherManager.handleHomeNavigation(this)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.folderToRemove.value != null) {
                        viewModel.setFolderToRemove(null)
                        return
                    }
                    if (viewModel.isManageGalleriesOpen.value) {
                        AppLog.i(TAG, "Back pressed -> closing Manage Galleries overlay")
                        viewModel.setManageGalleriesOpen(false)
                        return
                    }
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

            val allApps by InstalledAppsManager.installedApps.collectAsStateWithLifecycle()
            val favorites by InstalledAppsManager.favorites.collectAsStateWithLifecycle()
            val hidden by InstalledAppsManager.hiddenApps.collectAsStateWithLifecycle()
            val lastUsed by InstalledAppsManager.lastUsed.collectAsStateWithLifecycle()
            val customRomFolders by RomManager.romFolders.collectAsStateWithLifecycle()
            val pairedApps by GameFocusPairManager.pairedApps.collectAsStateWithLifecycle()
            val galleryConfig by GameFocusGalleryManager.config.collectAsStateWithLifecycle()
            val collectionMembers =
                remember(galleryConfig.collections) {
                    galleryConfig.collections.associate { collection -> collection.id to collection.members.toSet() }
                }
            val availableAndroidApps =
                remember(allApps) {
                    getAvailableAndroidApps(allApps)
                }

            val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
            val isLibraryOpen by viewModel.isLibraryOpen.collectAsStateWithLifecycle()
            val librarySelectedTab by viewModel.librarySelectedTab.collectAsStateWithLifecycle()
            val libraryFocusedIndex by viewModel.libraryFocusedIndex.collectAsStateWithLifecycle()
            val focusedApp by viewModel.focusedApp.collectAsStateWithLifecycle()
            val isStarted by viewModel.isStarted.collectAsStateWithLifecycle()

            val allGalleryCategories =
                remember(customRomFolders, galleryConfig.collections, galleryConfig.order) {
                    GameFocusCategory.buildOrderedGalleries(customRomFolders, galleryConfig.collections, galleryConfig.order)
                }
            val categories =
                remember(allGalleryCategories, galleryConfig.hidden) {
                    GameFocusCategory.visibleGalleries(allGalleryCategories, galleryConfig.hidden)
                }

            val libraryTabs =
                remember(customRomFolders) {
                    LibraryTab.buildTabs(customRomFolders)
                }

            SideEffect {
                activeCategories = categories
                activeLibraryTabs = libraryTabs
            }

            SideEffect {
                if (selectedCategory !in categories) {
                    val fallbackCategory = categories.first()
                    AppLog.i(TAG, "Selected gallery ${selectedCategory.id} is not visible -> falling back to ${fallbackCategory.id}")
                    viewModel.setSelectedCategory(fallbackCategory)
                }
                if (librarySelectedTab !in libraryTabs) {
                    viewModel.setLibrarySelectedTab(LibraryTab.GAMES)
                }
            }

            var hasSetStartupCategory by remember { mutableStateOf(false) }
            val currentHidden = hidden

            LaunchedEffect(categories, allApps, favorites, lastUsed, currentHidden, collectionMembers) {
                if (!hasSetStartupCategory && allApps.isNotEmpty()) {
                    val startupCat =
                        categories.firstOrNull { cat ->
                            cat.filterApps(allApps, favorites, currentHidden, lastUsed, collectionMembers).isNotEmpty()
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

            MaterialTheme(
                colorScheme = colorSchemeFor(appColors),
                typography = megingiardTypography,
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
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

                    LaunchedEffect(currentHoveredApp, isStarted, remoteThemeState) {
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
                            viewModel = viewModel,
                            allApps = allApps,
                            favoritesSet = favorites,
                            hiddenSet = frozenHidden,
                            lastUsed = lastUsed,
                            collectionMembers = collectionMembers,
                            categories = categories,
                            allGalleryCategories = allGalleryCategories,
                            libraryTabs = libraryTabs,
                            galleryConfig = galleryConfig,
                            pairedApps = pairedApps,
                            availableAndroidApps = availableAndroidApps,
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
                            onToggleFavorite = { appInfo ->
                                InstalledAppsManager.toggleFavorite(this, appInfo.packageName)
                            },
                            onToggleHidden = { appInfo ->
                                InstalledAppsManager.toggleHidden(this, appInfo.packageName)
                            },
                            onOpenAppInfo = { appInfo ->
                                InstalledAppsManager.openAppInfo(this, appInfo.packageName, display.displayId)
                            },
                            onUninstallApp = { appInfo ->
                                InstalledAppsManager.uninstallApp(this, appInfo.packageName, display.displayId)
                            },
                            onChangeCore = { folder ->
                                openCoreChooserForFolder(folder)
                            },
                            onAddRomFolder = { openDocumentTreeLauncher.launch(null) },
                            onRemoveRomFolder = { folder -> RomManager.removeRomFolder(this, folder) },
                            onConfirmNewlyAddedFolderCore = { folder, coreOrPackage ->
                                if (InstalledEmulatorRegistry.isMultiEmulatorSystem(folder.systemId)) {
                                    RomManager.updateRomFolderEmulatorPackage(this, folder.uriString, coreOrPackage)
                                } else {
                                    RomManager.updateRomFolderCore(this, folder.uriString, coreOrPackage)
                                }
                                viewModel.setNewlyAddedFolder(null)
                            },
                            onOpenPairingDialog = { appInfo -> openPairingDialogForApp(appInfo) },
                            onRemovePair = { appInfo ->
                                GameFocusPairManager.removePair(this@FocusTopLauncherActivity, appInfo.packageName)
                            },
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
        registerPackageChangeReceiver()
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "FocusTopLauncherActivity resumed, refreshing installed apps")
        InstalledAppsManager.loadInstalledApps(this)
        launchedTopScreenPackage = null
    }

    override fun onPause() {
        super.onPause()
        stopRepeat()
    }

    override fun onStop() {
        super.onStop()
        unregisterPackageChangeReceiver()
        viewModel.setStarted(false)
        if (launchedTopScreenPackage == null) {
            MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageChangeReceiver()
        MegingiardSettingsClient.updateClientState(this, isActive = false, focusedPackage = null)
    }

    private fun registerPackageChangeReceiver() {
        if (isPackageReceiverRegistered) return
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme(PACKAGE_DATA_SCHEME)
            }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        isPackageReceiverRegistered = true
        AppLog.d(TAG, "Registered package change BroadcastReceiver")
    }

    private fun unregisterPackageChangeReceiver() {
        if (!isPackageReceiverRegistered) return
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (e: IllegalArgumentException) {
            AppLog.w(TAG, "Package change BroadcastReceiver was already unregistered: ${e.message}")
        }
        isPackageReceiverRegistered = false
        AppLog.d(TAG, "Unregistered package change BroadcastReceiver")
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

    private fun getAvailableAndroidApps(allApps: List<InstalledAppInfo>): List<InstalledAppInfo> =
        allApps.filter { !it.isRom && it.packageName != packageName }.sortedBy { it.label.lowercase() }

    private fun openPairingDialogForApp(targetApp: InstalledAppInfo) {
        val availableApps = getAvailableAndroidApps(InstalledAppsManager.installedApps.value)
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
        packageName == MegingiardIpcContract.COMPANION_PACKAGE ||
            packageName == MegingiardIpcContract.COMPANION_DEBUG_PACKAGE

    private fun currentGalleryApps(): List<InstalledAppInfo> {
        val allApps = InstalledAppsManager.installedApps.value
        val favorites = InstalledAppsManager.favorites.value
        val hidden = frozenHiddenSetForInput
        val lastUsed = InstalledAppsManager.lastUsed.value
        val selectedCategory = viewModel.selectedCategory.value
        val collectionMembers =
            GameFocusGalleryManager.config.value.collections
                .associate { collection -> collection.id to collection.members.toSet() }
        return selectedCategory.filterApps(allApps, favorites, hidden, lastUsed, collectionMembers)
    }

    private fun resetToGallery(): Boolean {
        stopRepeat()
        return viewModel.resetToGallery()
    }

    /** True while a primary overlay (Edit Game or Manage Galleries) owns gamepad input. */
    private fun isPrimaryOverlayOpen(): Boolean = viewModel.editingAppInfo.value != null || viewModel.isManageGalleriesOpen.value

    private fun stepLibraryFocus(direction: LauncherScrollDirection): Boolean {
        val allApps = InstalledAppsManager.installedApps.value
        val currentTab = viewModel.librarySelectedTab.value
        val filteredApps = currentTab.filterApps(allApps)
        return viewModel.stepLibraryFocus(direction, filteredApps.size, DEFAULT_LIBRARY_GRID_COLUMNS)
    }

    private fun openCoreChooserForFolder(folder: CustomRomFolder) {
        val registry = InstalledEmulatorRegistry.forSystem(folder.systemId)
        if (registry != null) {
            val emulators = registry.getInstalledEmulators(this, forceRefresh = true)
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
        val registry = InstalledEmulatorRegistry.forSystem(folder.systemId)
        val count =
            if (registry != null) {
                registry.getInstalledEmulators(this).size
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

    private fun currentGalleryMenuActions(): List<ExpandableActionItem> {
        val targetApp = viewModel.focusedApp.value
        val isPaired = targetApp != null && GameFocusPairManager.pairedApps.value.containsKey(targetApp.packageName)
        return buildGalleryMenuActions(
            context = this,
            currentApp = targetApp,
            isFavorite = targetApp != null && InstalledAppsManager.favorites.value.contains(targetApp.packageName),
            isHidden = targetApp != null && InstalledAppsManager.hiddenApps.value.contains(targetApp.packageName),
            isPaired = isPaired,
            onToggleFavorite = { app -> InstalledAppsManager.toggleFavorite(this, app.packageName) },
            onEditGame = { app -> viewModel.openEditGameOverlay(app) },
            onToggleHidden = { app -> InstalledAppsManager.toggleHidden(this, app.packageName) },
            onOpenPairingDialog = { app -> openPairingDialogForApp(app) },
            onRemovePair = { app -> GameFocusPairManager.removePair(this, app.packageName) },
            onOpenManageGalleries = {
                AppLog.i(TAG, "Opening Manage Galleries overlay from gallery menu")
                stopRepeat()
                viewModel.setManageGalleriesOpen(true)
            },
            onDismissMenu = { viewModel.setMainOptionsMenuExpanded(false) },
        )
    }

    private fun currentLibraryMenuActions(focusedLibraryApp: InstalledAppInfo?): List<ExpandableActionItem> =
        buildLibraryMenuActions(
            context = this,
            focusedApp = focusedLibraryApp,
            isHidden = focusedLibraryApp != null && InstalledAppsManager.hiddenApps.value.contains(focusedLibraryApp.packageName),
            selectedTab = viewModel.librarySelectedTab.value,
            romFolders = RomManager.romFolders.value,
            onEditArtwork = { app -> viewModel.openEditGameOverlay(app) },
            onToggleHidden = { app ->
                AppLog.i(TAG, "Toggling hidden state for ${app.label}")
                InstalledAppsManager.toggleHidden(this, app.packageName)
            },
            onOpenAppInfo = { app ->
                AppLog.i(TAG, "Opening App Info for ${app.label}")
                InstalledAppsManager.openAppInfo(this, app.packageName, display.displayId)
            },
            onUninstallApp = { app ->
                AppLog.i(TAG, "Uninstalling app ${app.label}")
                InstalledAppsManager.uninstallApp(this, app.packageName, display.displayId)
            },
            onAddRomFolder = {
                AppLog.i(TAG, "Adding ROM folder via DocumentTreeLauncher")
                openDocumentTreeLauncher.launch(null)
            },
            onManageRomFolders = {
                AppLog.i(TAG, "Managing ROM folders")
                viewModel.setRemoveRomFolderDialogSelectedIndex(0)
                viewModel.setRemoveRomFolderDialogOpen(true)
            },
            onChangeCore = { folder ->
                AppLog.i(TAG, "Changing core/emulator for folder ${folder.folderPath}")
                openCoreChooserForFolder(folder)
            },
            onOpenManageGalleries = {
                AppLog.i(TAG, "Opening Manage Galleries overlay from Library menu")
                viewModel.setManageGalleriesOpen(true)
            },
            onDismissMenu = { viewModel.setLibraryOptionsMenuExpanded(false) },
        )

    private fun stepDirectionalAction(direction: LauncherScrollDirection) {
        if (viewModel.isLibraryOptionsMenuExpanded.value) {
            val allApps = InstalledAppsManager.installedApps.value
            val currentTab = viewModel.librarySelectedTab.value
            val filteredApps = currentTab.filterApps(allApps)
            val focusedLibraryApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))
            val count = currentLibraryMenuActions(focusedLibraryApp).size
            when (direction) {
                LauncherScrollDirection.UP -> viewModel.navigateLibraryMenuUp(count)
                LauncherScrollDirection.DOWN -> viewModel.navigateLibraryMenuDown(count)
                else -> Unit
            }
            return
        }

        if (viewModel.isMainOptionsMenuExpanded.value) {
            val count = currentGalleryMenuActions().size
            when (direction) {
                LauncherScrollDirection.UP -> viewModel.navigateMainMenuUp(count)
                LauncherScrollDirection.DOWN -> viewModel.navigateMainMenuDown(count)
                else -> Unit
            }
            return
        }

        if (viewModel.pairingTargetApp.value != null) {
            val availableApps = getAvailableAndroidApps(InstalledAppsManager.installedApps.value)
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
            val moved = stepLibraryFocus(direction)
            if (moved) {
                GameFocusHaptics.tick(this)
            }
            return
        }

        when (direction) {
            LauncherScrollDirection.LEFT -> {
                viewModel.triggerDpadLeft()
                GameFocusHaptics.tick(this)
                AppLog.d(TAG, "dpadLeftTrigger = ${viewModel.dpadLeftTrigger.value}")
            }

            LauncherScrollDirection.RIGHT -> {
                viewModel.triggerDpadStepRight()
                GameFocusHaptics.tick(this)
                AppLog.d(TAG, "dpadStepRightTrigger = ${viewModel.dpadStepRightTrigger.value}")
            }

            LauncherScrollDirection.UP -> {
                if (viewModel.cycleCategoryUp(activeCategories)) {
                    GameFocusHaptics.click(this)
                }
            }

            LauncherScrollDirection.DOWN -> {
                if (viewModel.cycleCategoryDown(activeCategories)) {
                    GameFocusHaptics.click(this)
                }
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
            val availableApps = getAvailableAndroidApps(InstalledAppsManager.installedApps.value)

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

        if (isPrimaryOverlayOpen()) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
                return true
            }
            if (isDismissKey(keyCode)) {
                AppLog.i(TAG, "onKeyDown: Back/B-Button pressed while a primary overlay is open")
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    viewModel.setEditingAppInfo(null)
                    viewModel.setManageGalleriesOpen(false)
                }
                return true
            }
            if (keyCode == GamePadButton.BUTTON_A.keyCode) {
                // Translate gamepad A into DPAD_CENTER so Compose clickables inside the overlay activate.
                val dpadCenterDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                dispatchKeyEvent(dpadCenterDown)
                return true
            }
            return super.onKeyDown(keyCode, event)
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
                        currentLibraryMenuActions(focusedLibraryApp).getOrNull(selectedIndex)?.onClick?.invoke()
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
                    AppLog.i(TAG, "Gamepad Y/Menu pressed in Library -> Opening Library options menu (focused=${focusedLibraryApp?.label})")
                    viewModel.setLibraryOptionsMenuExpanded(true)
                    true
                }

                keyCode == GamePadButton.BUTTON_R2.keyCode || isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Closing Library section")
                    stopRepeat()
                    viewModel.setLibraryOpen(false)
                    GameFocusHaptics.click(this)
                    true
                }

                keyCode == GamePadButton.BUTTON_L1.keyCode -> {
                    if (viewModel.cycleLibraryTabUp(activeLibraryTabs)) {
                        viewModel.setLibraryFocusedIndex(0)
                        GameFocusHaptics.click(this)
                    }
                    true
                }

                keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                    if (viewModel.cycleLibraryTabDown(activeLibraryTabs)) {
                        viewModel.setLibraryFocusedIndex(0)
                        GameFocusHaptics.click(this)
                    }
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

                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    val newVisible = viewModel.toggleButtonPromptsVisible()
                    GameFocusPreferences.setButtonPromptsVisible(this, newVisible)
                    AppLog.i(TAG, "Gamepad R3 pressed in Library -> toggled button prompts visibility (now visible=$newVisible)")
                    true
                }

                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    AppLog.d(TAG, "Gamepad L3 pressed in Library -> consumed to prevent synthetic DPAD_CENTER launch")
                    true
                }

                else -> {
                    true
                }
            }
        }

        // Navigation when Main Launcher is active
        val apps = currentGalleryApps()

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
                    currentGalleryMenuActions().getOrNull(selectedIndex)?.onClick?.invoke()
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
                AppLog.i(TAG, "Gamepad Y/Menu pressed in gallery -> opening gallery options menu (entries=${apps.size})")
                stopRepeat()
                viewModel.setMainOptionsMenuExpanded(true)
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
                if (apps.size > 1) {
                    startRepeat(LauncherScrollDirection.LEFT)
                } else {
                    stopRepeat()
                }
                return true
            }

            isRightKey(keyCode) -> {
                if (apps.size > 1) {
                    startRepeat(LauncherScrollDirection.RIGHT)
                } else {
                    stopRepeat()
                }
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
                    GameFocusHaptics.click(this)
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R1.keyCode -> {
                if (apps.isNotEmpty()) {
                    AppLog.i(TAG, "Gamepad R1 pressed -> skipping to next starting letter")
                    viewModel.triggerNextLetter()
                    GameFocusHaptics.click(this)
                }
                return true
            }

            keyCode == GamePadButton.BUTTON_R2.keyCode -> {
                AppLog.i(TAG, "Gamepad R2 pressed -> Opening Library section")
                viewModel.setLibraryOpen(true)
                viewModel.setLibraryFocusedIndex(0)
                GameFocusHaptics.click(this)
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

            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                val newVisible = viewModel.toggleButtonPromptsVisible()
                GameFocusPreferences.setButtonPromptsVisible(this, newVisible)
                AppLog.i(TAG, "Gamepad R3 pressed -> toggled button prompts visibility (now visible=$newVisible)")
                return true
            }

            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                AppLog.d(TAG, "Gamepad L3 pressed -> consumed to prevent synthetic DPAD_CENTER launch")
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

        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true
        }

        if (isDirectionalKey(keyCode)) {
            stopRepeat()
            return true
        }

        if (isDismissKey(keyCode)) {
            return true
        }

        if (isPrimaryOverlayOpen()) {
            if (keyCode == GamePadButton.BUTTON_A.keyCode) {
                val dpadCenterUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
                val handled = dispatchKeyEvent(dpadCenterUp)
                if (!handled) {
                    PrimaryOverlayInputBridge.sendFocusRecovery(GamePadButton.BUTTON_A.keyCode)
                }
                return true
            }
            return super.onKeyUp(keyCode, event)
        }

        if (viewModel.newlyAddedFolder.value != null ||
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

            if (isPrimaryOverlayOpen()) {
                // A primary overlay owns all joystick input; translate axes into D-pad key events for Compose focus.
                PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, dpadKeyCode ->
                    val keyEvent = KeyEvent(action, dpadKeyCode)
                    val handled = dispatchKeyEvent(keyEvent)
                    if (!handled && action == KeyEvent.ACTION_DOWN) {
                        PrimaryOverlayInputBridge.sendFocusRecovery(dpadKeyCode)
                    }
                }
                return true
            }

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

            if (viewModel.folderToRemove.value != null) {
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
                if (currentGalleryApps().size > 1) {
                    startRepeat(LauncherScrollDirection.LEFT)
                } else {
                    stopRepeat()
                }
                return true
            } else if (x > JOYSTICK_THRESHOLD) {
                if (currentGalleryApps().size > 1) {
                    startRepeat(LauncherScrollDirection.RIGHT)
                } else {
                    stopRepeat()
                }
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
        }
        return if (isPrimaryOverlayOpen() ||
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
