package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppIcon
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EditorInlineOverlays"
private val EIO_APP_ICON_SIZE = 36.dp
private val EIO_APP_ICON_CORNER = 8.dp

@Composable
internal fun NewProfileSubPageContent(
    existingNames: List<String>,
    selectedPackage: String?,
    accentColor: Color,
    onOpenAppPicker: () -> Unit,
    onClearApp: () -> Unit,
    onCreate: (name: String, packageName: String?) -> Unit,
) {
    val defaultName = stringResource(R.string.integration_home_new_profile)
    val initialName =
        remember(existingNames) {
            if (existingNames.none { it.equals(defaultName, ignoreCase = true) }) {
                defaultName
            } else {
                var index = 2
                while (existingNames.any { it.equals("$defaultName ($index)", ignoreCase = true) }) {
                    index++
                }
                "$defaultName ($index)"
            }
        }
    var nameText by remember { mutableStateOf(initialName) }
    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError
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
                        AppLog.w(TAG, "Failed to resolve app label for $pkg: ${e.message}")
                        pkg
                    }
                }
        }
    }

    GamepadTextFieldCard(
        title = stringResource(R.string.profile_settings_name),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_profile_name_desc)
            },
        placeholder = stringResource(R.string.profile_settings_name_placeholder),
        value = nameText,
        onValueChange = { nameText = it },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
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
        title = stringResource(R.string.macropad_editor_create_profile_title),
        description = stringResource(R.string.macropad_editor_create_profile_desc),
        actionText = stringResource(R.string.gamepad_action_create),
        icon = Icons.Rounded.Add,
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
                        AppLog.w(TAG, "Failed to resolve app label for $pkg: ${e.message}")
                        pkg
                    }
                }
        }
    }

    GamepadTextFieldCard(
        title = stringResource(R.string.profile_settings_name),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_edit_profile_name_desc)
            },
        placeholder = stringResource(R.string.profile_settings_name_placeholder),
        value = nameText,
        onValueChange = { nameText = it },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
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
        title = stringResource(R.string.macropad_editor_save_profile_title),
        description = stringResource(R.string.macropad_editor_edit_profile_desc),
        actionText = stringResource(R.string.gamepad_action_save),
        icon = Icons.Rounded.Save,
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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingApps = true
        appsList = queryInstalledLauncherApps(context)
        isLoadingApps = false
    }

    GamepadTextFieldCard(
        title = stringResource(R.string.profile_settings_search_apps),
        description = stringResource(R.string.profile_settings_search_apps_desc),
        placeholder = stringResource(R.string.profile_settings_search_apps_placeholder),
        value = searchQuery,
        onValueChange = { searchQuery = it },
        icon = Icons.Rounded.Search,
        modifier = Modifier.firstDeckItem(),
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
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.packageName }) { appItem ->
                    val isAssigned = assignedPackages.contains(appItem.packageName.trim().lowercase())

                    GamepadActionCard(
                        title = appItem.appName,
                        description = appItem.packageName,
                        actionText = if (isAssigned) stringResource(R.string.profile_settings_app_assigned) else null,
                        leadingContent = {
                            AppIcon(
                                packageName = appItem.packageName,
                                modifier =
                                    Modifier
                                        .size(EIO_APP_ICON_SIZE)
                                        .clip(RoundedCornerShape(EIO_APP_ICON_CORNER))
                                        .alpha(if (isAssigned) 0.38f else 1f),
                            )
                        },
                        enabled = !isAssigned,
                        onClick = { onSelectApp(appItem.packageName) },
                    )
                }
            }
        }
    }
}
