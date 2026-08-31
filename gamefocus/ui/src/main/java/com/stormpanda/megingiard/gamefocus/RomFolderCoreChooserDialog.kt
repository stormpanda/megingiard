package com.stormpanda.megingiard.gamefocus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.VerticalRollingCarousel

private const val TAG = "RomFolderCoreChooser"

// File scope dimensions as per AGENTS.md §8.3
private val DIALOG_SPACING = 16.dp
private val DIALOG_TITLE_PADDING_BOTTOM = 8.dp
private val DIALOG_INNER_SPACING = 8.dp

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
    val cores =
        remember(systemDef) {
            if (systemDef != null) listOf(null) + systemDef.retroArchCoreAlternatives else emptyList()
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
        widthFraction = 0.45f,
        modifier = modifier,
    ) {
        if (systemDef == null) {
            Text(
                text = stringResource(R.string.gamefocus_dialog_unknown_system),
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
                    text = stringResource(R.string.gamefocus_dialog_core_native_close),
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

            VerticalRollingCarousel(
                selectedIndex = selectedIndex,
                items = cores,
                onSelectedIndexChange = onSelectedIndexChange,
                labelProvider = { core ->
                    if (core == null) {
                        stringResource(R.string.gamefocus_dialog_core_default, systemDef.retroArchCore ?: "")
                    } else {
                        core
                    }
                },
                visibleItemsCount = 5,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = DIALOG_INNER_SPACING),
            )

            Spacer(modifier = Modifier.height(DIALOG_SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = stringResource(R.string.gamefocus_dialog_core_save),
                    onClick = {
                        val selectedCore = cores.getOrNull(selectedIndex.coerceIn(0, cores.lastIndex))
                        AppLog.i(TAG, "User confirmed RetroArch core assignment: '$selectedCore' for recognized system ${folder.systemId}")
                        onConfirm(selectedCore)
                    },
                )
                Spacer(modifier = Modifier.width(DIALOG_INNER_SPACING))
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_B,
                    text = stringResource(R.string.gamefocus_dialog_cancel),
                    onClick = {
                        AppLog.d(TAG, "User cancelled core assignment dialog")
                        onDismiss()
                    },
                )
            }
        }
    }
}
