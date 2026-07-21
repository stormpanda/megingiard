package com.stormpanda.megingiard.settings

enum class ThemeMode(
    val supportsCustomAccent: Boolean,
) {
    DARK(supportsCustomAccent = true),
    DARK_OLED(supportsCustomAccent = true),
    LIGHT(supportsCustomAccent = true),
    CYBERPUNK(supportsCustomAccent = false),
}
