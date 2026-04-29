package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "GamepadRecordStartDialog"

/**
 * Confirmation dialog shown before gamepad macro recording starts.
 *
 * @param onStart          Called when the user confirms (without "don't show again").
 * @param onCancel         Called when the user cancels the dialog.
 * @param onDontShowAgain  Called when the user confirms with "don't show again" checked.
 */
@Composable
internal fun GamepadRecordStartDialog(
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    val colors = LocalAppColors.current
    var dontShowAgain by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        AppLog.d(TAG, "visible")
        onDispose { AppLog.d(TAG, "disposed") }
    }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(R.string.macropad_macro_record_gamepad_dialog_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.macropad_macro_record_gamepad_dialog_message),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                    )
                    Text(
                        stringResource(R.string.macropad_macro_record_touch_dont_show_again),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (dontShowAgain) onDontShowAgain else onStart) {
                Text(
                    stringResource(R.string.macropad_macro_record_gamepad_dialog_confirm),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.macropad_editor_cancel),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}