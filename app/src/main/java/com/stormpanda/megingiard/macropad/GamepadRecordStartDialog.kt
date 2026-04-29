package com.stormpanda.megingiard.macropad

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "GamepadRecordStartDialog"

/**
 * Confirmation dialog shown before gamepad macro recording starts.
 *
 * @param onStart          Called when the user confirms and wants to begin recording.
 * @param onCancel         Called when the user cancels the dialog.
 * @param onDontShowAgain  Called when the user confirms and wants to skip this dialog in future.
 */
@Composable
internal fun GamepadRecordStartDialog(
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    val colors = LocalAppColors.current
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
            Text(
                stringResource(R.string.macropad_macro_record_gamepad_dialog_message),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onStart) {
                Text(
                    stringResource(R.string.macropad_macro_record_gamepad_start),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDontShowAgain) {
                Text(
                    stringResource(R.string.macropad_macro_record_touch_dont_show_again),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.macropad_editor_cancel),
                    color = colors.onSurfaceSecondary,
                )
            }
        },
    )
}