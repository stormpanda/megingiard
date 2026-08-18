package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "LayoutSettingsEditor"

@Composable
private fun describeColorOption(
    option: ColorOption,
    resolvedColor: Color,
): String =
    when (option) {
        is ColorOption.Neutral -> stringResource(R.string.layout_settings_color_neutral)
        is ColorOption.Accent -> stringResource(R.string.layout_settings_color_accent)
        is ColorOption.Custom -> String.format("#%06X", 0xFFFFFF and resolvedColor.toArgb())
    }

@Composable
internal fun LayoutAppearanceSubPageContent(
    layout: PadLayout,
    existingNames: List<String>,
    accentColor: Color,
    onOpenColorSubMenu: (target: LayoutColorTarget, currentDraft: PadLayout) -> Unit,
    onSave: (name: String, textColor: ColorOption, borderColor: ColorOption, bgColor: ColorOption, invisibleButtons: Boolean) -> Unit,
) {
    var nameText by remember(layout.id, layout.name) { mutableStateOf(layout.name) }
    var textColorOption by remember(layout.id, layout.buttonTextColor) { mutableStateOf(layout.buttonTextColor) }
    var borderColorOption by remember(layout.id, layout.buttonBorderColor) { mutableStateOf(layout.buttonBorderColor) }
    var bgColorOption by remember(layout.id, layout.buttonBgColor) { mutableStateOf(layout.buttonBgColor) }
    var invisibleButtons by remember(layout.id, layout.invisibleButtons) { mutableStateOf(layout.invisibleButtons) }

    fun buildWorkingLayout(): PadLayout =
        layout.copy(
            name = nameText,
            buttonTextColor = textColorOption,
            buttonBorderColor = borderColorOption,
            buttonBgColor = bgColorOption,
            invisibleButtons = invisibleButtons,
        )

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError

    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val currentResolvedText = resolveColorOption(textColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentResolvedBorder = resolveColorOption(borderColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentResolvedBg = resolveColorOption(bgColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)

    val previewButton: @Composable () -> Unit = {
        SwordsButtonPreview(
            textColor = currentResolvedText,
            borderColor = currentResolvedBorder,
            bgColor = currentResolvedBg,
            isIconOnly = invisibleButtons,
        )
    }

    GamepadTextFieldCard(
        title = stringResource(R.string.quick_menu_layout_name_hint),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_layout_name_desc)
            },
        placeholder = stringResource(R.string.quick_menu_layout_name_placeholder),
        value = nameText,
        onValueChange = { nameText = it },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSectionHeader(
        text = stringResource(R.string.layout_settings_colors_section_title),
        color = accentColor,
    )

    // ── Text Color Menu Item ────────────────────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_text),
        description = describeColorOption(textColorOption, currentResolvedText),
        icon = Icons.Rounded.FormatColorText,
        actionLeadingContent = previewButton,
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.TEXT, buildWorkingLayout()) },
    )

    // ── Border Color Menu Item ──────────────────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_border),
        description = describeColorOption(borderColorOption, currentResolvedBorder),
        icon = Icons.Rounded.Palette,
        actionLeadingContent = previewButton,
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.BORDER, buildWorkingLayout()) },
    )

    // ── Background / Fading Color Menu Item ─────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_bg),
        description = describeColorOption(bgColorOption, currentResolvedBg),
        icon = Icons.Rounded.FormatColorFill,
        actionLeadingContent = previewButton,
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.BG, buildWorkingLayout()) },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_visibility_behavior),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = invisibleButtons,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = { invisibleButtons = it },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_save_layout_title),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        actionText = stringResource(R.string.gamepad_action_save),
        icon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        onClick = {
            if (isConfirmEnabled) {
                onSave(normalizedName, textColorOption, borderColorOption, bgColorOption, invisibleButtons)
            }
        },
    )
}

@Composable
internal fun LayoutColorSubPageContent(
    layout: PadLayout,
    target: LayoutColorTarget,
    accentColor: Color,
    onColorOptionSelected: (ColorOption) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color) -> Unit,
) {
    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val currentOption =
        when (target) {
            LayoutColorTarget.TEXT -> layout.buttonTextColor
            LayoutColorTarget.BORDER -> layout.buttonBorderColor
            LayoutColorTarget.BG -> layout.buttonBgColor
        }

    val defaultNeutralColor =
        when (target) {
            LayoutColorTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
            LayoutColorTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
            LayoutColorTarget.BG -> MP_AMBIENT_NEUTRAL_BG
        }

    val currentColor = resolveColorOption(currentOption, globalAccentColor, defaultNeutralColor)

    val targetTitle =
        when (target) {
            LayoutColorTarget.TEXT -> stringResource(R.string.layout_settings_color_text)
            LayoutColorTarget.BORDER -> stringResource(R.string.layout_settings_color_border)
            LayoutColorTarget.BG -> stringResource(R.string.layout_settings_color_bg)
        }

    val selectColorWheelTitle =
        when (target) {
            LayoutColorTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
            LayoutColorTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
            LayoutColorTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
        }

    val colorWheelBreadcrumbs =
        listOf(
            stringResource(R.string.macropad_editor_section_layout),
            stringResource(R.string.macropad_editor_appearance_title),
            targetTitle,
            stringResource(R.string.settings_accent_custom_title),
        )

    val isNeutralSelected = currentOption is ColorOption.Neutral
    val isAccentSelected = currentOption is ColorOption.Accent
    val isCustomSelected = currentOption is ColorOption.Custom

    // Option 1: Theme Neutral
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_neutral),
        description = stringResource(R.string.macropad_editor_color_palette_desc),
        icon = Icons.Rounded.FormatColorText,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = defaultNeutralColor,
                isSelected = isNeutralSelected,
            )
        },
        actionText = if (isNeutralSelected) stringResource(R.string.gamepad_color_selected) else null,
        onClick = { onColorOptionSelected(ColorOption.Neutral) },
        modifier = Modifier.firstDeckItem(),
    )

    // Option 2: App Accent
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_accent),
        description = stringResource(R.string.settings_accent_color_desc),
        icon = Icons.Rounded.Palette,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = globalAccentColor,
                isSelected = isAccentSelected,
            )
        },
        actionText = if (isAccentSelected) stringResource(R.string.gamepad_color_selected) else null,
        onClick = { onColorOptionSelected(ColorOption.Accent) },
    )

    // Option 3: Custom Color (Color Wheel)
    GamepadActionCard(
        title = stringResource(R.string.settings_accent_custom_title),
        description =
            if (isCustomSelected) {
                String.format("#%06X", 0xFFFFFF and currentColor.toArgb())
            } else {
                stringResource(R.string.macropad_editor_color_wheel_desc)
            },
        icon = Icons.Rounded.Colorize,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = if (isCustomSelected) currentColor else Color.Transparent,
                isSelected = isCustomSelected,
            )
        },
        actionText = stringResource(R.string.gamepad_action_color_wheel),
        onClick = {
            onOpenColorWheel(
                selectColorWheelTitle,
                colorWheelBreadcrumbs,
                currentColor,
            )
        },
    )
}

@Composable
internal fun NewLayoutSubPageContent(
    existingNames: List<String>,
    accentColor: Color,
    onCreate: (name: String, invisibleButtons: Boolean) -> Unit,
) {
    val defaultLayoutName = stringResource(R.string.macropad_editor_section_layout)
    val initialLayoutName =
        remember(existingNames) {
            if (existingNames.none { it.equals(defaultLayoutName, ignoreCase = true) }) {
                defaultLayoutName
            } else {
                var index = 2
                while (existingNames.any { it.equals("$defaultLayoutName ($index)", ignoreCase = true) }) {
                    index++
                }
                "$defaultLayoutName ($index)"
            }
        }
    var nameText by remember { mutableStateOf(initialLayoutName) }
    var invisibleButtons by remember { mutableStateOf(false) }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError

    GamepadTextFieldCard(
        title = stringResource(R.string.quick_menu_layout_name_hint),
        description =
            when {
                normalizedName.isEmpty() -> stringResource(R.string.settings_name_error_empty)
                isDuplicate -> stringResource(R.string.settings_name_error_duplicate)
                else -> stringResource(R.string.macropad_editor_layout_name_desc)
            },
        placeholder = stringResource(R.string.quick_menu_layout_name_placeholder),
        value = nameText,
        onValueChange = { nameText = it },
        icon = Icons.Rounded.Edit,
        isError = hasError,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = invisibleButtons,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = { invisibleButtons = it },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_create_layout_title),
        description = stringResource(R.string.macropad_editor_create_layout_desc),
        actionText = stringResource(R.string.gamepad_action_create),
        icon = Icons.Rounded.Add,
        enabled = isConfirmEnabled,
        onClick = {
            if (isConfirmEnabled) {
                onCreate(normalizedName, invisibleButtons)
            }
        },
    )
}
