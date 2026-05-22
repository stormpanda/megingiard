package com.stormpanda.megingiard.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppDropdown
import java.time.LocalDate

private const val TAG = "GlobalSettingsDialogs"

// ── Dialog Constants ────────────────────────────────────────────────────────
private const val GSD_DIALOG_SCRIM_ALPHA = 0.5f
private val GSD_DIALOG_WIDTH_FRACTION = 0.85f
private val GSD_DIALOG_CORNER = 16.dp
private val GSD_DIALOG_PADDING = 20.dp

@Composable
internal fun ExportMetadataDialog(
    defaultMetadata: ExportMetadata,
    colors: AppColors,
    accentColor: Color,
    onConfirm: (ExportMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = colors.onSurfaceSecondary,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
    )
    val focusManager = LocalFocusManager.current
    val doConfirm = {
        val parsedTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        onConfirm(
            defaultMetadata.copy(
                author = author.trim().ifEmpty { null },
                description = description.trim().ifEmpty { null },
                tags = parsedTags,
            )
        )
    }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GSD_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.config_export_dialog_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.config_export_author), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.config_export_description), style = MaterialTheme.typography.bodySmall) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.config_export_tags), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.config_export_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(onClick = doConfirm) {
                    Text(stringResource(R.string.config_export_confirm), color = accentColor)
                }
            }
        }
    }
}

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
    val warningText = when (importMode) {
        ConfigManager.ImportMode.BACKUP_RESTORE -> stringResource(R.string.config_import_warning_backup)
        ConfigManager.ImportMode.PROFILE_SHARE  -> stringResource(R.string.config_import_warning_profile)
    }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GSD_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
            val hasSettingsBullets = importMode == ConfigManager.ImportMode.BACKUP_RESTORE &&
                ("global" in export.settings || "mirror" in export.settings ||
                    "touchpad" in export.settings || "keyboard" in export.settings ||
                    "macropad_settings" in export.settings)
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
                    Text("\u2022 ${stringResource(R.string.config_import_section_global)}", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if ("mirror" in export.settings) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_mirror)}", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if ("touchpad" in export.settings) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_touchpad)}", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if ("keyboard" in export.settings) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_keyboard)}", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if ("macropad_settings" in export.settings) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_macropad_settings)}", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (export.profiles.isNotEmpty() || export.profiles.any { it.macros.isNotEmpty() }) {
                Text(
                    text = "\u2022 ${stringResource(R.string.config_import_section_macropad, export.profiles.size, export.profiles.sumOf { it.macros.size })}",
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GSD_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            if (text.isNotBlank()) {
                Text(text, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelMedium)
            }
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
}

@Composable
internal fun ProfileExportDialog(
    colors: AppColors,
    accentColor: Color,
    onConfirm: (ExportMetadata, PadProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val profiles by MacroPadState.profiles.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    var selectedProfile by remember(profiles) {
        mutableStateOf(activeProfile ?: profiles.firstOrNull())
    }
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = colors.onSurfaceSecondary,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
    )
    val focusManager = LocalFocusManager.current
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GSD_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.config_profile_export_dialog_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (profiles.size > 1) {
                Text(
                    text = stringResource(R.string.config_profile_export_select),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                AppDropdown(
                    selected = selectedProfile ?: profiles.first(),
                    options = profiles,
                    optionText = { it.name },
                    onSelected = { selectedProfile = it },
                    fillMaxWidth = true,
                )
            }
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.config_export_author), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.config_export_description), style = MaterialTheme.typography.bodySmall) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.config_export_tags), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.config_export_cancel), color = colors.onSurfaceSecondary)
                }
                val profile = selectedProfile ?: profiles.firstOrNull()
                TextButton(
                    onClick = {
                        if (profile == null) return@TextButton
                        val parsedTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val metadata = ConfigManager.defaultMetadata(context).copy(
                            author = author.trim().ifEmpty { null },
                            description = description.trim().ifEmpty { null },
                            tags = parsedTags,
                        )
                        onConfirm(metadata, profile)
                    },
                    enabled = profile != null,
                ) {
                    Text(stringResource(R.string.config_export_confirm), color = if (profile != null) accentColor else colors.onSurfaceSecondary)
                }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GSD_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            if (text.isNotBlank()) {
                Text(text, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelMedium)
            }
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
}

// ── Filename builders ───────────────────────────────────────────────────────
private val FILENAME_UNSAFE = Regex("[^A-Za-z0-9]")

internal fun buildExportFilename(metadata: ExportMetadata): String {
    val parts = mutableListOf("megingiard", LocalDate.now().toString())
    metadata.author?.takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.trim().take(20).replace(FILENAME_UNSAFE, "_"))
    }
    metadata.description?.takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.trim().take(30).replace(FILENAME_UNSAFE, "_"))
    }
    return parts.joinToString("_") + ".mgrd"
}

internal fun buildProfileExportFilename(metadata: ExportMetadata, profileName: String): String {
    val parts = mutableListOf("megingiard_profile", LocalDate.now().toString())
    profileName.trim().takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.take(30).replace(FILENAME_UNSAFE, "_"))
    }
    metadata.author?.takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.trim().take(20).replace(FILENAME_UNSAFE, "_"))
    }
    return parts.joinToString("_") + ".mgrd"
}
