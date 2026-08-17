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
internal fun ImportPreviewDialog(
    export: MegingiardExport,
    importMode: ConfigManager.ImportMode,
    colors: AppColors,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metadata = export.metadata
    val warningText =
        when (importMode) {
            ConfigManager.ImportMode.BACKUP_RESTORE -> stringResource(R.string.config_import_warning_backup)
            ConfigManager.ImportMode.PROFILE_SHARE -> stringResource(R.string.config_import_warning_profile)
        }
    BackHandler(onBack = onDismiss)
    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = GSD_DIALOG_WIDTH_FRACTION,
        cornerRadius = GSD_DIALOG_CORNER,
        contentPadding = GSD_DIALOG_PADDING,
        scrimAlpha = GSD_DIALOG_SCRIM_ALPHA,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.config_import_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        if (!metadata.author.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.config_import_meta_author, metadata.author!!),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        val description = metadata.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (metadata.tags.isNotEmpty()) {
            Text(
                text = "${stringResource(R.string.config_import_tags_label)}: ${metadata.tags.joinToString(", ")}",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        val hasSettingsBullets =
            importMode == ConfigManager.ImportMode.BACKUP_RESTORE &&
                (
                    "global" in export.settings || "mirror" in export.settings ||
                        "touchpad" in export.settings || "keyboard" in export.settings ||
                        "macropad_settings" in export.settings
                )
        val hasSectionBullets = hasSettingsBullets || export.profiles.isNotEmpty()
        if (hasSectionBullets) {
            Text(
                text = stringResource(R.string.config_import_sections_label),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (importMode == ConfigManager.ImportMode.BACKUP_RESTORE) {
            if ("global" in export.settings) {
                Text(
                    "\u2022 ${stringResource(R.string.config_import_section_global)}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if ("mirror" in export.settings) {
                Text(
                    "\u2022 ${stringResource(R.string.config_import_section_mirror)}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if ("touchpad" in export.settings) {
                Text(
                    "\u2022 ${stringResource(R.string.config_import_section_touchpad)}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if ("keyboard" in export.settings) {
                Text(
                    "\u2022 ${stringResource(R.string.config_import_section_keyboard)}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if ("macropad_settings" in export.settings) {
                Text(
                    "\u2022 ${stringResource(R.string.config_import_section_macropad_settings)}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (export.profiles.isNotEmpty() || export.profiles.any { it.macros.isNotEmpty() }) {
            Text(
                text = "\u2022 ${stringResource(
                    R.string.config_import_section_macropad,
                    export.profiles.size,
                    export.profiles.sumOf { it.macros.size },
                )}",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val imageCount =
            ConfigManager.getPendingInAppImageCount().takeIf { it > 0 }
                ?: export.profiles.sumOf { p -> p.layouts.count { !it.backgroundImagePath.isNullOrEmpty() } }
        if (imageCount > 0) {
            Text(
                text = "\u2022 ${stringResource(R.string.config_import_section_background_images, imageCount)}",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = warningText,
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.config_import_cancel), color = colors.onSurfaceSecondary)
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.config_import_confirm), color = accentColor)
            }
        }
    }
}

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
