package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val TAG = "LayoutSettingsEditor"
private const val LSE_PULSE_DURATION_MS = 1400
private const val LSE_PULSE_ACCENT_ALPHA = 0.35f
private const val LSE_PULSE_SURFACE_ALPHA = 0.55f

@Composable
private fun describeColorOption(
    option: ColorOption,
    resolvedColor: Color,
): String =
    when (option) {
        is ColorOption.Neutral -> {
            stringResource(R.string.layout_settings_color_neutral)
        }

        is ColorOption.Accent -> {
            stringResource(R.string.layout_settings_color_accent)
        }

        is ColorOption.Custom -> {
            if (resolvedColor.alpha < 0.99f) {
                String.format("#%06X (%d%%)", 0xFFFFFF and resolvedColor.toArgb(), (resolvedColor.alpha * 100).roundToInt())
            } else {
                String.format("#%06X", 0xFFFFFF and resolvedColor.toArgb())
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayoutAppearanceSubPageContent(
    layout: PadLayout,
    savedLayout: PadLayout,
    existingNames: List<String>,
    accentColor: Color,
    onNameChange: (String) -> Unit,
    onInvisibleButtonsChange: (Boolean) -> Unit,
    onOpenColorSubMenu: (target: LayoutColorTarget) -> Unit,
    onDiscard: () -> Unit = {},
    onSaveColors: (textColor: ColorOption, borderColor: ColorOption, bgColor: ColorOption) -> Unit,
) {
    val colors = LocalAppColors.current
    var nameText by remember(savedLayout.id, savedLayout.name) { mutableStateOf(savedLayout.name) }

    LaunchedEffect(Unit) {
        snapshotFlow { layout }
            .collectLatest { inFlightLayout ->
                MacroPadState.setPreviewLayout(inFlightLayout)
            }
    }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate

    val hasColorChanges =
        layout.buttonTextColor != savedLayout.buttonTextColor ||
            layout.buttonBorderColor != savedLayout.buttonBorderColor ||
            layout.buttonBgColor != savedLayout.buttonBgColor

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasColorChanges,
            onSave = {
                onSaveColors(
                    layout.buttonTextColor,
                    layout.buttonBorderColor,
                    layout.buttonBgColor,
                )
            },
            onDiscard = onDiscard,
        )

    val pulseTransition = rememberInfiniteTransition(label = "appearanceSavePulse")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = LSE_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "appearanceSavePulseFraction",
    )
    val saveCardBgColor =
        if (hasColorChanges) {
            lerp(
                colors.surface.copy(alpha = LSE_PULSE_SURFACE_ALPHA),
                colors.accent.copy(alpha = LSE_PULSE_ACCENT_ALPHA),
                pulseFraction,
            )
        } else {
            null
        }

    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val currentResolvedText = resolveColorOption(layout.buttonTextColor, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentResolvedBorder = resolveColorOption(layout.buttonBorderColor, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentResolvedBg = resolveBgColorOption(layout.buttonBgColor, globalAccentColor)

    val savedResolvedText = resolveColorOption(savedLayout.buttonTextColor, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val savedResolvedBorder = resolveColorOption(savedLayout.buttonBorderColor, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val savedResolvedBg = resolveBgColorOption(savedLayout.buttonBgColor, globalAccentColor)

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

    GamepadSectionHeader(
        text = stringResource(R.string.layout_settings_colors_section_title),
        color = accentColor,
    )

    // ── Text Color Menu Item ────────────────────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_text),
        description = describeColorOption(layout.buttonTextColor, currentResolvedText),
        icon = Icons.Rounded.FormatColorText,
        actionLeadingContent = {
            SwordsButtonPreview(
                textColor = currentResolvedText,
                borderColor = Color.Transparent,
                bgColor = Color.Transparent,
                isIconOnly = true,
            )
        },
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.TEXT) },
    )

    // ── Border Color Menu Item ──────────────────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_border),
        description = describeColorOption(layout.buttonBorderColor, currentResolvedBorder),
        icon = Icons.Rounded.Palette,
        actionLeadingContent = {
            SwordsButtonPreview(
                textColor = Color.Transparent,
                borderColor = currentResolvedBorder,
                bgColor = Color.Transparent,
                isIconOnly = false,
            )
        },
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.BORDER) },
    )

    // ── Background / Fading Color Menu Item ─────────────────────
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_bg),
        description = describeColorOption(layout.buttonBgColor, currentResolvedBg),
        icon = Icons.Rounded.FormatColorFill,
        actionLeadingContent = {
            SwordsButtonPreview(
                textColor = Color.Transparent,
                borderColor = Color.Transparent,
                bgColor = currentResolvedBg,
                isIconOnly = false,
            )
        },
        actionText = stringResource(R.string.gamepad_action_edit),
        onClick = { onOpenColorSubMenu(LayoutColorTarget.BG) },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_visibility_behavior),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = savedLayout.invisibleButtons,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = onInvisibleButtonsChange,
    )

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    ColorPreviewInfoBox(
        title = stringResource(R.string.macropad_editor_color_preview_title),
        description = stringResource(R.string.macropad_editor_color_preview_desc),
        savedPreview = {
            SwordsButtonPreview(
                textColor = savedResolvedText,
                borderColor = savedResolvedBorder,
                bgColor = savedResolvedBg,
                isIconOnly = false,
            )
        },
        currentPreview = {
            SwordsButtonPreview(
                textColor = currentResolvedText,
                borderColor = currentResolvedBorder,
                bgColor = currentResolvedBg,
                isIconOnly = false,
            )
        },
    )

    // ── Save & Exit Action Row ───────────────────────────────────────────────
    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_save_button_colors_title),
        description = stringResource(R.string.macropad_editor_save_button_colors_desc),
        cardBgColor = saveCardBgColor,
        saveActionText = stringResource(R.string.gamepad_action_confirm),
        saveIcon = Icons.Rounded.Save,
        enabled = true,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayoutColorSubPageContent(
    layout: PadLayout,
    savedLayout: PadLayout?,
    target: LayoutColorTarget,
    accentColor: Color,
    onColorOptionChanged: (ColorOption) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, inFlightLayout: PadLayout) -> Unit,
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

    val currentColor =
        if (target == LayoutColorTarget.BG) {
            resolveBgColorOption(currentOption, globalAccentColor)
        } else {
            resolveColorOption(currentOption, globalAccentColor, defaultNeutralColor)
        }

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
            stringResource(R.string.gamepad_action_custom_color),
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
        actionText =
            if (isNeutralSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_select)
            },
        onClick = { onColorOptionChanged(ColorOption.Neutral) },
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
        actionText =
            if (isAccentSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_select)
            },
        onClick = { onColorOptionChanged(ColorOption.Accent) },
    )

    // Option 3: Custom Color (Color Wheel)
    GamepadActionCard(
        title = stringResource(R.string.gamepad_action_custom_color),
        description =
            if (isCustomSelected) {
                if (currentColor.alpha < 0.99f) {
                    String.format("#%06X (%d%%)", 0xFFFFFF and currentColor.toArgb(), (currentColor.alpha * 100).roundToInt())
                } else {
                    String.format("#%06X", 0xFFFFFF and currentColor.toArgb())
                }
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
        actionText =
            if (isCustomSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_choose)
            },
        onClick = {
            onOpenColorWheel(
                selectColorWheelTitle,
                colorWheelBreadcrumbs,
                currentColor,
                layout,
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewLayoutSubPageContent(
    existingNames: List<String>,
    accentColor: Color,
    onDiscard: () -> Unit = {},
    onCreate: (name: String, invisibleButtons: Boolean) -> Unit,
) {
    val defaultLayoutName = stringResource(R.string.macropad_editor_new_layout_default_name)
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

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = true,
            onSave = {
                if (isConfirmEnabled) {
                    onCreate(normalizedName, invisibleButtons)
                }
            },
            onDiscard = onDiscard,
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

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = invisibleButtons,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = { invisibleButtons = it },
    )

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_create_layout_title),
        description = stringResource(R.string.macropad_editor_create_layout_desc),
        saveActionText = stringResource(R.string.gamepad_action_create),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )
}
