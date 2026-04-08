package com.stormpanda.megingiard.macropad

/**
 * Registry for Material Symbol icon names used in the MacroPad feature.
 * Icons are rendered via [MaterialSymbol] using the bundled Material Symbols Rounded variable font.
 */
internal object MaterialIconRegistry {

    /**
     * Returns all icon names whose lowercase representation contains [query] (case-insensitive).
     * When [query] is blank the full [ALL_ROUNDED_ICON_NAMES] list is returned unchanged.
     */
    fun searchIcons(query: String): List<String> {
        if (query.isBlank()) return ALL_ROUNDED_ICON_NAMES
        val lower = query.lowercase()
        return ALL_ROUNDED_ICON_NAMES.filter { it.lowercase().contains(lower) }
    }
}
