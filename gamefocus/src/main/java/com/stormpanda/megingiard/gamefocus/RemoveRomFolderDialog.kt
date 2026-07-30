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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.rom.CustomRomFolder
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "RemoveRomFolderDialog"

private val DIALOG_ITEM_PADDING_VERTICAL = 10.dp
private val DIALOG_ITEM_PADDING_HORIZONTAL = 12.dp
private val DIALOG_SPACING = 12.dp
private val DIALOG_LIST_MAX_HEIGHT = 200.dp
private val DIALOG_CORNER_RADIUS = 8.dp

@Composable
fun RemoveRomFolderDialog(
    romFolders: List<CustomRomFolder>,
    selectedIndex: Int = 0,
    onSelectedIndexChange: (Int) -> Unit = {},
    onDismiss: () -> Unit,
    onSelectFolder: (CustomRomFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Showing Remove ROM Folder dialog, folders size: ${romFolders.size}")
    }

    AppModalDialog(
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.gamefocus_dialog_remove_rom_folder_title),
            style = MaterialTheme.typography.titleMedium,
            color = appColors.onSurface,
            modifier = Modifier.padding(bottom = DIALOG_SPACING),
        )
        Text(
            text = stringResource(R.string.gamefocus_dialog_remove_rom_folder_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors.onSurfaceSecondary,
            modifier = Modifier.padding(bottom = DIALOG_SPACING),
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = DIALOG_LIST_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(romFolders) { index, folder ->
                val isFocused = index == selectedIndex
                val rowBg = if (isFocused) appColors.accent.copy(alpha = 0.2f) else Color.Transparent
                val rowBorderModifier =
                    if (isFocused) {
                        Modifier.border(1.dp, appColors.accent, RoundedCornerShape(DIALOG_CORNER_RADIUS))
                    } else {
                        Modifier
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DIALOG_CORNER_RADIUS))
                            .background(rowBg)
                            .then(rowBorderModifier)
                            .clickable {
                                onSelectedIndexChange(index)
                                onSelectFolder(folder)
                            }.padding(
                                vertical = DIALOG_ITEM_PADDING_VERTICAL,
                                horizontal = DIALOG_ITEM_PADDING_HORIZONTAL,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${folder.systemName} (${folder.folderPath})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isFocused) appColors.onSurface else appColors.onSurfaceSecondary,
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
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
            )

            if (romFolders.isNotEmpty()) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = "Select",
                    onClick = {
                        romFolders.getOrNull(selectedIndex)?.let {
                            onSelectFolder(it)
                        }
                    },
                )
            }
        }
    }
}
