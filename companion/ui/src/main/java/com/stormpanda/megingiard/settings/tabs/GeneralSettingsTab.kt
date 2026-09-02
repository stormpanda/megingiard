package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.privd.PrivdConstants
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsSubPage
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.update.AppReleaseInfo

@Composable
fun GeneralSettingsTab(
    updateAvailable: Boolean,
    latestReleaseInfo: AppReleaseInfo?,
    privdState: PrivdState,
    appLanguage: AppLanguage,
    excludeFromRecents: Boolean,
    onNavigateToSubPage: (SettingsSubPage) -> Unit,
    onStartWelcomeTour: () -> Unit,
    onOpenPrivdSetup: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onExcludeFromRecentsChange: (Boolean) -> Unit,
    onResetTutorials: () -> Unit,
) {
    if (updateAvailable && latestReleaseInfo != null) {
        GamepadActionCard(
            title =
                stringResource(
                    R.string.settings_update_available_banner,
                    latestReleaseInfo.tagName,
                ),
            description = stringResource(R.string.settings_update_available_banner_desc),
            icon = Icons.Rounded.SystemUpdate,
            onClick = { onNavigateToSubPage(SettingsSubPage.UPDATE_AVAILABLE) },
            modifier = Modifier.firstDeckItem(),
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.settings_start_welcome_tour),
        description = stringResource(R.string.settings_start_welcome_tour_desc),
        icon = Icons.Rounded.PlayCircle,
        onClick = onStartWelcomeTour,
        modifier = Modifier.firstDeckItem(isFirst = !updateAvailable || latestReleaseInfo == null),
    )

    val isPrivdRunning = privdState == PrivdState.RUNNING
    GamepadActionCard(
        title = stringResource(R.string.privd_title),
        description = stringResource(R.string.help_settings_privd_desc),
        icon = Icons.Rounded.Security,
        trailingContent = {
            GamepadPill(
                text =
                    if (isPrivdRunning) {
                        stringResource(
                            R.string.privd_status_running_version,
                            PrivdConstants.PRIVD_VERSION,
                        )
                    } else {
                        stringResource(R.string.gamepad_toggle_off)
                    },
                isAccent = isPrivdRunning,
            )
        },
        onClick = onOpenPrivdSetup,
    )

    GamepadChoiceCard(
        title = stringResource(R.string.settings_language),
        description = stringResource(R.string.help_settings_language_desc),
        selectedText = stringResource(appLanguage.displayNameResId()),
        icon = Icons.Rounded.Language,
        onPrevious = {
            onLanguageChange(AppLanguage.entries.cycle(appLanguage, BumperDirection.PREV))
        },
        onNext = {
            onLanguageChange(AppLanguage.entries.cycle(appLanguage, BumperDirection.NEXT))
        },
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_exclude_from_recents),
        description = stringResource(R.string.settings_exclude_from_recents_desc),
        checked = excludeFromRecents,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = onExcludeFromRecentsChange,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_reset_tutorials),
        description = stringResource(R.string.settings_reset_tutorials_desc),
        icon = Icons.AutoMirrored.Rounded.HelpOutline,
        onClick = onResetTutorials,
    )
}
