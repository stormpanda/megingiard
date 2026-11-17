package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "ManualMacroSteps"
private val MMS_ITEM_SPACING = 8.dp
private val MMS_VERTICAL_PADDING = 4.dp

@Composable
internal fun ManualMacroStepsSubPageContent(
    macro: Macro,
    accentColor: Color,
    onOpenAddStep: () -> Unit,
    onOpenEditStep: (stepIndex: Int) -> Unit,
    onOpenReorderSteps: () -> Unit,
) {
    AppLog.d(TAG, "ManualMacroStepsSubPageContent rendered for '${macro.name}' (${macro.steps.size} steps)")
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MMS_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(MMS_ITEM_SPACING),
    ) {
        item(key = "macro_manual_add_step") {
            GamepadActionCard(
                title = stringResource(R.string.macropad_macro_step_new),
                description = stringResource(R.string.macro_step_action_type_desc),
                icon = Icons.Rounded.Add,
                itemKey = "macro_manual_add_step",
                onClick = onOpenAddStep,
                modifier = Modifier.firstDeckItem(),
            )
        }

        if (macro.steps.size > 1) {
            item(key = "macro_manual_reorder_steps") {
                GamepadActionCard(
                    title = stringResource(R.string.macropad_macro_reorder_steps_title),
                    description = stringResource(R.string.macropad_macro_reorder_steps_desc),
                    icon = Icons.Rounded.SwapVert,
                    itemKey = "macro_manual_reorder_steps",
                    onClick = onOpenReorderSteps,
                )
            }
        }

        item(key = "macro_manual_steps_header") {
            GamepadSectionHeader(
                text = "${stringResource(R.string.macropad_macro_section_steps)} (${macro.steps.size})",
                color = accentColor,
            )
        }

        if (macro.steps.isEmpty()) {
            item(key = "macro_manual_steps_empty") {
                GamepadInfoBox(
                    text = stringResource(R.string.macro_timeline_list_empty_desc),
                    iconTint = accentColor,
                )
            }
        } else {
            itemsIndexed(
                items = macro.steps,
                key = { idx, _ -> "macro_manual_step_$idx" },
            ) { idx, step ->
                val typeTitle = stepTypeLabel(step)
                val actionDesc = stepActionDescription(step, swapFaceButtons)
                GamepadActionCard(
                    title = "${idx + 1}. $typeTitle: $actionDesc",
                    description = stringResource(R.string.macropad_macro_step_timing, step.startTimeMs, step.durationMs),
                    icon = stepIcon(step),
                    onClick = { onOpenEditStep(idx) },
                    itemKey = "macro_manual_step_$idx",
                )
            }
        }
    }
}
