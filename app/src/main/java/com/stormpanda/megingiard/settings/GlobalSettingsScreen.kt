package com.stormpanda.megingiard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.privd.PrivdSettingsCard
import com.stormpanda.megingiard.privd.PrivdSetupWizardDialog
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

private const val TAG = "GlobalSettingsScreen"

// ── In-tree dialog constants ──────────────────────────────────────────────────
// Dialogs must NOT use Compose AlertDialog (which creates an Android sub-window)
// because MirrorPresentation has no valid Activity window token for sub-windows.
// All dialogs are rendered as full-screen scrim + card inside the Compose tree.
private const val GS_DIALOG_SCRIM_ALPHA = 0.5f
private val GS_DIALOG_WIDTH_FRACTION = 0.85f
private val GS_DIALOG_CORNER = 16.dp
private val GS_DIALOG_PADDING = 20.dp
private val GS_SECTION_CHIP_SPACING = 8.dp
private val GS_SECTION_HEADER_PADDING_H = 16.dp
private val GS_SECTION_HEADER_PADDING_V = 10.dp

private enum class SettingsSectionFilter {
    GENERAL, APPEARANCE, DATA, CONFIGURATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val accentColorArgb by viewModel.accentColor.collectAsState()
    val accentColor = Color(accentColorArgb)
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val showNavigationCoachMarks by viewModel.showNavigationCoachMarks.collectAsState()
    val showMirrorControlLabels by viewModel.showMirrorControlLabels.collectAsState()
    val showFullscreenExitHints by viewModel.showFullscreenExitHints.collectAsState()
    val gamepadSwapFaceButtons by viewModel.gamepadSwapFaceButtons.collectAsState()
    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    // Export-result feedback (set by ConfigManager after MainActivity writes the file)
    val exportResult by ConfigManager.exportResult.collectAsState()
    // All dialog states are hoisted here so they can be rendered at the top-level Box
    // (in-tree, covering the Scaffold). AlertDialog creates a new Android sub-window
    // whose token is null inside MirrorPresentation → BadTokenException crash.
    val context = LocalContext.current
    var showExportMetadataDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivdWizard by rememberSaveable { mutableStateOf(false) }
    // MegingiardExport is not Parcelable/Serializable — cannot survive process death; keep as remember
    var showImportPreviewDialog by remember { mutableStateOf<MegingiardExport?>(null) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importSuccess by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var showRestoreDefaultsConfirm by rememberSaveable { mutableStateOf(false) }
    // SettingsSectionFilter is a private local enum — not Parcelable; UI-ephemeral, keep as remember
    var selectedSectionFilter by remember { mutableStateOf<SettingsSectionFilter?>(null) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionJumpRow(
                    colors = colors,
                    accentColor = effectiveAccent,
                    selectedSectionFilter = selectedSectionFilter,
                    onSelectAll = { selectedSectionFilter = null },
                    onSelectGeneral = { selectedSectionFilter = SettingsSectionFilter.GENERAL },
                    onSelectAppearance = { selectedSectionFilter = SettingsSectionFilter.APPEARANCE },
                    onSelectData = { selectedSectionFilter = SettingsSectionFilter.DATA },
                    onSelectConfig = { selectedSectionFilter = SettingsSectionFilter.CONFIGURATION },
                )
                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.GENERAL) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_general),
                        accentColor = effectiveAccent,
                        colors = colors,
                    ) {
                        OverlayPositionRow(
                            overlayAtBottom = overlayAtBottom,
                            accentColor = effectiveAccent,
                            colors = colors,
                            onChanged = { viewModel.setOverlayAtBottom(it) }
                        )
                        HorizontalDivider(color = colors.divider)
                        RememberSettingRow(
                            label = stringResource(R.string.settings_show_navigation_coach_marks),
                            description = stringResource(R.string.settings_show_navigation_coach_marks_desc),
                            checked = showNavigationCoachMarks,
                            accentColor = effectiveAccent,
                            onCheckedChange = { viewModel.setShowNavigationCoachMarks(it) },
                        )
                        HorizontalDivider(color = colors.divider)
                        RememberSettingRow(
                            label = stringResource(R.string.settings_show_mirror_control_labels),
                            description = stringResource(R.string.settings_show_mirror_control_labels_desc),
                            checked = showMirrorControlLabels,
                            accentColor = effectiveAccent,
                            onCheckedChange = { viewModel.setShowMirrorControlLabels(it) },
                        )
                        HorizontalDivider(color = colors.divider)
                        RememberSettingRow(
                            label = stringResource(R.string.settings_show_fullscreen_exit_hints),
                            description = stringResource(R.string.settings_show_fullscreen_exit_hints_desc),
                            checked = showFullscreenExitHints,
                            accentColor = effectiveAccent,
                            onCheckedChange = { viewModel.setShowFullscreenExitHints(it) },
                        )
                        HorizontalDivider(color = colors.divider)
                        LanguagePickerRow(
                            language = appLanguage,
                            accentColor = effectiveAccent,
                            colors = colors,
                            onChanged = { viewModel.setAppLanguage(it) }
                        )
                        HorizontalDivider(color = colors.divider)
                        LogLevelPickerRow(
                            logLevel = logLevel,
                            accentColor = effectiveAccent,
                            colors = colors,
                            onChanged = { viewModel.setLogLevel(it) }
                        )
                        HorizontalDivider(color = colors.divider)
                        RememberSettingRow(
                            label = stringResource(R.string.settings_gamepad_swap_face_buttons),
                            description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
                            checked = gamepadSwapFaceButtons,
                            accentColor = effectiveAccent,
                            onCheckedChange = { viewModel.setGamepadSwapFaceButtons(it) },
                        )
                        HorizontalDivider(color = colors.divider)
                        PrivdSettingsCard(
                            viewModel = viewModel,
                            onShowWizard = { showPrivdWizard = true },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.APPEARANCE) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_appearance),
                        accentColor = effectiveAccent,
                        colors = colors,
                    ) {
                        ThemePickerRow(
                            themeMode = themeMode,
                            accentColor = effectiveAccent,
                            colors = colors,
                            onChanged = { viewModel.setThemeMode(it) }
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
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.DATA) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_data),
                        accentColor = effectiveAccent,
                        colors = colors,
                    ) {
                        ConfigActionRow(
                            label = stringResource(R.string.settings_restore_defaults),
                            description = stringResource(R.string.settings_restore_defaults_desc),
                            accentColor = effectiveAccent,
                            colors = colors,
                            onClick = { showRestoreDefaultsConfirm = true },
                        )
                    }
                }

                if (selectedSectionFilter == null || selectedSectionFilter == SettingsSectionFilter.CONFIGURATION) {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_config),
                        accentColor = effectiveAccent,
                        colors = colors,
                    ) {
                        ConfigSection(
                            colors = colors,
                            accentColor = effectiveAccent,
                            onShowExportDialog = { showExportMetadataDialog = true },
                            onImportPreviewReady = { showImportPreviewDialog = it },
                        )
                    }
                }
            }
        }
        // ── In-tree overlays (work in Activity and MirrorPresentation contexts) ─────
        if (showPrivdWizard) {
            PrivdSetupWizardDialog(
                viewModel = viewModel,
                onDismiss = { showPrivdWizard = false },
            )
        }
        if (showColorPicker) {
            ColorWheelPicker(
                initialColor = accentColor,
                onColorSelected = { color ->
                    viewModel.setAccentColor(color.toArgb())
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
        if (showRestoreDefaultsConfirm) {
            InTreeConfirmDialog(
                title = stringResource(R.string.settings_restore_defaults),
                text = stringResource(R.string.settings_restore_defaults_confirm),
                confirmText = stringResource(R.string.settings_restore_defaults_confirm_button),
                dismissText = stringResource(R.string.settings_cancel),
                colors = colors,
                accentColor = effectiveAccent,
                onConfirm = {
                    showRestoreDefaultsConfirm = false
                    MacroPadState.restoreDefaults()
                },
                onDismiss = { showRestoreDefaultsConfirm = false },
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

@Composable
private fun SectionJumpRow(
    colors: AppColors,
    accentColor: Color,
    selectedSectionFilter: SettingsSectionFilter?,
    onSelectAll: () -> Unit,
    onSelectGeneral: () -> Unit,
    onSelectAppearance: () -> Unit,
    onSelectData: () -> Unit,
    onSelectConfig: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GS_SECTION_CHIP_SPACING),
    ) {
        Text(
            text = stringResource(R.string.settings_filter_label),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_all),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == null,
            onClick = onSelectAll,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_general),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.GENERAL,
            onClick = onSelectGeneral,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_appearance),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.APPEARANCE,
            onClick = onSelectAppearance,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_data),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.DATA,
            onClick = onSelectData,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_config),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.CONFIGURATION,
            onClick = onSelectConfig,
        )
    }
}

@Composable
private fun SectionJumpChip(
    label: String,
    colors: AppColors,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppSelectableChip(
        text     = label,
        selected = selected,
        onClick  = onClick,
    )
}

@Composable
private fun SettingsSection(
    title: String,
    accentColor: Color,
    colors: AppColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title.uppercase(Locale.ROOT),
        color = accentColor,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = GS_SECTION_HEADER_PADDING_H, vertical = GS_SECTION_HEADER_PADDING_V),
    )
    Column { content() }
    HorizontalDivider(color = colors.divider)
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
            Text(
                text = stringResource(R.string.config_import_sections_label),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
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
            if (export.profiles.isNotEmpty() || export.profiles.any { it.macros.isNotEmpty() }) {
                Text(
                    text = "\u2022 ${stringResource(R.string.config_import_section_macropad, export.profiles.size, export.profiles.sumOf { it.macros.size })}",
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.config_import_warning),
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

// ─────────────────────────────────────────────────────────────────────────────
// Filename builder
// ─────────────────────────────────────────────────────────────────────────────

// In-tree two-button confirm dialog (works inside MirrorPresentation).
@Composable
private fun InTreeConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
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
                TextButton(onClick = onConfirm) {
                    Text(confirmText, color = accentColor)
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
