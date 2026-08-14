package com.stormpanda.megingiard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppModalDialog

private const val TAG = "UpdatePromptDialog"

private val UPD_DIALOG_SPACING = 16.dp

@Composable
internal fun UpdatePromptDialog(
    tagName: String,
    colors: AppColors,
    accentColor: Color,
    onBackupAndOpen: () -> Unit,
    onOpenDirectly: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppLog.d(TAG, "UpdatePromptDialog displayed for release $tagName")

    AppModalDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.update_dialog_title, tagName),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.update_dialog_message, tagName),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceSecondary,
        )

        Spacer(modifier = Modifier.height(UPD_DIALOG_SPACING))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    AppLog.d(TAG, "User chose Backup & Open Link")
                    onBackupAndOpen()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_btn_backup_and_open),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = {
                    AppLog.d(TAG, "User chose Open Link Directly")
                    onOpenDirectly()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_btn_open_directly),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.update_dialog_btn_cancel),
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
