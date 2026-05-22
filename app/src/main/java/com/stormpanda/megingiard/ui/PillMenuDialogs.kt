package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R

private const val TAG = "PillMenuDialogs"

@Composable
internal fun InTreeNameInputDialog(
    title: String,
    hint: String,
    colors: AppColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    existingNames: List<String>,
    currentName: String? = null,
) {
    var name by remember { mutableStateOf("") }
    val normalizedName = name.trim()
    val isDuplicate = existingNames.any { existing ->
        !existing.equals(currentName?.trim(), ignoreCase = true) &&
            existing.equals(normalizedName, ignoreCase = true)
    }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val dismissContentDescription = stringResource(R.string.pill_menu_dismiss_dialog)
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = PM_NAME_DIALOG_SCRIM_ALPHA))
                .semantics { contentDescription = dismissContentDescription }
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(PM_NAME_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(PM_PANEL_CORNER))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.forEach { change ->
                                if (!change.isConsumed) change.consume()
                             }
                        }
                    }
                }
                .padding(PM_CONTENT_PADDING),
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(hint, color = colors.onSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!hasError) onConfirm(normalizedName) }),
                isError = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.controlOverlayBorder,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_close), color = colors.onSurface)
                }
                TextButton(onClick = { onConfirm(normalizedName) }, enabled = !hasError) {
                    Text(stringResource(R.string.config_ok), color = colors.accent)
                }
            }
        }
    }
}
