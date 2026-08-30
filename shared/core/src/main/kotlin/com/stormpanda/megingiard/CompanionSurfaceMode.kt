package com.stormpanda.megingiard

/**
 * Represents the active base input/display surface on the companion (secondary/bottom) display.
 *
 * This state is completely independent from any primary (top/modal) overlays or dialogs.
 */
enum class CompanionSurfaceMode {
    MACROPAD,
    KEYBOARD,
    TOUCHPAD,
    VIEWPORT_EDIT,
}
