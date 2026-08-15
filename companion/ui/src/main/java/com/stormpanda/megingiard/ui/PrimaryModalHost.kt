package com.stormpanda.megingiard.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KeyboardSettingsOverlay
import com.stormpanda.megingiard.macropad.BackgroundSettingsOverlay
import com.stormpanda.megingiard.macropad.ButtonEditDialog
import com.stormpanda.megingiard.macropad.InlineProfileSettingsOverlay
import com.stormpanda.megingiard.macropad.LayoutSettingsEditor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroTimelineEditor
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadSettingsOverlay
import com.stormpanda.megingiard.ui.onboarding.OnboardingWizardDialog

private const val PMH_BUMPER_HINT_TABS = "LB / RB: Tabs"

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

    when (config.type) {
        PrimaryModalType.GLOBAL_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_global_title),
                icon = Icons.Rounded.Settings,
                bumperHint = PMH_BUMPER_HINT_TABS,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                GlobalSettingsScreen(onBack = onDismiss)
            }
        }

        PrimaryModalType.KEYBOARD_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_keyboard_title),
                icon = Icons.Rounded.Keyboard,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                KeyboardSettingsOverlay(onBack = onDismiss)
            }
        }

        PrimaryModalType.TOUCHPAD_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_touchpad_title),
                icon = Icons.Rounded.Mouse,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                TouchpadSettingsOverlay(onBack = onDismiss)
            }
        }

        PrimaryModalType.BACKGROUND_SETTINGS -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.quick_menu_ambient_settings),
                icon = Icons.Rounded.Videocam,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                BackgroundSettingsOverlay(onDone = onDismiss)
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

        PrimaryModalType.ONBOARDING_WIZARD -> {
            val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
            OnboardingWizardDialog(
                overlayAtBottom = overlayAtBottom,
                onDismiss = onDismiss,
            )
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

        PrimaryModalType.CONFIG_IMPORT_EXPORT -> {
            PrimaryOverlayContainer(
                title = stringResource(R.string.settings_global_title),
                icon = Icons.Rounded.Settings,
                onDismiss = onDismiss,
                modifier = modifier,
            ) {
                GlobalSettingsScreen(onBack = onDismiss)
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
