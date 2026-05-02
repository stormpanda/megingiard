package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.keyboard.KbLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Shape enums
// ─────────────────────────────────────────────────────────────────────────────

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
// Vignette shape — used by PadLayout ambient settings
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
enum class VignetteShape { RADIAL, LETTERBOX, PILLARBOX, TOP, BOTTOM, LEFT, RIGHT }

// ─────────────────────────────────────────────────────────────────────────────
// Haptic feedback strength — used by PadButton
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Haptic feedback intensity for a MacroPad button.
 *
 * - [OFF]    — no vibration.
 * - [LIGHT]  — minimal detectable tick (5 ms, amplitude 1).
 * - [MEDIUM] — slightly stronger tick (7 ms, amplitude 10).
 * - [STRONG] — most prominent tick (9 ms, amplitude 25).
 *
 * For [PadAction.TrackpointMove] the vibration repeats on every move event
 * while the finger is dragging (continuous haptic, rate-limited to ≈60 Hz).
 * For [PadAction.ScrollWheel] the vibration fires once per discrete scroll unit.
 * For all other action types the vibration fires once on button-down.
 */
@Serializable
enum class HapticStrength { OFF, LIGHT, MEDIUM, STRONG }

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
        /**
         * Optional modifier keycodes pressed simultaneously with [keycode].
         * Maximum 2 entries. Modifiers are pressed before the base key (down)
         * and released after it (up), in reverse order.
         * Keycodes must be in range 1–464 (see [LinuxKeycodes]).
         */
        val modifiers: List<Int> = emptyList(),
    ) : PadAction()

    /** Injects a Linux gamepad button event via gamepadinjector_arm64. */
    @Serializable
    @SerialName("gamepad_button")
    data class GamepadButton(
        val btnCode: Int,
        val label: String,
        /**
         * Optional extra button codes pressed simultaneously with [btnCode].
         * Maximum 3 entries. Extra buttons are pressed after the primary (down)
         * and released before it (up), in reverse order.
         */
        val extraBtnCodes: List<Int> = emptyList(),
    ) : PadAction()

    /**
     * Injects a mouse button event via mouseinjector_arm64.
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
     * [size] controls the rendered circle diameter.
     */
    @Serializable
    @SerialName("trackpoint")
    data class TrackpointMove(
        val size: TrackpointSize = TrackpointSize.MEDIUM,
    ) : PadAction()

    /**
     * Executes a [Macro] from the active [PadProfile] when this button is pressed.
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

    // ── Screen Mirroring ───────────────────────────────────────────────────

    /** Toggles screen mirroring on/off (starts/stops MediaProjection capture). */
    @Serializable
    @SerialName("mirror_play_stop")
    data object MirrorPlayStop : PadAction()

    /** Toggles freeze frame (captures current mirror frame as a static bitmap). */
    @Serializable
    @SerialName("mirror_freeze")
    data object MirrorFreeze : PadAction()

    /** Activates the viewport-edit overlay (fullscreen pan/zoom on the mirror image). */
    @Serializable
    @SerialName("mirror_viewport_edit")
    data object MirrorViewportEdit : PadAction()

    /** Toggles touch projection (forwards touch events to the primary display). */
    @Serializable
    @SerialName("mirror_touch_projection")
    data object MirrorTouchProjection : PadAction()

    // ── Profile / Navigation ──────────────────────────────────────────────

    /** Switches to the next enabled layout within the active profile. */
    @Serializable
    @SerialName("layout_next")
    data object LayoutNext : PadAction()

    /** Switches to the previous enabled layout within the active profile. */
    @Serializable
    @SerialName("layout_previous")
    data object LayoutPrevious : PadAction()

    /** Opens the profile-switcher dialog to select a different profile. */
    @Serializable
    @SerialName("profile_switcher")
    data object ProfileSwitcher : PadAction()

    // ── Special ────────────────────────────────────────────────────────────

    /** Opens the fullscreen relative-mouse overlay. */
    @Serializable
    @SerialName("full_screen_mouse")
    data class FullScreenMouse(
        val sensitivity: Float = 1.0f,
    ) : PadAction()

    /** Opens the fullscreen keyboard overlay with the specified layout. */
    @Serializable
    @SerialName("full_screen_keyboard")
    data class FullScreenKeyboard(
        val layout: KbLayout = KbLayout.QWERTZ,
    ) : PadAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// PadAction defaults — pre-fill helpers for new buttons
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the Material Symbols Rounded icon name (snake_case ligature string)
 * to use as a default icon for this action type when a new button is created.
 * Returns `null` for action types that have no meaningful icon default (e.g.
 * [PadAction.KeyboardKey], [PadAction.GamepadButton], [PadAction.MouseButton]).
 */
fun PadAction.defaultIconName(): String? = when (this) {
    is PadAction.LayoutNext            -> "arrow_forward"
    is PadAction.LayoutPrevious        -> "arrow_back"
    is PadAction.ProfileSwitcher       -> "swap_horiz"
    is PadAction.MirrorPlayStop        -> "cast"
    is PadAction.MirrorFreeze          -> "pause_circle"
    is PadAction.MirrorViewportEdit    -> "crop_free"
    is PadAction.MirrorTouchProjection -> "touch_app"
    is PadAction.FullScreenMouse       -> "mouse"
    is PadAction.FullScreenKeyboard    -> "keyboard"
    is PadAction.Macro                 -> "smart_button"
    else                               -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// PadButton — a single interactable element on the pad
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id        Stable unique identifier (UUID string).
 * @param label     Text shown on the button face (and always in the editor list). Used even when an icon is set.
 * @param iconName  Optional Material Rounded icon ligature name in snake_case
 *                  (e.g. `"home"`, `"sports_esports"`).
 *                  When set, the icon is displayed on the button face instead of [label].
 *                  The [label] remains visible in the editor list. Null means no icon — show label.
 * @param iconFilled Whether the icon is rendered filled (`true`, default) or outline (`false`).
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
    val iconFilled: Boolean = true,
    val posX: Float,
    val posY: Float,
    val buttonSize: ButtonSize = ButtonSize.SIZE_1X1,
    val buttonShape: ButtonShape = ButtonShape.CIRCLE,
    val action: PadAction,
    val hapticStrength: HapticStrength = HapticStrength.OFF,
)

// ─────────────────────────────────────────────────────────────────────────────
// PadLayout — a single button arrangement within a profile
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A named button arrangement within a [PadProfile]. Each profile can contain
 * multiple layouts; the user switches between them at runtime.
 *
 * @param id                          Stable unique identifier (UUID string).
 * @param name                        User-visible layout name.
 * @param enabled                     Whether this layout participates in next/previous navigation.
 * @param buttons                     All buttons placed on this layout.
 * @param ambientDim                  Dim overlay alpha [0.0, 0.9] when screen mirror is active.
 * @param ambientVignetteEnabled      Whether the vignette effect is active.
 * @param ambientVignetteShape        Vignette shape (RADIAL, LETTERBOX, PILLARBOX, TOP, BOTTOM, LEFT, RIGHT).
 * @param ambientVignetteVisibleArea  Vignette inner transparent zone size [0.0, 1.0].
 * @param ambientVignetteTransition   Vignette gradient softness [0.0, 1.0].
 * @param ambientVignetteOpacity      Vignette alpha [0.0, 1.0].
 * @param ambientVignetteColor        Vignette color (ARGB int).
 * @param mirrorSavedScale            Persisted mirror zoom level.
 * @param mirrorSavedOffsetX          Persisted mirror pan X offset.
 * @param mirrorSavedOffsetY          Persisted mirror pan Y offset.
 */
@Serializable
data class PadLayout(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val buttons: List<PadButton> = emptyList(),
    val ambientDim: Float = 0f,
    val ambientVignetteEnabled: Boolean = false,
    val ambientVignetteShape: VignetteShape = VignetteShape.RADIAL,
    val ambientVignetteVisibleArea: Float = 0.7f,
    val ambientVignetteTransition: Float = 0.5f,
    val ambientVignetteOpacity: Float = 0.6f,
    val ambientVignetteColor: Int = 0xFF000000.toInt(),
    val mirrorSavedScale: Float = 1f,
    val mirrorSavedOffsetX: Float = 0f,
    val mirrorSavedOffsetY: Float = 0f,
)

// ─────────────────────────────────────────────────────────────────────────────
// PadProfile — a named collection of layouts, macros, and device settings
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id               Stable unique identifier (UUID string).
 * @param name             User-visible profile name.
 * @param layouts          All layouts in this profile (at least one).
 * @param activeLayoutId   ID of the currently displayed layout. Null means first layout.
 * @param macros           Macros belonging to this profile (flat list, no folders).
 * @param enableKeyboard   Whether the keyboard virtual device is active (auto-computed).
 * @param enableGamepad    Whether the gamepad virtual device is active (auto-computed).
 * @param enableMouse      Whether the mouse virtual device is active (auto-computed).
 * @param isDefault        Whether this is a restorable default profile.
 */
@Serializable
data class PadProfile(
    val id: String,
    val name: String,
    val layouts: List<PadLayout> = emptyList(),
    val activeLayoutId: String? = null,
    val macros: List<Macro> = emptyList(),
    val enableKeyboard: Boolean = false,
    val enableGamepad: Boolean = false,
    val enableMouse: Boolean = false,
    val isDefault: Boolean = false,
)
