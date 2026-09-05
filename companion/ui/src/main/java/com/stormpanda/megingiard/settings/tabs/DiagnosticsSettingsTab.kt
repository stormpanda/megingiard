package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem

@Composable
fun DiagnosticsSettingsTab(
    logLevel: AppLog.Level,
    onLogLevelChange: (AppLog.Level) -> Unit,
    onSaveLogReport: () -> Unit,
) {
    GamepadChoiceCard(
        title = stringResource(R.string.settings_log_level),
        description = stringResource(R.string.help_settings_log_level_desc),
        selectedText = logLevel.name,
        icon = Icons.Rounded.BugReport,
        onPrevious = { onLogLevelChange(AppLog.Level.entries.cycle(logLevel, BumperDirection.PREV)) },
        onNext = { onLogLevelChange(AppLog.Level.entries.cycle(logLevel, BumperDirection.NEXT)) },
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_save_log_report),
        description = stringResource(R.string.help_settings_save_log_desc),
        icon = Icons.Rounded.SaveAlt,
        onClick = onSaveLogReport,
    )
}
