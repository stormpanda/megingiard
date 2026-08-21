package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadTwoColumnGrid

private const val TAG = "ChooseMacroModeSubPage"

private enum class MacroCreationChoice {
    RECORD_GAMEPAD,
    RECORD_TOUCH,
    BUILD_MANUAL,
}

private data class MacroChoiceItem(
    val choice: MacroCreationChoice,
    val titleRes: Int,
    val descRes: Int,
    val actionRes: Int,
    val icon: ImageVector,
)

@Composable
internal fun ChooseMacroModeSubPageContent(
    onRecordGamepad: () -> Unit,
    onRecordTouch: () -> Unit,
    onBuildManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "ChooseMacroModeSubPageContent rendered")

    val items =
        remember {
            listOf(
                MacroChoiceItem(
                    choice = MacroCreationChoice.RECORD_GAMEPAD,
                    titleRes = R.string.macropad_macro_create_record_gamepad_title,
                    descRes = R.string.macropad_macro_create_record_gamepad_desc,
                    actionRes = R.string.macropad_macro_record_gamepad,
                    icon = Icons.Rounded.SportsEsports,
                ),
                MacroChoiceItem(
                    choice = MacroCreationChoice.RECORD_TOUCH,
                    titleRes = R.string.macropad_macro_create_record_touch_title,
                    descRes = R.string.macropad_macro_create_record_touch_desc,
                    actionRes = R.string.macropad_macro_record_touch,
                    icon = Icons.Rounded.TouchApp,
                ),
                MacroChoiceItem(
                    choice = MacroCreationChoice.BUILD_MANUAL,
                    titleRes = R.string.macropad_macro_create_manual_title,
                    descRes = R.string.macropad_macro_create_manual_desc,
                    actionRes = R.string.gamepad_action_create,
                    icon = Icons.Rounded.Tune,
                ),
            )
        }

    GamepadTwoColumnGrid(
        items = items,
        modifier = modifier,
    ) { item, _, cardModifier ->
        GamepadActionCard(
            title = stringResource(item.titleRes),
            description = stringResource(item.descRes),
            icon = item.icon,
            actionText = stringResource(item.actionRes),
            alwaysShowFullDescription = true,
            onClick = {
                when (item.choice) {
                    MacroCreationChoice.RECORD_GAMEPAD -> onRecordGamepad()
                    MacroCreationChoice.RECORD_TOUCH -> onRecordTouch()
                    MacroCreationChoice.BUILD_MANUAL -> onBuildManual()
                }
            },
            modifier = cardModifier,
        )
    }
}
