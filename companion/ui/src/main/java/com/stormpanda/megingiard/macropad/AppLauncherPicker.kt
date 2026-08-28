package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppIcon
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.firstDeckItem

private val ALP_ICON_SIZE = 24.dp

@Composable
internal fun AppLauncherPicker(
    current: PadAction.AppLauncher,
    onOpenPicker: () -> Unit,
    isFirstItem: Boolean = false,
) {
    val context = LocalContext.current
    val resolvedName = resolveAppName(context, current.packageName)
    val appTitle =
        resolvedName.ifBlank {
            current.packageName.ifBlank {
                stringResource(R.string.app_launcher_picker_select_app)
            }
        }

    GamepadActionCard(
        title = stringResource(R.string.app_launcher_picker_title),
        description = appTitle,
        icon = Icons.Rounded.Apps,
        actionLeadingContent = {
            if (current.packageName.isNotBlank()) {
                AppIcon(
                    packageName = current.packageName,
                    modifier = Modifier.size(ALP_ICON_SIZE),
                )
            }
        },
        onClick = onOpenPicker,
        modifier = Modifier.firstDeckItem(isFirstItem),
    )
}
