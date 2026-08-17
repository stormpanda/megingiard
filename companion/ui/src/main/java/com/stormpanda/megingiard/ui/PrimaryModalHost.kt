package com.stormpanda.megingiard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KeyboardSettingsOverlay
import com.stormpanda.megingiard.macropad.BackgroundSettingsOverlay
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.touchpad.TouchpadSettingsOverlay

private const val TAG = "PrimaryModalHost"
private val PMH_KOFI_BUTTON_HEIGHT = 32.dp
private val PMH_KOFI_CORNER = 8.dp

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
    val colors = LocalAppColors.current
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
                                .clip(RoundedCornerShape(PMH_KOFI_CORNER))
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
            var showHelp by remember { mutableStateOf(false) }
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_keyboard_title),
                icon = Icons.Rounded.Keyboard,
                onDismiss = onDismiss,
                modifier = modifier,
                actions = {
                    HelpIconButton(onClick = { showHelp = true })
                },
            ) {
                KeyboardSettingsOverlay(
                    onBack = onDismiss,
                    showHelp = showHelp,
                    onDismissHelp = { showHelp = false },
                )
            }
        }

        PrimaryModalType.TOUCHPAD_SETTINGS -> {
            var showHelp by remember { mutableStateOf(false) }
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_touchpad_title),
                icon = Icons.Rounded.Mouse,
                onDismiss = onDismiss,
                modifier = modifier,
                actions = {
                    HelpIconButton(onClick = { showHelp = true })
                },
            ) {
                TouchpadSettingsOverlay(
                    onBack = onDismiss,
                    showHelp = showHelp,
                    onDismissHelp = { showHelp = false },
                )
            }
        }

        PrimaryModalType.BACKGROUND_SETTINGS -> {
            var showHelp by remember { mutableStateOf(false) }
            PrimaryOverlayContainer(
                title = stringResource(R.string.quick_menu_ambient_settings),
                icon = Icons.Rounded.Videocam,
                onDismiss = onDismiss,
                modifier = modifier,
                actions = {
                    HelpIconButton(onClick = { showHelp = true })
                },
            ) {
                BackgroundSettingsOverlay(
                    onDone = onDismiss,
                    showHelp = showHelp,
                    onDismissHelp = { showHelp = false },
                )
            }
        }

        PrimaryModalType.MACROPAD_EDITOR,
        PrimaryModalType.MACROPAD_INSPECTOR,
        PrimaryModalType.LAYOUT_SETTINGS,
        PrimaryModalType.PROFILE_SETTINGS,
        PrimaryModalType.MACRO_TIMELINE_EDITOR,
        -> {
            var showHelp by remember { mutableStateOf(false) }
            PrimaryOverlayContainer(
                title = stringResource(R.string.macropad_editor_title),
                icon = Icons.Rounded.Widgets,
                onDismiss = onDismiss,
                modifier = modifier,
                actions = {
                    HelpIconButton(onClick = { showHelp = true })
                },
            ) {
                MacroPadEditor(
                    onDone = onDismiss,
                    showTopBar = false,
                    showHelp = showHelp,
                    onDismissHelp = { showHelp = false },
                )
            }
        }

        PrimaryModalType.HELP_TUTORIAL -> {
            HelpModal(
                visible = true,
                title = stringResource(R.string.help_editor_title),
                onDismiss = onDismiss,
            ) {
                HelpIntro(text = stringResource(R.string.help_editor_intro))
            }
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
    }
}
