package com.stormpanda.megingiard.macropad

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private const val TAG = "MacroTimelineEditor"

private const val MT_TOP_BAR_HEIGHT = 68
private const val MT_PADDING = 16
private const val MT_TICK_INTERVAL_MS = 500L
private const val MT_AXIS_TEXT_SIZE_SP = 11
private const val MT_BAR_CORNER_RADIUS = 4
private const val MT_DEFAULT_TOUCH_DURATION_MS = 100L
private const val MT_UNDO_STACK_MAX = 50
private const val MT_VERTICAL_DP_PER_MS = 0.22f
private const val MT_VERTICAL_AXIS_WIDTH = 52
private const val MT_VERTICAL_BAR_PADDING = 3f
private const val MT_BAR_LABEL_TEXT_SIZE_SP = 10
private const val MT_TIMELINE_SIDE_PADDING = 10
private const val MT_VIEW_CHIP_CORNER = 20
private const val MT_VIEW_CHIP_H_PADDING = 12
private const val MT_VIEW_CHIP_V_PADDING = 6
private const val MT_VIEW_CHIP_SPACING = 6
private const val MT_TIMING_MAX_MS = 10_000L
private const val MT_NEW_STEP_START_OFFSET_MS = 2_000L

private enum class MacroEditorViewMode { LIST, TIMELINE }

private fun assignLanes(steps: List<MacroStep>): List<Int> {
    val sortedIndices = steps.indices.sortedBy { steps[it].startTimeMs }
    val result = IntArray(steps.size)
    val laneEndTimes = mutableListOf<Long>()
    sortedIndices.forEach { originalIdx ->
        val step = steps[originalIdx]
        val laneIdx = laneEndTimes.indexOfFirst { it <= step.startTimeMs }
        if (laneIdx == -1) {
            result[originalIdx] = laneEndTimes.size
            laneEndTimes.add(step.endTimeMs())
        } else {
            result[originalIdx] = laneIdx
            laneEndTimes[laneIdx] = step.endTimeMs()
        }
    }
    return result.toList()
}

private fun MacroStep.withStartTime(newStartTimeMs: Long): MacroStep = when (this) {
    is MacroStep.GamepadButtonTap -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.JoystickMove -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.DPadTap -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.TouchTap -> copy(startTimeMs = newStartTimeMs)
}

private fun List<MacroStep>.offsetBy(offsetMs: Long): List<MacroStep> =
    map { step -> step.withStartTime(step.startTimeMs + offsetMs) }

private fun dirArrow(dirX: Int, dirY: Int): String = when {
    dirX > 0 && dirY < 0 -> "↗"
    dirX > 0 && dirY == 0 -> "→"
    dirX > 0 && dirY > 0 -> "↘"
    dirX == 0 && dirY < 0 -> "↑"
    dirX == 0 && dirY > 0 -> "↓"
    dirX < 0 && dirY < 0 -> "↖"
    dirX < 0 && dirY == 0 -> "←"
    dirX < 0 && dirY > 0 -> "↙"
    else -> "·"
}

private fun joyDirArrow(x: Float, y: Float): String {
    val mag = sqrt(x * x + y * y)
    if (mag < 0.1f) return "·"
    val nx = when {
        x / mag > 0.5f -> 1
        x / mag < -0.5f -> -1
        else -> 0
    }
    val ny = when {
        y / mag > 0.5f -> 1
        y / mag < -0.5f -> -1
        else -> 0
    }
    return dirArrow(nx, ny)
}

private fun shortStepLabel(step: MacroStep, swapFaceButtons: Boolean): String = when (step) {
    is MacroStep.GamepadButtonTap -> gamepadCodeDisplayShortLabel(step.btnCode, swapFaceButtons)
    is MacroStep.JoystickMove -> {
        val stick = if (step.stick == JoystickStick.LEFT) "L" else "R"
        "$stick${joyDirArrow(step.x, step.y)}"
    }
    is MacroStep.DPadTap -> dirArrow(step.dirX, step.dirY)
    is MacroStep.TouchTap -> "Tap"
}

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
    var shiftSubsequentDefault by remember { mutableStateOf(true) }
    var undoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordedTap by TouchRecordingManager.recordedTap.collectAsState()
    val gamepadRecordingState by GamepadRecordingManager.state.collectAsState()
    val swapFaceButtons by SettingsManager.gamepadSwapFaceButtons.collectAsState()

    fun pushUndo(previous: List<MacroStep>) {
        val bounded = (undoStack + listOf(previous)).takeLast(MT_UNDO_STACK_MAX)
        undoStack = bounded
        redoStack = emptyList()
    }

    fun startGamepadRecording() {
        GamepadInjector.start(context)
        if (!GamepadInjector.isRunning) {
            AppLog.e(TAG, "gamepad recording overlay aborted because GamepadInjector failed to start")
            gamepadRecordingError = context.getString(R.string.macropad_macro_record_gamepad_error_start)
            showRecordGamepadDialog = false
            showGamepadRecordingOverlay = false
            return
        }
        GamepadRecordingManager.startRecording()
        showRecordGamepadDialog = false
        showGamepadRecordingOverlay = true
    }

    fun requestTouchRecording() {
        if (SettingsManager.skipTouchRecordDialog.value) {
            if (!ScreenCaptureManager.isCapturing.value) AppStateManager.requestMirrorStart()
            TouchRecordingManager.requestRecording()
        } else {
            showRecordTouchDialog = true
        }
    }

    fun requestGamepadRecording() {
        if (SettingsManager.skipGamepadRecordDialog.value) {
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
            durationMs = MT_DEFAULT_TOUCH_DURATION_MS,
            normX = tap.first,
            normY = tap.second,
        )
        AppLog.d(TAG, "recordedTouchAdded startMs=$nextStart")
        TouchRecordingManager.consumeRecordedTap()
    }

    LaunchedEffect(gamepadRecordingState) {
        val recorded = gamepadRecordingState as? GamepadRecordingState.Done ?: return@LaunchedEffect
        val nextStart = steps.totalDurationMs()
        val shiftedSteps = recorded.steps.offsetBy(nextStart)
        pushUndo(steps)
        steps = steps + shiftedSteps
        AppLog.d(TAG, "recordedGamepadAdded count=${shiftedSteps.size} startMs=$nextStart")
        GamepadInjector.stop()
        GamepadRecordingManager.resetState()
        showGamepadRecordingOverlay = false
    }

    if (showRecordTouchDialog) {
        TouchRecordStartDialog(
            onStart = {
                if (!ScreenCaptureManager.isCapturing.value) AppStateManager.requestMirrorStart()
                TouchRecordingManager.requestRecording()
                showRecordTouchDialog = false
            },
            onCancel = { showRecordTouchDialog = false },
            onDontShowAgain = {
                SettingsManager.setSkipTouchRecordDialog(true)
                if (!ScreenCaptureManager.isCapturing.value) AppStateManager.requestMirrorStart()
                TouchRecordingManager.requestRecording()
                showRecordTouchDialog = false
            },
        )
    }

    if (showRecordGamepadDialog) {
        GamepadRecordStartDialog(
            onStart = { startGamepadRecording() },
            onCancel = { showRecordGamepadDialog = false },
            onDontShowAgain = {
                SettingsManager.setSkipGamepadRecordDialog(true)
                startGamepadRecording()
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MT_TOP_BAR_HEIGHT.dp)
                    .background(colors.surface)
                    .padding(horizontal = MT_PADDING.dp),
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
                        onSave(macro.copy(name = localName.trim().ifBlank { macro.name }, steps = steps))
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

            HorizontalDivider(color = colors.divider)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = MT_PADDING.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_editor_view_label),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(8.dp))
                MacroViewModeChip(
                    text = stringResource(R.string.macropad_macro_editor_view_list),
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    selected = viewMode == MacroEditorViewMode.LIST,
                    onClick = { viewMode = MacroEditorViewMode.LIST },
                )
                Spacer(Modifier.width(MT_VIEW_CHIP_SPACING.dp))
                MacroViewModeChip(
                    text = stringResource(R.string.macropad_macro_editor_view_timeline),
                    icon = Icons.Rounded.Timeline,
                    selected = viewMode == MacroEditorViewMode.TIMELINE,
                    onClick = { viewMode = MacroEditorViewMode.TIMELINE },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(start = MT_PADDING.dp, end = MT_PADDING.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val previous = undoStack.last()
                            undoStack = undoStack.dropLast(1)
                            redoStack = (redoStack + listOf(steps)).takeLast(MT_UNDO_STACK_MAX)
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
                            undoStack = (undoStack + listOf(steps)).takeLast(MT_UNDO_STACK_MAX)
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
                Switch(
                    checked = shiftSubsequentDefault,
                    onCheckedChange = { shiftSubsequentDefault = it },
                )
            }

            HorizontalDivider(color = colors.divider)

            if (viewMode == MacroEditorViewMode.LIST) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (steps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MT_PADDING.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.macropad_macro_no_steps),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        HorizontalDivider(color = colors.divider)
                    }

                    steps.forEachIndexed { idx, step ->
                        StepListItem(
                            index = idx,
                            step = step,
                            accentColor = accentColor,
                            onEdit = { editingStepIndex = idx },
                            onDelete = { deleteStepIndex = idx },
                        )
                        HorizontalDivider(color = colors.divider)
                    }

                    StepActionRow(
                        steps = steps,
                        accentColor = accentColor,
                        onAdd = { showAddStep = true },
                        onRecordGamepad = { requestGamepadRecording() },
                        onRecordTouch = { requestTouchRecording() },
                        onTest = {
                            MacroExecutor.execute(
                                macro.copy(
                                    name = localName.trim().ifBlank { macro.name },
                                    steps = steps,
                                ),
                            )
                        },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (steps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(MT_PADDING.dp),
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
                                .padding(horizontal = MT_TIMELINE_SIDE_PADDING.dp),
                            steps = steps,
                            accentColor = accentColor,
                            onEditStep = { editingStepIndex = it },
                        )
                    }

                    HorizontalDivider(color = colors.divider)

                    StepActionRow(
                        steps = steps,
                        accentColor = accentColor,
                        onAdd = { showAddStep = true },
                        onRecordGamepad = { requestGamepadRecording() },
                        onRecordTouch = { requestTouchRecording() },
                        onTest = {
                            MacroExecutor.execute(
                                macro.copy(
                                    name = localName.trim().ifBlank { macro.name },
                                    steps = steps,
                                ),
                            )
                        },
                    )
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
                    GamepadInjector.stop()
                    showGamepadRecordingOverlay = false
                },
            )
        }
    }

    if (showAddStep || editingStepIndex != null) {
        val stepToEdit: MacroStep? = editingStepIndex?.let { steps[it] }
        val suggestedStartTimeMs = (steps.totalDurationMs() + MT_NEW_STEP_START_OFFSET_MS).coerceAtLeast(0L)
        MacroStepEditDialog(
            step = stepToEdit,
            accentColor = accentColor,
            suggestedStartTimeMs = suggestedStartTimeMs,
            initialShiftSubsequent = shiftSubsequentDefault,
            onConfirm = { newStep, applyShift ->
                if (editingStepIndex != null) {
                    val idx = editingStepIndex!!
                    val oldStep = steps[idx]
                    val updated = if (!applyShift) {
                        steps.toMutableList().also { it[idx] = newStep }
                    } else {
                        val oldStepEndTimeMs = oldStep.endTimeMs()
                        val endDelta = newStep.endTimeMs() - oldStepEndTimeMs
                        steps.mapIndexed { i, step ->
                            when {
                                i == idx -> newStep
                                endDelta == 0L -> step
                                step.startTimeMs >= oldStepEndTimeMs -> {
                                    step.withStartTime(
                                        (step.startTimeMs + endDelta).coerceIn(0L, MT_TIMING_MAX_MS),
                                    )
                                }
                                else -> step
                            }
                        }
                    }
                    pushUndo(steps)
                    steps = updated
                    editingStepIndex = null
                } else {
                    val updated = if (!applyShift) {
                        steps + newStep
                    } else {
                        val shifted = steps.map { existing ->
                            if (existing.startTimeMs >= newStep.startTimeMs) {
                                existing.withStartTime(
                                    (existing.startTimeMs + newStep.durationMs)
                                        .coerceIn(0L, MT_TIMING_MAX_MS),
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

@Composable
private fun MacroViewModeChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(MT_VIEW_CHIP_CORNER.dp))
            .background(
                if (selected) colors.accent.copy(alpha = 0.85f)
                else colors.navPillBody.copy(alpha = 0.5f),
            )
            .border(
                1.dp,
                if (selected) colors.accent else colors.controlOverlayBorder,
                RoundedCornerShape(MT_VIEW_CHIP_CORNER.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = MT_VIEW_CHIP_H_PADDING.dp,
                vertical = MT_VIEW_CHIP_V_PADDING.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else colors.onControlOverlay,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                color = if (selected) colors.onAccent else colors.onControlOverlay,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun MacroVerticalTimeline(
    modifier: Modifier,
    steps: List<MacroStep>,
    accentColor: Color,
    onEditStep: (Int) -> Unit,
) {
    val laneAssignment = remember(steps) { assignLanes(steps) }
    val numLanes = remember(laneAssignment) { (laneAssignment.maxOrNull() ?: 0) + 1 }
    val totalMs = remember(steps) { steps.totalDurationMs().coerceAtLeast(1000L) }
    val density = LocalDensity.current
    val colors = LocalAppColors.current
    val swapFaceButtons by SettingsManager.gamepadSwapFaceButtons.collectAsState()
    val joystickColor = colors.actionColorGamepad
    val dpadColor = colors.actionColorSystem
    val touchColor = MaterialTheme.colorScheme.tertiary
    val tickFormat = stringResource(R.string.macropad_macro_timeline_tick)
    val stepLabels = remember(steps, swapFaceButtons) {
        steps.map { shortStepLabel(it, swapFaceButtons) }
    }

    val pxPerMs = with(density) { MT_VERTICAL_DP_PER_MS.dp.toPx() }
    val axisWidthPx = with(density) { MT_VERTICAL_AXIS_WIDTH.dp.toPx() }
    val contentHeightDp = (totalMs * MT_VERTICAL_DP_PER_MS).dp

    val textPaint = remember(density, colors.onSurfaceSecondary) {
        NativePaint().apply {
            isAntiAlias = true
            textSize = with(density) { MT_AXIS_TEXT_SIZE_SP.sp.toPx() }
            color = colors.onSurfaceSecondary.toArgb()
        }
    }
    val labelPaint = remember(density, colors.onSurface) {
        NativePaint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            textSize = with(density) { MT_BAR_LABEL_TEXT_SIZE_SP.sp.toPx() }
            color = colors.onSurface.toArgb()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(colors.appBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val laneWidthPx = max((canvasWidthPx - axisWidthPx) / numLanes.toFloat(), 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeightDp)
                .pointerInput(steps, laneWidthPx, axisWidthPx, pxPerMs) {
                    detectTapGestures { tap ->
                        val y = tap.y
                        val x = tap.x
                        val hitIndex = steps.indices.reversed().firstOrNull { idx ->
                            val step = steps[idx]
                            val lane = laneAssignment.getOrElse(idx) { 0 }
                            val left = axisWidthPx + lane * laneWidthPx + MT_VERTICAL_BAR_PADDING
                            val right = left + laneWidthPx - MT_VERTICAL_BAR_PADDING * 2
                            val top = step.startTimeMs * pxPerMs
                            val bottom = top + (step.durationMs * pxPerMs).coerceAtLeast(6f)
                            x in left..right && y in top..bottom
                        }
                        if (hitIndex != null) onEditStep(hitIndex)
                    }
                },
        ) {
            drawVerticalTicks(
                totalMs = totalMs,
                pxPerMs = pxPerMs,
                width = size.width,
                axisWidthPx = axisWidthPx,
                textPaint = textPaint,
                dividerColor = colors.divider,
                tickLabel = { ms -> tickFormat.format(ms) },
            )

            repeat(numLanes + 1) { laneIndex ->
                val x = axisWidthPx + laneIndex * laneWidthPx
                drawLine(
                    color = colors.divider.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }

            steps.forEachIndexed { idx, step ->
                val lane = laneAssignment.getOrElse(idx) { 0 }
                val barTop = step.startTimeMs * pxPerMs
                val barHeight = (step.durationMs * pxPerMs).coerceAtLeast(6f)
                val barLeft = axisWidthPx + lane * laneWidthPx + MT_VERTICAL_BAR_PADDING
                val barWidth = laneWidthPx - MT_VERTICAL_BAR_PADDING * 2
                val barLabel = stepLabels.getOrElse(idx) { "" }
                drawRoundRect(
                    color = stepColor(step, accentColor, joystickColor, dpadColor, touchColor).copy(alpha = 0.85f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(MT_BAR_CORNER_RADIUS.dp.toPx()),
                )
                if (barLabel.isNotEmpty() && barHeight > labelPaint.textSize + 6f) {
                    drawContext.canvas.nativeCanvas.apply {
                        save()
                        clipRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight)
                        drawText(barLabel, barLeft + 4f, barTop + labelPaint.textSize + 2f, labelPaint)
                        restore()
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawVerticalTicks(
    totalMs: Long,
    pxPerMs: Float,
    width: Float,
    axisWidthPx: Float,
    textPaint: NativePaint,
    dividerColor: Color,
    tickLabel: (Long) -> String,
) {
    var tick = 0L
    while (tick <= totalMs + MT_TICK_INTERVAL_MS) {
        val y = tick * pxPerMs
        drawLine(
            color = dividerColor.copy(alpha = 0.45f),
            start = Offset(axisWidthPx, y),
            end = Offset(width, y),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(tickLabel(tick), 2f, y + textPaint.textSize, textPaint)
        tick += MT_TICK_INTERVAL_MS
    }
}

@Composable
private fun StepActionRow(
    steps: List<MacroStep>,
    accentColor: Color,
    onAdd: () -> Unit,
    onRecordGamepad: () -> Unit,
    onRecordTouch: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MT_PADDING.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onAdd() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.macropad_macro_add_step),
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onRecordGamepad() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MaterialSymbol(
                name = "sports_esports",
                size = 18.dp,
                tint = accentColor,
                filled = true,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.macropad_macro_record_gamepad),
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onRecordTouch() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = stringResource(R.string.cd_record_touch),
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.macropad_macro_record_touch),
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (steps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onTest() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_test_macro),
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.macropad_macro_test_run),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun stepColor(
    step: MacroStep,
    accentColor: Color,
    joystickColor: Color,
    dpadColor: Color,
    touchColor: Color,
): Color = when (step) {
    is MacroStep.GamepadButtonTap -> accentColor
    is MacroStep.JoystickMove -> joystickColor
    is MacroStep.DPadTap -> dpadColor
    is MacroStep.TouchTap -> touchColor
}

@Composable
private fun StepListItem(
    index: Int,
    step: MacroStep,
    accentColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val swapFaceButtons by SettingsManager.gamepadSwapFaceButtons.collectAsState()
    val joystickColor = colors.actionColorGamepad
    val dpadColor = colors.actionColorSystem
    val touchColor = MaterialTheme.colorScheme.tertiary
    val typeLabel = stringResource(
        when (step) {
            is MacroStep.GamepadButtonTap -> R.string.macropad_macro_step_type_gamepad
            is MacroStep.JoystickMove -> R.string.macropad_macro_step_type_joystick
            is MacroStep.DPadTap -> R.string.macropad_macro_step_type_dpad
            is MacroStep.TouchTap -> R.string.macropad_macro_step_type_touch
        },
    )
    val description = when (step) {
        is MacroStep.GamepadButtonTap -> gamepadCodeDisplayLabel(step.btnCode, swapFaceButtons)
        is MacroStep.JoystickMove -> {
            val stickLabel = if (step.stick == JoystickStick.LEFT) "L" else "R"
            "$stickLabel ${joyDirArrow(step.x, step.y)}"
        }
        is MacroStep.DPadTap -> dirArrow(step.dirX, step.dirY)
        is MacroStep.TouchTap -> "${"%.2f".format(step.normX)}, ${"%.2f".format(step.normY)}"
    }
    val indicatorColor = stepColor(step, accentColor, joystickColor, dpadColor, touchColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = MT_PADDING.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${index + 1}. $typeLabel: $description",
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.macropad_macro_step_timing, step.startTimeMs, step.durationMs),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_macro_step_edit),
                tint = colors.onSurfaceSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.macropad_editor_delete_button),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}
