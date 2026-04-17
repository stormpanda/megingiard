package com.stormpanda.megingiard.keyboard

/**
 * Linux keycode constants for use with [KeyInjector].
 *
 * Values match `linux/input-event-codes.h`. Only keycodes used by the
 * virtual keyboard are listed here.
 */
object LinuxKeycodes {
    // Control keys
    const val KEY_ESC         = 1
    const val KEY_BACKSPACE   = 14
    const val KEY_TAB         = 15
    const val KEY_ENTER       = 28
    const val KEY_LEFTCTRL    = 29
    const val KEY_LEFTSHIFT   = 42
    const val KEY_RIGHTSHIFT  = 54
    const val KEY_LEFTALT     = 56
    const val KEY_CAPSLOCK    = 58
    const val KEY_LEFTMETA    = 125
    const val KEY_RIGHTALT    = 100  // AltGr
    const val KEY_RIGHTCTRL   = 97
    const val KEY_FN          = 464  // Fn key (extended range, beyond standard 1–254)

    // F-keys
    const val KEY_F1          = 59
    const val KEY_F2          = 60
    const val KEY_F3          = 61
    const val KEY_F4          = 62
    const val KEY_F5          = 63
    const val KEY_F6          = 64
    const val KEY_F7          = 65
    const val KEY_F8          = 66
    const val KEY_F9          = 67
    const val KEY_F10         = 68
    const val KEY_F11         = 87
    const val KEY_F12         = 88

    // Number row
    const val KEY_GRAVE       = 41   // ` ~
    const val KEY_1           = 2
    const val KEY_2           = 3
    const val KEY_3           = 4
    const val KEY_4           = 5
    const val KEY_5           = 6
    const val KEY_6           = 7
    const val KEY_7           = 8
    const val KEY_8           = 9
    const val KEY_9           = 10
    const val KEY_0           = 11
    const val KEY_MINUS       = 12
    const val KEY_EQUAL       = 13

    // Top row (QWERTY positions)
    const val KEY_Q           = 16
    const val KEY_W           = 17
    const val KEY_E           = 18
    const val KEY_R           = 19
    const val KEY_T           = 20
    const val KEY_Y           = 21
    const val KEY_U           = 22
    const val KEY_I           = 23
    const val KEY_O           = 24
    const val KEY_P           = 25
    const val KEY_LEFTBRACE   = 26  // [
    const val KEY_RIGHTBRACE  = 27  // ]
    const val KEY_BACKSLASH   = 43

    // Home row
    const val KEY_A           = 30
    const val KEY_S           = 31
    const val KEY_D           = 32
    const val KEY_F           = 33
    const val KEY_G           = 34
    const val KEY_H           = 35
    const val KEY_J           = 36
    const val KEY_K           = 37
    const val KEY_L           = 38
    const val KEY_SEMICOLON   = 39
    const val KEY_APOSTROPHE  = 40

    // Bottom row
    const val KEY_Z           = 44
    const val KEY_X           = 45
    const val KEY_C           = 46
    const val KEY_V           = 47
    const val KEY_B           = 48
    const val KEY_N           = 49
    const val KEY_M           = 50
    const val KEY_COMMA       = 51
    const val KEY_DOT         = 52
    const val KEY_SLASH       = 53

    // Space
    const val KEY_SPACE       = 57

    // Navigation cluster
    const val KEY_UP          = 103
    const val KEY_LEFT        = 105
    const val KEY_RIGHT       = 106
    const val KEY_DOWN        = 108
    const val KEY_INSERT      = 110
    const val KEY_DELETE      = 111
    const val KEY_HOME        = 102
    const val KEY_END         = 107
    const val KEY_PAGEUP      = 104
    const val KEY_PAGEDOWN    = 109

    // Extra
    const val KEY_SYSRQ       = 99   // Print Screen
    const val KEY_102ND       = 86   // Key between Shift and Z on ISO keyboards (< >)
}
