package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MacroTimelineEditor"

private const val MTE_DEFAULT_TOUCH_DURATION_MS = 100L
private const val MTE_UNDO_STACK_MAX = 50
private const val MTE_PULSE_DURATION_MS = 1400
private const val MTE_PULSE_ACCENT_ALPHA = 0.35f
private const val MTE_PULSE_SURFACE_ALPHA = 0.55f
private const val MTE_TEST_RUN_PRE_DELAY_MS = 350L
private const val MTE_TEST_RUN_POST_DELAY_MS = 300L

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MacroTimelineSubPageContent(
    macro: Macro,
    savedMacro: Macro? = null,
    accentColor: Color,
    onOpenManualSteps: () -> Unit,
    onDiscard: () -> Unit = {},
    onSave: (Macro) -> Unit,
    onDelete: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localName by remember(macro) { mutableStateOf(macro.name) }
    var steps by remember(macro) { mutableStateOf(macro.steps) }
    var undoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<MacroStep>>>(emptyList()) }
    var loopEnabled by remember(macro) { mutableStateOf(macro.loopEnabled) }
    var loopPauseMs by remember(macro) { mutableIntStateOf(macro.loopPauseMs) }
    var randomizeTimingEnabled by remember(macro) { mutableStateOf(macro.randomizeTimingEnabled) }
    var randomizeTimingRangeMs by remember(macro) { mutableIntStateOf(macro.randomizeTimingRangeMs.coerceIn(5, 100)) }

    val isNew = savedMacro == null
    val currentMacro =
        macro.copy(
            name = localName.trim().ifBlank { macro.name },
            steps = steps,
            loopEnabled = loopEnabled,
            loopPauseMs = loopPauseMs,
            randomizeTimingEnabled = randomizeTimingEnabled,
            randomizeTimingRangeMs = randomizeTimingRangeMs,
        )
    val hasChanges = currentMacro != savedMacro || isNew
    val isConfirmEnabled = localName.isNotBlank()

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    AppLog.i(TAG, "Saving macro '${currentMacro.name}' (${currentMacro.id}) with ${steps.size} steps")
                    onSave(currentMacro)
                }
            },
            onDiscard = {
                AppLog.i(TAG, "Discarding changes for macro '${macro.name}' (${macro.id})")
                onDiscard()
            },
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

    val touchRecordingState by TouchRecordingManager.state.collectAsState()
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
        if (!physicalRecordingAvailable) {
            DialogToastManager.show(context.getString(R.string.privd_error_daemon_unreachable))
            return
        }
        AppLog.i(
            TAG,
            "startGamepadRecording() -> suspending editor and starting physical recording",
        )
        if (savedMacro != null) {
            MacroPadState.updateMacro(currentMacro)
        }
        AppStateManager.suspendCurrentAndDismiss()
        PhysicalGamepadRecordingManager.startRecording()
    }

    fun requestGamepadRecording() {
        startGamepadRecording()
    }

    fun requestTouchTapRecording() {
        if (savedMacro != null) {
            MacroPadState.updateMacro(currentMacro)
        }
        AppStateManager.suspendCurrentAndDismiss()
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
    }

    fun requestTouchGestureRecording() {
        if (savedMacro != null) {
            MacroPadState.updateMacro(currentMacro)
        }
        AppStateManager.suspendCurrentAndDismiss()
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
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
        val newSteps = steps + shiftedSteps
        steps = newSteps
        val updated = currentMacro.copy(steps = newSteps)
        if (savedMacro != null) {
            AppLog.i(TAG, "Auto-persisting ${newSteps.size} total steps into MacroPadState for macro '${updated.name}' (${updated.id})")
            MacroPadState.updateMacro(updated)
        }
        val count = recorded.steps.size
        val toastMsg =
            if (count == 1) {
                context.getString(R.string.macropad_macro_recorded_steps_toast_single)
            } else {
                context.getString(R.string.macropad_macro_recorded_steps_toast_multiple, count)
            }
        DialogToastManager.show(toastMsg)
        TouchRecordingManager.resetState()
    }

    LaunchedEffect(physicalRecordingState) {
        val recorded = physicalRecordingState as? GamepadRecordingState.Done ?: return@LaunchedEffect
        AppLog.i(TAG, "LaunchedEffect(physicalRecordingState) received Done with ${recorded.steps.size} steps")
        if (recorded.steps.isNotEmpty()) {
            val nextStart = steps.totalDurationMs()
            val shiftedSteps = recorded.steps.offsetBy(nextStart)
            pushUndo(steps)
            val newSteps = steps + shiftedSteps
            steps = newSteps
            val updated = currentMacro.copy(steps = newSteps)
            if (savedMacro != null) {
                AppLog.i(TAG, "Auto-persisting ${newSteps.size} total steps into MacroPadState for macro '${updated.name}' (${updated.id})")
                MacroPadState.updateMacro(updated)
            }
            val count = recorded.steps.size
            val toastMsg =
                if (count == 1) {
                    context.getString(R.string.macropad_macro_recorded_steps_toast_single)
                } else {
                    context.getString(R.string.macropad_macro_recorded_steps_toast_multiple, count)
                }
            DialogToastManager.show(toastMsg)
            PhysicalGamepadRecordingManager.resetState()
        }
    }

    // ── General Section ──────────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_general),
        color = accentColor,
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.help_timeline_name_label),
        description = stringResource(R.string.help_timeline_name_desc),
        value = localName,
        onValueChange = { localName = it },
        placeholder = stringResource(R.string.macropad_macro_default_name),
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_test_run),
        description =
            if (steps.isNotEmpty()) {
                stringResource(R.string.macropad_macro_step_count_multiple, steps.size)
            } else {
                stringResource(R.string.macro_timeline_list_empty_desc)
            },
        actionText = stringResource(R.string.macropad_macro_test_run),
        icon = Icons.Rounded.PlayArrow,
        itemKey = "macro_test_run",
        enabled = steps.isNotEmpty(),
        onClick = {
            scope.launch {
                AppLog.i(
                    TAG,
                    "Starting test run for macro '${currentMacro.name}' (${currentMacro.id}) with ${currentMacro.steps.size} steps",
                )
                AppStateManager.suspendCurrentAndDismiss()
                try {
                    delay(MTE_TEST_RUN_PRE_DELAY_MS)
                    MacroExecutor.executeAndWait(currentMacro, context)
                    delay(MTE_TEST_RUN_POST_DELAY_MS)
                } finally {
                    AppStateManager.resumeSuspended()
                    DialogToastManager.show(
                        context.getString(R.string.macropad_macro_test_run_success, currentMacro.name),
                    )
                }
            }
        },
    )

    // ── Recording & Quick Actions Section ────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_recording),
        color = accentColor,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_create_record_gamepad_title),
        description = stringResource(R.string.macropad_macro_create_record_gamepad_desc),
        actionText = stringResource(R.string.macropad_macro_record_gamepad),
        icon = Icons.Rounded.SportsEsports,
        itemKey = "macro_record_gamepad",
        onClick = { requestGamepadRecording() },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_manual_steps_title),
        description = stringResource(R.string.macropad_macro_manual_steps_desc, steps.size),
        actionText = stringResource(R.string.macropad_macro_manual_steps_action),
        icon = Icons.Rounded.Tune,
        itemKey = "macro_manual_steps",
        onClick = onOpenManualSteps,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_create_record_touch_tap_title),
        description = stringResource(R.string.macropad_macro_create_record_touch_tap_desc),
        actionText = stringResource(R.string.macropad_macro_record_touch),
        icon = Icons.Rounded.TouchApp,
        itemKey = "macro_record_touch_tap",
        onClick = { requestTouchTapRecording() },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_macro_create_record_touch_gesture_title),
        description = stringResource(R.string.macropad_macro_create_record_touch_gesture_desc),
        actionText = stringResource(R.string.macropad_macro_record_touch),
        icon = Icons.Rounded.Gesture,
        itemKey = "macro_record_touch_gesture",
        onClick = { requestTouchGestureRecording() },
    )

    // ── Playback & Looping Section ───────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_playback),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.macropad_macro_loop_toggle),
        description = stringResource(R.string.macropad_macro_loop_pause_label),
        checked = loopEnabled,
        icon = Icons.Rounded.Repeat,
        onCheckedChange = { loopEnabled = it },
    )

    if (loopEnabled) {
        GamepadSliderCard(
            title = stringResource(R.string.macropad_macro_loop_pause_label),
            description = stringResource(R.string.macropad_macro_loop_pause_label),
            value = loopPauseMs.toFloat(),
            valueRange = 0f..10000f,
            step = 100f,
            valueLabel = "$loopPauseMs ms",
            icon = Icons.Rounded.HourglassEmpty,
            onValueChange = { loopPauseMs = it.toInt() },
        )
    }

    GamepadToggleCard(
        title = stringResource(R.string.macropad_macro_randomize_toggle),
        description = stringResource(R.string.macropad_macro_randomize_desc),
        checked = randomizeTimingEnabled,
        icon = Icons.Rounded.Shuffle,
        onCheckedChange = { randomizeTimingEnabled = it },
    )

    if (randomizeTimingEnabled) {
        GamepadSliderCard(
            title = stringResource(R.string.macropad_macro_randomize_range_label),
            description = stringResource(R.string.macropad_macro_randomize_desc),
            value = randomizeTimingRangeMs.toFloat(),
            valueRange = 5f..100f,
            step = 5f,
            valueLabel = "±$randomizeTimingRangeMs ms",
            icon = Icons.Rounded.Tune,
            onValueChange = { randomizeTimingRangeMs = it.toInt() },
        )
    }

    // ── Save & Delete Section ────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save_and_delete),
        color = accentColor,
    )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_save_macro_title),
        description = stringResource(R.string.macropad_editor_save_macro_desc),
        cardBgColor = saveCardBgColor,
        saveActionText = stringResource(R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_macro_delete_title),
        confirmTitle = stringResource(R.string.macropad_macro_delete_confirm_title, macro.name),
        description = stringResource(R.string.macropad_macro_delete_confirm),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        icon = Icons.Rounded.Delete,
        isDestructive = true,
        onConfirm = {
            AppLog.i(TAG, "Deleting macro '${macro.name}' (${macro.id})")
            onDelete()
        },
    )
}
