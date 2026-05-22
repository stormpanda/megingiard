package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.SettingLabelColumn
import java.util.Locale

private const val TAG = "GlobalSettingsComponents"

// ── Constants ───────────────────────────────────────────────────────────────
private val GS_COLOR_PREVIEW_SIZE = 28.dp
private val GS_COLOR_ICON_SPACER = 8.dp
private val GS_ACCENT_ARROW_SIZE = 16.dp
private val GS_DIVIDER_START_INSET = 56.dp
private val GS_SECTION_CHIP_SPACING = 8.dp
private val GS_SECTION_HEADER_PADDING_H = 16.dp
private val GS_SECTION_HEADER_PADDING_V = 10.dp

internal enum class SettingsSectionFilter {
    GENERAL, APPEARANCE, DATA, CONFIGURATION, PRIVILEGED_MODE, DIAGNOSTICS
}

// ─────────────────────────────────────────────────────────────────────────────
// Global settings UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsCategoryHeader(
    text: String,
    accentColor: Color,
    colors: AppColors,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = accentColor,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun OverlayPositionRow(
    overlayAtBottom: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    AppSettingsRow {
        Text(
            text = stringResource(R.string.settings_overlay_position),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = overlayAtBottom,
            onCheckedChange = onChanged,
        )
    }
}

internal fun ThemeMode.displayNameResId(): Int = when (this) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.CYBERPUNK -> R.string.theme_cyberpunk
}

@Composable
internal fun ThemePickerRow(
    themeMode: ThemeMode,
    accentColor: Color,
    onChanged: (ThemeMode) -> Unit,
) {
    AppSettingsRow {
        SettingLabelColumn(
            label = stringResource(R.string.settings_theme),
            subtitle = stringResource(themeMode.displayNameResId()),
            subtitleColor = accentColor,
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected   = themeMode,
            options    = ThemeMode.entries,
            optionText = { option -> stringResource(option.displayNameResId()) },
            onSelected = onChanged,
        )
    }
}

private val GS_ACCENT_ROW_V_PADDING = 16.dp

@Composable
internal fun AccentColorRow(
    accentColor: Color,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    AppSettingsRow(onClick = onClick, verticalPadding = GS_ACCENT_ROW_V_PADDING) {
        Text(
            text = stringResource(R.string.settings_accent_color),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(GS_COLOR_PREVIEW_SIZE)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, colors.accentBorder, CircleShape)
        )
        Spacer(modifier = Modifier.size(GS_COLOR_ICON_SPACER))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.onSurfaceSecondary,
            modifier = Modifier.size(GS_ACCENT_ARROW_SIZE)
        )
    }
}

internal fun AppLog.Level.displayName(): String = name

internal fun AppLanguage.displayNameResId(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.EN     -> R.string.settings_language_en
    AppLanguage.DE     -> R.string.settings_language_de
}

@Composable
internal fun LogLevelPickerRow(
    logLevel: AppLog.Level,
    accentColor: Color,
    onChanged: (AppLog.Level) -> Unit,
) {
    AppSettingsRow {
        SettingLabelColumn(
            label = stringResource(R.string.settings_log_level),
            subtitle = logLevel.displayName(),
            subtitleColor = accentColor,
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected   = logLevel,
            options    = AppLog.Level.entries,
            optionText = { option -> option.displayName() },
            onSelected = onChanged,
        )
    }
}

@Composable
internal fun LanguagePickerRow(
    language: AppLanguage,
    accentColor: Color,
    onChanged: (AppLanguage) -> Unit,
) {
    AppSettingsRow {
        SettingLabelColumn(
            label = stringResource(R.string.settings_language),
            subtitle = stringResource(language.displayNameResId()),
            subtitleColor = accentColor,
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected   = language,
            options    = AppLanguage.entries,
            optionText = { option -> stringResource(option.displayNameResId()) },
            onSelected = onChanged,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Log report row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Settings row that triggers saving a plain-text log report to a user-chosen
 * location via the SAF "Create Document" picker.
 */
@Composable
internal fun SaveLogReportRow(
    accentColor: Color,
    onClick: () -> Unit,
) {
    AppSettingsRow(onClick = onClick) {
        SettingLabelColumn(
            label = stringResource(R.string.settings_save_log_report),
            subtitle = stringResource(R.string.settings_save_log_report_desc),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(GS_ACCENT_ARROW_SIZE),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Config export/import row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A settings row representing an actionable config operation (export or import).
 * Displays a label with a secondary description line and an arrow icon.
 */
@Composable
internal fun ConfigActionRow(
    label: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    AppSettingsRow(onClick = onClick) {
        SettingLabelColumn(label = label, subtitle = description, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(GS_ACCENT_ARROW_SIZE),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extracted Layout & Categories Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionJumpRow(
    colors: AppColors,
    accentColor: Color,
    selectedSectionFilter: SettingsSectionFilter?,
    onSelectAll: () -> Unit,
    onSelectGeneral: () -> Unit,
    onSelectAppearance: () -> Unit,
    onSelectData: () -> Unit,
    onSelectConfig: () -> Unit,
    onSelectPrivilegedMode: () -> Unit,
    onSelectDiagnostics: () -> Unit,
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
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_privileged_mode),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.PRIVILEGED_MODE,
            onClick = onSelectPrivilegedMode,
        )
        SectionJumpChip(
            label = stringResource(R.string.settings_jump_diagnostics),
            colors = colors,
            accentColor = accentColor,
            selected = selectedSectionFilter == SettingsSectionFilter.DIAGNOSTICS,
            onClick = onSelectDiagnostics,
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
internal fun SettingsSection(
    title: String,
    accentColor: Color,
    colors: AppColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title.uppercase(Locale.ROOT),
        color = colors.sectionHeaderColor,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = GS_SECTION_HEADER_PADDING_H, vertical = GS_SECTION_HEADER_PADDING_V),
    )
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) { content() }
    AppDivider()
}

@Composable
internal fun ConfigSection(
    onShowExportDialog: () -> Unit,
    onShowProfileExportDialog: () -> Unit,
    onImportPreviewReady: (MegingiardExport) -> Unit,
) {
    val pendingImport by ConfigManager.pendingInAppParsedImport.collectAsState()
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) onImportPreviewReady(pendingImport!!)
    }

    val effectiveAccent = LocalAppColors.current.accent
    ConfigActionRow(
        label = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.settings_config_export_desc),
        accentColor = effectiveAccent,
        onClick = onShowExportDialog,
    )
    AppDivider()
    ConfigActionRow(
        label = stringResource(R.string.settings_config_import),
        description = stringResource(R.string.settings_config_import_desc),
        accentColor = effectiveAccent,
        onClick = { ConfigManager.requestImport(ConfigManager.ImportMode.BACKUP_RESTORE) },
    )
    AppDivider()
    ConfigActionRow(
        label = stringResource(R.string.settings_config_export_profile),
        description = stringResource(R.string.settings_config_export_profile_desc),
        accentColor = effectiveAccent,
        onClick = onShowProfileExportDialog,
    )
    AppDivider()
    ConfigActionRow(
        label = stringResource(R.string.settings_config_import_profile),
        description = stringResource(R.string.settings_config_import_profile_desc),
        accentColor = effectiveAccent,
        onClick = { ConfigManager.requestImport(ConfigManager.ImportMode.PROFILE_SHARE) },
    )
}
