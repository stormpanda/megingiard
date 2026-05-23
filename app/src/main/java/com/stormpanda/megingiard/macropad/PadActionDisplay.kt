package com.stormpanda.megingiard.macropad

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.macropad.displayShortLabel
import com.stormpanda.megingiard.settings.MacroPadSettings

private const val TAG = "PadActionDisplay"

internal enum class ActionGroup {
    KEYBOARD,
    GAMEPAD,
    MOUSE,
    MACRO,
    LAYOUT,
    MIRROR,
    OTHER,
}

internal fun ActionGroup.labelResId(): Int = when (this) {
    ActionGroup.KEYBOARD -> R.string.macropad_action_group_keyboard
    ActionGroup.GAMEPAD  -> R.string.macropad_action_group_gamepad
    ActionGroup.MOUSE    -> R.string.macropad_action_group_mouse
    ActionGroup.MACRO    -> R.string.macropad_action_group_macro
    ActionGroup.LAYOUT   -> R.string.macropad_action_group_layout
    ActionGroup.MIRROR   -> R.string.macropad_action_group_mirror
    ActionGroup.OTHER    -> R.string.macropad_action_group_other
}

internal fun ActionGroup.actions(): List<ActionCategory> = when (this) {
    ActionGroup.KEYBOARD -> listOf(ActionCategory.KEYBOARD_KEY)
    ActionGroup.GAMEPAD  -> listOf(ActionCategory.GAMEPAD_BUTTON)
    ActionGroup.MOUSE    -> listOf(
        ActionCategory.MOUSE_BUTTON,
        ActionCategory.SCROLL_WHEEL,
        ActionCategory.TRACKPOINT,
    )
    ActionGroup.MACRO    -> listOf(ActionCategory.MACRO)
    ActionGroup.LAYOUT   -> listOf(
        ActionCategory.LAYOUT_NEXT,
        ActionCategory.LAYOUT_PREVIOUS,
        ActionCategory.PROFILE_SWITCHER,
    )
    ActionGroup.MIRROR   -> listOf(
        ActionCategory.MIRROR_PLAY_STOP,
        ActionCategory.MIRROR_FREEZE,
        ActionCategory.MIRROR_VIEWPORT_EDIT,
        ActionCategory.MIRROR_TOUCH_PROJECTION,
        ActionCategory.BACKGROUND_PEEK,
    )
    ActionGroup.OTHER    -> listOf(
        ActionCategory.FULLSCREEN_MOUSE,
        ActionCategory.FULLSCREEN_KEYBOARD,
    )
}

internal enum class ActionCategory {
    KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_BUTTON, SCROLL_WHEEL, TRACKPOINT, MACRO, BACKGROUND_PEEK,
    LAYOUT_NEXT, LAYOUT_PREVIOUS, PROFILE_SWITCHER,
    MIRROR_PLAY_STOP, MIRROR_FREEZE, MIRROR_VIEWPORT_EDIT, MIRROR_TOUCH_PROJECTION,
    FULLSCREEN_MOUSE, FULLSCREEN_KEYBOARD,
}

internal fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY          -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON        -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_BUTTON          -> R.string.macropad_action_mouse_button
    ActionCategory.SCROLL_WHEEL          -> R.string.macropad_action_scroll_wheel
    ActionCategory.TRACKPOINT            -> R.string.macropad_action_trackpoint
    ActionCategory.MACRO                 -> R.string.macropad_action_macro
    ActionCategory.BACKGROUND_PEEK       -> R.string.macropad_action_ambient_peek
    ActionCategory.LAYOUT_NEXT           -> R.string.macropad_action_layout_next
    ActionCategory.LAYOUT_PREVIOUS       -> R.string.macropad_action_layout_previous
    ActionCategory.PROFILE_SWITCHER      -> R.string.macropad_action_profile_switcher
    ActionCategory.MIRROR_PLAY_STOP      -> R.string.macropad_action_mirror_play_stop
    ActionCategory.MIRROR_FREEZE         -> R.string.macropad_action_mirror_freeze
    ActionCategory.MIRROR_VIEWPORT_EDIT  -> R.string.macropad_action_mirror_viewport_edit
    ActionCategory.MIRROR_TOUCH_PROJECTION -> R.string.macropad_action_mirror_touch_projection
    ActionCategory.FULLSCREEN_MOUSE      -> R.string.macropad_action_fullscreen_mouse
    ActionCategory.FULLSCREEN_KEYBOARD   -> R.string.macropad_action_fullscreen_keyboard
}

internal fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY          -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON        -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_BUTTON          -> PadAction.MouseButton(MouseButton.LEFT)
    ActionCategory.SCROLL_WHEEL          -> PadAction.ScrollWheel
    ActionCategory.TRACKPOINT            -> PadAction.TrackpointMove()
    ActionCategory.MACRO                 -> PadAction.Macro(MacroPadState.activeProfile.value?.macros?.firstOrNull()?.id ?: "")
    ActionCategory.BACKGROUND_PEEK       -> PadAction.BackgroundPeek
    ActionCategory.LAYOUT_NEXT           -> PadAction.LayoutNext
    ActionCategory.LAYOUT_PREVIOUS       -> PadAction.LayoutPrevious
    ActionCategory.PROFILE_SWITCHER      -> PadAction.ProfileSwitcher
    ActionCategory.MIRROR_PLAY_STOP      -> PadAction.MirrorPlayStop
    ActionCategory.MIRROR_FREEZE         -> PadAction.MirrorFreeze
    ActionCategory.MIRROR_VIEWPORT_EDIT  -> PadAction.MirrorViewportEdit
    ActionCategory.MIRROR_TOUCH_PROJECTION -> PadAction.MirrorTouchProjection
    ActionCategory.FULLSCREEN_MOUSE      -> PadAction.FullScreenMouse()
    ActionCategory.FULLSCREEN_KEYBOARD   -> PadAction.FullScreenKeyboard()
}

internal fun ActionCategory.group(): ActionGroup = when (this) {
    ActionCategory.KEYBOARD_KEY           -> ActionGroup.KEYBOARD
    ActionCategory.GAMEPAD_BUTTON         -> ActionGroup.GAMEPAD
    ActionCategory.MOUSE_BUTTON,
    ActionCategory.SCROLL_WHEEL,
    ActionCategory.TRACKPOINT             -> ActionGroup.MOUSE
    ActionCategory.MACRO                  -> ActionGroup.MACRO
    ActionCategory.LAYOUT_NEXT,
    ActionCategory.LAYOUT_PREVIOUS,
    ActionCategory.PROFILE_SWITCHER       -> ActionGroup.LAYOUT
    ActionCategory.MIRROR_PLAY_STOP,
    ActionCategory.MIRROR_FREEZE,
    ActionCategory.MIRROR_VIEWPORT_EDIT,
    ActionCategory.MIRROR_TOUCH_PROJECTION,
    ActionCategory.BACKGROUND_PEEK        -> ActionGroup.MIRROR
    ActionCategory.FULLSCREEN_MOUSE,
    ActionCategory.FULLSCREEN_KEYBOARD    -> ActionGroup.OTHER
}

internal fun PadAction.categoryResId(): Int = when (this) {
    is PadAction.KeyboardKey        -> R.string.macropad_action_keyboard_key
    is PadAction.GamepadButton      -> R.string.macropad_action_gamepad_button
    is PadAction.MouseButton        -> R.string.macropad_action_mouse_button
    is PadAction.ScrollWheel        -> R.string.macropad_action_scroll_wheel
    is PadAction.TrackpointMove     -> R.string.macropad_action_trackpoint
    is PadAction.Macro              -> R.string.macropad_action_macro
    is PadAction.BackgroundPeek     -> R.string.macropad_action_ambient_peek
    is PadAction.LayoutNext         -> R.string.macropad_action_layout_next
    is PadAction.LayoutPrevious     -> R.string.macropad_action_layout_previous
    is PadAction.ProfileSwitcher    -> R.string.macropad_action_profile_switcher
    is PadAction.MirrorPlayStop     -> R.string.macropad_action_mirror_play_stop
    is PadAction.MirrorFreeze       -> R.string.macropad_action_mirror_freeze
    is PadAction.MirrorViewportEdit -> R.string.macropad_action_mirror_viewport_edit
    is PadAction.MirrorTouchProjection -> R.string.macropad_action_mirror_touch_projection
    is PadAction.FullScreenMouse    -> R.string.macropad_action_fullscreen_mouse
    is PadAction.FullScreenKeyboard -> R.string.macropad_action_fullscreen_keyboard
}

internal fun PadAction.toCategory(): ActionCategory = when (this) {
    is PadAction.KeyboardKey           -> ActionCategory.KEYBOARD_KEY
    is PadAction.GamepadButton         -> ActionCategory.GAMEPAD_BUTTON
    is PadAction.MouseButton           -> ActionCategory.MOUSE_BUTTON
    is PadAction.ScrollWheel           -> ActionCategory.SCROLL_WHEEL
    is PadAction.TrackpointMove        -> ActionCategory.TRACKPOINT
    is PadAction.Macro                 -> ActionCategory.MACRO
    is PadAction.BackgroundPeek        -> ActionCategory.BACKGROUND_PEEK
    is PadAction.LayoutNext            -> ActionCategory.LAYOUT_NEXT
    is PadAction.LayoutPrevious        -> ActionCategory.LAYOUT_PREVIOUS
    is PadAction.ProfileSwitcher       -> ActionCategory.PROFILE_SWITCHER
    is PadAction.MirrorPlayStop        -> ActionCategory.MIRROR_PLAY_STOP
    is PadAction.MirrorFreeze          -> ActionCategory.MIRROR_FREEZE
    is PadAction.MirrorViewportEdit    -> ActionCategory.MIRROR_VIEWPORT_EDIT
    is PadAction.MirrorTouchProjection -> ActionCategory.MIRROR_TOUCH_PROJECTION
    is PadAction.FullScreenMouse       -> ActionCategory.FULLSCREEN_MOUSE
    is PadAction.FullScreenKeyboard    -> ActionCategory.FULLSCREEN_KEYBOARD
}

internal fun ActionCategory.isEnabled(
    enableKeyboard: Boolean,
    enableGamepad: Boolean,
    enableMouse: Boolean,
    hasMacros: Boolean,
): Boolean = when (this) {
    ActionCategory.KEYBOARD_KEY           -> enableKeyboard
    ActionCategory.GAMEPAD_BUTTON         -> enableGamepad
    ActionCategory.MOUSE_BUTTON,
    ActionCategory.SCROLL_WHEEL,
    ActionCategory.TRACKPOINT             -> enableMouse
    ActionCategory.MACRO                  -> hasMacros
    ActionCategory.BACKGROUND_PEEK,
    ActionCategory.LAYOUT_NEXT,
    ActionCategory.LAYOUT_PREVIOUS,
    ActionCategory.PROFILE_SWITCHER,
    ActionCategory.MIRROR_PLAY_STOP,
    ActionCategory.MIRROR_FREEZE,
    ActionCategory.MIRROR_VIEWPORT_EDIT,
    ActionCategory.MIRROR_TOUCH_PROJECTION -> true
    ActionCategory.FULLSCREEN_MOUSE       -> enableMouse
    ActionCategory.FULLSCREEN_KEYBOARD    -> enableKeyboard
}

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
    return when (this) {
        is PadAction.KeyboardKey -> {
            val modLabel = if (modifiers.isEmpty()) label else {
                val modNames = modifiers.mapNotNull { code ->
                    MODIFIER_PRESETS.firstOrNull { it.first == code }?.second
                }.joinToString("+")
                "$modNames+$label"
            }
            context.getString(R.string.macropad_display_keyboard_key, modLabel)
        }
        is PadAction.GamepadButton -> {
            val primaryLabel = GamepadKeycodes.PRESETS
                .firstOrNull { it.code == btnCode }
                ?.displayShortLabel(swapFaceButtons)
                ?: label
            val comboLabel = if (extraBtnCodes.isEmpty()) primaryLabel else {
                val extraNames = extraBtnCodes.mapNotNull { code ->
                    GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.displayShortLabel(swapFaceButtons)
                }.joinToString("+")
                "$primaryLabel+$extraNames"
            }
            context.getString(R.string.macropad_display_gamepad_button, comboLabel)
        }
        is PadAction.MouseButton     -> context.getString(R.string.macropad_display_mouse_button, button.displayLabel())
        is PadAction.ScrollWheel     -> context.getString(R.string.macropad_display_scroll_wheel)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
        is PadAction.Macro           -> {
            val macroName = MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == macroId }?.name ?: macroId
            context.getString(R.string.macropad_display_macro, macroName)
        }
        is PadAction.BackgroundPeek     -> context.getString(R.string.macropad_action_ambient_peek)
        is PadAction.LayoutNext         -> context.getString(R.string.macropad_action_layout_next)
        is PadAction.LayoutPrevious     -> context.getString(R.string.macropad_action_layout_previous)
        is PadAction.ProfileSwitcher    -> context.getString(R.string.macropad_action_profile_switcher)
        is PadAction.MirrorPlayStop     -> context.getString(R.string.macropad_action_mirror_play_stop)
        is PadAction.MirrorFreeze       -> context.getString(R.string.macropad_action_mirror_freeze)
        is PadAction.MirrorViewportEdit -> context.getString(R.string.macropad_action_mirror_viewport_edit)
        is PadAction.MirrorTouchProjection -> context.getString(R.string.macropad_action_mirror_touch_projection)
        is PadAction.FullScreenMouse    -> context.getString(R.string.macropad_action_fullscreen_mouse)
        is PadAction.FullScreenKeyboard -> context.getString(R.string.macropad_action_fullscreen_keyboard)
    }
}

@Composable
internal fun ButtonSize.displayLabel(): String = when (this) {
    ButtonSize.SIZE_1X1 -> stringResource(R.string.macropad_button_size_1x1)
    ButtonSize.SIZE_2X1 -> stringResource(R.string.macropad_button_size_2x1)
    ButtonSize.SIZE_1X2 -> stringResource(R.string.macropad_button_size_1x2)
    ButtonSize.SIZE_2X2 -> stringResource(R.string.macropad_button_size_2x2)
}

internal fun MouseButton.displayLabel(): String = when (this) {
    MouseButton.LEFT   -> "Left"
    MouseButton.RIGHT  -> "Right"
    MouseButton.MIDDLE -> "Middle"
    MouseButton.MOUSE4 -> "Mouse 4"
    MouseButton.MOUSE5 -> "Mouse 5"
}

internal fun gamepadCodeDisplayShortLabel(code: Int, swapFaceButtons: Boolean): String {
    return when {
        swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B"
        swapFaceButtons && code == GamepadKeycodes.BTN_EAST  -> "A"
        swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X"
        swapFaceButtons && code == GamepadKeycodes.BTN_WEST  -> "Y"
        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.shortLabel ?: code.toString()
    }
}

/**
 * Non-Composable variant of [gamepadCodeDisplayLabel] for use in non-Composable contexts
 * (e.g. inside `map` / `mapNotNull` lambdas). Uses [Context.getString] instead of `stringResource`.
 */
internal fun gamepadCodeDisplayLabel(code: Int, swapFaceButtons: Boolean, context: Context): String {
    val primary = gamepadCodeDisplayShortLabel(code, swapFaceButtons)
    return when (code) {
        GamepadKeycodes.BTN_SOUTH -> context.getString(
            R.string.macropad_gamepad_face_label_template,
            primary,
            context.getString(R.string.macropad_gamepad_symbol_cross),
            context.getString(R.string.macropad_gamepad_position_south),
        )
        GamepadKeycodes.BTN_EAST -> context.getString(
            R.string.macropad_gamepad_face_label_template,
            primary,
            context.getString(R.string.macropad_gamepad_symbol_circle),
            context.getString(R.string.macropad_gamepad_position_east),
        )
        GamepadKeycodes.BTN_NORTH -> context.getString(
            R.string.macropad_gamepad_face_label_template,
            primary,
            context.getString(R.string.macropad_gamepad_symbol_triangle),
            context.getString(R.string.macropad_gamepad_position_north),
        )
        GamepadKeycodes.BTN_WEST -> context.getString(
            R.string.macropad_gamepad_face_label_template,
            primary,
            context.getString(R.string.macropad_gamepad_symbol_square),
            context.getString(R.string.macropad_gamepad_position_west),
        )
        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: code.toString()
    }
}

@Composable
internal fun gamepadCodeDisplayLabel(code: Int, swapFaceButtons: Boolean): String {
    val primary = gamepadCodeDisplayShortLabel(code, swapFaceButtons)
    return when (code) {
        GamepadKeycodes.BTN_SOUTH -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_cross),
            stringResource(R.string.macropad_gamepad_position_south),
        )

        GamepadKeycodes.BTN_EAST -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_circle),
            stringResource(R.string.macropad_gamepad_position_east),
        )

        GamepadKeycodes.BTN_NORTH -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_triangle),
            stringResource(R.string.macropad_gamepad_position_north),
        )

        GamepadKeycodes.BTN_WEST -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_square),
            stringResource(R.string.macropad_gamepad_position_west),
        )

        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: code.toString()
    }
}

@Composable
internal fun GamepadKeycodes.GamepadButtonPreset.localizedDisplayLabel(swapFaceButtons: Boolean): String {
    return gamepadCodeDisplayLabel(code, swapFaceButtons)
}
