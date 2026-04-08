package com.stormpanda.megingiard.macropad

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Shape enums
// ─────────────────────────────────────────────────────────────────────────────

enum class PadShape { SQUARE, CIRCLE }  // retained for JSON back-compat; no longer used in UI

enum class ButtonShape { SQUARE, CIRCLE }

/**
 * Grid multiplier for a button: cols × rows relative to the base button unit.
 * Non-square buttons always render as rounded-rectangle regardless of ButtonShape.
 */
enum class ButtonSize(val cols: Int, val rows: Int) {
    SIZE_1X1(1, 1),
    SIZE_2X1(2, 1),
    SIZE_1X2(1, 2),
    SIZE_2X2(2, 2),
}

/**
 * Visual size of a trackpoint button.
 * The [multiplier] is applied to the base button unit (MP_BUTTON_UNIT_DP / ED_BUTTON_UNIT_DP)
 * to derive the rendered circle diameter.
 */
enum class TrackpointSize(val multiplier: Float) {
    SMALL(1.5f),
    MEDIUM(2.0f),
    LARGE(3.0f),
}

// ─────────────────────────────────────────────────────────────────────────────
// Mouse button enum — used by PadAction.MouseButton
// ─────────────────────────────────────────────────────────────────────────────

enum class MouseButton { LEFT, RIGHT, MIDDLE, MOUSE4, MOUSE5 }

// ─────────────────────────────────────────────────────────────────────────────
// Action — what happens when a button is pressed / held
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
sealed class PadAction {

    /** Injects a Linux keyboard keycode via keyinjector_arm64. */
    @Serializable
    @SerialName("keyboard_key")
    data class KeyboardKey(
        val keycode: Int,
        val label: String,
    ) : PadAction()

    /** Injects a Linux gamepad button event via gamepadinjector_arm64. */
    @Serializable
    @SerialName("gamepad_button")
    data class GamepadButton(
        val btnCode: Int,
        val label: String,
    ) : PadAction()

    /**
     * Injects a mouse button event via mouseinjector_arm64.
     * Replaces the legacy MouseLeftClick / MouseRightClick data objects.
     */
    @Serializable
    @SerialName("mouse_button")
    data class MouseButton(val button: com.stormpanda.megingiard.macropad.MouseButton) : PadAction()

    /**
     * A 1×2 button that translates vertical drag distance into scroll-wheel events.
     * The further the finger moves from the touch-down point, the faster it scrolls.
     * Always rendered with SIZE_1X2; the size is locked in the editor.
     */
    @Serializable
    @SerialName("scroll_wheel")
    data object ScrollWheel : PadAction()

    /**
     * Marks this element as a relative-mouse trackpoint area.
     * No key injection; drag deltas are forwarded to MouseInjector.moveMouse().
     * [size] controls the rendered circle diameter; defaults to MEDIUM for JSON back-compat
     * with old profiles that stored this as a bare `data object` with no body.
     */
    @Serializable
    @SerialName("trackpoint")
    data class TrackpointMove(
        val size: TrackpointSize = TrackpointSize.MEDIUM,
    ) : PadAction()

    /**
     * Executes a [Macro] from the global [MacroState] library when this button is pressed.
     * The macro is identified by [macroId] (UUID string). If the referenced macro has been
     * deleted, the button press is silently ignored.
     */
    @Serializable
    @SerialName("macro")
    data class Macro(val macroId: String) : PadAction()

    /**
     * Toggles the Ambient Peek mode. When active, all other MacroPad buttons are hidden
     * and blur/dim are set to zero, revealing the clear screen mirror behind the pad.
     * Tapping again restores the previous state.
     */
    @Serializable
    @SerialName("ambient_peek")
    data object AmbientPeek : PadAction()

    // ── Legacy: retained for JSON back-compat only ─────────────────────────
    // Old profiles saved these types before MouseButton was introduced.
    // They are deserialized normally; the app treats them as MouseButton(LEFT/RIGHT).

    @Suppress("unused")
    @Serializable
    @SerialName("mouse_left_click")
    data object MouseLeftClick : PadAction()

    @Suppress("unused")
    @Serializable
    @SerialName("mouse_right_click")
    data object MouseRightClick : PadAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// PadButton — a single interactable element on the pad
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id        Stable unique identifier (UUID string).
 * @param label     Text shown on the button face (and always in the editor list). Used even when an icon is set.
 * @param iconName  Optional Material Rounded icon name (e.g. `"Home"`, `"SportsEsports"`).
 *                  When set, the icon is displayed on the button face instead of [label].
 *                  The [label] remains visible in the editor list. Null means no icon — show label.
 * @param posX      Horizontal centre position, normalised [0.0, 1.0] relative to pad width.
 * @param posY      Vertical centre position, normalised [0.0, 1.0] relative to pad height.
 * @param buttonSize Grid multiplier (cols × rows). Non-square sizes always render as rounded rectangle.
 * @param buttonShape Visual shape — only honoured for SIZE_1X1; larger sizes always use rounded rectangle.
 * @param action    What this button injects when pressed / held.
 */
@Serializable
data class PadButton(
    val id: String,
    val label: String,
    val iconName: String? = null,
    val posX: Float,
    val posY: Float,
    val buttonSize: ButtonSize = ButtonSize.SIZE_1X1,
    val buttonShape: ButtonShape = ButtonShape.CIRCLE,
    val action: PadAction,
)

// ─────────────────────────────────────────────────────────────────────────────
// PadProfile — a complete layout that can be selected and stored
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id               Stable unique identifier (UUID string).
 * @param name             User-visible profile name.
 * @param buttons          All buttons placed on this pad.
 * @param enableKeyboard   Whether the keyboard virtual device (keyinjector_arm64) is active for this profile.
 * @param enableGamepad    Whether the gamepad virtual device (gamepadinjector_arm64) is active for this profile.
 * @param enableMouse      Whether the mouse virtual device (mouseinjector_arm64) is active for this profile.
 */
@Serializable
data class PadProfile(
    val id: String,
    val name: String,
    val buttons: List<PadButton> = emptyList(),
    val enableKeyboard: Boolean = true,
    val enableGamepad: Boolean = true,
    val enableMouse: Boolean = true,
    // Legacy fields — kept for JSON deserialization of existing profiles; ignored at runtime.
    // Profiles with hasTrackpoint=true are migrated to a TrackpointMove button in MacroPadState.loadFrom().
    @Suppress("unused") val hasTrackpoint: Boolean = false,
    @Suppress("unused") val trackpointPosX: Float = 0.5f,
    @Suppress("unused") val trackpointPosY: Float = 0.5f,
    @Suppress("unused") val trackpointSize: Float = 2f,
    @Suppress("unused") val padShape: PadShape = PadShape.SQUARE,
    @Suppress("unused") val padSizePercent: Int = 80,
)
