package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsKeysTest {
    @Test
    fun `every Preference Key in SettingsKeys file is explicitly categorized`() {
        val clazz = Class.forName("com.stormpanda.megingiard.settings.SettingsKeysKt")

        // Inspect both getter methods and static fields to discover all top-level Preferences.Key properties
        val keysFromMethods =
            clazz.declaredMethods
                .filter { Preferences.Key::class.java.isAssignableFrom(it.returnType) }
                .mapNotNull { method ->
                    method.isAccessible = true
                    runCatching { method.invoke(null) as? Preferences.Key<*> }.getOrNull()
                }

        val keysFromFields =
            clazz.declaredFields
                .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
                .mapNotNull { field ->
                    field.isAccessible = true
                    runCatching { field.get(null) as? Preferences.Key<*> }.getOrNull()
                }

        val allDeclaredKeys = (keysFromMethods + keysFromFields).toSet()

        assertTrue("SettingsKeysKt must contain declared Preferences.Keys", allDeclaredKeys.isNotEmpty())

        val exportedKeys = SECTION_MAP.values.flatten().toSet()
        val excludedKeys = EXCLUDED_KEYS

        // 1. Verify SECTION_MAP section sets are disjoint (no key in multiple sections)
        val sectionKeysList = SECTION_MAP.values.flatten()
        val duplicatesInSections = sectionKeysList.groupingBy { it.name }.eachCount().filter { it.value > 1 }
        assertTrue(
            "The following keys appear in multiple SECTION_MAP sections: ${duplicatesInSections.keys}",
            duplicatesInSections.isEmpty(),
        )

        // 2. Verify SECTION_MAP and EXCLUDED_KEYS are disjoint (no key marked as both exported and excluded)
        val overlap = exportedKeys.intersect(excludedKeys)
        assertTrue(
            "The following keys are in both SECTION_MAP and EXCLUDED_KEYS: ${overlap.map { it.name }}",
            overlap.isEmpty(),
        )

        // 3. Verify exact 100% coverage: every key in SettingsKeys is either exported or excluded
        val categorizedKeys = exportedKeys + excludedKeys
        val uncategorized = allDeclaredKeys - categorizedKeys

        assertTrue(
            "The following Preference.Key(s) in SettingsKeys.kt are uncategorized (neither in SECTION_MAP nor EXCLUDED_KEYS): ${uncategorized.map {
                it.name
            }}. Every new key must be explicitly categorized!",
            uncategorized.isEmpty(),
        )

        assertEquals(
            "Total declared Preference.Keys (${allDeclaredKeys.size}) must equal the sum of exported (${exportedKeys.size}) and excluded (${excludedKeys.size}) keys",
            allDeclaredKeys.size,
            categorizedKeys.size,
        )
    }
}
