package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.BuildConfig
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.update.AppReleaseInfo

const val GS_OBTAINIUM_REPO_URL = "https://github.com/stormpanda/megingiard"
const val GS_OBTAINIUM_FALLBACK_URL = "https://github.com/ImranR98/Obtainium"

@Composable
fun UpdatesSettingsTab(
    autoUpdateCheckEnabled: Boolean,
    updateAvailable: Boolean,
    latestReleaseInfo: AppReleaseInfo?,
    isCheckingUpdates: Boolean,
    updateCheckError: String?,
    hasTriggeredManualCheck: Boolean,
    onAutoUpdateCheckEnabledChange: (Boolean) -> Unit,
    onManualCheckClick: () -> Unit,
    onOpenObtainium: () -> Unit,
) {
    GamepadToggleCard(
        title = stringResource(R.string.settings_auto_update_check),
        description = stringResource(R.string.help_settings_auto_update_desc),
        checked = autoUpdateCheckEnabled,
        icon = Icons.Rounded.Update,
        onCheckedChange = onAutoUpdateCheckEnabledChange,
        modifier = Modifier.firstDeckItem(),
    )

    val updateBadgeText =
        when {
            !hasTriggeredManualCheck -> null
            isCheckingUpdates -> stringResource(R.string.gamepad_action_checking)
            updateAvailable -> stringResource(R.string.settings_update_now_btn)
            updateCheckError != null -> stringResource(R.string.settings_check_failed)
            else -> stringResource(R.string.settings_up_to_date)
        }

    GamepadActionCard(
        title = stringResource(R.string.settings_check_for_updates),
        description =
            if (hasTriggeredManualCheck && updateAvailable) {
                stringResource(R.string.settings_update_available_tag, latestReleaseInfo?.tagName ?: "")
            } else {
                stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME)
            },
        icon = Icons.Rounded.Refresh,
        actionLeadingContent =
            updateBadgeText?.let { text ->
                {
                    GamepadPill(
                        text = text,
                        isAccent = updateAvailable,
                        isHighlighted = isCheckingUpdates,
                        isDestructive = updateCheckError != null,
                    )
                }
            },
        onClick = onManualCheckClick,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_add_to_obtainium),
        description = stringResource(R.string.help_settings_add_to_obtainium_desc),
        icon = Icons.Rounded.Download,
        onClick = onOpenObtainium,
    )
}

@Composable
fun UpdateAvailableSubPage(
    tagName: String,
    effectiveAccent: Color,
    onBackupAndOpen: () -> Unit,
    onOpenDirectly: () -> Unit,
) {
    GamepadInfoBox(
        text = stringResource(R.string.update_dialog_title, tagName),
        description = stringResource(R.string.update_dialog_message, tagName),
        icon = Icons.Rounded.SystemUpdate,
        iconTint = effectiveAccent,
    )

    GamepadActionCard(
        title = stringResource(R.string.update_dialog_btn_backup_and_open),
        description = stringResource(R.string.update_dialog_backup_and_open_desc),
        icon = Icons.Rounded.SaveAlt,
        onClick = onBackupAndOpen,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.update_dialog_btn_open_directly),
        description = stringResource(R.string.update_dialog_open_directly_desc),
        icon = Icons.Rounded.OpenInBrowser,
        onClick = onOpenDirectly,
    )
}
