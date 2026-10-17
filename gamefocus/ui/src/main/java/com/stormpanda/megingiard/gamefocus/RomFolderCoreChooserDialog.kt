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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledEmulatorRegistry
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.VerticalRollingCarousel

private const val TAG = "RomFolderCoreChooser"
private const val CAROUSEL_VISIBLE_ITEMS_COUNT = 5
private const val DIALOG_WIDTH_FRACTION = 0.45f

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
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isSwitchSystem = remember(folder.systemId) { folder.systemId == SYSTEM_ID_SWITCH }
    val emulatorRegistry = remember(folder.systemId) { InstalledEmulatorRegistry.forSystem(folder.systemId) }
    val isMultiEmulatorSystem = emulatorRegistry != null
    val emulatorOptions =
        remember(emulatorRegistry) {
            emulatorRegistry
                ?.getInstalledEmulators(context)
                ?.map { it.packageName to it.displayName }
                ?: emptyList()
        }
    val systemDef =
        remember(folder.systemId) {
            SUPPORTED_SYSTEMS.find { it.id == folder.systemId }
        } ?: return
    val cores =
        remember(systemDef) {
            listOf(null) + systemDef.retroArchCoreAlternatives
        }

    LaunchedEffect(folder) {
        AppLog.d(TAG, "Showing core/emulator chooser dialog for folder: ${folder.folderPath}, recognized system: ${folder.systemId}")
    }

    LaunchedEffect(confirmTrigger) {
        if (confirmTrigger > 0) {
            if (isMultiEmulatorSystem) {
                val safeIdx = selectedIndex.coerceIn(0, emulatorOptions.lastIndex.coerceAtLeast(0))
                val chosen = emulatorOptions.getOrNull(safeIdx)?.first
                AppLog.i(TAG, "Confirming emulator selection via trigger: system=${folder.systemId}, index=$safeIdx, package='$chosen'")
                onConfirm(chosen)
            } else {
                val safeIdx = selectedIndex.coerceIn(0, cores.lastIndex.coerceAtLeast(0))
                val chosen = cores.getOrNull(safeIdx)
                AppLog.i(TAG, "Confirming RetroArch core selection via trigger: index=$safeIdx, core='$chosen'")
                onConfirm(chosen)
            }
        }
    }

    AppModalDialog(
        onDismiss = {
            AppLog.d(TAG, "Dialog dismissed by scrim tap")
            onDismiss()
        },
        widthFraction = DIALOG_WIDTH_FRACTION,
        modifier = modifier,
    ) {
        Text(
            text =
                stringResource(
                    if (isMultiEmulatorSystem) {
                        R.string.gamefocus_dialog_select_emulator_title
                    } else {
                        R.string.gamefocus_dialog_system_recognized_title
                    },
                ),
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

        if (isMultiEmulatorSystem) {
            Text(
                text =
                    stringResource(
                        if (isSwitchSystem) {
                            R.string.gamefocus_dialog_select_emulator_label
                        } else {
                            R.string.gamefocus_dialog_select_pc_emulator_label
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.onSurface,
                modifier = Modifier.padding(bottom = DIALOG_TITLE_PADDING_BOTTOM),
            )

            VerticalRollingCarousel(
                selectedIndex = selectedIndex,
                items = emulatorOptions,
                onSelectedIndexChange = onSelectedIndexChange,
                labelProvider = { it.second },
                visibleItemsCount = CAROUSEL_VISIBLE_ITEMS_COUNT,
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
                        val selectedEmulator =
                            emulatorOptions.getOrNull(selectedIndex.coerceIn(0, emulatorOptions.lastIndex.coerceAtLeast(0)))
                        AppLog.i(
                            TAG,
                            "User confirmed emulator assignment: '${selectedEmulator?.first}' for folder ${folder.folderPath}",
                        )
                        onConfirm(selectedEmulator?.first)
                    },
                )
                Spacer(modifier = Modifier.width(DIALOG_INNER_SPACING))
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_B,
                    text = stringResource(R.string.gamefocus_dialog_cancel),
                    onClick = {
                        AppLog.d(TAG, "User cancelled emulator assignment dialog")
                        onDismiss()
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
                visibleItemsCount = CAROUSEL_VISIBLE_ITEMS_COUNT,
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
                        val safeIdx = selectedIndex.coerceIn(0, cores.lastIndex.coerceAtLeast(0))
                        val selectedCore = cores.getOrNull(safeIdx)
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
