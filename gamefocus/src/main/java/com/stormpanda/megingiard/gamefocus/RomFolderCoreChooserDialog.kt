package com.stormpanda.megingiard.gamefocus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.rom.CustomRomFolder
import com.stormpanda.megingiard.focus.rom.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "RomFolderCoreChooser"

// File scope dimensions as per AGENTS.md §8.3
private val DIALOG_ITEM_PADDING_VERTICAL = 12.dp
private val DIALOG_ITEM_PADDING_HORIZONTAL = 16.dp
private val DIALOG_SPACING = 16.dp
private val DIALOG_TITLE_PADDING_BOTTOM = 8.dp
private val DIALOG_INNER_SPACING = 8.dp
private val DIALOG_MAX_HEIGHT = 200.dp

@Composable
fun RomFolderCoreChooserDialog(
    folder: CustomRomFolder,
    selectedIndex: Int = 0,
    onSelectedIndexChange: (Int) -> Unit = {},
    confirmTrigger: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val systemDef =
        remember(folder.systemId) {
            SUPPORTED_SYSTEMS.find { it.id == folder.systemId }
        }

    LaunchedEffect(folder) {
        AppLog.d(TAG, "Showing core chooser dialog for folder: ${folder.folderPath}, recognized system: ${folder.systemId}")
    }

    LaunchedEffect(confirmTrigger) {
        if (confirmTrigger > 0) {
            if (systemDef == null) {
                onDismiss()
            } else if (systemDef.emulatorId != "retroarch") {
                AppLog.i(TAG, "Confirming native emulation dialog via trigger")
                onConfirm(null)
            } else {
                val cores = listOf(null) + systemDef.retroArchCoreAlternatives
                val safeIdx = selectedIndex.coerceIn(0, cores.lastIndex)
                AppLog.i(TAG, "Confirming RetroArch core selection via trigger: index=$safeIdx, core='${cores[safeIdx]}'")
                onConfirm(cores[safeIdx])
            }
        }
    }

    AppModalDialog(
        onDismiss = {
            AppLog.d(TAG, "Dialog dismissed by scrim tap")
            onDismiss()
        },
        modifier = modifier,
    ) {
        if (systemDef == null) {
            Text(
                text = "Unknown System",
                style = MaterialTheme.typography.titleMedium,
                color = appColors.onSurface,
            )
            Spacer(modifier = Modifier.height(DIALOG_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = "Close",
                    onClick = {
                        AppLog.d(TAG, "Dismissing due to unknown system definition")
                        onDismiss()
                    },
                )
            }
            return@AppModalDialog
        }

        Text(
            text = stringResource(R.string.gamefocus_dialog_system_recognized_title),
            style = MaterialTheme.typography.titleMedium,
            color = appColors.onSurface,
            modifier = Modifier.padding(bottom = DIALOG_TITLE_PADDING_BOTTOM),
        )

        Text(
            text = stringResource(R.string.gamefocus_dialog_system_recognized_msg, systemDef.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors.onSurfaceSecondary,
            modifier = Modifier.padding(bottom = DIALOG_SPACING),
        )

        if (systemDef.emulatorId != "retroarch") {
            Text(
                text = stringResource(R.string.gamefocus_dialog_core_native_msg),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.onSurfaceSecondary,
                modifier = Modifier.padding(bottom = DIALOG_SPACING),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = stringResource(R.string.gamefocus_dialog_core_native_close),
                    onClick = {
                        AppLog.i(TAG, "Native emulation folder added. Dismissing dialog.")
                        onConfirm(null)
                    },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.gamefocus_dialog_select_core_label),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.onSurface,
                modifier = Modifier.padding(bottom = DIALOG_TITLE_PADDING_BOTTOM),
            )

            val cores =
                remember(systemDef) {
                    listOf(null) + systemDef.retroArchCoreAlternatives
                }

            var selectedCore by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(selectedIndex, cores) {
                if (cores.isNotEmpty()) {
                    val safeIdx = selectedIndex.coerceIn(0, cores.lastIndex)
                    selectedCore = cores[safeIdx]
                }
            }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = DIALOG_MAX_HEIGHT),
            ) {
                itemsIndexed(cores) { index, core ->
                    val displayName =
                        if (core == null) {
                            stringResource(R.string.gamefocus_dialog_core_default, systemDef.retroArchCore ?: "")
                        } else {
                            core
                        }
                    val isSelected = selectedCore == core
                    val isFocused = index == selectedIndex

                    val rowBg = if (isFocused) appColors.accent.copy(alpha = 0.2f) else Color.Transparent
                    val rowBorderModifier =
                        if (isFocused) {
                            Modifier.border(1.dp, appColors.accent, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(rowBg)
                                .then(rowBorderModifier)
                                .clickable {
                                    onSelectedIndexChange(index)
                                }.padding(
                                    vertical = DIALOG_ITEM_PADDING_VERTICAL,
                                    horizontal = DIALOG_ITEM_PADDING_HORIZONTAL,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectedIndexChange(index) },
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = appColors.accent,
                                    unselectedColor = appColors.onSurfaceSecondary,
                                ),
                        )
                        Spacer(modifier = Modifier.width(DIALOG_INNER_SPACING))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected || isFocused) appColors.onSurface else appColors.onSurfaceSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DIALOG_SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_B,
                    text = stringResource(R.string.gamefocus_dialog_cancel),
                    onClick = {
                        AppLog.d(TAG, "User cancelled core assignment dialog")
                        onDismiss()
                    },
                )
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = stringResource(R.string.gamefocus_dialog_core_save),
                    onClick = {
                        AppLog.i(TAG, "User confirmed RetroArch core assignment: '$selectedCore' for recognized system ${folder.systemId}")
                        onConfirm(selectedCore)
                    },
                )
            }
        }
    }
}
