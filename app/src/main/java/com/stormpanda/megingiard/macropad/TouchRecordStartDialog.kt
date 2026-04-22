package com.stormpanda.megingiard.macropad

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "TouchRecordStartDialog"

/**
 * Confirmation dialog shown before the recording mirror is opened.
 * Informs the user that the screen mirror will appear on the secondary display and
 * asks them to tap the desired position to record it as a [MacroStep.TouchTap].
 *
 * @param onStart  Called when the user confirms and wants to begin recording.
 * @param onCancel Called when the user cancels the dialog.
 */
@Composable
internal fun TouchRecordStartDialog(
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(R.string.macropad_macro_record_touch_dialog_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                stringResource(R.string.macropad_macro_record_touch_dialog_message),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onStart) {
                Text(
                    stringResource(R.string.macropad_macro_record_touch_start),
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
                )
            }
        },
    )
}
