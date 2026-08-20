package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadConfirmDialog
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MacroEditorTutorialDialog
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MacroTimelineEditor"

private const val MTE_TOP_BAR_HEIGHT = 68
private const val MTE_PADDING = 16
private const val MTE_DEFAULT_TOUCH_DURATION_MS = 100L
private const val MTE_UNDO_STACK_MAX = 50
private const val MTE_TIMING_MAX_MS = 10_000L
private const val MTE_PULSE_DURATION_MS = 1400
private const val MTE_PULSE_ACCENT_ALPHA = 0.35f
private const val MTE_PULSE_SURFACE_ALPHA = 0.55f

// Post-start delay before showing the recording overlay: waits for InputFlinger to register
// the uinput device so early user taps are not silently dropped (mirrors MAC_GAMEPAD_INJECTOR_INIT_MS).
private const val MTE_GAMEPAD_INJECTOR_INIT_MS = 200L

private const val MTE_VIEW_CHIP_SPACING = 6

private enum class MacroEditorViewMode { LIST, TIMELINE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MacroTimelineSubPageContent(
    macro: Macro,
    accentColor: Color,
    onOpenAddStep: () -> Unit,
    onOpenEditStep: (stepIndex: Int) -> Unit,
    onDiscard: () -> Unit = {},
    onSave: (Macro) -> Unit,
    onDelete: () -> Unit = {},
) {
    val colors = LocalAppColors.current

    var localName by remember(macro) { mutableStateOf(macro.name) }
    var steps by remember(macro) { mutableStateOf(macro.steps) }
    var deleteStepIndex by remember { mutableStateOf<Int?>(null) }
    var showRecordTouchDialog by remember { mutableStateOf(false) }
    var showRecordGamepadDialog by remember { mutableStateOf(false) }
    var showGamepadRecordingOverlay by remember { mutableStateOf(false) }
    var gamepadRecordingError by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(MacroEditorViewMode.LIST) }
    var shiftModeDefault by remember { mutableStateOf(ShiftMode.END_DELTA) }
    var undoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var loopEnabled by remember(macro) { mutableStateOf(macro.loopEnabled) }
    var loopPauseMs by remember(macro) { mutableIntStateOf(macro.loopPauseMs) }
    var loopPauseMaxMs by remember(macro) {
        mutableIntStateOf(mtExpandLoopScale(MTE_LOOP_PAUSE_INIT_MAX_MS, macro.loopPauseMs).coerceAtLeast(MTE_LOOP_PAUSE_INIT_MAX_MS))
    }
    var randomizeTimingEnabled by remember(macro) { mutableStateOf(macro.randomizeTimingEnabled) }
    var randomizeTimingRangeMs by remember(macro) { mutableIntStateOf(macro.randomizeTimingRangeMs.coerceIn(10, 100)) }
    var recordingStartedGamepad by remember { mutableStateOf(false) }
    var usingPhysicalRecorder by remember { mutableStateOf(false) }
    var showPhysicalRecordingSheet by remember { mutableStateOf(false) }

    val currentMacro =
        macro.copy(
            name = localName.trim().ifBlank { macro.name },
            steps = steps,
            loopEnabled = loopEnabled,
            loopPauseMs = loopPauseMs,
            randomizeTimingEnabled = randomizeTimingEnabled,
            randomizeTimingRangeMs = randomizeTimingRangeMs,
        )
    val hasChanges = currentMacro != macro
    val isConfirmEnabled = localName.isNotBlank()

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    onSave(currentMacro)
                }
            },
            onDiscard = onDiscard,
        )

    val pulseTransition = rememberInfiniteTransition(label = "macroSavePulse")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = MTE_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "macroSavePulseFraction",
    )
    val saveCardBgColor =
        if (hasChanges) {
            lerp(
                colors.surface.copy(alpha = MTE_PULSE_SURFACE_ALPHA),
                colors.accent.copy(alpha = MTE_PULSE_ACCENT_ALPHA),
                pulseFraction,
            )
        } else {
            null
        }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordedTap by TouchRecordingManager.recordedTap.collectAsState()
    val touchRecordingState by TouchRecordingManager.state.collectAsState()
    val gamepadRecordingState by GamepadRecordingManager.state.collectAsState()
    val physicalRecordingState by PhysicalGamepadRecordingManager.state.collectAsState()
    val privdState by PrivdManager.state.collectAsState()
    val physicalRecordingAvailable = privdState == PrivdState.RUNNING
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    fun pushUndo(previous: List<MacroStep>) {
        val bounded = (undoStack + listOf(previous)).takeLast(MTE_UNDO_STACK_MAX)
        undoStack = bounded
        redoStack = emptyList()
    }

    fun startGamepadRecording() {
        if (physicalRecordingAvailable) {
            AppLog.i(TAG, "startGamepadRecording() → physical path")
            usingPhysicalRecorder = true
            showPhysicalRecordingSheet = true
            showRecordGamepadDialog = false
            PhysicalGamepadRecordingManager.startRecording()
            return
        }
        val wasAlreadyRunning = GamepadInjector.isRunning
        GamepadInjector.start(context)
        if (!GamepadInjector.isRunning) {
            AppLog.e(TAG, "gamepad recording overlay aborted because GamepadInjector failed to start")
            gamepadRecordingError = context.getString(R.string.macropad_macro_record_gamepad_error_start)
            showRecordGamepadDialog = false
            showGamepadRecordingOverlay = false
            return
        }
        recordingStartedGamepad = !wasAlreadyRunning
        scope.launch {
            delay(MTE_GAMEPAD_INJECTOR_INIT_MS)
            GamepadRecordingManager.startRecording()
            showRecordGamepadDialog = false
            showGamepadRecordingOverlay = true
        }
    }

    fun requestTouchRecording() {
        showRecordTouchDialog = true
    }

    fun requestGamepadRecording() {
        if (physicalRecordingAvailable || MacroPadSettings.skipGamepadRecordDialog.value) {
            startGamepadRecording()
        } else {
            showRecordGamepadDialog = true
        }
    }

    LaunchedEffect(recordedTap) {
        val tap = recordedTap ?: return@LaunchedEffect
        val nextStart = steps.totalDurationMs()
        pushUndo(steps)
        steps = steps +
            MacroStep.TouchTap(
                startTimeMs = nextStart,
                durationMs = MTE_DEFAULT_TOUCH_DURATION_MS,
                normX = tap.first,
                normY = tap.second,
            )
        TouchRecordingManager.consumeRecordedTap()
    }

    LaunchedEffect(touchRecordingState) {
        val recorded = touchRecordingState as? TouchRecordingState.Done ?: return@LaunchedEffect
        if (recorded.steps.isEmpty()) {
            TouchRecordingManager.resetState()
            return@LaunchedEffect
        }
        val nextStart = steps.totalDurationMs()
        val shiftedSteps = recorded.steps.offsetBy(nextStart)
        pushUndo(steps)
        steps = steps + shiftedSteps
        TouchRecordingManager.resetState()
    }

    LaunchedEffect(gamepadRecordingState) {
        val recorded = gamepadRecordingState as? GamepadRecordingState.Done ?: return@LaunchedEffect
        val nextStart = steps.totalDurationMs()
        val shiftedSteps = recorded.steps.offsetBy(nextStart)
        pushUndo(steps)
        steps = steps + shiftedSteps
        if (recordingStartedGamepad) GamepadInjector.stop()
        recordingStartedGamepad = false
        GamepadRecordingManager.resetState()
        showGamepadRecordingOverlay = false
    }

    LaunchedEffect(physicalRecordingState) {
        val recorded = physicalRecordingState as? GamepadRecordingState.Done ?: return@LaunchedEffect
        val nextStart = steps.totalDurationMs()
        val shiftedSteps = recorded.steps.offsetBy(nextStart)
        pushUndo(steps)
        steps = steps + shiftedSteps
        PhysicalGamepadRecordingManager.resetState()
        usingPhysicalRecorder = false
        showPhysicalRecordingSheet = false
    }

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_macro_editor_save),
        description = stringResource(R.string.macropad_macro_editor_save),
        cardBgColor = saveCardBgColor,
        saveActionText = stringResource(R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.help_timeline_name_label),
        value = localName,
        onValueChange = { localName = it },
        icon = Icons.Rounded.Edit,
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.macropad_macro_editor_view_label),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        AppSelectableChip(
            text = stringResource(R.string.macropad_macro_editor_view_list),
            selected = viewMode == MacroEditorViewMode.LIST,
            onClick = { viewMode = MacroEditorViewMode.LIST },
            leadingIcon = { color ->
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Spacer(Modifier.width(MTE_VIEW_CHIP_SPACING.dp))
        AppSelectableChip(
            text = stringResource(R.string.macropad_macro_editor_view_timeline),
            selected = viewMode == MacroEditorViewMode.TIMELINE,
            onClick = { viewMode = MacroEditorViewMode.TIMELINE },
            leadingIcon = { color ->
                Icon(
                    imageVector = Icons.Rounded.Timeline,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }

    if (viewMode == MacroEditorViewMode.LIST) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (undoStack.isNotEmpty()) {
                        val previous = undoStack.last()
                        undoStack = undoStack.dropLast(1)
                        redoStack = (redoStack + listOf(steps)).takeLast(MTE_UNDO_STACK_MAX)
                        steps = previous
                    }
                },
                enabled = undoStack.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.macropad_macro_editor_undo),
                    tint = if (undoStack.isNotEmpty()) colors.onSurface else colors.onSurfaceSecondary,
                )
            }
            IconButton(
                onClick = {
                    if (redoStack.isNotEmpty()) {
                        val restored = redoStack.last()
                        redoStack = redoStack.dropLast(1)
                        undoStack = (undoStack + listOf(steps)).takeLast(MTE_UNDO_STACK_MAX)
                        steps = restored
                    }
                },
                enabled = redoStack.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.macropad_macro_editor_redo),
                    tint = if (redoStack.isNotEmpty()) colors.onSurface else colors.onSurfaceSecondary,
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.macropad_macro_editor_shift_subsequent),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(MTE_VIEW_CHIP_SPACING.dp)) {
                ShiftMode.entries.forEach { mode ->
                    AppSelectableChip(
                        text =
                            stringResource(
                                when (mode) {
                                    ShiftMode.NONE -> R.string.macropad_macro_editor_shift_none
                                    ShiftMode.START_DELTA -> R.string.macropad_macro_editor_shift_start_delta
                                    ShiftMode.END_DELTA -> R.string.macropad_macro_editor_shift_end_delta
                                },
                            ),
                        selected = shiftModeDefault == mode,
                        onClick = { shiftModeDefault = mode },
                    )
                }
            }
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_steps),
        color = accentColor,
    )

    if (viewMode == MacroEditorViewMode.LIST) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (steps.isEmpty()) {
                GamepadEmptyState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.macropad_macro_no_steps),
                    description = stringResource(R.string.macro_timeline_list_empty_desc),
                    actionText = stringResource(R.string.macropad_macro_add_step),
                    onAction = onOpenAddStep,
                )
            } else {
                steps.forEachIndexed { idx, step ->
                    StepListItem(
                        index = idx,
                        step = step,
                        accentColor = accentColor,
                        onEdit = { onOpenEditStep(idx) },
                        onDelete = { deleteStepIndex = idx },
                    )
                }
            }

            StepActionRow(
                steps = steps,
                accentColor = accentColor,
                onAdd = onOpenAddStep,
                onRecordGamepad = { requestGamepadRecording() },
                onRecordTouch = { requestTouchRecording() },
                onTest = {
                    MacroExecutor.execute(
                        macro.copy(
                            name = localName.trim().ifBlank { macro.name },
                            steps = steps,
                            loopEnabled = false,
                            loopPauseMs = 0,
                            randomizeTimingEnabled = randomizeTimingEnabled,
                            randomizeTimingRangeMs = randomizeTimingRangeMs,
                        ),
                    )
                },
            )

            GamepadSectionHeader(
                text = stringResource(R.string.macropad_macro_section_settings),
                color = accentColor,
            )
            MtLoopSection(
                loopEnabled = loopEnabled,
                loopPauseMs = loopPauseMs,
                loopPauseMaxMs = loopPauseMaxMs,
                accentColor = accentColor,
                onLoopEnabledChange = { loopEnabled = it },
                onLoopPauseMsChange = { loopPauseMs = it },
                onLoopPauseMaxMsChange = { loopPauseMaxMs = it },
            )
            MtRandomizationSection(
                randomizeEnabled = randomizeTimingEnabled,
                randomizeRangeMs = randomizeTimingRangeMs,
                accentColor = accentColor,
                onRandomizeEnabledChange = { randomizeTimingEnabled = it },
                onRandomizeRangeMsChange = { randomizeTimingRangeMs = it },
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (steps.isEmpty()) {
                GamepadEmptyState(
                    icon = Icons.Rounded.Timeline,
                    title = stringResource(R.string.macropad_macro_no_steps),
                    description = stringResource(R.string.macro_timeline_empty_desc),
                    actionText = stringResource(R.string.macropad_macro_add_step),
                    onAction = onOpenAddStep,
                )
            } else {
                MacroVerticalTimeline(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = MTE_PADDING.dp),
                    steps = steps,
                    accentColor = accentColor,
                    onEditStep = onOpenEditStep,
                )
            }
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_delete_title),
        color = accentColor,
    )
    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_macro_delete_title),
        confirmTitle = stringResource(R.string.macropad_macro_delete_confirm_title, macro.name),
        description = stringResource(R.string.macropad_macro_delete_confirm),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        icon = Icons.Rounded.Delete,
        isDestructive = true,
        onConfirm = onDelete,
    )

    if (deleteStepIndex != null) {
        val idx = deleteStepIndex!!
        GamepadConfirmDialog(
            title = stringResource(R.string.macropad_macro_step_delete_title),
            message = stringResource(R.string.macropad_macro_step_delete_confirm),
            confirmText = stringResource(R.string.macropad_editor_confirm),
            cancelText = stringResource(R.string.macropad_editor_cancel),
            isDestructive = true,
            onConfirm = {
                pushUndo(steps)
                steps = steps.filterIndexed { i, _ -> i != idx }
                deleteStepIndex = null
            },
            onDismiss = { deleteStepIndex = null },
        )
    }

    if (showRecordTouchDialog) {
        TouchRecordStartDialog(
            onRecordTap = {
                if (!ScreenCaptureManager.isCapturing.value) AppStateManager.requestMirrorStart()
                TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
                showRecordTouchDialog = false
            },
            onRecordGesture = {
                if (!ScreenCaptureManager.isCapturing.value) AppStateManager.requestMirrorStart()
                TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
                showRecordTouchDialog = false
            },
            onCancel = { showRecordTouchDialog = false },
        )
    }

    if (showRecordGamepadDialog) {
        GamepadRecordStartDialog(
            onStart = { startGamepadRecording() },
            onCancel = { showRecordGamepadDialog = false },
            onDontShowAgain = {
                MacroPadSettings.setSkipGamepadRecordDialog(true)
                startGamepadRecording()
            },
        )
    }

    if (showPhysicalRecordingSheet) {
        PhysicalGamepadRecordingSheet(
            state = physicalRecordingState,
            swapFaceButtons = swapFaceButtons,
            onStop = { PhysicalGamepadRecordingManager.finishRecording() },
            onCancel = { PhysicalGamepadRecordingManager.cancelRecording() },
        )
    }

    val activeGamepadRecording = gamepadRecordingState as? GamepadRecordingState.Recording
    if (showGamepadRecordingOverlay && activeGamepadRecording != null) {
        GamepadRecordingOverlay(
            state = activeGamepadRecording,
            swapFaceButtons = swapFaceButtons,
            onButtonDown = { code ->
                GamepadInjector.buttonDown(code)
                GamepadRecordingManager.recordButtonDown(code)
            },
            onButtonUp = { code ->
                GamepadInjector.buttonUp(code)
                GamepadRecordingManager.recordButtonUp(code)
            },
            onDpadChanged = { x, y ->
                GamepadInjector.hat(0, x)
                GamepadInjector.hat(1, y)
                GamepadRecordingManager.setDpad(x, y)
            },
            onJoystickChanged = { stick, x, y ->
                val snapped = GamepadRecordingManager.setJoystick(stick, x, y)
                val axisX = if (stick == JoystickStick.LEFT) GamepadKeycodes.ABS_X else GamepadKeycodes.ABS_Z
                val axisY = if (stick == JoystickStick.LEFT) GamepadKeycodes.ABS_Y else GamepadKeycodes.ABS_RZ
                GamepadInjector.joystick(axisX, (snapped.first * 32767).toInt())
                GamepadInjector.joystick(axisY, (snapped.second * 32767).toInt())
            },
            onStop = {
                scope.launch {
                    GamepadRecordingManager.finishRecording()
                }
            },
            onCancel = {
                scope.launch {
                    GamepadRecordingManager.cancelRecording()
                }
            },
        )
    }
}
