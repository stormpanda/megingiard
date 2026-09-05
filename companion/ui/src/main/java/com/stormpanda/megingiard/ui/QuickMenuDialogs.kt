package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R

private const val TAG = "QuickMenuDialogs"

private val QMD_SPACING_12 = 12.dp
private val QMD_SPACING_16 = 16.dp
private val QMD_SPACING_8 = 8.dp

@Composable
internal fun ShutOffConfirmDialog(
    colors: AppColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalDialog(
        onDismiss = {
            AppLog.d(TAG, "ShutOffConfirmDialog dismissed via scrim")
            onDismiss()
        },
        widthFraction = PM_NAME_DIALOG_WIDTH_FRACTION,
        cornerRadius = PM_PANEL_CORNER,
        contentPadding = PM_CONTENT_PADDING,
        scrimAlpha = PM_NAME_DIALOG_SCRIM_ALPHA,
    ) {
        Text(
            text = stringResource(R.string.shut_off_dialog_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(QMD_SPACING_12))
        Text(
            text = stringResource(R.string.shut_off_dialog_body),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(QMD_SPACING_16))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                AppLog.d(TAG, "ShutOffConfirmDialog cancelled")
                onDismiss()
            }) {
                Text(stringResource(R.string.settings_color_cancel), color = colors.onSurfaceSecondary)
            }
            Spacer(Modifier.width(QMD_SPACING_8))
            TextButton(onClick = {
                AppLog.d(TAG, "ShutOffConfirmDialog confirmed")
                onConfirm()
            }) {
                Text(stringResource(R.string.shut_off_dialog_confirm), color = colors.error)
            }
        }
    }
}
