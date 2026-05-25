package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "TouchRecordStartDialog"

/**
 * Confirmation dialog shown before the recording mirror is opened.
 * Informs the user that the screen mirror will appear on the secondary display and
 * asks them to choose whether to record a single tap or a continuous gesture.
 *
 * @param onRecordTap      Called when the user selects the Tap option.
 * @param onRecordGesture  Called when the user selects the Gesture option.
 * @param onCancel         Called when the user cancels the dialog.
 */
@Composable
internal fun TouchRecordStartDialog(
    onRecordTap: () -> Unit,
    onRecordGesture: () -> Unit,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRecordTap) {
                    Text(
                        stringResource(R.string.macropad_macro_record_touch_option_tap),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onRecordGesture) {
                    Text(
                        stringResource(R.string.macropad_macro_record_touch_option_gesture),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
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
