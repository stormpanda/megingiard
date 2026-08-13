package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R

private const val TAG = "AppTutorialModal"

/**
 * Standardized tutorial / onboarding modal scaffold for Megingiard.
 */
@Composable
fun AppTutorialModal(
    title: String,
    tag: String = TAG,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.welcome_btn_got_it),
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) {
        AppLog.d(tag, "$title tutorial dialog shown")
    }

    AppAlertDialog(
        onDismissRequest = {
            AppLog.d(tag, "$title tutorial dialog dismissed")
            onDismiss()
        },
        title = {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AppLog.d(tag, "$title tutorial dialog confirmed")
                    onDismiss()
                },
            ) {
                Text(
                    text = confirmText,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = dismissButton,
    )
}
