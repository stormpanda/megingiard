package com.stormpanda.megingiard.macropad

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
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MacroTimelineEditor"

private const val MTE_TOP_BAR_HEIGHT = 68
private const val MTE_PADDING = 16
private const val MTE_DEFAULT_TOUCH_DURATION_MS = 100L
private const val MTE_UNDO_STACK_MAX = 50
private const val MTE_TIMING_MAX_MS = 10_000L

// Post-start delay before showing the recording overlay: waits for InputFlinger to register
// the uinput device so early user taps are not silently dropped (mirrors MAC_GAMEPAD_INJECTOR_INIT_MS).
private const val MTE_GAMEPAD_INJECTOR_INIT_MS = 200L

private const val MTE_VIEW_CHIP_SPACING = 6

private enum class MacroEditorViewMode { LIST, TIMELINE }

@Composable
internal fun MacroTimelineSubPageContent(
    macro: Macro,
    accentColor: Color,
    onOpenAddStep: () -> Unit,
    onOpenEditStep: (stepIndex: Int) -> Unit,
    onSave: (Macro) -> Unit,
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordedTap by TouchRecordingManager.recordedTap.collectAsState()
    val touchRecordingState by TouchRecordingManager.state.collectAsState()
    val gamepadRecordingState by GamepadRecordingManager.state.collectAsState()
    val physicalRecordingState by PhysicalGamepadRecordingManager.state.collectAsState()
    val privdState by PrivdManager.state.collectAsState()
    val physicalRecordingAvailable = privdState == PrivdState.RUNNING

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

    GamepadTextFieldCard(
        title = stringResource(R.string.help_timeline_name_label),
        value = localName,
        onValueChange = { localName = it },
        icon = Icons.Rounded.Edit,
        modifier = Modifier.firstDeckItem(),
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

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_editor_save),
        description = stringResource(R.string.macropad_macro_editor_save),
        actionText = stringResource(R.string.macropad_macro_editor_save),
        enabled = localName.isNotBlank(),
        onClick = {
            onSave(
                macro.copy(
                    name = localName.trim().ifBlank { macro.name },
                    steps = steps,
                    loopEnabled = loopEnabled,
                    loopPauseMs = loopPauseMs,
                    randomizeTimingEnabled = randomizeTimingEnabled,
                    randomizeTimingRangeMs = randomizeTimingRangeMs,
                ),
            )
        },
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
}

@Composable
private fun MacroTimelineHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_timeline_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_timeline_intro))

        HelpSection(stringResource(R.string.help_timeline_section_topbar))
        HelpEntry(
            label = stringResource(R.string.help_timeline_name_label),
            description = stringResource(R.string.help_timeline_name_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_timeline_cancel_label),
            description = stringResource(R.string.help_timeline_cancel_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_timeline_save_label),
            description = stringResource(R.string.help_timeline_save_desc),
        )

        HelpSection(stringResource(R.string.help_timeline_section_secondary))
        HelpEntry(
            icon = Icons.AutoMirrored.Rounded.Undo,
            label = stringResource(R.string.help_timeline_undo_label),
            description = stringResource(R.string.help_timeline_undo_desc),
        )
        HelpEntry(
            icon = Icons.AutoMirrored.Rounded.Redo,
            label = stringResource(R.string.help_timeline_redo_label),
            description = stringResource(R.string.help_timeline_redo_desc),
        )
        HelpEntry(
            icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
            label = stringResource(R.string.help_timeline_view_list_label),
            description = stringResource(R.string.help_timeline_view_list_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Timeline,
            label = stringResource(R.string.help_timeline_view_timeline_label),
            description = stringResource(R.string.help_timeline_view_timeline_desc),
        )
        HelpEntry(
            icon = null,
            label = stringResource(R.string.help_timeline_shift_label),
            description = stringResource(R.string.help_timeline_shift_desc),
        )

        HelpSection(stringResource(R.string.help_timeline_section_steps))
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.help_timeline_add_step_label),
            description = stringResource(R.string.help_timeline_add_step_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.TouchApp,
            label = stringResource(R.string.help_timeline_record_touch_label),
            description = stringResource(R.string.help_timeline_record_touch_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.SportsEsports,
            label = stringResource(R.string.help_timeline_record_gamepad_label),
            description = stringResource(R.string.help_timeline_record_gamepad_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.help_timeline_test_run_label),
            description = stringResource(R.string.help_timeline_test_run_desc),
        )

        HelpSection(stringResource(R.string.help_timeline_section_step_items))
        HelpEntry(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.help_timeline_step_edit_label),
            description = stringResource(R.string.help_timeline_step_edit_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.help_timeline_step_delete_label),
            description = stringResource(R.string.help_timeline_step_delete_desc),
        )

        HelpSection(stringResource(R.string.help_timeline_section_loop))
        HelpEntry(
            icon = Icons.Rounded.Repeat,
            label = stringResource(R.string.help_timeline_loop_label),
            description = stringResource(R.string.help_timeline_loop_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(R.string.help_timeline_randomise_label),
            description = stringResource(R.string.help_timeline_randomise_desc),
        )
    }
}
