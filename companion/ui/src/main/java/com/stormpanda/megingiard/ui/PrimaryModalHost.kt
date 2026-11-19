package com.stormpanda.megingiard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KeyboardSettingsOverlay
import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadNavState
import com.stormpanda.megingiard.mirror.AnchorSelectorOverlay
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.mirror.MirrorEditorTopOverlay
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.touchpad.TouchpadSettingsOverlay

private const val TAG = "PrimaryModalHost"
private const val PMH_DISCORD_URL = "https://discord.gg/F6RtHv9YqR"
private const val PMH_KOFI_URL = "https://ko-fi.com/stormpanda"
private val PMH_HEADER_BADGE_HEIGHT = 32.dp
private val PMH_HEADER_BADGE_CORNER = 8.dp
private val PMH_HEADER_BADGE_SHAPE = RoundedCornerShape(PMH_HEADER_BADGE_CORNER)
private val PMH_DISCORD_BADGE_START_PADDING = 6.dp
private val PMH_DISCORD_BADGE_END_PADDING = 10.dp
private val PMH_DISCORD_ICON_CIRCLE_SIZE = 22.dp
private val PMH_DISCORD_ICON_SIZE = 14.dp
private val PMH_DISCORD_ICON_SPACING = 8.dp
private const val PMH_DISCORD_LABEL_MAX_LINES = 1

/**
 * Composable dispatcher that renders the appropriate content for a given [PrimaryModalConfig].
 */
@Composable
fun PrimaryModalHost(
    config: PrimaryModalConfig,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "PrimaryModalHost: dispatching modal type=${config.type}")
    val context = LocalContext.current

    when (config.type) {
        PrimaryModalType.GLOBAL_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_global_title),
                icon = Icons.Rounded.Settings,
                onDismiss = onDismiss,
                modifier = modifier,
                actions = {
                    DiscordHeaderBadge(
                        onClick = {
                            AppLog.i(TAG, "PrimaryModalHost: launching Discord invite URL")
                            launchUrlOnPrimaryDisplay(context, PMH_DISCORD_URL)
                            onDismiss()
                        },
                    )
                    Image(
                        painter = painterResource(R.drawable.support_me_on_kofi_dark),
                        contentDescription = stringResource(R.string.settings_support_app),
                        modifier =
                            Modifier
                                .height(PMH_HEADER_BADGE_HEIGHT)
                                .clip(PMH_HEADER_BADGE_SHAPE)
                                .focusProperties { canFocus = false }
                                .clickable {
                                    AppLog.i(TAG, "PrimaryModalHost: launching Ko-fi support URL")
                                    launchUrlOnPrimaryDisplay(context, PMH_KOFI_URL)
                                    onDismiss()
                                },
                    )
                },
            ) {
                GlobalSettingsScreen(
                    onBack = onDismiss,
                )
            }
        }

        PrimaryModalType.KEYBOARD_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_keyboard_title),
                icon = Icons.Rounded.Keyboard,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                KeyboardSettingsOverlay()
            }
        }

        PrimaryModalType.TOUCHPAD_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_touchpad_title),
                icon = Icons.Rounded.Mouse,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                TouchpadSettingsOverlay()
            }
        }

        PrimaryModalType.MACROPAD_EDITOR -> {
            val handleEditorDismissRequest: () -> Boolean = {
                val shouldPromptReactivate =
                    AppStateManager.wasAutoSwitchDeactivatedInEditor.value &&
                        AppStateManager.companionViewMode.value != CompanionViewMode.AUTO
                MacroPadNavState.reset()
                if (shouldPromptReactivate) {
                    AppLog.i(TAG, "PrimaryModalHost: editor dismissed, immediately opening reactivate modal")
                    AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.REACTIVATE_AUTO_SWITCH))
                    true
                } else {
                    false
                }
            }

            PrimaryOverlayContainer(
                title = stringResource(R.string.macropad_editor_title),
                icon = Icons.Rounded.Widgets,
                onDismiss = onDismiss,
                onDismissRequest = handleEditorDismissRequest,
                modifier = modifier,
            ) {
                MacroPadEditor(
                    onDone = {
                        if (!handleEditorDismissRequest()) {
                            onDismiss()
                        }
                    },
                    showTopBar = false,
                )
            }
        }

        PrimaryModalType.REACTIVATE_AUTO_SWITCH -> {
            GamepadConfirmModal(
                visible = true,
                title = stringResource(R.string.macropad_reactivate_auto_switch_title),
                description = stringResource(R.string.macropad_reactivate_auto_switch_message),
                confirmTitle = stringResource(R.string.macropad_reactivate_auto_switch_confirm),
                confirmDescription = stringResource(R.string.macropad_reactivate_auto_switch_confirm_desc),
                confirmIcon = Icons.Rounded.AutoFixHigh,
                dismissTitle = stringResource(R.string.macropad_reactivate_auto_switch_dismiss),
                dismissDescription = stringResource(R.string.macropad_reactivate_auto_switch_dismiss_desc),
                dismissIcon = Icons.Rounded.Close,
                headerIcon = Icons.Rounded.AutoFixHigh,
                onConfirm = {
                    AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
                    AppStateManager.resetAutoSwitchDeactivatedInEditor()
                    onDismiss()
                },
                onDismissAction = {
                    AppStateManager.resetAutoSwitchDeactivatedInEditor()
                    onDismiss()
                },
                onCancel = {
                    AppStateManager.resetAutoSwitchDeactivatedInEditor()
                    onDismiss()
                },
            )
        }

        PrimaryModalType.CROP_SELECTOR -> {
            val payload = config.payload as? PrimaryModalPayload.CropSelector
            val cutoutId = payload?.cutoutId
            if (cutoutId != null) {
                CropSelectorOverlay(
                    cutoutId = cutoutId,
                    onDismiss = onDismiss,
                )
            }
        }

        PrimaryModalType.ANCHOR_SELECTOR -> {
            val payload = config.payload as? PrimaryModalPayload.AnchorSelector
            if (payload != null) {
                AnchorSelectorOverlay(
                    layoutId = payload.layoutId,
                    onDismiss = {
                        if (AppStateManager.suspendedPrimaryModal.value != null) {
                            AppStateManager.resumeSuspended()
                        } else {
                            onDismiss()
                        }
                    },
                )
            }
        }

        PrimaryModalType.MIRROR_VIEWPORT_EDITOR -> {
            val closeViewportEdit = {
                AppStateManager.openPrimaryModal(
                    PrimaryModalConfig(
                        type = PrimaryModalType.MACROPAD_EDITOR,
                        payload = PrimaryModalPayload.MacroPad(section = EditorSection.MIRROR),
                    ),
                )
            }
            MirrorEditorTopOverlay(
                onDone = closeViewportEdit,
                onCancel = closeViewportEdit,
            )
        }
    }
}

@Composable
private fun DiscordHeaderBadge(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .height(PMH_HEADER_BADGE_HEIGHT)
                .clip(PMH_HEADER_BADGE_SHAPE)
                .background(AppPalettes.headerBadgeDarkBackground)
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick)
                .padding(
                    start = PMH_DISCORD_BADGE_START_PADDING,
                    end = PMH_DISCORD_BADGE_END_PADDING,
                ),
        horizontalArrangement = Arrangement.spacedBy(PMH_DISCORD_ICON_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PMH_DISCORD_ICON_CIRCLE_SIZE)
                    .clip(CircleShape)
                    .background(AppPalettes.discordBlurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_discord),
                contentDescription = null,
                tint = AppPalettes.discordOnBlurple,
                modifier = Modifier.size(PMH_DISCORD_ICON_SIZE),
            )
        }
        Text(
            text = stringResource(R.string.settings_join_discord),
            color = AppPalettes.discordOnBlurple,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = PMH_DISCORD_LABEL_MAX_LINES,
        )
    }
}
