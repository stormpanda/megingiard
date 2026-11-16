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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
import com.stormpanda.megingiard.gamefocus.domain.GalleryConfig
import com.stormpanda.megingiard.gamefocus.domain.GameFocusDefaultLauncherManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusGalleryManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusPairManager
import com.stormpanda.megingiard.gamefocus.domain.GameFocusPreferences
import com.stormpanda.megingiard.gamefocus.domain.GameFocusSessionTracker
import com.stormpanda.megingiard.gamefocus.domain.initGameFocusLaunchers
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.gamefocus.viewmodel.LauncherScrollDirection
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.ExpandableActionItem
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "FocusTopLauncherAct"
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
        if (isRetroArchSystem) {
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

    private var cachedGalleryAllApps: List<InstalledAppInfo>? = null
    private var cachedGalleryFavorites: Set<String>? = null
    private var cachedGalleryHidden: Set<String>? = null
    private var cachedGalleryLastUsed: List<String>? = null
    private var cachedGalleryCategory: GameFocusCategory? = null
    private var cachedGalleryConfig: GalleryConfig? = null
    private var cachedGalleryResult: List<InstalledAppInfo> = emptyList()

    private var cachedLibraryAllApps: List<InstalledAppInfo>? = null
    private var cachedLibraryTab: LibraryTab? = null
    private var cachedLibraryResult: List<InstalledAppInfo> = emptyList()

    private var cachedPairingAllApps: List<InstalledAppInfo>? = null
    private var cachedPairingResult: List<InstalledAppInfo> = emptyList()

    private val controllerHost =
        object : GameFocusControllerHost {
            override val activeCategories: List<GameFocusCategory>
                get() = this@FocusTopLauncherActivity.activeCategories

            override val activeLibraryTabs: List<LibraryTab>
                get() = this@FocusTopLauncherActivity.activeLibraryTabs

            override fun currentGalleryApps(): List<InstalledAppInfo> = this@FocusTopLauncherActivity.currentGalleryApps()

            override fun currentLibraryApps(): List<InstalledAppInfo> = this@FocusTopLauncherActivity.currentLibraryApps()

            override fun currentAvailableAndroidApps(): List<InstalledAppInfo> = this@FocusTopLauncherActivity.currentAvailableAndroidApps()

            override fun romFolders(): List<CustomRomFolder> = RomManager.romFolders.value

            override fun galleryMenuActionCount(): Int = currentGalleryMenuActions().size

            override fun invokeGalleryMenuAction(index: Int) {
                currentGalleryMenuActions().getOrNull(index)?.onClick?.invoke()
            }

            override fun libraryMenuActionCount(focusedApp: InstalledAppInfo?): Int = currentLibraryMenuActions(focusedApp).size

            override fun invokeLibraryMenuAction(
                focusedApp: InstalledAppInfo?,
                index: Int,
            ) {
                currentLibraryMenuActions(focusedApp).getOrNull(index)?.onClick?.invoke()
            }

            override fun stepCoreChooserFocus(direction: LauncherScrollDirection): Boolean =
                this@FocusTopLauncherActivity.stepCoreChooserFocus(direction)

            override fun stepRemoveRomFolderFocus(direction: LauncherScrollDirection): Boolean =
                this@FocusTopLauncherActivity.stepRemoveRomFolderFocus(direction)

            override fun removeRomFolder(folder: CustomRomFolder) {
                RomManager.removeRomFolder(this@FocusTopLauncherActivity, folder)
            }

            override fun confirmAppPair(
                targetApp: InstalledAppInfo,
                chosenApp: InstalledAppInfo,
            ) {
                GameFocusPairManager.setPair(this@FocusTopLauncherActivity, targetApp.packageName, chosenApp.packageName)
            }

            override fun launchGalleryTopApp(appInfo: InstalledAppInfo) {
                this@FocusTopLauncherActivity.launchGalleryTopApp(appInfo)
            }

            override fun launchLibraryTopApp(appInfo: InstalledAppInfo) {
                this@FocusTopLauncherActivity.launchLibraryTopApp(appInfo)
            }

            override fun launchBottomApp(
                appInfo: InstalledAppInfo,
                source: String,
            ) {
                this@FocusTopLauncherActivity.launchBottomApp(appInfo, source)
            }

            override fun toggleButtonPrompts() {
                this@FocusTopLauncherActivity.toggleButtonPrompts()
            }

            override fun onHomeNavigation() {
                GameFocusDefaultLauncherManager.handleHomeNavigation(this@FocusTopLauncherActivity)
            }

            override fun onGalleryDismiss() {
                if (GameFocusSessionTracker.hasActiveSession()) {
                    AppLog.i(TAG, "Active session found -> restoring previous session on Back")
                    lifecycleScope.launch {
                        GameFocusSessionTracker.restorePreviousSession(this@FocusTopLauncherActivity)
                    }
                } else {
                    AppLog.d(TAG, "No previous session tracked -> consuming Back key to stay in gallery")
                }
            }

            override fun onPrimaryOverlayBack() {
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    viewModel.setEditingAppInfo(null)
                    viewModel.setManageGalleriesOpen(false)
                }
            }

            override fun dispatchPrimaryOverlayCenterDown() {
                // Translate gamepad A into DPAD_CENTER so Compose clickables inside the overlay activate.
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            }

            override fun dispatchPrimaryOverlayCenterUp(): Boolean =
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))

            override fun sendPrimaryOverlayFocusRecovery(keyCode: Int) {
                PrimaryOverlayInputBridge.sendFocusRecovery(keyCode)
            }

            override fun playTickHaptic() {
                GameFocusHaptics.tick(this@FocusTopLauncherActivity)
            }

            override fun playClickHaptic() {
                GameFocusHaptics.click(this@FocusTopLauncherActivity)
            }
        }

    private val controllerDispatcher by lazy(LazyThreadSafetyMode.NONE) {
        GameFocusControllerDispatcher(
            viewModel = viewModel,
            scope = lifecycleScope,
            host = controllerHost,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system status and navigation bars
        applyImmersiveSystemBars()

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
            val appColors = remember(themeMode, userAccent) { paletteFor(themeMode, userAccent) }
            val colorScheme = remember(appColors) { colorSchemeFor(appColors) }

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
                    currentAvailableAndroidApps(allApps)
                }

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

            LaunchedEffect(libraryTabs) {
                if (viewModel.librarySelectedTab.value !in libraryTabs) {
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

            var frozenHidden by remember { mutableStateOf(hidden) }
            val lastCatalogTracker = remember { LastCatalogRef(allApps) }
            val editingAppInfo by viewModel.editingAppInfo.collectAsStateWithLifecycle()
            val isManageGalleriesOpen by viewModel.isManageGalleriesOpen.collectAsStateWithLifecycle()
            val isPrimaryOverlayOpen = editingAppInfo != null || isManageGalleriesOpen

            LaunchedEffect(isPrimaryOverlayOpen) {
                if (isPrimaryOverlayOpen) {
                    controllerDispatcher.stopRepeat()
                }
            }

            SideEffect {
                if (lastCatalogTracker.apps !== allApps) {
                    lastCatalogTracker.apps = allApps
                    if (frozenHidden != hidden) {
                        frozenHidden = hidden
                    }
                }
            }
            val handleSnapHidden: (Set<String>) -> Unit =
                remember {
                    { snapped -> frozenHidden = snapped }
                }

            LauncherSelectionGuard(
                viewModel = viewModel,
                categories = categories,
                liveHidden = hidden,
                frozenHidden = frozenHidden,
                onSnapHidden = handleSnapHidden,
            )

            SideEffect {
                frozenHiddenSetForInput = frozenHidden
            }

            val handleAppClickTop: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        launchGalleryTopApp(appInfo)
                    }
                }
            val handleAppClickBottom: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        launchBottomApp(appInfo, "touch")
                    }
                }
            val handleToggleFavorite: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        InstalledAppsManager.toggleFavorite(this@FocusTopLauncherActivity, appInfo.packageName)
                    }
                }
            val handleToggleHidden: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        InstalledAppsManager.toggleHidden(this@FocusTopLauncherActivity, appInfo.packageName)
                    }
                }
            val handleOpenAppInfo: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        InstalledAppsManager.openAppInfo(this@FocusTopLauncherActivity, appInfo.packageName, display.displayId)
                    }
                }
            val handleUninstallApp: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        InstalledAppsManager.uninstallApp(this@FocusTopLauncherActivity, appInfo.packageName, display.displayId)
                    }
                }
            val handleChangeCore: (CustomRomFolder) -> Unit =
                remember {
                    { folder ->
                        openCoreChooserForFolder(folder)
                    }
                }
            val handleAddRomFolder: () -> Unit =
                remember {
                    { openDocumentTreeLauncher.launch(null) }
                }
            val handleRemoveRomFolder: (CustomRomFolder) -> Unit =
                remember {
                    { folder -> RomManager.removeRomFolder(this@FocusTopLauncherActivity, folder) }
                }
            val handleConfirmNewlyAddedFolderCore: (CustomRomFolder, String?) -> Unit =
                remember {
                    { folder, coreOrPackage ->
                        if (InstalledEmulatorRegistry.isMultiEmulatorSystem(folder.systemId)) {
                            RomManager.updateRomFolderEmulatorPackage(this@FocusTopLauncherActivity, folder.uriString, coreOrPackage)
                        } else {
                            RomManager.updateRomFolderCore(this@FocusTopLauncherActivity, folder.uriString, coreOrPackage)
                        }
                        viewModel.setNewlyAddedFolder(null)
                    }
                }
            val handleOpenPairingDialog: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo -> openPairingDialogForApp(appInfo) }
                }
            val handleRemovePair: (InstalledAppInfo) -> Unit =
                remember {
                    { appInfo ->
                        GameFocusPairManager.removePair(this@FocusTopLauncherActivity, appInfo.packageName)
                    }
                }
            val handleConfirmPairApp: (InstalledAppInfo, InstalledAppInfo) -> Unit =
                remember {
                    { topApp, bottomApp ->
                        AppLog.i(TAG, "Pairing app ${topApp.label} with ${bottomApp.label}")
                        GameFocusPairManager.setPair(this@FocusTopLauncherActivity, topApp.packageName, bottomApp.packageName)
                        viewModel.dismissPairingDialog()
                    }
                }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = megingiardTypography,
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
                ) {
                    CompanionHoverStateSync(
                        context = this@FocusTopLauncherActivity,
                        viewModel = viewModel,
                        allApps = allApps,
                        appColors = appColors,
                        remoteThemeState = remoteThemeState,
                        hasLaunchedTopPackage = { launchedTopScreenPackage != null },
                    )

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
                        onAppClickTop = handleAppClickTop,
                        onAppClickBottom = handleAppClickBottom,
                        onToggleFavorite = handleToggleFavorite,
                        onToggleHidden = handleToggleHidden,
                        onOpenAppInfo = handleOpenAppInfo,
                        onUninstallApp = handleUninstallApp,
                        onChangeCore = handleChangeCore,
                        onAddRomFolder = handleAddRomFolder,
                        onRemoveRomFolder = handleRemoveRomFolder,
                        onConfirmNewlyAddedFolderCore = handleConfirmNewlyAddedFolderCore,
                        onOpenPairingDialog = handleOpenPairingDialog,
                        onRemovePair = handleRemovePair,
                        onConfirmPairApp = handleConfirmPairApp,
                    )
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
        applyImmersiveSystemBars()
        InstalledAppsManager.loadInstalledApps(this)
        launchedTopScreenPackage = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveSystemBars()
        } else {
            controllerDispatcher.stopRepeat()
        }
    }

    private fun applyImmersiveSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        controllerDispatcher.stopRepeat()
    }

    override fun onStop() {
        super.onStop()
        controllerDispatcher.stopRepeat()
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

    private fun launchLibraryTopApp(appInfo: InstalledAppInfo) {
        AppLog.i(TAG, "Library launch on top display: ${appInfo.label}")
        launchedTopScreenPackage = appInfo.packageName
        GameFocusSessionTracker.recordTopLaunch(appInfo)
        MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = appInfo.packageName)
        lifecycleScope.launch {
            InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, appInfo)
        }
    }

    private fun launchBottomApp(
        appInfo: InstalledAppInfo,
        source: String,
    ) {
        AppLog.i(TAG, "Launching app on bottom display ($source): ${appInfo.label}")
        if (!isCompanionApp(appInfo.packageName)) {
            MegingiardSettingsClient.updateClientState(
                this,
                isActive = true,
                focusedPackage = appInfo.packageName,
            )
        }
        GameFocusSessionTracker.recordBottomLaunch(appInfo)
        lifecycleScope.launch {
            InstalledAppsManager.launchAppOnSecondaryDisplay(this@FocusTopLauncherActivity, appInfo)
        }
    }

    private fun toggleButtonPrompts() {
        val newVisible = viewModel.toggleButtonPromptsVisible()
        GameFocusPreferences.setButtonPromptsVisible(this, newVisible)
        AppLog.i(TAG, "Gamepad R3 pressed -> toggled button prompts visibility (now visible=$newVisible)")
    }

    private fun currentAvailableAndroidApps(
        allApps: List<InstalledAppInfo> = InstalledAppsManager.installedApps.value,
    ): List<InstalledAppInfo> {
        if (allApps === cachedPairingAllApps) {
            return cachedPairingResult
        }
        val filtered = allApps.filter { !it.isRom && it.packageName != packageName }.sortedBy { it.label.lowercase() }
        cachedPairingAllApps = allApps
        cachedPairingResult = filtered
        return filtered
    }

    private fun openPairingDialogForApp(targetApp: InstalledAppInfo) {
        val availableApps = currentAvailableAndroidApps()
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
        val galleryConfig = GameFocusGalleryManager.config.value

        if (allApps === cachedGalleryAllApps &&
            favorites === cachedGalleryFavorites &&
            hidden === cachedGalleryHidden &&
            lastUsed === cachedGalleryLastUsed &&
            selectedCategory == cachedGalleryCategory &&
            galleryConfig === cachedGalleryConfig
        ) {
            return cachedGalleryResult
        }

        val collectionMembers =
            galleryConfig.collections
                .associate { collection -> collection.id to collection.members.toSet() }
        val filtered = selectedCategory.filterApps(allApps, favorites, hidden, lastUsed, collectionMembers)
        cachedGalleryAllApps = allApps
        cachedGalleryFavorites = favorites
        cachedGalleryHidden = hidden
        cachedGalleryLastUsed = lastUsed
        cachedGalleryCategory = selectedCategory
        cachedGalleryConfig = galleryConfig
        cachedGalleryResult = filtered
        return filtered
    }

    private fun currentLibraryApps(): List<InstalledAppInfo> {
        val allApps = InstalledAppsManager.installedApps.value
        val currentTab = viewModel.librarySelectedTab.value
        if (allApps === cachedLibraryAllApps && currentTab == cachedLibraryTab) {
            return cachedLibraryResult
        }
        val filtered = currentTab.filterApps(allApps)
        cachedLibraryAllApps = allApps
        cachedLibraryTab = currentTab
        cachedLibraryResult = filtered
        return filtered
    }

    private fun resetToGallery(): Boolean = controllerDispatcher.resetToGallery()

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

    private fun stepCoreChooserFocus(direction: LauncherScrollDirection): Boolean {
        val folder = viewModel.newlyAddedFolder.value ?: return false
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
        return viewModel.stepCoreChooserFocus(direction, count)
    }

    private fun stepRemoveRomFolderFocus(direction: LauncherScrollDirection): Boolean {
        val folders = RomManager.romFolders.value
        return viewModel.stepRemoveRomFolderFocus(direction, folders.size)
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
            onEditGame = { app ->
                controllerDispatcher.stopRepeat()
                viewModel.openEditGameOverlay(app)
            },
            onToggleHidden = { app -> InstalledAppsManager.toggleHidden(this, app.packageName) },
            onOpenPairingDialog = { app -> openPairingDialogForApp(app) },
            onRemovePair = { app -> GameFocusPairManager.removePair(this, app.packageName) },
            onOpenManageGalleries = {
                AppLog.i(TAG, "Opening Manage Galleries overlay from gallery menu")
                controllerDispatcher.stopRepeat()
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
            onEditArtwork = { app ->
                controllerDispatcher.stopRepeat()
                viewModel.openEditGameOverlay(app)
            },
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
                controllerDispatcher.stopRepeat()
                viewModel.setManageGalleriesOpen(true)
            },
            onDismissMenu = { viewModel.setLibraryOptionsMenuExpanded(false) },
        )

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean =
        if (controllerDispatcher.handleKeyDown(keyCode, event?.repeatCount ?: 0)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean =
        if (controllerDispatcher.handleKeyUp(keyCode)) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (controllerDispatcher.isPrimaryOverlayOpen()) {
                controllerDispatcher.stopRepeat()
                // A primary overlay owns all joystick input; translate axes into D-pad key events for Compose focus.
                PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, dpadKeyCode ->
                    val handled = dispatchKeyEvent(KeyEvent(action, dpadKeyCode))
                    if (!handled && action == KeyEvent.ACTION_DOWN) {
                        PrimaryOverlayInputBridge.sendFocusRecovery(dpadKeyCode)
                    }
                }
                return true
            }

            val axisHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val axisHatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = if (axisHatX != 0f) axisHatX else event.getAxisValue(MotionEvent.AXIS_X)
            val y = if (axisHatY != 0f) axisHatY else event.getAxisValue(MotionEvent.AXIS_Y)
            if (controllerDispatcher.handleJoystickAxes(x, y)) {
                return true
            }
        }
        return if (controllerDispatcher.consumesUnhandledMotionEvents()) {
            true
        } else {
            super.onGenericMotionEvent(event)
        }
    }
}

private class LastCatalogRef(
    var apps: List<InstalledAppInfo>,
)

private class LastSelectionTracker(
    var category: GameFocusCategory,
    var isLibraryOpen: Boolean,
)

@Composable
private fun LauncherSelectionGuard(
    viewModel: FocusTopLauncherViewModel,
    categories: List<GameFocusCategory>,
    liveHidden: Set<String>,
    frozenHidden: Set<String>,
    onSnapHidden: (Set<String>) -> Unit,
) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isLibraryOpen by viewModel.isLibraryOpen.collectAsStateWithLifecycle()

    val selectionTracker = remember { LastSelectionTracker(selectedCategory, isLibraryOpen) }
    SideEffect {
        if (selectionTracker.category != selectedCategory || selectionTracker.isLibraryOpen != isLibraryOpen) {
            selectionTracker.category = selectedCategory
            selectionTracker.isLibraryOpen = isLibraryOpen
            if (frozenHidden != liveHidden) {
                onSnapHidden(liveHidden)
            }
        }
        if (selectedCategory !in categories) {
            val fallbackCategory = categories.first()
            AppLog.i(TAG, "Selected gallery ${selectedCategory.id} is not visible -> falling back to ${fallbackCategory.id}")
            viewModel.setSelectedCategory(fallbackCategory)
        }
    }
}

@Composable
private fun CompanionHoverStateSync(
    context: Context,
    viewModel: FocusTopLauncherViewModel,
    allApps: List<InstalledAppInfo>,
    appColors: AppColors,
    remoteThemeState: Pair<ThemeMode, Color?>,
    hasLaunchedTopPackage: () -> Boolean,
) {
    val isLibraryOpen by viewModel.isLibraryOpen.collectAsStateWithLifecycle()
    val librarySelectedTab by viewModel.librarySelectedTab.collectAsStateWithLifecycle()
    val libraryFocusedIndex by viewModel.libraryFocusedIndex.collectAsStateWithLifecycle()
    val focusedApp by viewModel.focusedApp.collectAsStateWithLifecycle()
    val isStarted by viewModel.isStarted.collectAsStateWithLifecycle()

    val activeLibraryApps =
        remember(isLibraryOpen, librarySelectedTab, allApps) {
            if (isLibraryOpen) librarySelectedTab.filterApps(allApps) else emptyList()
        }

    val currentHoveredApp =
        remember(
            isLibraryOpen,
            focusedApp,
            libraryFocusedIndex,
            activeLibraryApps,
        ) {
            if (isLibraryOpen) {
                activeLibraryApps.getOrNull(libraryFocusedIndex.coerceAtLeast(0))
            } else {
                focusedApp
            }
        }

    LaunchedEffect(currentHoveredApp, isStarted, remoteThemeState) {
        if (isStarted && !hasLaunchedTopPackage()) {
            val palette =
                currentHoveredApp?.let { app ->
                    AppPaletteExtractor.getCachedColorsOrNull(app)
                        ?: AppPaletteExtractor.extractColorsAsync(app, appColors.accent, appColors.appBackground)
                }
            val primaryArgb = palette?.primaryColor?.toArgb()
            val secondaryArgb = palette?.secondaryColor?.toArgb()

            MegingiardSettingsClient.updateClientState(
                context = context,
                isActive = true,
                focusedPackage = null,
                hoveredPackage = currentHoveredApp?.packageName,
                hoveredLabel = currentHoveredApp?.label,
                hoveredPrimaryColor = primaryArgb,
                hoveredSecondaryColor = secondaryArgb,
            )
        }
    }
}
