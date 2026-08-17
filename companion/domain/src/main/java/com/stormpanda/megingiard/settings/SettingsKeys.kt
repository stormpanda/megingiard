package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for [SettingsManager], extracted to a separate file
 * to keep the manager focused on state + persistence logic.
 *
 * All keys are `internal` — they are hidden from other modules (`:app`, `:core`)
 * but accessible to **any file within `:domain`**, not just the `settings` package.
 * This is a slight relaxation of the original visibility: the keys previously lived
 * as `private val`s inside `object SettingsManager`, which restricted access to
 * that single object. The `internal` modifier preserves the module boundary but
 * widens access within `:domain`.
 */

internal val KEY_AUTO_SWITCH_PROFILES = booleanPreferencesKey("auto_switch_profiles")
internal val KEY_EXCLUDE_FROM_RECENTS = booleanPreferencesKey("exclude_from_recents")
internal val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
internal val KEY_CUSTOM_ACCENT_COLOR = intPreferencesKey("custom_accent_color")
internal val KEY_OVERLAY_AT_BOTTOM = booleanPreferencesKey("overlay_at_bottom")
internal val KEY_OVERLAY_FADE_OUT = booleanPreferencesKey("overlay_fade_out")
internal val KEY_STEAMGRIDDB_API_TOKEN = stringPreferencesKey("steamgriddb_api_token")

// Mirror session state persistence — "remember" flags
internal val KEY_REMEMBER_VIEWPORT = booleanPreferencesKey("mirror_remember_viewport")
internal val KEY_REMEMBER_LOCK = booleanPreferencesKey("mirror_remember_lock")
internal val KEY_REMEMBER_PROJECTION = booleanPreferencesKey("mirror_remember_projection")
// Mirror session state persistence — saved values (viewport moved to PadLayout.mirrorSaved*)

// Appearance
internal val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

// MacroPad settings
internal val KEY_MACROPAD_PROFILES = stringPreferencesKey("macropad_profiles")
internal val KEY_MACROPAD_ACTIVE_PROFILE_ID = stringPreferencesKey("macropad_active_profile_id")

// Keyboard settings
internal val KEY_KB_LAYOUT = stringPreferencesKey("kb_layout")
internal val KEY_KB_TRACKPOINT_ENABLED = booleanPreferencesKey("kb_trackpoint_enabled")
internal val KEY_KB_REPEAT_ENABLED = booleanPreferencesKey("kb_repeat_enabled")
internal val KEY_KB_FULLSCREEN = booleanPreferencesKey("kb_fullscreen")
internal val KEY_KB_MOUSE_BTN_POS = stringPreferencesKey("kb_mouse_btn_pos")
internal val KEY_KB_TOUCHPAD_ENABLED = booleanPreferencesKey("kb_touchpad_enabled")

// Language
internal val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

// Logging
internal val KEY_LOG_LEVEL = stringPreferencesKey("log_level")

// Touchpad settings
internal val KEY_TOUCHPAD_USE_MOUSE = booleanPreferencesKey("touchpad_use_mouse")
internal val KEY_TOUCHPAD_TAP_TO_CLICK = booleanPreferencesKey("touchpad_tap_to_click")
internal val KEY_TOUCHPAD_TWO_FINGER_TAP = booleanPreferencesKey("touchpad_two_finger_tap")
internal val KEY_TOUCHPAD_THREE_FINGER_TAP = booleanPreferencesKey("touchpad_three_finger_tap")
internal val KEY_TOUCHPAD_TAP_DRAG = booleanPreferencesKey("touchpad_tap_drag")
internal val KEY_TOUCHPAD_TWO_FINGER_SCROLL = booleanPreferencesKey("touchpad_two_finger_scroll")
internal val KEY_TOUCHPAD_MIRRORING_ENABLED = booleanPreferencesKey("touchpad_mirroring_enabled")
internal val KEY_TOUCHPAD_MIRROR_DIM = intPreferencesKey("touchpad_mirror_dim")
internal val KEY_TOUCHPAD_MOUSE_4_5_ENABLED = booleanPreferencesKey("touchpad_mouse_4_5_enabled")
internal val KEY_TOUCHPAD_SENSITIVITY = floatPreferencesKey("touchpad_sensitivity")
internal val KEY_TOUCHPAD_NATURAL_SCROLL = booleanPreferencesKey("touchpad_natural_scroll")
internal val KEY_TOUCHPAD_SCROLL_SPEED = floatPreferencesKey("touchpad_scroll_speed")
internal val KEY_TOUCHPAD_HAPTICS_ENABLED = booleanPreferencesKey("touchpad_haptics_enabled")

// MacroPad touch recording
internal val KEY_SKIP_TOUCH_RECORD_DIALOG = booleanPreferencesKey("skip_touch_record_dialog")

// MacroPad gamepad recording
internal val KEY_SKIP_GAMEPAD_RECORD_DIALOG = booleanPreferencesKey("skip_gamepad_record_dialog")

// MacroPad — gamepad face-button label swap (display only, keycodes unchanged)
internal val KEY_GAMEPAD_SWAP_FACE_BUTTONS = booleanPreferencesKey("gamepad_swap_face_buttons")

// MacroPad — 10 most recently used colors (stored as comma-separated ARGB integers)
internal val KEY_MACROPAD_RECENT_COLORS = stringPreferencesKey("macropad_recent_colors")

internal val KEY_PRIVD_PROMPT_DISMISSED = booleanPreferencesKey("privd_prompt_dismissed")

// Privileged Mode — per-stick evdev dead zone for physical gamepad recording (0.0–1.0, default 0.15).
internal val KEY_PRIVD_DEADZONE_LEFT = floatPreferencesKey("privd_deadzone_left")
internal val KEY_PRIVD_DEADZONE_RIGHT = floatPreferencesKey("privd_deadzone_right")

// MacroPad ambient display settings
internal val KEY_MACROPAD_AMBIENT_DIM = floatPreferencesKey("macropad_ambient_dim")
internal val KEY_MACROPAD_AMBIENT_PREVIEW = booleanPreferencesKey("macropad_ambient_preview")
internal val KEY_MACROPAD_AMBIENT_APPLY_THEME = booleanPreferencesKey("macropad_ambient_apply_theme")

internal val KEY_SAVED_LOCKED = booleanPreferencesKey("mirror_saved_locked")
internal val KEY_SAVED_PROJECTION = booleanPreferencesKey("mirror_saved_projection")

// Tutorials persistence
internal val KEY_SHOW_MACRO_EDITOR_TUTORIAL = booleanPreferencesKey("show_macro_editor_tutorial")
internal val KEY_WELCOME_TOUR_COMPLETED_VERSION = intPreferencesKey("welcome_tour_completed_version")

// Internal backups storage key — isolated from SECTION_MAP export/import
internal val KEY_INTERNAL_BACKUPS = stringPreferencesKey("internal_backups")

// Automatic update check settings
internal val KEY_AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
internal val KEY_LATEST_RELEASE_TAG = stringPreferencesKey("latest_release_tag")
internal val KEY_LATEST_RELEASE_URL = stringPreferencesKey("latest_release_url")
internal val KEY_LATEST_RELEASE_NOTES = stringPreferencesKey("latest_release_notes")

// ── Section key groups for config export/import ───────────────────────────

private val GLOBAL_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_ACCENT_COLOR,
        KEY_CUSTOM_ACCENT_COLOR,
        KEY_OVERLAY_AT_BOTTOM,
        KEY_OVERLAY_FADE_OUT,
        KEY_THEME_MODE,
        KEY_APP_LANGUAGE,
        KEY_LOG_LEVEL,
        KEY_AUTO_SWITCH_PROFILES,
        KEY_EXCLUDE_FROM_RECENTS,
        KEY_STEAMGRIDDB_API_TOKEN,
        KEY_AUTO_UPDATE_CHECK_ENABLED,
    )
private val MIRROR_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_REMEMBER_VIEWPORT,
        KEY_REMEMBER_LOCK,
        KEY_REMEMBER_PROJECTION,
    )
private val TOUCHPAD_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_TOUCHPAD_USE_MOUSE,
        KEY_TOUCHPAD_TAP_TO_CLICK,
        KEY_TOUCHPAD_TWO_FINGER_TAP,
        KEY_TOUCHPAD_THREE_FINGER_TAP,
        KEY_TOUCHPAD_TAP_DRAG,
        KEY_TOUCHPAD_TWO_FINGER_SCROLL,
        KEY_TOUCHPAD_MIRRORING_ENABLED,
        KEY_TOUCHPAD_MIRROR_DIM,
        KEY_TOUCHPAD_MOUSE_4_5_ENABLED,
        KEY_TOUCHPAD_SENSITIVITY,
        KEY_TOUCHPAD_NATURAL_SCROLL,
        KEY_TOUCHPAD_SCROLL_SPEED,
        KEY_TOUCHPAD_HAPTICS_ENABLED,
    )
private val KEYBOARD_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_KB_LAYOUT,
        KEY_KB_TRACKPOINT_ENABLED,
        KEY_KB_REPEAT_ENABLED,
        KEY_KB_FULLSCREEN,
        KEY_KB_MOUSE_BTN_POS,
    )
private val MACROPAD_SETTINGS_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_MACROPAD_AMBIENT_DIM,
        KEY_MACROPAD_AMBIENT_PREVIEW,
        KEY_MACROPAD_AMBIENT_APPLY_THEME,
        KEY_GAMEPAD_SWAP_FACE_BUTTONS,
        KEY_MACROPAD_RECENT_COLORS,
        KEY_PRIVD_DEADZONE_LEFT,
        KEY_PRIVD_DEADZONE_RIGHT,
    )

internal val SECTION_MAP: Map<String, Set<Preferences.Key<*>>> =
    mapOf(
        "global" to GLOBAL_KEYS,
        "mirror" to MIRROR_KEYS,
        "touchpad" to TOUCHPAD_KEYS,
        "keyboard" to KEYBOARD_KEYS,
        "macropad_settings" to MACROPAD_SETTINGS_KEYS,
    )

/** Preference keys deliberately excluded from .mgrd export/import (internal state, backups, tutorials). */
internal val EXCLUDED_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_MACROPAD_PROFILES,
        KEY_MACROPAD_ACTIVE_PROFILE_ID,
        KEY_KB_TOUCHPAD_ENABLED,
        KEY_SKIP_TOUCH_RECORD_DIALOG,
        KEY_SKIP_GAMEPAD_RECORD_DIALOG,
        KEY_SAVED_LOCKED,
        KEY_SAVED_PROJECTION,
        KEY_SHOW_MACRO_EDITOR_TUTORIAL,
        KEY_WELCOME_TOUR_COMPLETED_VERSION,
        KEY_INTERNAL_BACKUPS,
        KEY_PRIVD_PROMPT_DISMISSED,
        KEY_LATEST_RELEASE_TAG,
        KEY_LATEST_RELEASE_URL,
        KEY_LATEST_RELEASE_NOTES,
    )

/** Reverse lookup: DataStore key name → section name. */
internal val KEY_TO_SECTION: Map<String, String> by lazy {
    SECTION_MAP.flatMap { (section, keys) -> keys.map { it.name to section } }.toMap()
}

/** Flat map from DataStore key name to the actual typed key instance, used by config import. */
internal val KEY_BY_NAME: Map<String, Preferences.Key<*>> by lazy {
    SECTION_MAP.values.flatten().associateBy { it.name }
}

internal val BOOLEAN_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_AUTO_SWITCH_PROFILES,
        KEY_EXCLUDE_FROM_RECENTS,
        KEY_OVERLAY_AT_BOTTOM,
        KEY_OVERLAY_FADE_OUT,
        KEY_REMEMBER_VIEWPORT,
        KEY_REMEMBER_LOCK,
        KEY_REMEMBER_PROJECTION,
        KEY_KB_TRACKPOINT_ENABLED,
        KEY_KB_REPEAT_ENABLED,
        KEY_KB_FULLSCREEN,
        KEY_TOUCHPAD_USE_MOUSE,
        KEY_TOUCHPAD_TAP_TO_CLICK,
        KEY_TOUCHPAD_TWO_FINGER_TAP,
        KEY_TOUCHPAD_THREE_FINGER_TAP,
        KEY_TOUCHPAD_TAP_DRAG,
        KEY_TOUCHPAD_TWO_FINGER_SCROLL,
        KEY_TOUCHPAD_MIRRORING_ENABLED,
        KEY_TOUCHPAD_MOUSE_4_5_ENABLED,
        KEY_TOUCHPAD_NATURAL_SCROLL,
        KEY_TOUCHPAD_HAPTICS_ENABLED,
        KEY_SKIP_TOUCH_RECORD_DIALOG,
        KEY_SKIP_GAMEPAD_RECORD_DIALOG,
        KEY_GAMEPAD_SWAP_FACE_BUTTONS,
        KEY_MACROPAD_AMBIENT_PREVIEW,
        KEY_MACROPAD_AMBIENT_APPLY_THEME,
        KEY_SAVED_LOCKED,
        KEY_SAVED_PROJECTION,
        KEY_SHOW_MACRO_EDITOR_TUTORIAL,
        KEY_AUTO_UPDATE_CHECK_ENABLED,
    )

internal val INT_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_ACCENT_COLOR,
        KEY_CUSTOM_ACCENT_COLOR,
        KEY_TOUCHPAD_MIRROR_DIM,
        KEY_WELCOME_TOUR_COMPLETED_VERSION,
    )

internal val FLOAT_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_PRIVD_DEADZONE_LEFT,
        KEY_PRIVD_DEADZONE_RIGHT,
        KEY_MACROPAD_AMBIENT_DIM,
        KEY_TOUCHPAD_SENSITIVITY,
        KEY_TOUCHPAD_SCROLL_SPEED,
    )

internal val STRING_KEYS: Set<Preferences.Key<*>> =
    setOf(
        KEY_STEAMGRIDDB_API_TOKEN,
        KEY_THEME_MODE,
        KEY_MACROPAD_PROFILES,
        KEY_MACROPAD_ACTIVE_PROFILE_ID,
        KEY_KB_LAYOUT,
        KEY_KB_MOUSE_BTN_POS,
        KEY_APP_LANGUAGE,
        KEY_LOG_LEVEL,
        KEY_MACROPAD_RECENT_COLORS,
        KEY_INTERNAL_BACKUPS,
        KEY_LATEST_RELEASE_TAG,
        KEY_LATEST_RELEASE_URL,
        KEY_LATEST_RELEASE_NOTES,
    )
