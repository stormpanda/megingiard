package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "LayoutSettingsEditor"

@Composable
internal fun LayoutAppearanceSubPageContent(
    layout: PadLayout,
    existingNames: List<String>,
    accentColor: Color,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, onApplyColor: (Color) -> PadLayout) -> Unit,
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

    // ── Text Color ──────────────────────────────────────────────
    val selectTextColorTitle = stringResource(R.string.layout_settings_select_text_color)
    val textBreadcrumbs =
        listOf(
            stringResource(R.string.macropad_editor_section_layout),
            stringResource(R.string.macropad_editor_appearance_title),
            stringResource(R.string.layout_settings_color_text),
        )
    ColorOptionPaletteSection(
        title = stringResource(R.string.layout_settings_color_text),
        description = stringResource(R.string.macropad_editor_color_palette_desc),
        icon = Icons.Rounded.FormatColorText,
        colorOption = textColorOption,
        defaultNeutralColor = MP_AMBIENT_NEUTRAL_TEXT,
        globalAccentColor = globalAccentColor,
        onOptionSelected = { textColorOption = it },
        trailingContent = previewButton,
        onOpenColorWheel = {
            val draft = buildWorkingLayout()
            onOpenColorWheel(
                selectTextColorTitle,
                textBreadcrumbs,
                currentResolvedText,
            ) { selectedColor ->
                draft.copy(buttonTextColor = ColorOption.Custom(selectedColor.toArgb()))
            }
        },
    )

    // ── Border Color ────────────────────────────────────────────
    val selectBorderColorTitle = stringResource(R.string.layout_settings_select_border_color)
    val borderBreadcrumbs =
        listOf(
            stringResource(R.string.macropad_editor_section_layout),
            stringResource(R.string.macropad_editor_appearance_title),
            stringResource(R.string.layout_settings_color_border),
        )
    ColorOptionPaletteSection(
        title = stringResource(R.string.layout_settings_color_border),
        description = stringResource(R.string.macropad_editor_border_style_desc),
        icon = Icons.Rounded.Palette,
        colorOption = borderColorOption,
        defaultNeutralColor = MP_AMBIENT_NEUTRAL_BORDER,
        globalAccentColor = globalAccentColor,
        onOptionSelected = { borderColorOption = it },
        trailingContent = previewButton,
        onOpenColorWheel = {
            val draft = buildWorkingLayout()
            onOpenColorWheel(
                selectBorderColorTitle,
                borderBreadcrumbs,
                currentResolvedBorder,
            ) { selectedColor ->
                draft.copy(buttonBorderColor = ColorOption.Custom(selectedColor.toArgb()))
            }
        },
    )

    // ── Background Color ────────────────────────────────────────
    val selectBgColorTitle = stringResource(R.string.layout_settings_select_bg_color)
    val bgBreadcrumbs =
        listOf(
            stringResource(R.string.macropad_editor_section_layout),
            stringResource(R.string.macropad_editor_appearance_title),
            stringResource(R.string.layout_settings_color_bg),
        )
    ColorOptionPaletteSection(
        title = stringResource(R.string.layout_settings_color_bg),
        description = stringResource(R.string.macropad_editor_fill_style_desc),
        icon = Icons.Rounded.FormatColorFill,
        colorOption = bgColorOption,
        defaultNeutralColor = MP_AMBIENT_NEUTRAL_BG,
        globalAccentColor = globalAccentColor,
        onOptionSelected = { bgColorOption = it },
        trailingContent = previewButton,
        onOpenColorWheel = {
            val draft = buildWorkingLayout()
            onOpenColorWheel(
                selectBgColorTitle,
                bgBreadcrumbs,
                currentResolvedBg,
            ) { selectedColor ->
                draft.copy(buttonBgColor = ColorOption.Custom(selectedColor.toArgb()))
            }
        },
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
