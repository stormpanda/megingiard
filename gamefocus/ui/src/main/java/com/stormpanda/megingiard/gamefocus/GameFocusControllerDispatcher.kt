package com.stormpanda.megingiard.gamefocus

import android.view.KeyEvent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_LIBRARY_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.DEFAULT_PAIRING_GRID_COLUMNS
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.gamefocus.viewmodel.LauncherScrollDirection
import com.stormpanda.megingiard.ui.GamePadButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GFControllerDispatcher"
internal const val JOYSTICK_THRESHOLD = 0.5f
internal const val JOYSTICK_NEGATIVE_THRESHOLD = -0.5f

internal enum class RepeatableInput {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    L1,
    R1,
}

internal fun RepeatableInput.toScrollDirection(): LauncherScrollDirection =
    when (this) {
        RepeatableInput.UP -> LauncherScrollDirection.UP

        RepeatableInput.DOWN -> LauncherScrollDirection.DOWN

        RepeatableInput.LEFT -> LauncherScrollDirection.LEFT

        RepeatableInput.RIGHT -> LauncherScrollDirection.RIGHT

        RepeatableInput.L1,
        RepeatableInput.R1,
        -> LauncherScrollDirection.NONE
    }

internal fun RepeatableInput.isDirectional(): Boolean = this != RepeatableInput.L1 && this != RepeatableInput.R1

/**
 * Host callbacks bridging [GameFocusControllerDispatcher] to Android Activity / singleton side effects.
 *
 * Keeping Android View, Intent, and vibrator operations behind this contract allows the full modal,
 * key-repeat, and joystick routing state machine to execute deterministically in pure JVM unit tests.
 */
internal interface GameFocusControllerHost {
    val activeCategories: List<GameFocusCategory>
    val activeLibraryTabs: List<LibraryTab>

    fun currentGalleryApps(): List<InstalledAppInfo>

    fun currentLibraryApps(): List<InstalledAppInfo>

    fun currentAvailableAndroidApps(): List<InstalledAppInfo>

    fun romFolders(): List<CustomRomFolder>

    fun galleryMenuActionCount(): Int

    fun invokeGalleryMenuAction(index: Int)

    fun libraryMenuActionCount(focusedApp: InstalledAppInfo?): Int

    fun invokeLibraryMenuAction(
        focusedApp: InstalledAppInfo?,
        index: Int,
    )

    fun stepCoreChooserFocus(direction: LauncherScrollDirection): Boolean

    fun stepRemoveRomFolderFocus(direction: LauncherScrollDirection): Boolean

    fun removeRomFolder(folder: CustomRomFolder)

    fun confirmAppPair(
        targetApp: InstalledAppInfo,
        chosenApp: InstalledAppInfo,
    )

    fun launchGalleryTopApp(appInfo: InstalledAppInfo)

    fun launchLibraryTopApp(appInfo: InstalledAppInfo)

    fun launchBottomApp(
        appInfo: InstalledAppInfo,
        source: String,
    )

    fun toggleButtonPrompts()

    fun onHomeNavigation()

    fun onGalleryDismiss()

    fun onPrimaryOverlayBack()

    fun dispatchPrimaryOverlayCenterDown()

    fun dispatchPrimaryOverlayCenterUp(): Boolean

    fun sendPrimaryOverlayFocusRecovery(keyCode: Int)

    fun playTickHaptic()

    fun playClickHaptic()
}

/**
 * Coordinates physical gamepad keys, OS key-repeat filtering, joystick axes, and coroutine hold-repeat
 * pacing across all Game Focus views, menus, and dialogs.
 */
internal class GameFocusControllerDispatcher(
    private val viewModel: FocusTopLauncherViewModel,
    private val scope: CoroutineScope,
    private val host: GameFocusControllerHost,
) {
    private var currentRepeatInput: RepeatableInput? = null
    private var repeatJob: Job? = null

    /** True while a primary overlay (Edit Game or Manage Galleries) owns gamepad input. */
    fun isPrimaryOverlayOpen(): Boolean = viewModel.editingAppInfo.value != null || viewModel.isManageGalleriesOpen.value

    fun consumesUnhandledMotionEvents(): Boolean = isPrimaryOverlayOpen() || viewModel.pairingTargetApp.value != null

    fun resetToGallery(): Boolean {
        stopRepeat()
        return viewModel.resetToGallery()
    }

    fun stopRepeat() {
        if (currentRepeatInput == null && repeatJob == null) return
        AppLog.d(TAG, "stopRepeat: currentRepeatInput was $currentRepeatInput")
        currentRepeatInput = null
        repeatJob?.cancel()
        repeatJob = null
    }

    private fun stopDirectionalRepeat() {
        if (currentRepeatInput?.isDirectional() == true) {
            stopRepeat()
        }
    }

    private fun isVerticalOnlyModalActive(): Boolean =
        viewModel.newlyAddedFolder.value != null ||
            viewModel.isRemoveRomFolderDialogOpen.value ||
            viewModel.isMainOptionsMenuExpanded.value ||
            viewModel.isLibraryOptionsMenuExpanded.value

    private fun isSecondaryModalOpen(): Boolean =
        viewModel.newlyAddedFolder.value != null ||
            viewModel.folderToRemove.value != null ||
            viewModel.isRemoveRomFolderDialogOpen.value ||
            viewModel.pairingTargetApp.value != null

    private fun isGalleryCategoryNavigationActive(): Boolean =
        !isVerticalOnlyModalActive() &&
            viewModel.pairingTargetApp.value == null &&
            viewModel.folderToRemove.value == null &&
            !viewModel.isLibraryOpen.value

    private fun isRepeatableInputSupported(input: RepeatableInput): Boolean {
        if (viewModel.folderToRemove.value != null) return false
        return when (input) {
            RepeatableInput.UP,
            RepeatableInput.DOWN,
            -> {
                true
            }

            RepeatableInput.LEFT,
            RepeatableInput.RIGHT,
            -> {
                !isVerticalOnlyModalActive() &&
                    (
                        viewModel.pairingTargetApp.value != null ||
                            viewModel.isLibraryOpen.value ||
                            host.currentGalleryApps().size > 1
                    )
            }

            RepeatableInput.L1,
            RepeatableInput.R1,
            -> {
                !isVerticalOnlyModalActive() &&
                    viewModel.pairingTargetApp.value == null &&
                    (viewModel.isLibraryOpen.value || host.currentGalleryApps().isNotEmpty())
            }
        }
    }

    private fun resolveInputChannel(input: RepeatableInput): GameFocusInputChannel =
        when (input) {
            RepeatableInput.L1,
            RepeatableInput.R1,
            -> {
                if (viewModel.isLibraryOpen.value) {
                    GameFocusInputChannel.CATEGORY_OR_TAB
                } else {
                    GameFocusInputChannel.GALLERY_LETTER
                }
            }

            RepeatableInput.LEFT,
            RepeatableInput.RIGHT,
            -> {
                GameFocusInputChannel.ITEM_HORIZONTAL
            }

            RepeatableInput.UP,
            RepeatableInput.DOWN,
            -> {
                if (isGalleryCategoryNavigationActive()) {
                    GameFocusInputChannel.CATEGORY_OR_TAB
                } else {
                    GameFocusInputChannel.ITEM_VERTICAL
                }
            }
        }

    private fun performRepeatableStep(input: RepeatableInput): Boolean {
        if (!isRepeatableInputSupported(input)) return false

        if (input == RepeatableInput.L1 || input == RepeatableInput.R1) {
            if (viewModel.isLibraryOpen.value) {
                val moved =
                    if (input == RepeatableInput.L1) {
                        viewModel.cycleLibraryTabUp(host.activeLibraryTabs)
                    } else {
                        viewModel.cycleLibraryTabDown(host.activeLibraryTabs)
                    }
                if (moved) {
                    viewModel.setLibraryFocusedIndex(0)
                    host.playClickHaptic()
                }
                return moved
            }

            if (input == RepeatableInput.L1) {
                viewModel.triggerPrevLetter()
            } else {
                viewModel.triggerNextLetter()
            }
            host.playClickHaptic()
            return true
        }

        val direction = input.toScrollDirection()
        if (direction == LauncherScrollDirection.NONE) return false

        return when {
            viewModel.newlyAddedFolder.value != null -> {
                host.stepCoreChooserFocus(direction)
            }

            viewModel.isRemoveRomFolderDialogOpen.value -> {
                host.stepRemoveRomFolderFocus(direction)
            }

            viewModel.pairingTargetApp.value != null -> {
                viewModel.stepPairingFocus(direction, host.currentAvailableAndroidApps().size, DEFAULT_PAIRING_GRID_COLUMNS)
            }

            viewModel.isLibraryOptionsMenuExpanded.value -> {
                val focusedLibraryApp = host.currentLibraryApps().getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))
                val count = host.libraryMenuActionCount(focusedLibraryApp)
                when (direction) {
                    LauncherScrollDirection.UP -> viewModel.navigateLibraryMenuUp(count)
                    LauncherScrollDirection.DOWN -> viewModel.navigateLibraryMenuDown(count)
                    else -> false
                }
            }

            viewModel.isMainOptionsMenuExpanded.value -> {
                val count = host.galleryMenuActionCount()
                when (direction) {
                    LauncherScrollDirection.UP -> viewModel.navigateMainMenuUp(count)
                    LauncherScrollDirection.DOWN -> viewModel.navigateMainMenuDown(count)
                    else -> false
                }
            }

            viewModel.isLibraryOpen.value -> {
                val moved = viewModel.stepLibraryFocus(direction, host.currentLibraryApps().size, DEFAULT_LIBRARY_GRID_COLUMNS)
                if (moved) {
                    host.playTickHaptic()
                }
                moved
            }

            else -> {
                when (direction) {
                    LauncherScrollDirection.LEFT -> {
                        viewModel.triggerDpadLeft()
                        host.playTickHaptic()
                        true
                    }

                    LauncherScrollDirection.RIGHT -> {
                        viewModel.triggerDpadStepRight()
                        host.playTickHaptic()
                        true
                    }

                    LauncherScrollDirection.UP -> {
                        val moved = viewModel.cycleCategoryUp(host.activeCategories)
                        if (moved) {
                            host.playClickHaptic()
                        }
                        moved
                    }

                    LauncherScrollDirection.DOWN -> {
                        val moved = viewModel.cycleCategoryDown(host.activeCategories)
                        if (moved) {
                            host.playClickHaptic()
                        }
                        moved
                    }

                    LauncherScrollDirection.NONE -> {
                        false
                    }
                }
            }
        }
    }

    private fun startRepeat(input: RepeatableInput) {
        if (currentRepeatInput == input) return

        currentRepeatInput = input
        repeatJob?.cancel()

        val initialChannel = resolveInputChannel(input)
        val remainingCooldownMs = viewModel.inputPacer.remainingCooldownMs(initialChannel)
        val firstWaitMs =
            if (remainingCooldownMs == 0L) {
                if (performRepeatableStep(input)) {
                    viewModel.inputPacer.commit(initialChannel)
                }
                initialChannel.initialHoldDelayMs
            } else {
                AppLog.d(TAG, "Deferring rapid input $input on ${initialChannel.name} by ${remainingCooldownMs}ms")
                remainingCooldownMs
            }

        repeatJob =
            scope.launch {
                var nextWaitMs = firstWaitMs
                while (isActive && currentRepeatInput == input) {
                    delay(nextWaitMs)
                    if (!isActive || currentRepeatInput != input) return@launch
                    val channel = resolveInputChannel(input)
                    val cooldownMs = viewModel.inputPacer.remainingCooldownMs(channel)
                    if (cooldownMs > 0L) {
                        nextWaitMs = cooldownMs
                        continue
                    }
                    if (performRepeatableStep(input)) {
                        viewModel.inputPacer.commit(channel)
                    }
                    nextWaitMs = channel.minIntervalMs
                }
            }
    }

    private fun resolveJoystickInput(
        x: Float,
        y: Float,
    ): RepeatableInput? {
        if (viewModel.folderToRemove.value != null) return null
        if (!isVerticalOnlyModalActive()) {
            if (x < JOYSTICK_NEGATIVE_THRESHOLD) return RepeatableInput.LEFT
            if (x > JOYSTICK_THRESHOLD) return RepeatableInput.RIGHT
        }
        return when {
            y < JOYSTICK_NEGATIVE_THRESHOLD -> RepeatableInput.UP
            y > JOYSTICK_THRESHOLD -> RepeatableInput.DOWN
            else -> null
        }
    }

    /**
     * Processes a `KeyEvent.ACTION_DOWN` key press.
     *
     * @return `true` when consumed by Game Focus, or `false` when the Activity should delegate to `super.onKeyDown`.
     */
    fun handleKeyDown(
        keyCode: Int,
        repeatCount: Int,
    ): Boolean {
        val isRepeat = repeatCount > 0

        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (!isRepeat) {
                AppLog.i(TAG, "Home key pressed (keyCode=$keyCode) -> returning to main gallery")
                resetToGallery()
                host.onHomeNavigation()
            }
            return true
        }

        if (isPrimaryOverlayOpen()) {
            stopRepeat()
            if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
                return true
            }
            if (isDismissKey(keyCode)) {
                if (!isRepeat) {
                    AppLog.i(TAG, "handleKeyDown: Back/B-Button pressed while a primary overlay is open")
                    host.onPrimaryOverlayBack()
                }
                return true
            }
            if (keyCode == GamePadButton.BUTTON_A.keyCode) {
                if (!isRepeat) {
                    host.dispatchPrimaryOverlayCenterDown()
                }
                return true
            }
            return false
        }

        if (isRepeat) {
            return isLauncherActionKey(keyCode) ||
                isSecondaryModalOpen() ||
                viewModel.isLibraryOpen.value ||
                viewModel.isMainOptionsMenuExpanded.value
        }

        keyCodeToRepeatableInput(keyCode)?.let { input ->
            if (isRepeatableInputSupported(input)) {
                startRepeat(input)
            } else if (input.isDirectional()) {
                stopDirectionalRepeat()
            } else {
                stopRepeat()
            }
            return true
        }

        if (viewModel.newlyAddedFolder.value != null) {
            when {
                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Confirming core chooser dialog via gamepad A")
                    viewModel.triggerConfirmCoreChooser()
                }

                isDismissKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Dismissing core chooser dialog via gamepad B")
                    viewModel.setNewlyAddedFolder(null)
                }
            }
            return true
        }

        viewModel.folderToRemove.value?.let { folder ->
            when {
                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Confirming removal of ROM folder via gamepad A: ${folder.systemName}")
                    host.removeRomFolder(folder)
                    viewModel.setFolderToRemove(null)
                }

                isDismissKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Cancelling folder removal via gamepad B")
                    viewModel.setFolderToRemove(null)
                }
            }
            return true
        }

        if (viewModel.isRemoveRomFolderDialogOpen.value) {
            when {
                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    val selectedFolder = host.romFolders().getOrNull(viewModel.removeRomFolderDialogSelectedIndex.value)
                    if (selectedFolder != null) {
                        AppLog.i(TAG, "Selected folder to remove via gamepad A: ${selectedFolder.systemName}")
                        viewModel.setFolderToRemove(selectedFolder)
                    }
                    viewModel.setRemoveRomFolderDialogOpen(false)
                }

                isDismissKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Closing Remove ROM Folder dialog via gamepad B")
                    viewModel.setRemoveRomFolderDialogOpen(false)
                }
            }
            return true
        }

        viewModel.pairingTargetApp.value?.let { targetApp ->
            when {
                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    val chosenApp = host.currentAvailableAndroidApps().getOrNull(viewModel.pairingFocusedIndex.value)
                    if (chosenApp != null) {
                        AppLog.i(TAG, "Confirming app pairing via Gamepad A: ${targetApp.label} -> ${chosenApp.label}")
                        host.confirmAppPair(targetApp, chosenApp)
                    }
                    viewModel.dismissPairingDialog()
                }

                isDismissKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Closing pairing dialog via Gamepad B")
                    viewModel.dismissPairingDialog()
                }
            }
            return true
        }

        if (viewModel.isLibraryOpen.value) {
            val filteredApps = host.currentLibraryApps()
            val focusedLibraryApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))

            if (viewModel.isLibraryOptionsMenuExpanded.value) {
                when {
                    isConfirmKey(keyCode) -> {
                        stopRepeat()
                        val selectedIndex = viewModel.libraryMenuSelectedIndex.value
                        AppLog.i(TAG, "Library options menu confirmed at index $selectedIndex via gamepad A")
                        host.invokeLibraryMenuAction(focusedLibraryApp, selectedIndex)
                        viewModel.setLibraryOptionsMenuExpanded(false)
                    }

                    isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                        stopRepeat()
                        AppLog.i(TAG, "Closing Library options menu")
                        viewModel.setLibraryOptionsMenuExpanded(false)
                    }
                }
                return true
            }

            when {
                isMenuKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(
                        TAG,
                        "Gamepad Y/Menu pressed in Library -> Opening Library options menu (focused=${focusedLibraryApp?.label})",
                    )
                    viewModel.setLibraryOptionsMenuExpanded(true)
                }

                keyCode == GamePadButton.BUTTON_R2.keyCode -> {
                    stopRepeat()
                    if (viewModel.requestLibraryOpen(false)) {
                        AppLog.i(TAG, "Gamepad R2 pressed -> Closing Library section")
                        host.playClickHaptic()
                    }
                }

                isDismissKey(keyCode) -> {
                    AppLog.i(TAG, "Dismiss/Back pressed in Library -> Closing Library section")
                    stopRepeat()
                    viewModel.setLibraryOpen(false)
                    viewModel.inputPacer.commit(GameFocusInputChannel.LIBRARY_TOGGLE)
                    host.playClickHaptic()
                }

                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    if (focusedLibraryApp != null) {
                        host.launchLibraryTopApp(focusedLibraryApp)
                    }
                }

                keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                    if (viewModel.libraryFocusedIndex.value >= 0) {
                        stopRepeat()
                        val targetApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value)
                        if (targetApp != null) {
                            host.launchBottomApp(targetApp, "Library X")
                        }
                    }
                }

                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    host.toggleButtonPrompts()
                }

                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    AppLog.d(TAG, "Gamepad L3 pressed in Library -> consumed to prevent synthetic DPAD_CENTER launch")
                }
            }
            return true
        }

        if (viewModel.isMainOptionsMenuExpanded.value) {
            when {
                isConfirmKey(keyCode) -> {
                    stopRepeat()
                    val selectedIndex = viewModel.mainMenuSelectedIndex.value
                    AppLog.i(
                        TAG,
                        "Main options menu confirmed at index $selectedIndex via gamepad A for ${viewModel.focusedApp.value?.label}",
                    )
                    host.invokeGalleryMenuAction(selectedIndex)
                    viewModel.setMainOptionsMenuExpanded(false)
                }

                isMenuKey(keyCode) || isDismissKey(keyCode) -> {
                    stopRepeat()
                    AppLog.i(TAG, "Closing Main options menu")
                    viewModel.setMainOptionsMenuExpanded(false)
                }
            }
            return true
        }

        when {
            isMenuKey(keyCode) -> {
                AppLog.i(
                    TAG,
                    "Gamepad Y/Menu pressed in gallery -> opening gallery options menu (entries=${host.currentGalleryApps().size})",
                )
                stopRepeat()
                viewModel.setMainOptionsMenuExpanded(true)
                return true
            }

            isConfirmKey(keyCode) -> {
                val targetApp = viewModel.focusedApp.value
                if (targetApp != null) {
                    stopRepeat()
                    host.launchGalleryTopApp(targetApp)
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_X.keyCode || keyCode == KeyEvent.KEYCODE_X -> {
                val targetApp = viewModel.focusedApp.value
                if (targetApp != null) {
                    stopRepeat()
                    host.launchBottomApp(targetApp, "Gallery X")
                    return true
                }
            }

            keyCode == GamePadButton.BUTTON_R2.keyCode -> {
                stopRepeat()
                if (viewModel.requestLibraryOpen(true)) {
                    AppLog.i(TAG, "Gamepad R2 pressed -> Opening Library section")
                    host.playClickHaptic()
                }
                return true
            }

            isDismissKey(keyCode) -> {
                AppLog.i(TAG, "Dismiss/Back key pressed on main gallery (keyCode=$keyCode)")
                host.onGalleryDismiss()
                return true
            }

            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                host.toggleButtonPrompts()
                return true
            }

            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                AppLog.d(TAG, "Gamepad L3 pressed -> consumed to prevent synthetic DPAD_CENTER launch")
                return true
            }
        }
        return false
    }

    /**
     * Processes a `KeyEvent.ACTION_UP` key release.
     *
     * @return `true` when consumed by Game Focus, or `false` when the Activity should delegate to `super.onKeyUp`.
     */
    fun handleKeyUp(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true
        }

        val releasedInput = keyCodeToRepeatableInput(keyCode)
        if (releasedInput != null && (releasedInput.isDirectional() || !isPrimaryOverlayOpen())) {
            if (currentRepeatInput == releasedInput) {
                stopRepeat()
            }
            return true
        }

        if (!isPrimaryOverlayOpen() && keyCode == GamePadButton.BUTTON_R2.keyCode) {
            return true
        }

        if (isDismissKey(keyCode)) {
            return true
        }

        if (isPrimaryOverlayOpen()) {
            if (keyCode == GamePadButton.BUTTON_A.keyCode) {
                val handled = host.dispatchPrimaryOverlayCenterUp()
                if (!handled) {
                    host.sendPrimaryOverlayFocusRecovery(GamePadButton.BUTTON_A.keyCode)
                }
                return true
            }
            return false
        }

        return isSecondaryModalOpen()
    }

    /**
     * Processes normalized joystick / hat axes `(x, y)` outside primary overlays.
     *
     * @return `true` when consumed by Game Focus, or `false` when neutral on the root gallery.
     */
    fun handleJoystickAxes(
        x: Float,
        y: Float,
    ): Boolean {
        val stickInput = resolveJoystickInput(x, y)
        if (stickInput != null) {
            if (isRepeatableInputSupported(stickInput)) {
                startRepeat(stickInput)
            } else {
                stopDirectionalRepeat()
            }
            return true
        }

        stopDirectionalRepeat()
        return isSecondaryModalOpen() ||
            viewModel.isMainOptionsMenuExpanded.value ||
            viewModel.isLibraryOptionsMenuExpanded.value ||
            viewModel.isLibraryOpen.value
    }
}

internal fun keyCodeToRepeatableInput(keyCode: Int): RepeatableInput? =
    when {
        isUpKey(keyCode) -> RepeatableInput.UP
        isDownKey(keyCode) -> RepeatableInput.DOWN
        isLeftKey(keyCode) -> RepeatableInput.LEFT
        isRightKey(keyCode) -> RepeatableInput.RIGHT
        keyCode == GamePadButton.BUTTON_L1.keyCode -> RepeatableInput.L1
        keyCode == GamePadButton.BUTTON_R1.keyCode -> RepeatableInput.R1
        else -> null
    }

internal fun isLauncherActionKey(keyCode: Int): Boolean =
    keyCodeToRepeatableInput(keyCode) != null ||
        isConfirmKey(keyCode) ||
        isDismissKey(keyCode) ||
        isMenuKey(keyCode) ||
        keyCode == GamePadButton.BUTTON_R2.keyCode ||
        keyCode == GamePadButton.BUTTON_X.keyCode ||
        keyCode == KeyEvent.KEYCODE_X ||
        keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
        keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR

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
