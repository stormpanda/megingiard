package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
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
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
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
internal fun MacroTimelineEditor(
    macro: Macro,
    accentColor: Color,
    onSave: (Macro) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current

    var localName by remember { mutableStateOf(macro.name) }
    var steps by remember { mutableStateOf(macro.steps) }
    var showAddStep by remember { mutableStateOf(false) }
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }
    var deleteStepIndex by remember { mutableStateOf<Int?>(null) }
    var showRecordTouchDialog by remember { mutableStateOf(false) }
    var showRecordGamepadDialog by remember { mutableStateOf(false) }
    var showGamepadRecordingOverlay by remember { mutableStateOf(false) }
    var gamepadRecordingError by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(MacroEditorViewMode.LIST) }
    var shiftModeDefault by remember { mutableStateOf(ShiftMode.END_DELTA) }
    var undoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var loopEnabled by remember { mutableStateOf(macro.loopEnabled) }
    var loopPauseMs by remember { mutableIntStateOf(macro.loopPauseMs) }
    var loopPauseMaxMs by remember { mutableIntStateOf(mtExpandLoopScale(MTE_LOOP_PAUSE_INIT_MAX_MS, macro.loopPauseMs).coerceAtLeast(MTE_LOOP_PAUSE_INIT_MAX_MS)) }
    var randomizeTimingEnabled by remember { mutableStateOf(macro.randomizeTimingEnabled) }
    var randomizeTimingRangeMs by remember { mutableIntStateOf(macro.randomizeTimingRangeMs.coerceIn(10, 100)) }
    // Tracks whether the recording session started GamepadInjector; guards the matching stop() call.
    var recordingStartedGamepad by remember { mutableStateOf(false) }
    // True when the physical recorder path was taken for the current session.
    var usingPhysicalRecorder by remember { mutableStateOf(false) }
    var showPhysicalRecordingSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordedTap by TouchRecordingManager.recordedTap.collectAsState()
    val touchRecordingState by TouchRecordingManager.state.collectAsState()
    val gamepadRecordingState by GamepadRecordingManager.state.collectAsState()
    val physicalRecordingState by PhysicalGamepadRecordingManager.state.collectAsState()
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
    val privdGamepadRecordingEnabled by MacroPadSettings.privdGamepadRecordingEnabled.collectAsState()
    val privdState by PrivdManager.state.collectAsState()
    val physicalRecordingAvailable = privdGamepadRecordingEnabled && privdState == PrivdState.RUNNING

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
            // Wait for InputFlinger to register the uinput device before showing the overlay,
            // so early user taps are not silently dropped.
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
        steps = steps + MacroStep.TouchTap(
            startTimeMs = nextStart,
            durationMs = MTE_DEFAULT_TOUCH_DURATION_MS,
            normX = tap.first,
            normY = tap.second,
        )
        AppLog.d(TAG, "recordedTouchAdded startMs=$nextStart")
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
        AppLog.d(TAG, "recordedTouchGestureAdded count=${shiftedSteps.size} startMs=$nextStart")
        TouchRecordingManager.resetState()
    }

    LaunchedEffect(gamepadRecordingState) {
        val recorded = gamepadRecordingState as? GamepadRecordingState.Done ?: return@LaunchedEffect
        val nextStart = steps.totalDurationMs()
        val shiftedSteps = recorded.steps.offsetBy(nextStart)
        pushUndo(steps)
        steps = steps + shiftedSteps
        AppLog.d(TAG, "recordedGamepadAdded count=${shiftedSteps.size} startMs=$nextStart")
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
        AppLog.d(TAG, "recordedPhysicalGamepadAdded count=${shiftedSteps.size} startMs=$nextStart")
        PhysicalGamepadRecordingManager.resetState()
        usingPhysicalRecorder = false
        showPhysicalRecordingSheet = false
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockPointerEvents(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MTE_TOP_BAR_HEIGHT.dp)
                    .background(colors.surface)
                    .padding(horizontal = MTE_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = colors.accentBorder,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        cursorColor = accentColor,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.macropad_macro_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(
                    onClick = {
                        onSave(
                            macro.copy(
                                name = localName.trim().ifBlank { macro.name },
                                steps = steps,
                                loopEnabled = loopEnabled,
                                loopPauseMs = loopPauseMs,
                                randomizeTimingEnabled = randomizeTimingEnabled,
                                randomizeTimingRangeMs = randomizeTimingRangeMs,
                            )
                        )
                    },
                    enabled = localName.isNotBlank(),
                ) {
                    Text(
                        stringResource(R.string.macropad_macro_editor_save),
                        color = if (localName.isNotBlank()) accentColor else colors.onSurfaceSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            AppDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = MTE_PADDING.dp, vertical = 4.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(start = MTE_PADDING.dp, end = MTE_PADDING.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val previous = undoStack.last()
                                undoStack = undoStack.dropLast(1)
                                redoStack = (redoStack + listOf(steps)).takeLast(MTE_UNDO_STACK_MAX)
                                steps = previous
                                AppLog.d(TAG, "undo stack=${undoStack.size} redo stack=${redoStack.size}")
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
                                AppLog.d(TAG, "undo stack=${undoStack.size} redo stack=${redoStack.size}")
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

                    Spacer(Modifier.width(8.dp))
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
                                text = stringResource(
                                    when (mode) {
                                        ShiftMode.NONE        -> R.string.macropad_macro_editor_shift_none
                                        ShiftMode.START_DELTA -> R.string.macropad_macro_editor_shift_start_delta
                                        ShiftMode.END_DELTA   -> R.string.macropad_macro_editor_shift_end_delta
                                    }
                                ),
                                selected = shiftModeDefault == mode,
                                onClick  = { shiftModeDefault = mode },
                            )
                        }
                    }
                }
            }

            AppDivider()

            MtSectionHeader(R.string.macropad_macro_section_steps)

            if (viewMode == MacroEditorViewMode.LIST) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surface)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (steps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MTE_PADDING.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.macropad_macro_no_steps),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        AppDivider()
                    }

                    steps.forEachIndexed { idx, step ->
                        StepListItem(
                            index = idx,
                            step = step,
                            accentColor = accentColor,
                            onEdit = { editingStepIndex = idx },
                            onDelete = { deleteStepIndex = idx },
                        )
                        AppDivider()
                    }

                    StepActionRow(
                        steps = steps,
                        accentColor = accentColor,
                        onAdd = { showAddStep = true },
                        onRecordGamepad = { requestGamepadRecording() },
                        onRecordTouch = { requestTouchRecording() },
                        onTest = {
                            // Force loopEnabled=false for test runs: a looping macro would run
                            // indefinitely in the editor with no obvious way to stop it.
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
                    AppDivider()
                    MtSectionHeader(R.string.macropad_macro_section_settings)
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
                Column(modifier = Modifier.fillMaxSize().background(colors.surface)) {
                    if (steps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(MTE_PADDING.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.macropad_macro_no_steps),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        MacroVerticalTimeline(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = MTE_TIMELINE_SIDE_PADDING.dp),
                            steps = steps,
                            accentColor = accentColor,
                            onEditStep = { editingStepIndex = it },
                        )
                    }
                }
            }
        }

        val recordingState = gamepadRecordingState as? GamepadRecordingState.Recording
        if (showGamepadRecordingOverlay && recordingState != null) {
            GamepadRecordingOverlay(
                state = recordingState,
                swapFaceButtons = swapFaceButtons,
                onButtonDown = { code ->
                    GamepadRecordingManager.recordButtonDown(code)
                    GamepadInjector.buttonDown(code)
                },
                onButtonUp = { code ->
                    GamepadRecordingManager.recordButtonUp(code)
                    GamepadInjector.buttonUp(code)
                },
                onDpadChanged = { dirX, dirY ->
                    GamepadRecordingManager.setDpad(dirX, dirY)
                    GamepadInjector.hat(axis = 0, value = dirX)
                    GamepadInjector.hat(axis = 1, value = dirY)
                },
                onJoystickChanged = { stick, x, y ->
                    // Use the snapped (octant-quantised) values returned by the manager for
                    // live injection. This ensures the target app sees the same input during
                    // recording as it will receive during macro playback.
                    val (snapX, snapY) = GamepadRecordingManager.setJoystick(stick, x, y)
                    when (stick) {
                        JoystickStick.LEFT -> {
                            GamepadInjector.joystick(GamepadKeycodes.ABS_X, normalizedAxisValue(snapX))
                            GamepadInjector.joystick(GamepadKeycodes.ABS_Y, normalizedAxisValue(snapY))
                        }

                        JoystickStick.RIGHT -> {
                            GamepadInjector.joystick(GamepadKeycodes.ABS_Z, normalizedAxisValue(snapX))
                            GamepadInjector.joystick(GamepadKeycodes.ABS_RZ, normalizedAxisValue(snapY))
                        }
                    }
                },
                onStop = {
                    scope.launch { GamepadRecordingManager.finishRecording() }
                },
                onCancel = {
                    scope.launch { GamepadRecordingManager.cancelRecording() }
                    if (recordingStartedGamepad) GamepadInjector.stop()
                    recordingStartedGamepad = false
                    showGamepadRecordingOverlay = false
                },
            )
        }

        val physicalRecordingStateSnapshot = physicalRecordingState
        if (showPhysicalRecordingSheet && usingPhysicalRecorder) {
            PhysicalGamepadRecordingSheet(
                state = physicalRecordingStateSnapshot,
                swapFaceButtons = swapFaceButtons,
                onStop = {
                    PhysicalGamepadRecordingManager.finishRecording()
                },
                onCancel = {
                    PhysicalGamepadRecordingManager.cancelRecording()
                    usingPhysicalRecorder = false
                    showPhysicalRecordingSheet = false
                },
            )
        }
    }

    if (showAddStep || editingStepIndex != null) {
        val stepToEdit: MacroStep? = editingStepIndex?.let { steps[it] }
        val suggestedStartTimeMs = steps.totalDurationMs()
        MacroStepEditDialog(
            step = stepToEdit,
            accentColor = accentColor,
            suggestedStartTimeMs = suggestedStartTimeMs,
            initialShiftMode = shiftModeDefault,
            onConfirm = { newStep, shiftMode ->
                if (editingStepIndex != null) {
                    val idx = editingStepIndex!!
                    val oldStep = steps[idx]
                    val updated = applyShiftSubsequent(
                        steps       = steps,
                        editedIndex = idx,
                        oldStep     = oldStep,
                        newStep     = newStep,
                        mode        = shiftMode,
                        maxTimeMs   = MTE_TIMING_MAX_MS,
                    )
                    pushUndo(steps)
                    steps = updated
                    editingStepIndex = null
                } else {
                    val updated = if (shiftMode == ShiftMode.NONE) {
                        steps + newStep
                    } else {
                        val shifted = steps.map { existing ->
                            if (existing.startTimeMs >= newStep.startTimeMs) {
                                existing.withStartTime(
                                    (existing.startTimeMs + newStep.durationMs)
                                        .coerceIn(0L, MTE_TIMING_MAX_MS),
                                )
                            } else {
                                existing
                            }
                        }
                        shifted + newStep
                    }
                    pushUndo(steps)
                    steps = updated
                    showAddStep = false
                }
            },
            onDismiss = { showAddStep = false; editingStepIndex = null },
        )
    }

    if (deleteStepIndex != null) {
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { deleteStepIndex = null },
            title = { Text(stringResource(R.string.macropad_macro_step_delete_title), color = colors.onSurface) },
            text = { Text(stringResource(R.string.macropad_macro_step_delete_confirm), color = colors.onSurfaceSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    deleteStepIndex?.let { idx ->
                        pushUndo(steps)
                        steps = steps.filterIndexed { i, _ -> i != idx }
                    }
                    deleteStepIndex = null
                }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStepIndex = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }

    gamepadRecordingError?.let { message ->
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { gamepadRecordingError = null },
            title = {
                Text(
                    text = stringResource(R.string.macropad_macro_record_gamepad_error_title),
                    color = colors.onSurface,
                )
            },
            text = {
                Text(
                    text = message,
                    color = colors.onSurfaceSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { gamepadRecordingError = null }) {
                    Text(
                        text = stringResource(R.string.config_ok),
                        color = colors.accent,
                    )
                }
            },
        )
    }
}
