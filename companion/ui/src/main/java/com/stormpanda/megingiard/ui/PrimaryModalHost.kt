package com.stormpanda.megingiard.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.view.Display
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
import com.stormpanda.megingiard.macropad.ButtonEditDialog
import com.stormpanda.megingiard.macropad.InlineProfileSettingsOverlay
import com.stormpanda.megingiard.macropad.LayoutSettingsEditor
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroTimelineEditor
import com.stormpanda.megingiard.macropad.ProfileAssociation
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
                                    val url = "https://ko-fi.com/stormpanda"
                                    try {
                                        val intent =
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        val options = ActivityOptions.makeBasic()
                                        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                        context.startActivity(intent, options.toBundle())
                                        onDismiss()
                                    } catch (e: Exception) {
                                        AppLog.e(TAG, "Failed to open Ko-fi link: ${e.message}")
                                    }
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

        PrimaryModalType.MACROPAD_EDITOR -> {
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

        PrimaryModalType.MACROPAD_INSPECTOR -> {
            val selectedButtonId by MacroPadState.selectedButtonId.collectAsState()
            val activeLayout by MacroPadState.activeLayout.collectAsState()
            val button = activeLayout?.buttons?.find { it.id == selectedButtonId }

            if (button != null) {
                ButtonEditDialog(
                    button = button,
                    accentColor = colors.accent,
                    onConfirm = { updated ->
                        MacroPadState.updateButton(updated)
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                )
            } else {
                activeLayout?.let { layout ->
                    PrimaryOverlayContainer(
                        title = stringResource(R.string.macropad_editor_title),
                        icon = Icons.Rounded.Widgets,
                        onDismiss = onDismiss,
                        modifier = modifier,
                    ) {
                        LayoutSettingsEditor(
                            title = stringResource(R.string.macropad_editor_title),
                            layoutId = layout.id,
                            initialName = layout.name,
                            initialButtonTextColor = layout.buttonTextColor,
                            initialButtonBorderColor = layout.buttonBorderColor,
                            initialButtonBgColor = layout.buttonBgColor,
                            initialInvisibleButtons = layout.invisibleButtons,
                            accentColor = colors.accent,
                            existingNames =
                                MacroPadState.activeProfile.value
                                    ?.layouts
                                    ?.map { it.name } ?: emptyList(),
                            onConfirm = { name, txt, bdr, bg, inv ->
                                MacroPadState.updateLayout(
                                    layout.copy(
                                        name = name,
                                        buttonTextColor = txt,
                                        buttonBorderColor = bdr,
                                        buttonBgColor = bg,
                                        invisibleButtons = inv,
                                    ),
                                )
                                onDismiss()
                            },
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }

        PrimaryModalType.LAYOUT_SETTINGS -> {
            val activeLayout by MacroPadState.activeLayout.collectAsState()
            activeLayout?.let { layout ->
                PrimaryOverlayContainer(
                    title = stringResource(R.string.macropad_editor_title),
                    icon = Icons.Rounded.Tune,
                    onDismiss = onDismiss,
                    modifier = modifier,
                ) {
                    LayoutSettingsEditor(
                        title = stringResource(R.string.macropad_editor_title),
                        layoutId = layout.id,
                        initialName = layout.name,
                        initialButtonTextColor = layout.buttonTextColor,
                        initialButtonBorderColor = layout.buttonBorderColor,
                        initialButtonBgColor = layout.buttonBgColor,
                        initialInvisibleButtons = layout.invisibleButtons,
                        accentColor = colors.accent,
                        existingNames =
                            MacroPadState.activeProfile.value
                                ?.layouts
                                ?.map { it.name } ?: emptyList(),
                        onConfirm = { name, txt, bdr, bg, inv ->
                            MacroPadState.updateLayout(
                                layout.copy(
                                    name = name,
                                    buttonTextColor = txt,
                                    buttonBorderColor = bdr,
                                    buttonBgColor = bg,
                                    invisibleButtons = inv,
                                ),
                            )
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
            }
        }

        PrimaryModalType.PROFILE_SETTINGS -> {
            val activeProfile by MacroPadState.activeProfile.collectAsState()
            val profiles by MacroPadState.profiles.collectAsState()
            activeProfile?.let { profile ->
                PrimaryOverlayContainer(
                    title = stringResource(R.string.profile_settings_title),
                    icon = Icons.Rounded.Tune,
                    onDismiss = onDismiss,
                    modifier = modifier,
                ) {
                    InlineProfileSettingsOverlay(
                        title = stringResource(R.string.profile_settings_title),
                        initialName = profile.name,
                        initialPackage = profile.association?.packageName,
                        accentColor = colors.accent,
                        existingNames = profiles.filter { it.id != profile.id }.map { it.name },
                        onConfirm = { name, pkg ->
                            val assoc =
                                if (pkg != null) {
                                    val existing = profile.association
                                    if (existing != null && existing.packageName.equals(pkg, ignoreCase = true)) {
                                        existing
                                    } else {
                                        ProfileAssociation(packageName = pkg)
                                    }
                                } else {
                                    null
                                }
                            MacroPadState.renameProfile(profile.id, name, assoc)
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
            }
        }

        PrimaryModalType.MACRO_TIMELINE_EDITOR -> {
            val payload = config.payload as? PrimaryModalPayload.MacroTimeline
            val activeProfile by MacroPadState.activeProfile.collectAsState()
            val macro = activeProfile?.macros?.find { it.id == payload?.macroId }
            if (macro != null) {
                PrimaryOverlayContainer(
                    title = macro.name,
                    icon = Icons.Rounded.Widgets,
                    onDismiss = onDismiss,
                    modifier = modifier,
                ) {
                    MacroTimelineEditor(
                        macro = macro,
                        accentColor = colors.accent,
                        onSave = { updated ->
                            MacroPadState.updateMacro(updated)
                            onDismiss()
                        },
                        onBack = onDismiss,
                    )
                }
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
