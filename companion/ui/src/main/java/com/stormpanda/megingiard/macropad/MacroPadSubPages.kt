package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamePadGlyph
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardText
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

private const val TAG = "MacroPadSubPages"

internal enum class LayoutColorTarget {
    TEXT,
    BORDER,
    BG,
}

internal enum class ButtonColorTarget {
    TEXT,
    BORDER,
    BG,
}

internal sealed interface AppPickerTarget {
    data object NewProfile : AppPickerTarget

    data class EditProfile(
        val profileId: String,
    ) : AppPickerTarget

    data object EditButton : AppPickerTarget
}

internal sealed interface MacroPadSubPage {
    val parentSection: EditorSection

    data object NewProfile : MacroPadSubPage {
        override val parentSection = EditorSection.PROFILES
    }

    data class EditProfile(
        val profileId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.PROFILES
    }

    data class AppPicker(
        val target: AppPickerTarget,
    ) : MacroPadSubPage {
        override val parentSection: EditorSection
            get() = if (target is AppPickerTarget.EditButton) EditorSection.BUTTONS else EditorSection.PROFILES
    }

    data object ReorderProfiles : MacroPadSubPage {
        override val parentSection = EditorSection.PROFILES
    }

    data object QuickActions : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data object NewLayout : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class LayoutAppearance(
        val layoutId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class LayoutColor(
        val layoutId: String,
        val target: LayoutColorTarget,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class LayoutBackground(
        val layoutId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class BackgroundCrop(
        val layoutId: String,
        val initialScale: Float = 1f,
        val initialOffsetX: Float = 0f,
        val initialOffsetY: Float = 0f,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class SteamGridDbScrape(
        val layoutId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class LayoutTouchpad(
        val layoutId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data class CopyLayout(
        val layoutId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data object ReorderLayouts : MacroPadSubPage {
        override val parentSection = EditorSection.LAYOUTS
    }

    data object ChooseButtonType : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data object EditButtonPositions : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class EditButton(
        val button: PadButton?,
        val draftButton: PadButton? = null,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ButtonColor(
        val button: PadButton?,
        val draftButton: PadButton,
        val target: ButtonColorTarget,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseKeyboardKey(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseGamepadButton(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseMouseAction(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseMirrorAction(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseOverlayAction(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class ChooseLayoutAction(
        val button: PadButton?,
        val draftButton: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data object ChooseIcon : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class CopyButton(
        val button: PadButton,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.BUTTONS
    }

    data class MacroTimeline(
        val macroId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.MACROS
    }

    data class MacroStepEdit(
        val macroId: String,
        val stepIndex: Int?,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.MACROS
    }

    data class ReorderMacroSteps(
        val macroId: String,
    ) : MacroPadSubPage {
        override val parentSection = EditorSection.MACROS
    }

    data class ColorWheel(
        val title: String,
        val breadcrumbs: List<String>,
        val initialColor: Color,
        val section: EditorSection,
        val showAlphaSlider: Boolean = true,
        val onSave: (Color) -> Unit,
    ) : MacroPadSubPage {
        override val parentSection = section
    }
}

@Composable
internal fun GamepadSubPageHeader(
    breadcrumbs: List<String>,
    accentColor: Color,
) {
    GamepadSectionHeader(
        text = breadcrumbs.joinToString("  ›  ") { it.uppercase() },
        color = accentColor,
    )
}

@Composable
internal fun GamepadSubPageHeader(
    parentTitle: String,
    subPageTitle: String,
    accentColor: Color,
) {
    GamepadSubPageHeader(
        breadcrumbs = listOf(parentTitle, subPageTitle),
        accentColor = accentColor,
    )
}

@Composable
internal fun ColorWheelSubPageContent(
    title: String,
    breadcrumbs: List<String>,
    initialColor: Color,
    accentColor: Color,
    showAlphaSlider: Boolean = true,
    onSaveColor: (Color) -> Unit,
) {
    val initHsv =
        remember(initialColor) {
            FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
        }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[0]) }
    var sat by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[1]) }
    var bri by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[2]) }
    var alpha by rememberSaveable(initialColor) {
        mutableFloatStateOf(if (showAlphaSlider) initialColor.alpha.coerceIn(0.1f, 1f) else 1f)
    }

    val workingColor by remember {
        derivedStateOf {
            val base = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
            if (showAlphaSlider) base.copy(alpha = alpha) else base
        }
    }

    val hueGradient =
        remember {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000),
                    ),
            )
        }

    val saturationGradient =
        remember(hue, bri) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, bri))),
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, bri))),
                    ),
            )
        }

    val brightnessGradient =
        remember(hue, sat) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color.Black,
                        Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f))),
                    ),
            )
        }

    val opacityGradient =
        remember(hue, sat, bri) {
            val baseRgb = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
            Brush.horizontalGradient(
                colors =
                    listOf(
                        baseRgb.copy(alpha = 0.1f),
                        baseRgb.copy(alpha = 1f),
                    ),
            )
        }

    if (breadcrumbs.isNotEmpty()) {
        GamepadSubPageHeader(
            breadcrumbs = breadcrumbs,
            accentColor = accentColor,
        )
    }

    val hex = String.format("#%06X", 0xFFFFFF and workingColor.toArgb())

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_hue),
        description = stringResource(R.string.settings_color_hue_desc),
        value = hue,
        valueRange = 0f..360f,
        onValueChange = { hue = it },
        valueLabel = "${hue.roundToInt()}°",
        step = 5f,
        trackBrush = hueGradient,
        thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_saturation),
        description = stringResource(R.string.settings_color_saturation_desc),
        value = sat,
        valueRange = 0f..1f,
        onValueChange = { sat = it },
        valueLabel = "${(sat * 100).roundToInt()}%",
        step = 0.02f,
        trackBrush = saturationGradient,
        thumbColor = workingColor,
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_brightness),
        description = stringResource(R.string.settings_color_brightness_desc),
        value = bri,
        valueRange = 0f..1f,
        onValueChange = { bri = it },
        valueLabel = "${(bri * 100).roundToInt()}%",
        step = 0.02f,
        trackBrush = brightnessGradient,
        thumbColor = workingColor,
    )

    if (showAlphaSlider) {
        GamepadSliderCard(
            title = stringResource(R.string.layout_settings_color_opacity),
            description = stringResource(R.string.settings_color_opacity_desc),
            value = alpha,
            valueRange = 0.1f..1f,
            onValueChange = { alpha = it },
            valueLabel = "${(alpha * 100).roundToInt()}%",
            step = 0.02f,
            trackBrush = opacityGradient,
            thumbColor = workingColor,
            icon = Icons.Rounded.Opacity,
        )
    }

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadActionCard(
        title = title,
        description = hex,
        icon = Icons.Rounded.Save,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = workingColor,
                isSelected = true,
            )
        },
        actionText = stringResource(R.string.gamepad_action_save),
        onClick = { onSaveColor(workingColor) },
    )
}
