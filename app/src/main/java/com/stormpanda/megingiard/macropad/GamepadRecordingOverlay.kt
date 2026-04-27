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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private const val TAG = "GamepadRecordingOverlay"

private val GRO_PANEL_RADIUS = 20.dp
private val GRO_PANEL_PADDING = 20.dp
private val GRO_SECTION_SPACING = 16.dp
private val GRO_EVENT_SPACING = 8.dp
private val GRO_BUTTON_SPACING = 12.dp
private val GRO_PANEL_MAX_WIDTH = 560.dp
private const val GRO_OVERLAY_ALPHA = 0.96f

@Composable
internal fun GamepadRecordingOverlay(
    accentColor: Color,
    onConfirm: (List<MacroStep>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val swapFaceButtons by SettingsManager.gamepadSwapFaceButtons.collectAsState()
    val recordingState by GamepadRecordingManager.state.collectAsState()
    val latestState by rememberUpdatedState(recordingState)
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        AppLog.d(TAG, "Gamepad recording overlay entered")
        onDispose {
            val stateToDispose = latestState
            AppLog.d(TAG, "Gamepad recording overlay disposed state=$stateToDispose")
            if (stateToDispose is GamepadRecordingState.Starting || stateToDispose is GamepadRecordingState.Recording) {
                scope.launch { GamepadRecordingManager.cancelRecording() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground.copy(alpha = GRO_OVERLAY_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(GRO_PANEL_MAX_WIDTH)
                .background(colors.surface, RoundedCornerShape(GRO_PANEL_RADIUS))
                .border(1.dp, colors.accentBorder, RoundedCornerShape(GRO_PANEL_RADIUS))
                .padding(GRO_PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(GRO_SECTION_SPACING),
        ) {
            Text(
                text = stringResource(R.string.macropad_macro_record_gamepad),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )

            when (val currentState = recordingState) {
                GamepadRecordingState.Idle -> {
                    Text(
                        text = stringResource(R.string.macropad_recording_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceSecondary,
                    )
                }

                GamepadRecordingState.Starting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GRO_BUTTON_SPACING),
                    ) {
                        CircularProgressIndicator(color = accentColor)
                        Text(
                            text = stringResource(R.string.macropad_recording_starting),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                        )
                    }
                }

                is GamepadRecordingState.Recording -> {
                    Text(
                        text = stringResource(R.string.macropad_recording_active),
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.macropad_recording_device, currentState.devicePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceSecondary,
                    )
                    if (currentState.usingFrameworkCapture) {
                        Text(
                            text = stringResource(R.string.macropad_recording_framework_fallback_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (currentState.liveEvents.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(GRO_EVENT_SPACING)) {
                            Text(
                                text = stringResource(R.string.macropad_recording_last_events),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(GRO_EVENT_SPACING),
                            ) {
                                currentState.liveEvents.forEach { event ->
                                    Text(
                                        text = eventLabel(event = event, swapFaceButtons = swapFaceButtons),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            AppLog.i(TAG, "Stop recording tapped")
                            scope.launch { GamepadRecordingManager.finishRecording() }
                        }) {
                            Text(
                                text = stringResource(R.string.macropad_recording_stop),
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                is GamepadRecordingState.Done -> {
                    Text(
                        text = stringResource(R.string.macropad_recording_done_steps, currentState.steps.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            AppLog.i(TAG, "Discard recording tapped")
                            onDismiss()
                        }) {
                            Text(
                                text = stringResource(R.string.macropad_recording_discard),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.width(GRO_BUTTON_SPACING))
                        TextButton(onClick = {
                            AppLog.i(TAG, "Confirm recording tapped steps=${currentState.steps.size}")
                            onConfirm(currentState.steps)
                        }) {
                            Text(
                                text = stringResource(R.string.macropad_recording_confirm),
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                is GamepadRecordingState.Error -> {
                    Text(
                        text = errorLabel(currentState.reason),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.error,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            AppLog.i(TAG, "Close recording error overlay tapped reason=${currentState.reason}")
                            onDismiss()
                        }) {
                            Text(
                                text = stringResource(R.string.macropad_recording_close),
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun errorLabel(reason: GamepadRecordingError): String = when (reason) {
    GamepadRecordingError.START_FAILED -> stringResource(R.string.macropad_recording_error_start_failed)
}

@Composable
private fun eventLabel(event: GamepadRecordingLiveEvent, swapFaceButtons: Boolean): String = when (event) {
    is GamepadRecordingLiveEvent.Button -> {
        val preset = GamepadKeycodes.PRESETS.firstOrNull { it.code == event.code }
        val buttonLabel = preset?.displayShortLabel(swapFaceButtons)
            ?: stringResource(R.string.macropad_recording_unknown_button, event.code)
        if (event.pressed) {
            stringResource(R.string.macropad_recording_event_button_down, buttonLabel)
        } else {
            stringResource(R.string.macropad_recording_event_button_up, buttonLabel)
        }
    }

    is GamepadRecordingLiveEvent.DPad -> {
        stringResource(R.string.macropad_recording_event_dpad, dirArrow(event.dirX, event.dirY))
    }

    is GamepadRecordingLiveEvent.Joystick -> {
        val stickLabel = when (event.stick) {
            JoystickStick.LEFT -> stringResource(R.string.macropad_macro_step_stick_left)
            JoystickStick.RIGHT -> stringResource(R.string.macropad_macro_step_stick_right)
        }
        stringResource(R.string.macropad_recording_event_joystick, stickLabel, joyDirArrow(event.x, event.y))
    }
}

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
    val magnitude = sqrt((x * x) + (y * y))
    if (magnitude < 0.1f) return "·"
    val normX = when {
        x / magnitude > 0.5f -> 1
        x / magnitude < -0.5f -> -1
        else -> 0
    }
    val normY = when {
        y / magnitude > 0.5f -> 1
        y / magnitude < -0.5f -> -1
        else -> 0
    }
    return dirArrow(normX, normY)
}