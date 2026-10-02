package com.stormpanda.megingiard.gamefocus.viewmodel

import androidx.lifecycle.ViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.gamefocus.GameFocusCategory
import com.stormpanda.megingiard.math.floorMod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "FocusTopLauncherViewModel"
const val INITIAL_LOOP_OFFSET = 10_000
const val DEFAULT_LIBRARY_GRID_COLUMNS = 6

enum class LauncherScrollDirection { NONE, LEFT, RIGHT, UP, DOWN }

/**
 * ViewModel managing UI state, navigation triggers, and dialog states for [com.stormpanda.megingiard.gamefocus.FocusTopLauncherActivity].
 */
class FocusTopLauncherViewModel : ViewModel() {
    private val _dialogVirtualIndex = MutableStateFlow(INITIAL_LOOP_OFFSET)
    val dialogVirtualIndex: StateFlow<Int> = _dialogVirtualIndex.asStateFlow()

    private val _confirmDialogTrigger = MutableStateFlow(0)
    val confirmDialogTrigger: StateFlow<Int> = _confirmDialogTrigger.asStateFlow()

    private val _dialogL1Trigger = MutableStateFlow(0)
    val dialogL1Trigger: StateFlow<Int> = _dialogL1Trigger.asStateFlow()

    private val _dialogR1Trigger = MutableStateFlow(0)
    val dialogR1Trigger: StateFlow<Int> = _dialogR1Trigger.asStateFlow()

    private val _prevLetterTrigger = MutableStateFlow(0)
    val prevLetterTrigger: StateFlow<Int> = _prevLetterTrigger.asStateFlow()

    private val _nextLetterTrigger = MutableStateFlow(0)
    val nextLetterTrigger: StateFlow<Int> = _nextLetterTrigger.asStateFlow()

    private val _selectedCategory = MutableStateFlow<GameFocusCategory>(GameFocusCategory.LAST_USED)
    val selectedCategory: StateFlow<GameFocusCategory> = _selectedCategory.asStateFlow()

    private val _isMainOptionsMenuExpanded = MutableStateFlow(false)
    val isMainOptionsMenuExpanded: StateFlow<Boolean> = _isMainOptionsMenuExpanded.asStateFlow()

    private val _mainMenuSelectedIndex = MutableStateFlow(0)
    val mainMenuSelectedIndex: StateFlow<Int> = _mainMenuSelectedIndex.asStateFlow()

    private val _newlyAddedFolder = MutableStateFlow<CustomRomFolder?>(null)
    val newlyAddedFolder: StateFlow<CustomRomFolder?> = _newlyAddedFolder.asStateFlow()

    private val _isRemoveRomFolderDialogOpen = MutableStateFlow(false)
    val isRemoveRomFolderDialogOpen: StateFlow<Boolean> = _isRemoveRomFolderDialogOpen.asStateFlow()

    private val _removeRomFolderDialogSelectedIndex = MutableStateFlow(0)
    val removeRomFolderDialogSelectedIndex: StateFlow<Int> = _removeRomFolderDialogSelectedIndex.asStateFlow()

    private val _folderToRemove = MutableStateFlow<CustomRomFolder?>(null)
    val folderToRemove: StateFlow<CustomRomFolder?> = _folderToRemove.asStateFlow()

    private val _coreChooserDialogSelectedIndex = MutableStateFlow(0)
    val coreChooserDialogSelectedIndex: StateFlow<Int> = _coreChooserDialogSelectedIndex.asStateFlow()

    private val _confirmCoreChooserTrigger = MutableStateFlow(0)
    val confirmCoreChooserTrigger: StateFlow<Int> = _confirmCoreChooserTrigger.asStateFlow()

    private val _isOptionsMenuExpanded = MutableStateFlow(false)
    val isOptionsMenuExpanded: StateFlow<Boolean> = _isOptionsMenuExpanded.asStateFlow()

    private val _artworkMenuSelectedIndex = MutableStateFlow(0)
    val artworkMenuSelectedIndex: StateFlow<Int> = _artworkMenuSelectedIndex.asStateFlow()

    private val _dpadUpOptionsTrigger = MutableStateFlow(0)
    val dpadUpOptionsTrigger: StateFlow<Int> = _dpadUpOptionsTrigger.asStateFlow()

    private val _dpadRightOptionsTrigger = MutableStateFlow(0)
    val dpadRightOptionsTrigger: StateFlow<Int> = _dpadRightOptionsTrigger.asStateFlow()

    private val _editingAppInfo = MutableStateFlow<InstalledAppInfo?>(null)
    val editingAppInfo: StateFlow<InstalledAppInfo?> = _editingAppInfo.asStateFlow()

    private val _isLibraryOpen = MutableStateFlow(false)
    val isLibraryOpen: StateFlow<Boolean> = _isLibraryOpen.asStateFlow()

    private val _librarySelectedTab = MutableStateFlow<LibraryTab>(LibraryTab.GAMES)
    val librarySelectedTab: StateFlow<LibraryTab> = _librarySelectedTab.asStateFlow()

    private val _libraryFocusedIndex = MutableStateFlow(0)
    val libraryFocusedIndex: StateFlow<Int> = _libraryFocusedIndex.asStateFlow()

    private val _isLibraryOptionsMenuExpanded = MutableStateFlow(false)
    val isLibraryOptionsMenuExpanded: StateFlow<Boolean> = _isLibraryOptionsMenuExpanded.asStateFlow()

    private val _libraryMenuSelectedIndex = MutableStateFlow(0)
    val libraryMenuSelectedIndex: StateFlow<Int> = _libraryMenuSelectedIndex.asStateFlow()

    private val _dpadLeftTrigger = MutableStateFlow(0)
    val dpadLeftTrigger: StateFlow<Int> = _dpadLeftTrigger.asStateFlow()

    private val _dpadStepRightTrigger = MutableStateFlow(0)
    val dpadStepRightTrigger: StateFlow<Int> = _dpadStepRightTrigger.asStateFlow()

    private val _focusedApp = MutableStateFlow<InstalledAppInfo?>(null)
    val focusedApp: StateFlow<InstalledAppInfo?> = _focusedApp.asStateFlow()

    private val _isResumed = MutableStateFlow(false)
    val isResumed: StateFlow<Boolean> = _isResumed.asStateFlow()

    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    fun setSelectedCategory(category: GameFocusCategory) {
        AppLog.d(TAG, "setSelectedCategory: ${category.id}")
        _selectedCategory.value = category
    }

    fun cycleCategoryUp(categories: List<GameFocusCategory>) {
        val prevCategory = _selectedCategory.value.previous(categories)
        AppLog.i(TAG, "Category UP -> switching launcher category to ${prevCategory.id}")
        _selectedCategory.value = prevCategory
    }

    fun cycleCategoryDown(categories: List<GameFocusCategory>) {
        val nextCategory = _selectedCategory.value.next(categories)
        AppLog.i(TAG, "Category DOWN -> switching launcher category to ${nextCategory.id}")
        _selectedCategory.value = nextCategory
    }

    fun setMainMenuSelectedIndex(index: Int) {
        _mainMenuSelectedIndex.value = index
    }

    fun navigateMainMenuUp(itemCount: Int) {
        if (itemCount <= 0) return
        _mainMenuSelectedIndex.value =
            if (_mainMenuSelectedIndex.value > 0) {
                _mainMenuSelectedIndex.value - 1
            } else {
                itemCount - 1
            }
    }

    fun navigateMainMenuDown(itemCount: Int) {
        if (itemCount <= 0) return
        _mainMenuSelectedIndex.value =
            if (_mainMenuSelectedIndex.value < itemCount - 1) {
                _mainMenuSelectedIndex.value + 1
            } else {
                0
            }
    }

    fun setMainOptionsMenuExpanded(expanded: Boolean) {
        if (expanded) {
            _mainMenuSelectedIndex.value = 0
        }
        _isMainOptionsMenuExpanded.value = expanded
    }

    fun toggleMainOptionsMenu() {
        setMainOptionsMenuExpanded(!_isMainOptionsMenuExpanded.value)
    }

    fun setNewlyAddedFolder(folder: CustomRomFolder?) {
        _newlyAddedFolder.value = folder
    }

    fun setRemoveRomFolderDialogOpen(open: Boolean) {
        _isRemoveRomFolderDialogOpen.value = open
    }

    fun setRemoveRomFolderDialogSelectedIndex(index: Int) {
        _removeRomFolderDialogSelectedIndex.value = index
    }

    fun setFolderToRemove(folder: CustomRomFolder?) {
        _folderToRemove.value = folder
    }

    fun setCoreChooserDialogSelectedIndex(index: Int) {
        _coreChooserDialogSelectedIndex.value = index
    }

    fun triggerConfirmCoreChooser() {
        _confirmCoreChooserTrigger.value += 1
    }

    fun setArtworkMenuSelectedIndex(index: Int) {
        _artworkMenuSelectedIndex.value = index
    }

    fun navigateArtworkMenuUp(itemCount: Int = 2) {
        if (itemCount <= 0) return
        _artworkMenuSelectedIndex.value =
            if (_artworkMenuSelectedIndex.value > 0) {
                _artworkMenuSelectedIndex.value - 1
            } else {
                itemCount - 1
            }
    }

    fun navigateArtworkMenuDown(itemCount: Int = 2) {
        if (itemCount <= 0) return
        _artworkMenuSelectedIndex.value =
            if (_artworkMenuSelectedIndex.value < itemCount - 1) {
                _artworkMenuSelectedIndex.value + 1
            } else {
                0
            }
    }

    fun setOptionsMenuExpanded(expanded: Boolean) {
        if (expanded) {
            _artworkMenuSelectedIndex.value = 0
        }
        _isOptionsMenuExpanded.value = expanded
    }

    fun toggleOptionsMenu() {
        setOptionsMenuExpanded(!_isOptionsMenuExpanded.value)
    }

    fun triggerDpadUpOptions() {
        _dpadUpOptionsTrigger.value += 1
    }

    fun triggerDpadRightOptions() {
        _dpadRightOptionsTrigger.value += 1
    }

    fun setEditingAppInfo(appInfo: InstalledAppInfo?) {
        _editingAppInfo.value = appInfo
    }

    fun openArtworkDialog(appInfo: InstalledAppInfo) {
        AppLog.i(TAG, "Opening artwork edit dialog for ${appInfo.label}")
        _dialogVirtualIndex.value = INITIAL_LOOP_OFFSET
        _confirmDialogTrigger.value = 0
        _dialogL1Trigger.value = 0
        _dialogR1Trigger.value = 0
        _artworkMenuSelectedIndex.value = 0
        _isOptionsMenuExpanded.value = false
        _dpadUpOptionsTrigger.value = 0
        _dpadRightOptionsTrigger.value = 0
        _editingAppInfo.value = appInfo
    }

    fun setDialogVirtualIndex(index: Int) {
        _dialogVirtualIndex.value = index
    }

    fun triggerConfirmDialog() {
        _confirmDialogTrigger.value += 1
    }

    fun triggerDialogL1() {
        _dialogL1Trigger.value += 1
    }

    fun triggerDialogR1() {
        _dialogR1Trigger.value += 1
    }

    fun triggerPrevLetter() {
        _prevLetterTrigger.value += 1
    }

    fun triggerNextLetter() {
        _nextLetterTrigger.value += 1
    }

    fun triggerDpadLeft() {
        _dpadLeftTrigger.value += 1
    }

    fun triggerDpadStepRight() {
        _dpadStepRightTrigger.value += 1
    }

    fun setLibraryOpen(open: Boolean) {
        AppLog.i(TAG, "setLibraryOpen: $open")
        _isLibraryOpen.value = open
    }

    fun setLibrarySelectedTab(tab: LibraryTab) {
        _librarySelectedTab.value = tab
    }

    fun cycleLibraryTabUp(tabs: List<LibraryTab>) {
        val prevTab = _librarySelectedTab.value.previous(tabs)
        AppLog.i(TAG, "Library tab UP -> switching tab to ${prevTab.id}")
        _librarySelectedTab.value = prevTab
    }

    fun cycleLibraryTabDown(tabs: List<LibraryTab>) {
        val nextTab = _librarySelectedTab.value.next(tabs)
        AppLog.i(TAG, "Library tab DOWN -> switching tab to ${nextTab.id}")
        _librarySelectedTab.value = nextTab
    }

    fun setLibraryFocusedIndex(index: Int) {
        _libraryFocusedIndex.value = index
    }

    fun setLibraryMenuSelectedIndex(index: Int) {
        _libraryMenuSelectedIndex.value = index
    }

    fun navigateLibraryMenuUp(itemCount: Int) {
        if (itemCount <= 0) return
        _libraryMenuSelectedIndex.value =
            if (_libraryMenuSelectedIndex.value > 0) {
                _libraryMenuSelectedIndex.value - 1
            } else {
                itemCount - 1
            }
    }

    fun navigateLibraryMenuDown(itemCount: Int) {
        if (itemCount <= 0) return
        _libraryMenuSelectedIndex.value =
            if (_libraryMenuSelectedIndex.value < itemCount - 1) {
                _libraryMenuSelectedIndex.value + 1
            } else {
                0
            }
    }

    fun setLibraryOptionsMenuExpanded(expanded: Boolean) {
        if (expanded) {
            _libraryMenuSelectedIndex.value = 0
        }
        _isLibraryOptionsMenuExpanded.value = expanded
    }

    fun toggleLibraryOptionsMenu() {
        setLibraryOptionsMenuExpanded(!_isLibraryOptionsMenuExpanded.value)
    }

    fun setFocusedApp(appInfo: InstalledAppInfo?) {
        _focusedApp.value = appInfo
    }

    fun setResumed(resumed: Boolean) {
        _isResumed.value = resumed
    }

    fun setStarted(started: Boolean) {
        _isStarted.value = started
    }

    fun stepLibraryFocus(
        direction: LauncherScrollDirection,
        total: Int,
        columns: Int = DEFAULT_LIBRARY_GRID_COLUMNS,
    ) {
        val current = _libraryFocusedIndex.value.coerceAtLeast(0)
        when (direction) {
            LauncherScrollDirection.LEFT -> {
                if (current > 0) {
                    _libraryFocusedIndex.value = current - 1
                }
            }

            LauncherScrollDirection.RIGHT -> {
                if (total > 0 && current < total - 1) {
                    _libraryFocusedIndex.value = current + 1
                }
            }

            LauncherScrollDirection.UP -> {
                if (current >= columns) {
                    _libraryFocusedIndex.value = current - columns
                }
            }

            LauncherScrollDirection.DOWN -> {
                if (total > 0) {
                    if (current + columns < total) {
                        _libraryFocusedIndex.value = current + columns
                    } else if (current < total - 1) {
                        _libraryFocusedIndex.value = total - 1
                    }
                }
            }

            LauncherScrollDirection.NONE -> {}
        }
    }

    fun stepCoreChooserFocus(
        direction: LauncherScrollDirection,
        coreCount: Int,
    ) {
        if (coreCount > 0) {
            val current = _coreChooserDialogSelectedIndex.value
            when (direction) {
                LauncherScrollDirection.UP -> {
                    _coreChooserDialogSelectedIndex.value = (current - 1).floorMod(coreCount)
                }

                LauncherScrollDirection.DOWN -> {
                    _coreChooserDialogSelectedIndex.value = (current + 1).floorMod(coreCount)
                }

                else -> {}
            }
        }
    }

    fun stepRemoveRomFolderFocus(
        direction: LauncherScrollDirection,
        foldersCount: Int,
    ) {
        if (foldersCount > 0) {
            val current = _removeRomFolderDialogSelectedIndex.value
            when (direction) {
                LauncherScrollDirection.UP -> {
                    _removeRomFolderDialogSelectedIndex.value = (current - 1).floorMod(foldersCount)
                }

                LauncherScrollDirection.DOWN -> {
                    _removeRomFolderDialogSelectedIndex.value = (current + 1).floorMod(foldersCount)
                }

                else -> {}
            }
        }
    }

    fun stepArtworkDialogVirtualIndex(delta: Int) {
        _dialogVirtualIndex.value += delta
    }

    fun resetToGallery(): Boolean {
        val wasNotInGallery =
            _isLibraryOpen.value ||
                _editingAppInfo.value != null ||
                _isMainOptionsMenuExpanded.value ||
                _isOptionsMenuExpanded.value ||
                _isLibraryOptionsMenuExpanded.value ||
                _isRemoveRomFolderDialogOpen.value ||
                _folderToRemove.value != null ||
                _newlyAddedFolder.value != null

        if (wasNotInGallery) {
            AppLog.i(TAG, "Resetting view state to main gallery")
            _isLibraryOpen.value = false
            _editingAppInfo.value = null
            _isMainOptionsMenuExpanded.value = false
            _mainMenuSelectedIndex.value = 0
            _isOptionsMenuExpanded.value = false
            _artworkMenuSelectedIndex.value = 0
            _isLibraryOptionsMenuExpanded.value = false
            _libraryMenuSelectedIndex.value = 0
            _isRemoveRomFolderDialogOpen.value = false
            _folderToRemove.value = null
            _newlyAddedFolder.value = null
            return true
        }
        return false
    }
}
