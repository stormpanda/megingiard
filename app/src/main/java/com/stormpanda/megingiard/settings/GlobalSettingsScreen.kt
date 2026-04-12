package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigActionCoordinator
import com.stormpanda.megingiard.config.ConfigExporter
import com.stormpanda.megingiard.config.ConfigImportCoordinator
import com.stormpanda.megingiard.config.ConfigImporter
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.ThemeMode
import java.time.LocalDate
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    val enabledTools by SettingsManager.enabledTools.collectAsState()
    val toolOrder by SettingsManager.toolOrder.collectAsState()
    val overlayTimeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val rememberLastTool by SettingsManager.rememberLastTool.collectAsState()
    val themeMode by SettingsManager.themeMode.collectAsState()
    val appLanguage by SettingsManager.appLanguage.collectAsState()
    val logLevel by SettingsManager.logLevel.collectAsState()
    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    var showColorPicker by remember { mutableStateOf(false) }
    // Export-result feedback (set by ConfigActionCoordinator after MainActivity writes the file)
    val exportResult by ConfigActionCoordinator.exportResult.collectAsState()
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset = 1 // header item before tool rows
        val newOrder = toolOrder.toMutableList()
        newOrder.add(to.index - offset, newOrder.removeAt(from.index - offset))
        SettingsManager.setToolOrder(newOrder)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_global_title),
                            color = colors.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tools section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_tools),
                        accentColor = effectiveAccent,
                        colors = colors
                    )
                }
                itemsIndexed(toolOrder, key = { _, tool -> tool.name }) { _, tool ->
                    val isEnabled = tool in enabledTools
                    ReorderableItem(reorderState, key = tool.name) { isDragging ->
                        ToolOrderRow(
                            tool = tool,
                            isEnabled = isEnabled,
                            isDragging = isDragging,
                            canDisable = enabledTools.size > 1 || !isEnabled,
                            accentColor = effectiveAccent,
                            colors = colors,
                            onToggle = { checked ->
                                val newEnabled = if (checked) enabledTools + tool else enabledTools - tool
                                if (newEnabled.isNotEmpty()) SettingsManager.setEnabledTools(newEnabled)
                            },
                            dragHandleModifier = Modifier.draggableHandle()
                        )
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }

                // General section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_general),
                        accentColor = effectiveAccent,
                        colors = colors
                    )
                    OverlayTimeoutRow(
                        overlayTimeoutMs = overlayTimeoutMs,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onTimeoutChanged = { SettingsManager.setOverlayTimeoutMs(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    OverlayPositionRow(
                        overlayAtBottom = overlayAtBottom,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setOverlayAtBottom(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    RememberLastToolRow(
                        rememberLastTool = rememberLastTool,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setRememberLastTool(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    LanguagePickerRow(
                        language = appLanguage,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setAppLanguage(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    LogLevelPickerRow(
                        logLevel = logLevel,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setLogLevel(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                }

                // Appearance section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_appearance),
                        accentColor = effectiveAccent,
                        colors = colors
                    )
                    ThemePickerRow(
                        themeMode = themeMode,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setThemeMode(it) }
                    )
                    if (themeMode.supportsCustomAccent) {
                        HorizontalDivider(color = colors.divider)
                        AccentColorRow(
                            accentColor = accentColor,
                            colors = colors,
                            onClick = { showColorPicker = true }
                        )
                    }
                }

                // Configuration section
                item {
                    ConfigSection(
                        colors = colors,
                        accentColor = effectiveAccent,
                    )
                }
            }
        }
        // Hosted color picker — rendered in-tree so it works in Presentation context too
        if (showColorPicker) {
            ColorWheelPicker(
                initialColor = accentColor,
                onColorSelected = { color ->
                    SettingsManager.setAccentColor(color)
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }

        // Export-result feedback dialogs (result delivered by MainActivity)
        when (val result = exportResult) {
            is ConfigActionCoordinator.ExportResult.Success -> {
                AlertDialog(
                    onDismissRequest = { ConfigActionCoordinator.clearExportResult() },
                    containerColor = colors.surface,
                    title = { Text(stringResource(R.string.config_success_title), color = colors.onSurface) },
                    text = { Text(stringResource(R.string.config_export_success), color = colors.onSurfaceSecondary, fontSize = 13.sp) },
                    confirmButton = {
                        TextButton(onClick = { ConfigActionCoordinator.clearExportResult() }) {
                            Text(stringResource(R.string.config_ok), color = effectiveAccent)
                        }
                    },
                )
            }
            is ConfigActionCoordinator.ExportResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { ConfigActionCoordinator.clearExportResult() },
                    containerColor = colors.surface,
                    title = { Text(stringResource(R.string.config_error_title), color = colors.onSurface) },
                    text = { Text(result.message ?: "", color = colors.onSurfaceSecondary, fontSize = 13.sp) },
                    confirmButton = {
                        TextButton(onClick = { ConfigActionCoordinator.clearExportResult() }) {
                            Text(stringResource(R.string.config_ok), color = effectiveAccent)
                        }
                    },
                )
            }
            null -> {}
        }

    }
}

/**
 * Configuration export / import section.
 *
 * Posts export/import requests to [ConfigActionCoordinator] — MainActivity holds
 * the actual [ActivityResultLauncher]s and opens the system file picker on behalf
 * of this composable. This means the section works correctly regardless of whether
 * GlobalSettingsScreen is rendered inside MainActivity or inside MirrorPresentation.
 */
@Composable
private fun ConfigSection(colors: AppColors, accentColor: Color) {
    val context = LocalContext.current

    var showExportMetadataDialog by remember { mutableStateOf(false) }
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }

    // Import preview: ConfigImportCoordinator carries the parsed file once MainActivity
    // reads it. Only show the dialog while Settings is open (collect here).
    val pendingImport by ConfigImportCoordinator.pendingParsedImport.collectAsState()

    // Show import preview as soon as the coordinator has a parsed export ready.
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) showImportPreviewDialog = pendingImport
    }

    SettingsCategoryHeader(
        text = stringResource(R.string.settings_section_config),
        accentColor = accentColor,
        colors = colors,
    )
    ConfigActionRow(
        label = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.settings_config_export_desc),
        accentColor = accentColor,
        colors = colors,
        onClick = { showExportMetadataDialog = true },
    )
    HorizontalDivider(color = colors.divider)
    ConfigActionRow(
        label = stringResource(R.string.settings_config_import),
        description = stringResource(R.string.settings_config_import_desc),
        accentColor = accentColor,
        colors = colors,
        onClick = { ConfigActionCoordinator.requestImport() },
    )

    if (showExportMetadataDialog) {
        ExportMetadataDialog(
            defaultMetadata = ConfigExporter.defaultMetadata(context),
            colors = colors,
            accentColor = accentColor,
            onConfirm = { metadata ->
                showExportMetadataDialog = false
                ConfigActionCoordinator.requestExport(
                    metadata = metadata,
                    filename = context.getString(
                        R.string.config_export_default_filename, LocalDate.now()
                    ),
                )
            },
            onDismiss = { showExportMetadataDialog = false },
        )
    }

    showImportPreviewDialog?.let { export ->
        ImportPreviewDialog(
            export = export,
            colors = colors,
            accentColor = accentColor,
            onConfirm = {
                showImportPreviewDialog = null
                ConfigImporter.applyImport(export)
                ConfigImportCoordinator.clear()
                importSuccess = true
            },
            onDismiss = {
                showImportPreviewDialog = null
                ConfigImportCoordinator.clear()
            },
        )
    }

    importError?.let { error ->
        AlertDialog(
            onDismissRequest = { importError = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.config_error_title), color = colors.onSurface) },
            text = { Text(error, color = colors.onSurfaceSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.config_ok), color = accentColor)
                }
            },
        )
    }

    if (importSuccess) {
        AlertDialog(
            onDismissRequest = { importSuccess = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.config_success_title), color = colors.onSurface) },
            text = { Text(stringResource(R.string.config_import_success), color = colors.onSurfaceSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { importSuccess = false }) {
                    Text(stringResource(R.string.config_ok), color = accentColor)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportMetadataDialog(
    defaultMetadata: ExportMetadata,
    colors: AppColors,
    accentColor: Color,
    onConfirm: (ExportMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accentColor,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = colors.onSurfaceSecondary,
        cursorColor = accentColor,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.config_export_dialog_title),
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.config_export_author), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.config_export_description), fontSize = 12.sp) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.config_export_tags), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                onConfirm(
                    defaultMetadata.copy(
                        author = author.trim().ifEmpty { null },
                        description = description.trim().ifEmpty { null },
                        tags = parsedTags,
                    )
                )
            }) {
                Text(stringResource(R.string.config_export_confirm), color = accentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.config_export_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}

@Composable
private fun ImportPreviewDialog(
    export: MegingiardExport,
    colors: AppColors,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metadata = export.metadata
    val sections = export.sections
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.config_import_title),
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!metadata.author.isNullOrBlank()) {
                    Text(
                        text = "${stringResource(R.string.config_import_author_label)}: ${metadata.author}",
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    )
                }
                if (!metadata.description.isNullOrBlank()) {
                    Text(
                        text = metadata.description,
                        color = colors.onSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (metadata.tags.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.config_import_tags_label)}: ${metadata.tags.joinToString(", ")}",
                        color = colors.onSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.config_import_sections_label),
                    color = colors.onSurface,
                    fontSize = 13.sp,
                )
                if (sections.global != null) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_global)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
                }
                if (sections.mirror != null) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_mirror)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
                }
                if (sections.touchpad != null) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_touchpad)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
                }
                if (sections.keyboard != null) {
                    Text("\u2022 ${stringResource(R.string.config_import_section_keyboard)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
                }
                sections.macropad?.let { mp ->
                    Text(
                        text = "\u2022 ${stringResource(R.string.config_import_section_macropad, mp.profiles.size, mp.macros.size)}",
                        color = colors.onSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.config_import_warning),
                    color = colors.onSurfaceSecondary,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.config_import_confirm), color = accentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.config_import_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}


