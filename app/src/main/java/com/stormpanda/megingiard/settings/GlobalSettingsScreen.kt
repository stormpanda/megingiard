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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.ThemeMode
import java.time.LocalDate
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

// ── In-tree dialog constants ──────────────────────────────────────────────────
// Dialogs must NOT use Compose AlertDialog (which creates an Android sub-window)
// because MirrorPresentation has no valid Activity window token for sub-windows.
// All dialogs are rendered as full-screen scrim + card inside the Compose tree.
private const val GS_DIALOG_SCRIM_ALPHA = 0.5f
private val GS_DIALOG_WIDTH_FRACTION = 0.85f
private val GS_DIALOG_CORNER = 16.dp
private val GS_DIALOG_PADDING = 20.dp
private val GS_DIALOG_TITLE_SIZE = 16.sp

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
    // Export-result feedback (set by ConfigManager after MainActivity writes the file)
    val exportResult by ConfigManager.exportResult.collectAsState()
    // All dialog states are hoisted here so they can be rendered at the top-level Box
    // (in-tree, covering the Scaffold). AlertDialog creates a new Android sub-window
    // whose token is null inside MirrorPresentation → BadTokenException crash.
    val context = LocalContext.current
    var showExportMetadataDialog by remember { mutableStateOf(false) }
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                        onShowExportDialog = { showExportMetadataDialog = true },
                        onImportPreviewReady = { showImportPreviewDialog = it },
                    )
                }
            }
        }
        // ── In-tree overlays (work in Activity and MirrorPresentation contexts) ─────
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
        if (showExportMetadataDialog) {
            ExportMetadataDialog(
                defaultMetadata = ConfigManager.defaultMetadata(context),
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = { metadata ->
                    showExportMetadataDialog = false
                    ConfigManager.requestExport(
                        metadata = metadata,
                        filename = buildExportFilename(metadata),
                    )
                },
                onDismiss = { showExportMetadataDialog = false },
            )
        }
        showImportPreviewDialog?.let { export ->
            ImportPreviewDialog(
                export = export,
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = {
                    showImportPreviewDialog = null
                    coroutineScope.launch {
                        runCatching { ConfigManager.applyImport(export) }
                            .onSuccess { importSuccess = true }
                            .onFailure { e -> importError = e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.config_error_unknown) }
                        ConfigManager.clearInAppPendingImport()
                    }
                },
                onDismiss = {
                    showImportPreviewDialog = null
                    ConfigManager.clearInAppPendingImport()
                },
            )
        }
        importError?.let { error ->
            InTreeMessageDialog(
                title = stringResource(R.string.config_error_title),
                text = error,
                buttonText = stringResource(R.string.config_ok),
                colors = colors,
                accentColor = effectiveAccent,
                onDismiss = { importError = null },
            )
        }
        if (importSuccess) {
            InTreeMessageDialog(
                title = stringResource(R.string.config_success_title),
                text = stringResource(R.string.config_import_success),
                buttonText = stringResource(R.string.config_ok),
                colors = colors,
                accentColor = effectiveAccent,
                onDismiss = { importSuccess = false },
            )
        }
        when (val result = exportResult) {
            is ConfigManager.ExportResult.Success -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_success_title),
                    text = stringResource(R.string.config_export_success),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { ConfigManager.clearExportResult() },
                )
            }
            is ConfigManager.ExportResult.Failure -> {
                InTreeMessageDialog(
                    title = stringResource(R.string.config_error_title),
                    text = result.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.config_error_unknown),
                    buttonText = stringResource(R.string.config_ok),
                    colors = colors,
                    accentColor = effectiveAccent,
                    onDismiss = { ConfigManager.clearExportResult() },
                )
            }
            null -> {}
        }
    }
}

/**
 * Configuration export / import section.
 *
 * Posts export/import requests to [ConfigManager] — MainActivity holds
 * the actual [ActivityResultLauncher]s and opens the system file picker on behalf
 * of this composable. This means the section works correctly regardless of whether
 * GlobalSettingsScreen is rendered inside MainActivity or inside MirrorPresentation.
 *
 * All dialog state is hoisted to [GlobalSettingsScreen] and rendered as in-tree
 * overlays so they work inside MirrorPresentation (no Activity window token).
 */
@Composable
private fun ConfigSection(
    colors: AppColors,
    accentColor: Color,
    onShowExportDialog: () -> Unit,
    onImportPreviewReady: (MegingiardExport) -> Unit,
) {
    // Import preview: ConfigManager carries the parsed file once MainAppScreen
    // reads and parses it from the in-app file picker. Notify parent to show the
    // in-tree import preview overlay.
    val pendingImport by ConfigManager.pendingInAppParsedImport.collectAsState()
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) onImportPreviewReady(pendingImport!!)
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
        onClick = onShowExportDialog,
    )
    HorizontalDivider(color = colors.divider)
    ConfigActionRow(
        label = stringResource(R.string.settings_config_import),
        description = stringResource(R.string.settings_config_import_desc),
        accentColor = accentColor,
        colors = colors,
        onClick = { ConfigManager.requestImport() },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Private dialogs
// ─────────────────────────────────────────────────────────────────────────────

// In-tree version — full-screen scrim + centered card, no Android Dialog window.
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
            .background(Color.Black.copy(alpha = GS_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GS_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GS_DIALOG_CORNER))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = true, onClick = {})
                .padding(GS_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.config_export_dialog_title),
                color = colors.onSurface,
                fontSize = GS_DIALOG_TITLE_SIZE,
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.config_export_author), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.config_export_description), fontSize = 12.sp) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.config_export_tags), fontSize = 12.sp) },
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

// In-tree version — full-screen scrim + centered card, no Android Dialog window.
@Composable
private fun ImportPreviewDialog(
    export: MegingiardExport,
    colors: AppColors,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metadata = export.metadata
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = GS_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GS_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GS_DIALOG_CORNER))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = true, onClick = {})
                .padding(GS_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.config_import_title),
                color = colors.onSurface,
                fontSize = GS_DIALOG_TITLE_SIZE,
            )
            Spacer(Modifier.height(4.dp))
            if (!metadata.author.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.config_import_meta_author, metadata.author!!),
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
            if ("global" in export.settings) {
                Text("\u2022 ${stringResource(R.string.config_import_section_global)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
            }
            if ("mirror" in export.settings) {
                Text("\u2022 ${stringResource(R.string.config_import_section_mirror)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
            }
            if ("touchpad" in export.settings) {
                Text("\u2022 ${stringResource(R.string.config_import_section_touchpad)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
            }
            if ("keyboard" in export.settings) {
                Text("\u2022 ${stringResource(R.string.config_import_section_keyboard)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
            }
            if ("macropad_settings" in export.settings) {
                Text("\u2022 ${stringResource(R.string.config_import_section_macropad_settings)}", color = colors.onSurfaceSecondary, fontSize = 12.sp)
            }
            if (export.profiles.isNotEmpty() || export.macros.isNotEmpty()) {
                Text(
                    text = "\u2022 ${stringResource(R.string.config_import_section_macropad, export.profiles.size, export.macros.size)}",
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

// Simple message + single button — in-tree version of AlertDialog for confirmations.
@Composable
private fun InTreeMessageDialog(
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
            .background(Color.Black.copy(alpha = GS_DIALOG_SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(GS_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GS_DIALOG_CORNER))
                .clickable(enabled = true, onClick = {})
                .padding(GS_DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = colors.onSurface, fontSize = GS_DIALOG_TITLE_SIZE)
            if (text.isNotBlank()) {
                Text(text, color = colors.onSurfaceSecondary, fontSize = 13.sp)
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

// ─────────────────────────────────────────────────────────────────────────────
// Filename builder
// ─────────────────────────────────────────────────────────────────────────────

private val FILENAME_UNSAFE = Regex("[^A-Za-z0-9]")

/**
 * Builds a descriptive export filename from [metadata] and the current date.
 *
 * Format: `megingiard_<date>[_<author up to 20 chars>][_<desc up to 30 chars>].mgrd`
 * All non-alphanumeric characters in author / description are replaced with `_`.
 */
private fun buildExportFilename(metadata: ExportMetadata): String {
    val parts = mutableListOf("megingiard", LocalDate.now().toString())
    metadata.author?.takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.trim().take(20).replace(FILENAME_UNSAFE, "_"))
    }
    metadata.description?.takeIf { it.isNotBlank() }?.let { raw ->
        parts.add(raw.trim().take(30).replace(FILENAME_UNSAFE, "_"))
    }
    return parts.joinToString("_") + ".mgrd"
}
