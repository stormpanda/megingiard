package com.stormpanda.megingiard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppModalDialog

private const val TAG = "GlobalSettingsDialogs"

// ── Dialog Constants ────────────────────────────────────────────────────────
private const val GSD_DIALOG_SCRIM_ALPHA = 0.5f
private val GSD_DIALOG_WIDTH_FRACTION = 0.85f
private val GSD_DIALOG_CORNER = 16.dp
private val GSD_DIALOG_PADDING = 20.dp

@Composable
internal fun InTreeMessageDialog(
    title: String,
    text: String,
    buttonText: String,
    colors: AppColors,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(title) {
        AppLog.d(TAG, "InTreeMessageDialog shown: title='$title'")
    }

    BackHandler(onBack = {
        AppLog.d(TAG, "InTreeMessageDialog dismissed via back: title='$title'")
        onDismiss()
    })
    AppModalDialog(
        onDismiss = {
            AppLog.d(TAG, "InTreeMessageDialog dismissed: title='$title'")
            onDismiss()
        },
        widthFraction = GSD_DIALOG_WIDTH_FRACTION,
        cornerRadius = GSD_DIALOG_CORNER,
        contentPadding = GSD_DIALOG_PADDING,
        scrimAlpha = GSD_DIALOG_SCRIM_ALPHA,
    ) {
        Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
        if (text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                AppLog.d(TAG, "InTreeMessageDialog confirmed with button: title='$title'")
                onDismiss()
            }) {
                Text(buttonText, color = accentColor)
            }
        }
    }
}

@Composable
internal fun InTreeConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    confirmEnabled: Boolean = true,
    dismissText: String,
    colors: AppColors,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(title) {
        AppLog.d(TAG, "InTreeConfirmDialog shown: title='$title'")
    }

    BackHandler(onBack = {
        AppLog.d(TAG, "InTreeConfirmDialog dismissed via back: title='$title'")
        onDismiss()
    })
    AppModalDialog(
        onDismiss = {
            AppLog.d(TAG, "InTreeConfirmDialog dismissed: title='$title'")
            onDismiss()
        },
        widthFraction = GSD_DIALOG_WIDTH_FRACTION,
        cornerRadius = GSD_DIALOG_CORNER,
        contentPadding = GSD_DIALOG_PADDING,
        scrimAlpha = GSD_DIALOG_SCRIM_ALPHA,
    ) {
        Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
        if (text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                AppLog.d(TAG, "InTreeConfirmDialog cancelled: title='$title'")
                onDismiss()
            }) {
                Text(dismissText, color = colors.onSurfaceSecondary)
            }
            TextButton(
                onClick = {
                    AppLog.d(TAG, "InTreeConfirmDialog confirmed: title='$title'")
                    onConfirm()
                },
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmText,
                    color = if (confirmEnabled) accentColor else colors.onSurfaceSecondary,
                )
            }
        }
    }
}
