package com.stormpanda.megingiard.settings

enum class ThemeMode(
    val supportsCustomAccent: Boolean,
) {
    DARK(supportsCustomAccent = true),
    DARK_OLED(supportsCustomAccent = true),
    MEGINGIARD(supportsCustomAccent = false),
    MJOLNIR(supportsCustomAccent = false),
    VALHALLA(supportsCustomAccent = false),
    AURORA(supportsCustomAccent = false),
    RETRO_PHOSPHOR(supportsCustomAccent = false),
    ROYAL_ASGARD(supportsCustomAccent = false),
}
