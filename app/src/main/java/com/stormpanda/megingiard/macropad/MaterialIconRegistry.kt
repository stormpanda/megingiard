package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves [Icons.Rounded] icon names (e.g. `"Home"`, `"SportsEsports"`) to [ImageVector] instances
 * via reflection. This is safe because R8/minification is disabled (`isMinifyEnabled = false`),
 * so all icon *Kt class objects remain in the APK.
 *
 * Resolved icons are cached in [cache] so each name is reflected at most once per app session.
 */
internal object MaterialIconRegistry {

    private val cache = mutableMapOf<String, ImageVector?>()

    /**
     * Returns the [ImageVector] for [name] from [Icons.Rounded], or `null` if the name does not
     * correspond to a known icon. The result is cached after the first call.
     */
    fun resolve(name: String): ImageVector? = cache.getOrPut(name) {
        try {
            val clazz = Class.forName("androidx.compose.material.icons.rounded.${name}Kt")
            val method = clazz.getDeclaredMethod("get${name}", Icons.Rounded::class.java)
            method.isAccessible = true
            method.invoke(null, Icons.Rounded) as? ImageVector
        } catch (_: Exception) {
            null
        }
    }

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
