package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppIcon
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "AppLauncherPicker"

private val ALP_ITEM_CORNER_RADIUS = 8.dp
private val ALP_ICON_SIZE = 36.dp
private val ALP_DIALOG_MAX_HEIGHT = 320.dp

@Composable
internal fun AppLauncherPicker(
    current: PadAction.AppLauncher,
    onChange: (PadAction) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.app_launcher_picker_title),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelSmall,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ALP_ITEM_CORNER_RADIUS))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.divider, RoundedCornerShape(ALP_ITEM_CORNER_RADIUS))
                    .clickable {
                        AppLog.d(TAG, "Opening AppLauncherDialog")
                        showDialog = true
                    }.padding(12.dp),
        ) {
            AppIcon(
                packageName = current.packageName,
                modifier = Modifier.size(ALP_ICON_SIZE),
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val resolvedName = resolveAppName(context, current.packageName)
                Text(
                    text =
                        resolvedName.ifBlank {
                            current.packageName.ifBlank {
                                stringResource(
                                    R.string.app_launcher_picker_select_app,
                                )
                            }
                        },
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current.packageName.isNotBlank()) {
                    Text(
                        text = current.packageName,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            MaterialSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = colors.onSurfaceSecondary,
            )
        }
    }

    if (showDialog) {
        AppLauncherDialog(
            onDismiss = {
                AppLog.d(TAG, "Dismiss AppLauncherDialog")
                showDialog = false
            },
            onAppSelected = { appItem ->
                AppLog.d(TAG, "Selected app: ${appItem.appName} (${appItem.packageName})")
                onChange(PadAction.AppLauncher(packageName = appItem.packageName))
                showDialog = false
            },
        )
    }
}

@Composable
private fun AppLauncherDialog(
    onDismiss: () -> Unit,
    onAppSelected: (InstalledAppItem) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        installedApps = queryInstalledLauncherApps(context)
        isLoading = false
    }

    val filteredApps =
        remember(searchQuery, installedApps) {
            if (searchQuery.isBlank()) {
                installedApps
            } else {
                val query = searchQuery.trim().lowercase()
                installedApps.filter {
                    it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                }
            }
        }

    AppModalDialog(
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.app_launcher_picker_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )

            AppTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.app_launcher_picker_search)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_launcher_picker_no_apps),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ALP_DIALOG_MAX_HEIGHT),
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                    ) { appItem ->
                        AppLauncherItemRow(
                            item = appItem,
                            onClick = { onAppSelected(appItem) },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.settings_cancel),
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLauncherItemRow(
    item: InstalledAppItem,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ALP_ITEM_CORNER_RADIUS))
                .clickable(onClick = onClick)
                .padding(8.dp),
    ) {
        AppIcon(
            packageName = item.packageName,
            modifier = Modifier.size(ALP_ICON_SIZE),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appName,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.packageName,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
