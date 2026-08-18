package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "LayoutSettingsEditor"
private val LSE_PREVIEW_BUTTON_SIZE = 56.dp
private val LSE_PREVIEW_CONTAINER_PADDING = 12.dp
private val LSE_PREVIEW_CONTAINER_CORNER = 12.dp

@Composable
internal fun LayoutAppearanceSubPageContent(
    layout: PadLayout,
    existingNames: List<String>,
    accentColor: Color,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, onSave: (Color) -> Unit) -> Unit,
    onSave: (name: String, textColor: ColorOption, borderColor: ColorOption, bgColor: ColorOption, invisibleButtons: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    var nameText by remember(layout) { mutableStateOf(layout.name) }
    var textColorOption by remember(layout) { mutableStateOf(layout.buttonTextColor) }
    var borderColorOption by remember(layout) { mutableStateOf(layout.buttonBorderColor) }
    var bgColorOption by remember(layout) { mutableStateOf(layout.buttonBgColor) }
    var invisibleButtons by remember(layout) { mutableStateOf(layout.invisibleButtons) }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError

    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val currentResolvedText = resolveColorOption(textColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentResolvedBorder = resolveColorOption(borderColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentResolvedBg = resolveColorOption(bgColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)

    // Live Button Preview Container
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(LSE_PREVIEW_CONTAINER_CORNER))
                .padding(LSE_PREVIEW_CONTAINER_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwordsButtonPreview(
                textColor = currentResolvedText,
                borderColor = currentResolvedBorder,
                bgColor = currentResolvedBg,
                size = LSE_PREVIEW_BUTTON_SIZE,
                isIconOnly = invisibleButtons,
            )
            Text(
                text =
                    if (invisibleButtons) {
                        stringResource(R.string.layout_settings_invisible_buttons_desc)
                    } else {
                        stringResource(R.string.layout_settings_colors_section_title)
                    },
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_layout_identity),
        color = accentColor,
    )

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
        onOpenColorWheel = {
            onOpenColorWheel(
                selectTextColorTitle,
                textBreadcrumbs,
                currentResolvedText,
            ) { selectedColor ->
                textColorOption = ColorOption.Custom(selectedColor.toArgb())
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
        onOpenColorWheel = {
            onOpenColorWheel(
                selectBorderColorTitle,
                borderBreadcrumbs,
                currentResolvedBorder,
            ) { selectedColor ->
                borderColorOption = ColorOption.Custom(selectedColor.toArgb())
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
        onOpenColorWheel = {
            onOpenColorWheel(
                selectBgColorTitle,
                bgBreadcrumbs,
                currentResolvedBg,
            ) { selectedColor ->
                bgColorOption = ColorOption.Custom(selectedColor.toArgb())
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
    val colors = LocalAppColors.current

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
