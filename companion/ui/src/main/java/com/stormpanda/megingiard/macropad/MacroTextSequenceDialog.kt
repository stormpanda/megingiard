package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlin.math.roundToInt

private const val TAG = "MacroTextSequenceDialog"

private const val MTSD_DEFAULT_KEY_DURATION_MS = 50L
private const val MTSD_DEFAULT_PAUSE_MS = 30L
private const val MTSD_MAX_SLIDER_MS = 500f
private const val MTSD_TIME_SLIDER_STEP = 10f
private const val MTSD_MAX_START_TIME_BUFFER_MS = 5000L
private const val MTSD_MIN_MAX_START_TIME_MS = 1000L

/**
 * Generates a list of [MacroStep.KeyboardKeyTap]s from [text] starting at [startOffsetMs].
 */
fun generateTextSequenceSteps(
    text: String,
    startOffsetMs: Long,
    keyDurationMs: Long = MTSD_DEFAULT_KEY_DURATION_MS,
    pauseMs: Long = MTSD_DEFAULT_PAUSE_MS,
): List<MacroStep.KeyboardKeyTap> {
    val steps = mutableListOf<MacroStep.KeyboardKeyTap>()
    var currentT = startOffsetMs
    for (char in text) {
        val mapping = LinuxKeycodes.charToKeyMapping(char) ?: continue
        val modifiers = if (mapping.shift) listOf(LinuxKeycodes.KEY_LEFTSHIFT) else emptyList()
        steps +=
            MacroStep.KeyboardKeyTap(
                startTimeMs = currentT,
                durationMs = keyDurationMs,
                keycode = mapping.keycode,
                label = mapping.label,
                modifiers = modifiers,
            )
        currentT += keyDurationMs + pauseMs
    }
    return steps
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextSequenceGeneratorSubPageContent(
    macroName: String,
    suggestedStartTimeMs: Long,
    accentColor: Color,
    onGenerate: (List<MacroStep.KeyboardKeyTap>) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    var textInput by remember { mutableStateOf("") }
    var keyDurationMs by remember { mutableIntStateOf(MTSD_DEFAULT_KEY_DURATION_MS.toInt()) }
    var pauseBetweenKeysMs by remember { mutableIntStateOf(MTSD_DEFAULT_PAUSE_MS.toInt()) }
    var startOffsetMs by remember { mutableIntStateOf(suggestedStartTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }

    val generatedSteps =
        remember(textInput, startOffsetMs, keyDurationMs, pauseBetweenKeysMs) {
            generateTextSequenceSteps(
                text = textInput,
                startOffsetMs = startOffsetMs.toLong(),
                keyDurationMs = keyDurationMs.toLong(),
                pauseMs = pauseBetweenKeysMs.toLong(),
            )
        }

    val totalDurationMs =
        if (generatedSteps.isNotEmpty()) {
            generatedSteps.last().endTimeMs() - startOffsetMs
        } else {
            0L
        }

    val isConfirmEnabled = generatedSteps.isNotEmpty()
    val hasChanges = textInput.isNotBlank()

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    AppLog.i(TAG, "Generating ${generatedSteps.size} keyboard steps for text sequence")
                    onGenerate(generatedSteps)
                }
            },
            onDiscard = {
                AppLog.d(TAG, "Discarding text sequence generator")
                onDiscard()
            },
        )

    GamepadInfoBox(
        text = stringResource(R.string.macropad_macro_text_sequence_info),
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.macropad_macro_text_sequence_title),
        description = stringResource(R.string.macropad_macro_text_sequence_desc),
        value = textInput,
        onValueChange = { textInput = it },
        placeholder = stringResource(R.string.macropad_macro_text_sequence_placeholder),
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_macro_section_timing),
        color = accentColor,
    )

    GamepadSliderCard(
        title = stringResource(R.string.macropad_macro_key_typing_speed),
        description = stringResource(R.string.macropad_macro_key_typing_speed_desc),
        value = keyDurationMs.toFloat(),
        valueRange = 10f..MTSD_MAX_SLIDER_MS,
        step = MTSD_TIME_SLIDER_STEP,
        fineStep = 1f,
        valueLabel = "$keyDurationMs ms",
        icon = Icons.Rounded.Schedule,
        onValueChange = { keyDurationMs = it.roundToInt().coerceIn(10, MTSD_MAX_SLIDER_MS.toInt()) },
    )

    GamepadSliderCard(
        title = stringResource(R.string.macropad_macro_key_typing_pause),
        description = stringResource(R.string.macropad_macro_key_typing_pause_desc),
        value = pauseBetweenKeysMs.toFloat(),
        valueRange = 0f..MTSD_MAX_SLIDER_MS,
        step = MTSD_TIME_SLIDER_STEP,
        fineStep = 1f,
        valueLabel = "$pauseBetweenKeysMs ms",
        icon = Icons.Rounded.Schedule,
        onValueChange = { pauseBetweenKeysMs = it.roundToInt().coerceIn(0, MTSD_MAX_SLIDER_MS.toInt()) },
    )

    val maxStartMs = (suggestedStartTimeMs + MTSD_MAX_START_TIME_BUFFER_MS).coerceAtLeast(MTSD_MIN_MAX_START_TIME_MS).toFloat()
    GamepadSliderCard(
        title = stringResource(R.string.macropad_macro_step_start_ms),
        description = stringResource(R.string.macropad_macro_step_start_desc),
        value = startOffsetMs.toFloat(),
        valueRange = 0f..maxStartMs,
        step = MTSD_TIME_SLIDER_STEP,
        fineStep = 1f,
        valueLabel = "$startOffsetMs ms",
        icon = Icons.Rounded.Schedule,
        onValueChange = { startOffsetMs = it.roundToInt().coerceAtLeast(0) },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_macro_text_sequence_generate_title),
        description =
            if (generatedSteps.isNotEmpty()) {
                stringResource(
                    R.string.macropad_macro_text_sequence_summary,
                    generatedSteps.size,
                    totalDurationMs,
                )
            } else {
                stringResource(R.string.macropad_macro_text_sequence_empty_summary)
            },
        pulseOnChanges = hasChanges,
        saveActionText = stringResource(R.string.macropad_macro_text_sequence_generate),
        saveIcon = Icons.Rounded.Add,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )
}
