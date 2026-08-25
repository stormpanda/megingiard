package com.stormpanda.megingiard.settings

/**
 * Top-level categories available in the Global Settings screen.
 */
enum class SettingsCategory {
    GENERAL,
    INPUT,
    APPEARANCE,
    CONFIGURATION,
    SCRAPING,
    UPDATES,
    DIAGNOSTICS,
}

/**
 * Sub-pages that can be drilled into within the Global Settings screen.
 */
enum class SettingsSubPage(
    val parentCategory: SettingsCategory,
) {
    DEADZONES(SettingsCategory.INPUT),
    STEAMGRIDDB_TOKEN(SettingsCategory.SCRAPING),
    CUSTOM_ACCENT(SettingsCategory.APPEARANCE),
    CREATE_BACKUP(SettingsCategory.CONFIGURATION),
    SHARE_PROFILE(SettingsCategory.CONFIGURATION),
    RESTORE_BACKUP(SettingsCategory.CONFIGURATION),
    RESTORE_REVIEW(SettingsCategory.CONFIGURATION),
    UPDATE_AVAILABLE(SettingsCategory.UPDATES),
}
