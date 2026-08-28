package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
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
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState

private const val TAG = "EditorInlineOverlays"
private val EIO_APP_ICON_SIZE = 36.dp
private val EIO_APP_ICON_CORNER = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewProfileSubPageContent(
    existingNames: List<String>,
    accentColor: Color,
    onDiscard: () -> Unit = {},
    onCreate: (name: String) -> Unit,
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

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = true,
            onSave = {
                if (isConfirmEnabled) {
                    onCreate(normalizedName)
                }
            },
            onDiscard = onDiscard,
        )

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

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_create_profile_title),
        description = stringResource(R.string.macropad_editor_create_profile_desc),
        pulseOnChanges = true,
        saveActionText = stringResource(R.string.gamepad_action_create),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )
}

@Composable
internal fun EditProfileSubPageContent(
    profile: PadProfile,
    existingNames: List<String>,
    accentColor: Color,
    onNameChange: (String) -> Unit,
    onUnlinkApp: () -> Unit,
    onDeleteProfile: () -> Unit,
) {
    val context = LocalContext.current
    var nameText by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate

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
        onValueChange = {
            nameText = it
            val trimmed = it.trim()
            if (trimmed.isNotEmpty() && !existingNames.any { n -> n.equals(trimmed, ignoreCase = true) }) {
                onNameChange(trimmed)
            }
        },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
    )

    // ── Actions Section ───────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_actions),
        color = accentColor,
    )

    val hasAssociation = profile.association != null
    val linkedTargetDesc =
        remember(profile.association) {
            val assoc = profile.association ?: return@remember null
            val romName = assoc.romFileName
            if (!romName.isNullOrBlank()) {
                romName
            } else {
                try {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(assoc.packageName, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    assoc.packageName
                }
            }
        }
    val unlinkTarget = linkedTargetDesc ?: profile.name

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_profile_unlink_app),
        confirmTitle = stringResource(R.string.macropad_profile_unlink_app_confirm_title, profile.name),
        description =
            if (hasAssociation) {
                stringResource(R.string.macropad_profile_unlink_app_desc, unlinkTarget)
            } else {
                stringResource(R.string.macropad_profile_no_app_linked)
            },
        actionText = if (hasAssociation) stringResource(R.string.gamepad_action_unlink) else null,
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        isDestructive = true,
        icon = Icons.Rounded.LinkOff,
        enabled = hasAssociation,
        onConfirm = onUnlinkApp,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_editor_delete_profile),
        confirmTitle = stringResource(R.string.macropad_profile_delete_confirm_title, profile.name),
        description = stringResource(R.string.macropad_editor_delete_profile_desc, profile.name),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        isDestructive = true,
        icon = Icons.Rounded.Delete,
        onConfirm = onDeleteProfile,
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
        AppLog.d(TAG, "Loaded ${appsList.size} launcher apps for button app action")
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
            GamepadInfoBox(
                text = stringResource(R.string.profile_settings_no_apps),
                description = stringResource(R.string.macropad_icon_picker_empty_desc),
                icon = Icons.Rounded.Search,
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
                        onClick = {
                            AppLog.d(TAG, "Selected app for button action: ${appItem.packageName}")
                            onSelectApp(appItem.packageName)
                        },
                    )
                }
            }
        }
    }
}
