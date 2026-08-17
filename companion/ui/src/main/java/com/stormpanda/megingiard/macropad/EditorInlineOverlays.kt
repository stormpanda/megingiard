package com.stormpanda.megingiard.macropad

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.ui.AppIcon
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadConfirmDialog
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadSearchBar
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.primaryOverlayFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EditorInlineOverlays"
private val EIO_APP_ICON_SIZE = 36.dp
private val EIO_CORNER_RADIUS = 12.dp

@Composable
internal fun InlineDialogOverlay(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.85f,
    titleAccessory: @Composable (() -> Unit)? = null,
    buttonsArrangement: Arrangement.Horizontal = Arrangement.End,
    buttonsRow: @Composable (RowScope.() -> Unit)? = {
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.macropad_editor_cancel),
                color = LocalAppColors.current.onSurfaceSecondary,
            )
        }
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    AppModalDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        widthFraction = widthFraction,
        cornerRadius = EIO_CORNER_RADIUS,
        contentPadding = MPE_PADDING,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (titleAccessory != null) {
                titleAccessory()
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
        if (buttonsRow != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = buttonsArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttonsRow()
            }
        }
    }
}

@Composable
internal fun InlineConfirmDeleteOverlay(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GamepadConfirmDialog(
        title = title,
        message = body,
        confirmText = stringResource(R.string.macropad_editor_confirm),
        cancelText = stringResource(R.string.macropad_editor_cancel),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun InlineNameInputOverlay(
    title: String,
    initialValue: String,
    accentColor: Color,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val normalizedName = text.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current

    InlineDialogOverlay(
        title = title,
        onDismiss = onDismiss,
        widthFraction = 0.8f,
        buttonsRow = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
            TextButton(
                onClick = { if (!hasError) onConfirm(normalizedName) },
                enabled = !hasError,
            ) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color = if (!hasError) accentColor else colors.onSurfaceSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(title, color = colors.onSurfaceSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = hasError,
            supportingText = {
                when {
                    normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                    isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                }
            },
        )
    }
}

@Composable
internal fun NewProfileSubPageContent(
    existingNames: List<String>,
    selectedPackage: String?,
    accentColor: Color,
    onOpenAppPicker: () -> Unit,
    onClearApp: () -> Unit,
    onCreate: (name: String, packageName: String?) -> Unit,
) {
    var nameText by remember { mutableStateOf("") }
    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var selectedAppName by remember(selectedPackage) { mutableStateOf(selectedPackage ?: "") }

    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        if (pkg != null) {
            selectedAppName =
                withContext(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                }
        }
    }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.quick_menu_profile_label),
        subPageTitle = stringResource(R.string.settings_macropad_new_profile),
        accentColor = accentColor,
    )

    AppTextField(
        value = nameText,
        onValueChange = { nameText = it },
        label = { Text(stringResource(R.string.profile_settings_name), color = colors.onSurfaceSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = hasError,
        supportingText = {
            when {
                normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
            }
        },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.profile_settings_app_mapping),
        color = accentColor,
    )

    if (selectedPackage != null) {
        GamepadActionCard(
            title = selectedAppName,
            description = selectedPackage,
            actionText = stringResource(R.string.gamepad_action_clear),
            isDestructive = true,
            icon = Icons.Rounded.Delete,
            onClick = onClearApp,
        )
    } else {
        GamepadActionCard(
            title = stringResource(R.string.macropad_profile_app_association_title),
            description = stringResource(R.string.macropad_profile_app_association_desc),
            actionText = stringResource(R.string.gamepad_action_choose_app),
            onClick = onOpenAppPicker,
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.settings_macropad_new_profile),
        description = stringResource(R.string.macropad_editor_new_profile_desc),
        actionText = stringResource(R.string.macropad_editor_done),
        enabled = isConfirmEnabled,
        onClick = {
            if (isConfirmEnabled) {
                onCreate(normalizedName, selectedPackage)
            }
        },
    )
}

@Composable
internal fun EditProfileSubPageContent(
    profile: PadProfile,
    existingNames: List<String>,
    selectedPackage: String?,
    accentColor: Color,
    onOpenAppPicker: () -> Unit,
    onClearApp: () -> Unit,
    onSave: (name: String, packageName: String?) -> Unit,
) {
    var nameText by remember(profile) { mutableStateOf(profile.name) }
    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var selectedAppName by remember(selectedPackage) { mutableStateOf(selectedPackage ?: "") }

    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        if (pkg != null) {
            selectedAppName =
                withContext(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                }
        }
    }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.quick_menu_profile_label),
        subPageTitle = stringResource(R.string.profile_settings_title),
        accentColor = accentColor,
    )

    AppTextField(
        value = nameText,
        onValueChange = { nameText = it },
        label = { Text(stringResource(R.string.profile_settings_name), color = colors.onSurfaceSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = hasError,
        supportingText = {
            when {
                normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
            }
        },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.profile_settings_app_mapping),
        color = accentColor,
    )

    if (selectedPackage != null) {
        GamepadActionCard(
            title = selectedAppName,
            description = selectedPackage,
            actionText = stringResource(R.string.gamepad_action_clear),
            isDestructive = true,
            icon = Icons.Rounded.Delete,
            onClick = onClearApp,
        )
    } else {
        GamepadActionCard(
            title = stringResource(R.string.macropad_profile_app_association_title),
            description = stringResource(R.string.macropad_profile_app_association_desc),
            actionText = stringResource(R.string.gamepad_action_choose_app),
            onClick = onOpenAppPicker,
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_done),
        description = stringResource(R.string.macropad_editor_edit_profile_desc),
        actionText = stringResource(R.string.macropad_editor_done),
        enabled = isConfirmEnabled,
        onClick = {
            if (isConfirmEnabled) {
                onSave(normalizedName, selectedPackage)
            }
        },
    )
}

@Composable
internal fun AppPickerSubPageContent(
    assignedPackages: Set<String>,
    accentColor: Color,
    onSelectApp: (packageName: String) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingApps = true
        appsList = queryInstalledLauncherApps(context)
        isLoadingApps = false
    }

    GamepadSubPageHeader(
        breadcrumbs =
            listOf(
                stringResource(R.string.quick_menu_profile_label),
                stringResource(R.string.profile_settings_app_mapping),
                stringResource(R.string.profile_settings_search_apps),
            ),
        accentColor = accentColor,
    )

    GamepadSearchBar(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = stringResource(R.string.profile_settings_search_apps),
        modifier = Modifier.fillMaxWidth(),
    )

    if (isLoadingApps) {
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = accentColor)
        }
    } else {
        val filtered =
            appsList.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        if (filtered.isEmpty()) {
            GamepadEmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.profile_settings_no_apps),
                description = stringResource(R.string.macropad_icon_picker_empty_desc),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.packageName }) { appItem ->
                    val isAssigned = assignedPackages.contains(appItem.packageName.trim().lowercase())

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .primaryOverlayFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        if (!isAssigned) {
                                            onSelectApp(appItem.packageName)
                                        }
                                    },
                                    enabled = !isAssigned,
                                ).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            packageName = appItem.packageName,
                            modifier =
                                Modifier
                                    .padding(end = 12.dp)
                                    .size(EIO_APP_ICON_SIZE)
                                    .alpha(if (isAssigned) 0.38f else 1f),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = appItem.appName,
                                    color = if (isAssigned) colors.onSurfaceSecondary.copy(alpha = 0.5f) else colors.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isAssigned) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.profile_settings_app_assigned),
                                        color = colors.onSurfaceSecondary.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Text(
                                text = appItem.packageName,
                                color = if (isAssigned) colors.onSurfaceSecondary.copy(alpha = 0.38f) else colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
