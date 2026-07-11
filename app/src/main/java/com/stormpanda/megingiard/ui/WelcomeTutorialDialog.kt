package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R

private const val TAG = "WelcomeTutorialDialog"
private val WT_CONTENT_SPACING = 16.dp
private val WT_INTRO_SPACING = 6.dp

@Composable
fun WelcomeTutorialDialog(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Welcome tutorial dialog shown")
    }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = {
            AppLog.d(TAG, "Welcome tutorial dialog dismissed via request")
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.welcome_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.welcome_desc),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(WT_CONTENT_SPACING))
                Text(
                    text = stringResource(R.string.welcome_help_title),
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(WT_INTRO_SPACING))
                Text(
                    text = stringResource(R.string.welcome_help_intro),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AppLog.d(TAG, "Welcome tutorial dialog confirmed")
                    onDismiss()
                },
            ) {
                Text(
                    text = stringResource(R.string.welcome_btn_got_it),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}
