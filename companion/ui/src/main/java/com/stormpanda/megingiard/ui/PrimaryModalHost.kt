package com.stormpanda.megingiard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KeyboardSettingsOverlay
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadNavState
import com.stormpanda.megingiard.mirror.AnchorSelectorOverlay
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.touchpad.TouchpadSettingsOverlay

private const val TAG = "PrimaryModalHost"
private val PMH_KOFI_BUTTON_HEIGHT = 32.dp
private val PMH_KOFI_CORNER = 8.dp
private val PMH_KOFI_SHAPE = RoundedCornerShape(PMH_KOFI_CORNER)

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
                    Image(
                        painter = painterResource(R.drawable.support_me_on_kofi_dark),
                        contentDescription = stringResource(R.string.settings_support_app),
                        modifier =
                            Modifier
                                .height(PMH_KOFI_BUTTON_HEIGHT)
                                .clip(PMH_KOFI_SHAPE)
                                .clickable {
                                    launchUrlOnPrimaryDisplay(context, "https://ko-fi.com/stormpanda")
                                    onDismiss()
                                }.focusProperties { canFocus = false },
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

        PrimaryModalType.BACKGROUND_SETTINGS,
        PrimaryModalType.MACROPAD_EDITOR,
        PrimaryModalType.MACROPAD_INSPECTOR,
        PrimaryModalType.LAYOUT_SETTINGS,
        PrimaryModalType.PROFILE_SETTINGS,
        PrimaryModalType.MACRO_TIMELINE_EDITOR,
        -> {
            val handleEditorDismiss = {
                val shouldPromptReactivate =
                    AppStateManager.wasAutoSwitchDeactivatedInEditor.value &&
                        AppStateManager.companionViewMode.value != CompanionViewMode.AUTO
                MacroPadNavState.reset()
                if (shouldPromptReactivate) {
                    AppLog.i(TAG, "PrimaryModalHost: editor dismissed, prompting auto switch reactivation")
                    AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.REACTIVATE_AUTO_SWITCH))
                } else {
                    onDismiss()
                }
            }

            PrimaryOverlayContainer(
                title = stringResource(R.string.macropad_editor_title),
                icon = Icons.Rounded.Widgets,
                onDismiss = handleEditorDismiss,
                modifier = modifier,
            ) {
                MacroPadEditor(
                    onDone = handleEditorDismiss,
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
    }
}
