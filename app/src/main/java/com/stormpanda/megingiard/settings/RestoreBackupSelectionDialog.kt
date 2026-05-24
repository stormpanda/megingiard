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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.ui.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "RestoreBackupSelectionDialog"
private const val GSD_DIALOG_SCRIM_ALPHA = 0.5f
private val GSD_DIALOG_WIDTH_FRACTION = 0.85f
private val GSD_DIALOG_CORNER = 16.dp
private val GSD_DIALOG_PADDING = 20.dp

@Composable
internal fun RestoreBackupSelectionDialog(
    internalBackups: List<InternalBackup>,
    colors: AppColors,
    accentColor: Color,
    onConfirm: (InternalBackup?) -> Unit, // null means External File...
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        AppLog.d(TAG, "RestoreBackupSelectionDialog opened with ${internalBackups.size} internal backups")
    }

    var selectedIndex by remember { mutableStateOf(0) } // 0 means External File, 1..N means internal backups
    val scrollState = rememberScrollState()

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
                .padding(vertical = 24.dp)
                .fillMaxWidth(GSD_DIALOG_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(GSD_DIALOG_CORNER))
                .clickable(enabled = true, onClick = {})
                .padding(GSD_DIALOG_PADDING),
        ) {
            Text(
                text = stringResource(R.string.config_restore_dialog_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                // External file option
                BackupOptionRow(
                    label = stringResource(R.string.config_restore_option_external),
                    subtitle = stringResource(R.string.config_restore_option_external_sub),
                    isSelected = selectedIndex == 0,
                    accentColor = accentColor,
                    colors = colors,
                    onClick = { selectedIndex = 0 }
                )

                // Internal backups options
                internalBackups.forEachIndexed { index, backup ->
                    val profilesCount = backup.export.profiles.size
                    val layoutsCount = backup.export.profiles.sumOf { it.layouts.size }
                    val macrosCount = backup.export.profiles.sumOf { it.macros.size }

                    val subtitle = stringResource(
                        R.string.config_restore_option_internal_sub,
                        profilesCount,
                        layoutsCount,
                        macrosCount
                    )

                    // Format localized weekday, date, and time of backup creation
                    val formattedTime = remember(backup.timestampMs) {
                        val instant = Instant.ofEpochMilli(backup.timestampMs)
                        val dateTime = instant.atZone(ZoneId.systemDefault())
                        val formatter = DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        dateTime.format(formatter)
                    }

                    BackupOptionRow(
                        label = formattedTime,
                        subtitle = subtitle,
                        isSelected = selectedIndex == index + 1,
                        accentColor = accentColor,
                        colors = colors,
                        onClick = { selectedIndex = index + 1 }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.config_import_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(onClick = {
                    AppLog.d(TAG, "Confirm clicked: selectedIndex=$selectedIndex")
                    if (selectedIndex == 0) {
                        onConfirm(null)
                    } else {
                        val index = selectedIndex - 1
                        if (index in internalBackups.indices) {
                            onConfirm(internalBackups[index])
                        } else {
                            AppLog.w(TAG, "Selected index $index is out of bounds for internalBackups (size ${internalBackups.size})")
                            onDismiss()
                        }
                    }
                }) {
                    Text(stringResource(R.string.config_import_confirm), color = accentColor)
                }
            }
        }
    }
}

@Composable
private fun BackupOptionRow(
    label: String,
    subtitle: String?,
    isSelected: Boolean,
    accentColor: Color,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isSelected) accentColor else colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Rounded.TripOrigin,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
