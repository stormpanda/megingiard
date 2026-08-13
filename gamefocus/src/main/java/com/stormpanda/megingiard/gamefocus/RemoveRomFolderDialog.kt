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
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.VerticalRollingCarousel

private const val TAG = "RemoveRomFolderDialog"

private val DIALOG_ITEM_PADDING_VERTICAL = 10.dp
private val DIALOG_ITEM_PADDING_HORIZONTAL = 12.dp
private val DIALOG_SPACING = 12.dp
private val DIALOG_LIST_MAX_HEIGHT = 200.dp
private val DIALOG_CORNER_RADIUS = 8.dp
private val DIALOG_INNER_SPACING = 8.dp

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
        widthFraction = 0.45f,
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

        VerticalRollingCarousel(
            selectedIndex = selectedIndex,
            items = romFolders,
            onSelectedIndexChange = onSelectedIndexChange,
            labelProvider = { folder ->
                "${folder.systemName} (${folder.folderPath})"
            },
            visibleItemsCount = 5,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = DIALOG_SPACING),
        )

        Spacer(modifier = Modifier.height(DIALOG_SPACING))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (romFolders.isNotEmpty()) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = stringResource(R.string.gamefocus_dialog_select),
                    onClick = {
                        romFolders.getOrNull(selectedIndex)?.let {
                            onSelectFolder(it)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(DIALOG_INNER_SPACING))
            }
            GamePadButtonAction(
                button = GamePadButton.BUTTON_B,
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
            )
        }
    }
}
