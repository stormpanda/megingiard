package com.stormpanda.megingiard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MegingiardExport
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
    BackHandler(onBack = onDismiss)
    AppModalDialog(
        onDismiss = onDismiss,
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
            TextButton(onClick = onDismiss) {
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
    BackHandler(onBack = onDismiss)
    AppModalDialog(
        onDismiss = onDismiss,
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
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = colors.onSurfaceSecondary)
            }
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmText,
                    color = if (confirmEnabled) accentColor else colors.onSurfaceSecondary,
                )
            }
        }
    }
}
