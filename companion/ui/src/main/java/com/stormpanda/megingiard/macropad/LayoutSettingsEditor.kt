package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents

private const val TAG = "LayoutSettingsEditor"
private val LSE_PREVIEW_BUTTON_SIZE = 54.dp
private val LSE_RECENT_COLORS_GRID_HEIGHT = 120.dp

private enum class ColorPickerTarget { TEXT, BORDER, BG }

@Composable
internal fun LayoutSettingsEditor(
    title: String,
    layoutId: String,
    initialName: String,
    initialButtonTextColor: ColorOption,
    initialButtonBorderColor: ColorOption,
    initialButtonBgColor: ColorOption,
    initialInvisibleButtons: Boolean = false,
    accentColor: Color,
    existingNames: List<String>,
    onConfirm: (String, ColorOption, ColorOption, ColorOption, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var nameText by remember { mutableStateOf(initialName) }
    var textColorOption by remember { mutableStateOf(initialButtonTextColor) }
    var borderColorOption by remember { mutableStateOf(initialButtonBorderColor) }
    var bgColorOption by remember { mutableStateOf(initialButtonBgColor) }
    var invisibleButtons by remember { mutableStateOf(initialInvisibleButtons) }

    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var activePaletteDialogTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showHelpMenu by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError && !isSaving

    val recentColors by MacroPadSettings.recentColors.collectAsState()
    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    fun colorOptionLabel(option: ColorOption): String =
        when (option) {
            ColorOption.Neutral -> "Default Neutral"
            ColorOption.Accent -> "Global Theme Accent"
            is ColorOption.Custom -> "Custom Color"
        }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().blockPointerEvents(),
            containerColor = colors.appBackground,
            topBar = {
                FullScreenTopBar(
                    title = title,
                    onDismiss = onDismiss,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (isConfirmEnabled) {
                                    isSaving = true
                                    onConfirm(
                                        normalizedName,
                                        textColorOption,
                                        borderColorOption,
                                        bgColorOption,
                                        invisibleButtons,
                                    )
                                    isSaving = false
                                }
                            },
                            enabled = isConfirmEnabled,
                        ) {
                            Text(
                                text = stringResource(R.string.macropad_editor_done),
                                color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        HelpIconButton(onClick = { showHelpMenu = true })
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "LAYOUT IDENTITY",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                AppTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.quick_menu_layout_name_hint), color = colors.onSurfaceSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = hasError,
                    supportingText = {
                        when {
                            normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                            isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                        }
                    },
                )

                Text(
                    text = stringResource(R.string.layout_settings_colors_section_title).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // Text Color
                val colorOptionsList = listOf(ColorOption.Neutral, ColorOption.Accent)
                GamepadChoiceCard(
                    title = stringResource(R.string.layout_settings_color_text),
                    description = "Choose default, theme accent, or custom palette",
                    selectedText = colorOptionLabel(textColorOption),
                    icon = Icons.Rounded.FormatColorText,
                    onPrevious = {
                        val curIdx = if (textColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(textColorOption)
                        val nextIdx = (curIdx - 1 + colorOptionsList.size) % colorOptionsList.size
                        textColorOption = colorOptionsList[nextIdx]
                    },
                    onNext = {
                        val curIdx = if (textColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(textColorOption)
                        val nextIdx = (curIdx + 1) % colorOptionsList.size
                        textColorOption = colorOptionsList[nextIdx]
                    },
                )

                GamepadActionCard(
                    title = "Text Custom Color / Palette",
                    description = "Open color wheel or recent color palette",
                    actionText = "Color Wheel",
                    icon = Icons.Rounded.Palette,
                    onClick = { activeColorPickerTarget = ColorPickerTarget.TEXT },
                )

                // Border Color
                GamepadChoiceCard(
                    title = stringResource(R.string.layout_settings_color_border),
                    description = "Button edge ring outline styling",
                    selectedText = colorOptionLabel(borderColorOption),
                    icon = Icons.Rounded.Palette,
                    onPrevious = {
                        val curIdx = if (borderColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(borderColorOption)
                        val nextIdx = (curIdx - 1 + colorOptionsList.size) % colorOptionsList.size
                        borderColorOption = colorOptionsList[nextIdx]
                    },
                    onNext = {
                        val curIdx = if (borderColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(borderColorOption)
                        val nextIdx = (curIdx + 1) % colorOptionsList.size
                        borderColorOption = colorOptionsList[nextIdx]
                    },
                )

                GamepadActionCard(
                    title = "Border Custom Color / Palette",
                    description = "Open color wheel or recent color palette",
                    actionText = "Color Wheel",
                    icon = Icons.Rounded.Palette,
                    onClick = { activeColorPickerTarget = ColorPickerTarget.BORDER },
                )

                // Background Color
                GamepadChoiceCard(
                    title = stringResource(R.string.layout_settings_color_bg),
                    description = "Button cap fill background tint",
                    selectedText = colorOptionLabel(bgColorOption),
                    icon = Icons.Rounded.Palette,
                    onPrevious = {
                        val curIdx = if (bgColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(bgColorOption)
                        val nextIdx = (curIdx - 1 + colorOptionsList.size) % colorOptionsList.size
                        bgColorOption = colorOptionsList[nextIdx]
                    },
                    onNext = {
                        val curIdx = if (bgColorOption is ColorOption.Custom) 0 else colorOptionsList.indexOf(bgColorOption)
                        val nextIdx = (curIdx + 1) % colorOptionsList.size
                        bgColorOption = colorOptionsList[nextIdx]
                    },
                )

                GamepadActionCard(
                    title = "Background Custom Color / Palette",
                    description = "Open color wheel or recent color palette",
                    actionText = "Color Wheel",
                    icon = Icons.Rounded.Palette,
                    onClick = { activeColorPickerTarget = ColorPickerTarget.BG },
                )

                Text(
                    text = "VISIBILITY & BEHAVIOR",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )

                GamepadToggleCard(
                    title = stringResource(R.string.layout_settings_invisible_buttons),
                    description = stringResource(R.string.layout_settings_invisible_buttons_desc),
                    checked = invisibleButtons,
                    icon = Icons.Rounded.VisibilityOff,
                    onCheckedChange = { invisibleButtons = it },
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        // Color Wheel overlays
        val activeWheelTarget = activeColorPickerTarget
        if (activeWheelTarget != null) {
            val currentText = resolveColorOption(textColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
            val currentBorder = resolveColorOption(borderColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
            val currentBg = resolveColorOption(bgColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)
            val initialColor =
                when (activeWheelTarget) {
                    ColorPickerTarget.TEXT -> currentText
                    ColorPickerTarget.BORDER -> currentBorder
                    ColorPickerTarget.BG -> currentBg
                }
            ColorWheelPicker(
                initialColor = initialColor,
                title =
                    when (activeWheelTarget) {
                        ColorPickerTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
                        ColorPickerTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
                        ColorPickerTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
                    },
                showAlphaSlider = true,
                onColorSelected = { selectedColor ->
                    val customOpt = ColorOption.Custom(selectedColor.toArgb())
                    when (activeWheelTarget) {
                        ColorPickerTarget.TEXT -> textColorOption = customOpt
                        ColorPickerTarget.BORDER -> borderColorOption = customOpt
                        ColorPickerTarget.BG -> bgColorOption = customOpt
                    }
                    MacroPadSettings.addRecentColor(selectedColor.toArgb())
                    activeColorPickerTarget = null
                },
                onDismiss = { activeColorPickerTarget = null },
                preview = { liveColor ->
                    SwordsButtonPreview(
                        textColor = if (activeWheelTarget == ColorPickerTarget.TEXT) liveColor else currentText,
                        borderColor = if (activeWheelTarget == ColorPickerTarget.BORDER) liveColor else currentBorder,
                        bgColor = if (activeWheelTarget == ColorPickerTarget.BG) liveColor else currentBg,
                        size = LSE_PREVIEW_BUTTON_SIZE,
                    )
                },
            )
        }

        // Palette Overlay Dialogs
        val activePaletteTarget = activePaletteDialogTarget
        if (activePaletteTarget != null) {
            val defaultNeutralColor =
                when (activePaletteTarget) {
                    ColorPickerTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
                    ColorPickerTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
                    ColorPickerTarget.BG -> MP_AMBIENT_NEUTRAL_BG
                }
            val currentText = resolveColorOption(textColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
            val currentBorder = resolveColorOption(borderColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
            val currentBg = resolveColorOption(bgColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)
            QuickColorSelectionDialog(
                title =
                    when (activePaletteTarget) {
                        ColorPickerTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
                        ColorPickerTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
                        ColorPickerTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
                    },
                recentColors = recentColors,
                onSelected = { opt ->
                    when (activePaletteTarget) {
                        ColorPickerTarget.TEXT -> textColorOption = opt
                        ColorPickerTarget.BORDER -> borderColorOption = opt
                        ColorPickerTarget.BG -> bgColorOption = opt
                    }
                    if (opt is ColorOption.Custom) {
                        MacroPadSettings.addRecentColor(opt.argb)
                    }
                    activePaletteDialogTarget = null
                },
                onDismiss = { activePaletteDialogTarget = null },
                preview = { option ->
                    val resolved =
                        when (option) {
                            ColorOption.Neutral -> defaultNeutralColor
                            ColorOption.Accent -> globalAccentColor
                            is ColorOption.Custom -> Color(option.argb)
                        }
                    SwordsButtonPreview(
                        textColor = if (activePaletteTarget == ColorPickerTarget.TEXT) resolved else currentText,
                        borderColor = if (activePaletteTarget == ColorPickerTarget.BORDER) resolved else currentBorder,
                        bgColor = if (activePaletteTarget == ColorPickerTarget.BG) resolved else currentBg,
                        size = LSE_PREVIEW_BUTTON_SIZE,
                    )
                },
            )
        }

        LayoutSettingsHelpModal(
            visible = showHelpMenu,
            onDismiss = { showHelpMenu = false },
        )
    }
}

@Composable
private fun QuickColorSelectionDialog(
    title: String,
    recentColors: List<Int>,
    onSelected: (ColorOption) -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable (ColorOption) -> Unit,
) {
    val colors = LocalAppColors.current

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = 0.85f,
        cornerRadius = 12.dp,
        contentPadding = 16.dp,
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Neutral) }
                        .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(LSE_PREVIEW_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(ColorOption.Neutral)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.layout_settings_color_neutral),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Accent) }
                        .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(LSE_PREVIEW_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(ColorOption.Accent)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.layout_settings_color_accent),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.layout_settings_recent_colors),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        if (recentColors.isEmpty()) {
            Text(
                text = stringResource(R.string.layout_settings_no_recent_colors),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(LSE_RECENT_COLORS_GRID_HEIGHT),
            ) {
                items(recentColors) { argb ->
                    Box(
                        modifier =
                            Modifier
                                .size(LSE_PREVIEW_BUTTON_SIZE)
                                .clickable { onSelected(ColorOption.Custom(argb)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        preview(ColorOption.Custom(argb))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        }
    }
}

@Composable
private fun LayoutSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_layout_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_layout_settings_intro_no_bg))

        HelpSection(stringResource(R.string.help_layout_settings_sec_properties))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_name_title),
            description = stringResource(R.string.help_layout_settings_name_desc),
        )

        HelpSection(stringResource(R.string.help_layout_settings_sec_colors))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_colors_title),
            description = stringResource(R.string.help_layout_settings_colors_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_palette_title),
            description = stringResource(R.string.help_layout_settings_palette_desc),
        )
    }
}
