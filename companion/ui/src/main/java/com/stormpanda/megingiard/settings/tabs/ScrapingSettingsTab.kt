package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.viewmodel.SteamGridDbTestStatus

@Composable
fun ScrapingSettingsTab(
    steamGridDbApiToken: String,
    onOpenTokenSubPage: () -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.settings_steamgriddb_token),
        description =
            if (steamGridDbApiToken.isNotBlank()) {
                stringResource(R.string.settings_steamgriddb_token_configured, steamGridDbApiToken.take(6))
            } else {
                stringResource(R.string.settings_steamgriddb_token_desc)
            },
        icon = Icons.Rounded.Key,
        onClick = onOpenTokenSubPage,
        modifier = Modifier.firstDeckItem(),
    )
}

@Composable
fun SteamGridDbTokenSubPage(
    token: String,
    onTokenChange: (String) -> Unit,
    testStatus: SteamGridDbTestStatus,
    onTestConnection: () -> Unit,
) {
    GamepadTextFieldCard(
        title = stringResource(R.string.settings_steamgriddb_token),
        description = stringResource(R.string.settings_steamgriddb_token_desc),
        placeholder = stringResource(R.string.settings_steamgriddb_token_placeholder),
        value = token,
        onValueChange = onTokenChange,
        icon = Icons.Rounded.Key,
        modifier = Modifier.firstDeckItem(),
    )

    val isDestructive =
        testStatus != SteamGridDbTestStatus.IDLE &&
            testStatus != SteamGridDbTestStatus.TESTING &&
            testStatus != SteamGridDbTestStatus.CONNECTED

    GamepadActionCard(
        title = stringResource(R.string.settings_steamgriddb_test_title),
        description = stringResource(R.string.settings_steamgriddb_test_desc),
        icon = Icons.Rounded.Sensors,
        actionLeadingContent = {
            GamepadPill(
                text = stringResource(testStatus.labelResId),
                isAccent = testStatus == SteamGridDbTestStatus.CONNECTED,
                isHighlighted = testStatus == SteamGridDbTestStatus.TESTING,
                isDestructive = isDestructive,
            )
        },
        enabled = true,
        onClick = {
            if (token.isNotBlank() && testStatus != SteamGridDbTestStatus.TESTING) {
                onTestConnection()
            }
        },
    )
}
