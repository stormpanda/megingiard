package com.stormpanda.megingiard.settings.tabs

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConfigurationSettingsTab(
    deleteCountdown: Int,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onShareProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onDeleteCountdownClick: () -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.help_settings_export_desc),
        icon = Icons.Rounded.FileDownload,
        onClick = onExportBackup,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_import),
        description = stringResource(R.string.settings_config_import_card_desc),
        icon = Icons.Rounded.FileUpload,
        onClick = onRestoreBackup,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export_profile),
        description = stringResource(R.string.help_settings_export_profile_desc),
        icon = Icons.Rounded.Share,
        onClick = onShareProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_import_profile),
        description = stringResource(R.string.help_settings_import_profile_desc),
        icon = Icons.Rounded.FileUpload,
        onClick = onImportProfile,
    )

    val deleteBadgeText =
        when {
            deleteCountdown > 0 -> {
                stringResource(
                    R.string.settings_restore_defaults_countdown,
                    deleteCountdown,
                )
            }

            deleteCountdown == 0 -> {
                stringResource(R.string.gamepad_action_confirm)
            }

            else -> {
                stringResource(R.string.gamepad_action_delete)
            }
        }

    GamepadActionCard(
        title = stringResource(R.string.settings_restore_defaults),
        description = stringResource(R.string.settings_restore_defaults_desc),
        actionLeadingContent = {
            GamepadPill(
                text = deleteBadgeText,
                isDestructive = true,
                isAccent = deleteCountdown == 0,
                isHighlighted = deleteCountdown >= 0,
            )
        },
        isDestructive = true,
        icon = Icons.Rounded.Delete,
        onClick = onDeleteCountdownClick,
    )
}

@Composable
fun ExportMetadataForm(
    author: String,
    onAuthorChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    includeBackgrounds: Boolean,
    onIncludeBackgroundsChange: (Boolean) -> Unit,
    isFirstDeckItem: Boolean = false,
) {
    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_author),
        description = stringResource(R.string.config_export_author_desc),
        placeholder = stringResource(R.string.config_export_author),
        value = author,
        onValueChange = onAuthorChange,
        icon = Icons.Rounded.Person,
        modifier = Modifier.firstDeckItem(isFirst = isFirstDeckItem),
    )

    GamepadTextFieldCard(
        title = stringResource(R.string.config_export_description),
        description = stringResource(R.string.config_export_description_desc),
        placeholder = stringResource(R.string.config_export_description),
        value = description,
        onValueChange = onDescriptionChange,
        icon = Icons.AutoMirrored.Rounded.Notes,
    )

    GamepadToggleCard(
        title = stringResource(R.string.config_export_include_backgrounds),
        description = stringResource(R.string.config_export_include_backgrounds_desc),
        checked = includeBackgrounds,
        onCheckedChange = onIncludeBackgroundsChange,
        icon = Icons.Rounded.Wallpaper,
    )
}

fun buildExportMetadata(
    context: Context,
    author: String,
    description: String,
): ExportMetadata =
    ConfigManager.defaultMetadata(context).copy(
        author = author.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
    )

@Composable
fun CreateBackupSubPage(onExport: (ExportMetadata, Boolean) -> Unit) {
    val context = LocalContext.current
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    ExportMetadataForm(
        author = author,
        onAuthorChange = { author = it },
        description = description,
        onDescriptionChange = { description = it },
        includeBackgrounds = includeBackgrounds,
        onIncludeBackgroundsChange = { includeBackgrounds = it },
        isFirstDeckItem = true,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export),
        description = stringResource(R.string.help_settings_export_desc),
        icon = Icons.Rounded.FileDownload,
        onClick = {
            onExport(buildExportMetadata(context, author, description), includeBackgrounds)
        },
    )
}

@Composable
fun ShareProfileSubPage(onExportProfile: (ExportMetadata, PadProfile, Boolean) -> Unit) {
    val context = LocalContext.current
    val rawProfiles by MacroPadState.profiles.collectAsStateWithLifecycle()
    val profiles = remember(rawProfiles) { rawProfiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    var selectedProfile by remember(profiles, activeProfile) {
        mutableStateOf(profiles.firstOrNull { it.id == activeProfile?.id } ?: activeProfile ?: profiles.firstOrNull())
    }
    val currentProfile = selectedProfile ?: activeProfile ?: profiles.firstOrNull()

    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeBackgrounds by rememberSaveable { mutableStateOf(true) }

    if (profiles.size > 1) {
        GamepadChoiceCard(
            title = stringResource(R.string.config_profile_export_select),
            description = stringResource(R.string.config_profile_export_select_desc),
            selectedText = currentProfile?.name ?: "",
            onPrevious = { currentProfile?.let { selectedProfile = profiles.cycle(it, BumperDirection.PREV) } },
            onNext = { currentProfile?.let { selectedProfile = profiles.cycle(it, BumperDirection.NEXT) } },
            icon = Icons.Rounded.SportsEsports,
            modifier = Modifier.firstDeckItem(),
        )
    }

    ExportMetadataForm(
        author = author,
        onAuthorChange = { author = it },
        description = description,
        onDescriptionChange = { description = it },
        includeBackgrounds = includeBackgrounds,
        onIncludeBackgroundsChange = { includeBackgrounds = it },
        isFirstDeckItem = profiles.size <= 1,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_config_export_profile),
        description = stringResource(R.string.help_settings_export_profile_desc),
        icon = Icons.Rounded.Share,
        enabled = currentProfile != null,
        onClick = {
            val targetProfile = currentProfile ?: return@GamepadActionCard
            onExportProfile(buildExportMetadata(context, author, description), targetProfile, includeBackgrounds)
        },
    )
}

@Composable
fun RestoreBackupSubPage(
    internalBackups: List<InternalBackup>,
    effectiveAccent: Color,
    onPickExternalFile: () -> Unit,
    onSelectInternalBackup: (InternalBackup) -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.config_restore_option_external),
        description = stringResource(R.string.config_restore_option_external_sub),
        icon = Icons.Rounded.FileDownload,
        onClick = onPickExternalFile,
        modifier = Modifier.firstDeckItem(),
    )

    if (internalBackups.isNotEmpty()) {
        GamepadSectionHeader(
            text = stringResource(R.string.config_restore_automatic_backups),
            color = effectiveAccent,
        )

        internalBackups.forEach { backup ->
            val profilesCount = backup.export.profiles.size
            val layoutsCount = backup.export.profiles.sumOf { it.layouts.size }
            val macrosCount = backup.export.profiles.sumOf { it.macros.size }

            val subtitle =
                stringResource(
                    R.string.config_restore_option_internal_sub,
                    profilesCount,
                    layoutsCount,
                    macrosCount,
                )

            val formattedTime =
                remember(backup.timestampMs) {
                    val instant = Instant.ofEpochMilli(backup.timestampMs)
                    val dateTime = instant.atZone(ZoneId.systemDefault())
                    val formatter =
                        DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm", Locale.getDefault())
                    dateTime.format(formatter)
                }

            GamepadActionCard(
                title = formattedTime,
                description = subtitle,
                icon = Icons.Rounded.Restore,
                onClick = { onSelectInternalBackup(backup) },
            )
        }
    }
}

@Composable
fun RestoreReviewSubPage(
    export: MegingiardExport,
    pendingInAppImportMode: ConfigManager.ImportMode,
    onConfirmImport: (MegingiardExport, ConfigManager.ImportMode) -> Unit,
) {
    var isDetailsExpanded by rememberSaveable(export) { mutableStateOf(false) }
    val colors = LocalAppColors.current

    val metadata = export.metadata
    val authorText = metadata.author?.ifBlank { null }
    val descText = metadata.description?.ifBlank { null }
    val tagsText = metadata.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val profilesCount = export.profiles.size
    val layoutsCount = export.profiles.sumOf { it.layouts.size }
    val macrosCount = export.profiles.sumOf { it.macros.size }
    val imageCount =
        ConfigManager.getPendingInAppImageCount().takeIf { it > 0 }
            ?: export.profiles.sumOf { p -> p.layouts.count { !it.backgroundImagePath.isNullOrEmpty() } }

    val includedSections =
        listOf(
            "global" to R.string.config_import_section_global,
            "mirror" to R.string.config_import_section_mirror,
            "touchpad" to R.string.config_import_section_touchpad,
            "keyboard" to R.string.config_import_section_keyboard,
            "macropad_settings" to R.string.config_import_section_macropad_settings,
        ).filter { it.first in export.settings }.map { stringResource(it.second) }

    val summarySubtitle =
        buildString {
            append(
                "$profilesCount ${if (profilesCount == 1) "profile" else "profiles"} • $layoutsCount ${if (layoutsCount == 1) "layout" else "layouts"} • $macrosCount ${if (macrosCount == 1) "macro" else "macros"}",
            )
            if (imageCount > 0) {
                append(" • $imageCount background${if (imageCount == 1) "" else "s"}")
            }
        }

    GamepadFocusCard(
        onClick = { isDetailsExpanded = !isDetailsExpanded },
        modifier = Modifier.firstDeckItem().animateContentSize(),
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GamepadCardRow(
                title =
                    authorText?.let { stringResource(R.string.config_import_meta_author, it) }
                        ?: stringResource(R.string.config_import_archive_title),
                description = summarySubtitle,
                icon = Icons.Rounded.Inventory2,
                isFocused = isFocused,
                trailingContent = {
                    GamepadPill(
                        text =
                            if (isDetailsExpanded) {
                                stringResource(R.string.config_import_hide_details)
                            } else {
                                stringResource(R.string.config_import_show_details)
                            },
                    )
                },
            )

            if (isDetailsExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!descText.isNullOrBlank()) {
                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                        )
                    }
                    if (!tagsText.isNullOrBlank()) {
                        Text(
                            text = "${stringResource(R.string.config_import_tags_label)}: $tagsText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (metadata.exportedAt.isNotBlank()) {
                        Text(
                            text = metadata.exportedAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (includedSections.isNotEmpty() && pendingInAppImportMode == ConfigManager.ImportMode.BACKUP_RESTORE) {
                        Text(
                            text = "${stringResource(R.string.config_import_sections_label)}: ${includedSections.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }

    val warningText =
        when (pendingInAppImportMode) {
            ConfigManager.ImportMode.BACKUP_RESTORE -> stringResource(R.string.config_import_warning_backup)
            ConfigManager.ImportMode.PROFILE_SHARE -> stringResource(R.string.config_import_warning_profile)
        }

    GamepadActionCard(
        title = stringResource(R.string.config_import_confirm),
        description = warningText,
        icon = Icons.Rounded.Restore,
        isDestructive = true,
        onClick = {
            onConfirmImport(export, pendingInAppImportMode)
        },
    )
}
