package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

internal val KEY_AUTO_START_CAPTURE = booleanPreferencesKey("auto_start_capture")
internal val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
internal val KEY_OVERLAY_AT_BOTTOM = booleanPreferencesKey("overlay_at_bottom")
internal val KEY_SHOW_MIRROR_CONTROL_LABELS = booleanPreferencesKey("show_mirror_control_labels")
internal val KEY_SHOW_FULLSCREEN_EXIT_HINTS = booleanPreferencesKey("show_fullscreen_exit_hints")

// Mirror touch projection settings
internal val KEY_PINCH_WHILE_PROJECTING = booleanPreferencesKey("mirror_pinch_while_projecting")
// Mirror session state persistence — "remember" flags
internal val KEY_REMEMBER_VIEWPORT = booleanPreferencesKey("mirror_remember_viewport")
internal val KEY_REMEMBER_LOCK = booleanPreferencesKey("mirror_remember_lock")
internal val KEY_REMEMBER_PROJECTION = booleanPreferencesKey("mirror_remember_projection")
internal val KEY_MIRROR_FOLLOW_SMOOTHING = booleanPreferencesKey("mirror_follow_smoothing")
internal val KEY_MIRROR_FOLLOW_ACCELERATION = floatPreferencesKey("mirror_follow_acceleration")
internal val KEY_MIRROR_FOLLOW_ZOOM = floatPreferencesKey("mirror_follow_zoom")
// Mirror session state persistence — saved values (viewport moved to PadLayout.mirrorSaved*)

// Appearance
internal val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

// MacroPad settings
internal val KEY_MACROPAD_PROFILES           = stringPreferencesKey("macropad_profiles")
internal val KEY_MACROPAD_ACTIVE_PROFILE_ID  = stringPreferencesKey("macropad_active_profile_id")

// Keyboard settings
internal val KEY_KB_LAYOUT = stringPreferencesKey("kb_layout")
internal val KEY_KB_TRACKPOINT_ENABLED = booleanPreferencesKey("kb_trackpoint_enabled")
internal val KEY_KB_REPEAT_ENABLED = booleanPreferencesKey("kb_repeat_enabled")
internal val KEY_KB_FULLSCREEN = booleanPreferencesKey("kb_fullscreen")
internal val KEY_KB_MOUSE_BTN_POS = stringPreferencesKey("kb_mouse_btn_pos")

// Language
internal val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

// Logging
internal val KEY_LOG_LEVEL = stringPreferencesKey("log_level")

// Touchpad settings
internal val KEY_TOUCHPAD_USE_MOUSE = booleanPreferencesKey("touchpad_use_mouse")
internal val KEY_TOUCHPAD_TAP_TO_CLICK = booleanPreferencesKey("touchpad_tap_to_click")
internal val KEY_TOUCHPAD_TWO_FINGER_TAP = booleanPreferencesKey("touchpad_two_finger_tap")

// MacroPad touch recording
internal val KEY_SKIP_TOUCH_RECORD_DIALOG = booleanPreferencesKey("skip_touch_record_dialog")

// MacroPad gamepad recording
internal val KEY_SKIP_GAMEPAD_RECORD_DIALOG = booleanPreferencesKey("skip_gamepad_record_dialog")

// MacroPad — gamepad face-button label swap (display only, keycodes unchanged)
internal val KEY_GAMEPAD_SWAP_FACE_BUTTONS = booleanPreferencesKey("gamepad_swap_face_buttons")

// Privileged Mode — per-feature enable flags (only effective while PrivdManager is RUNNING)
internal val KEY_PRIVD_GAMEPAD_MERGE_ENABLED = booleanPreferencesKey("privd_gamepad_merge_enabled")
internal val KEY_PRIVD_GAMEPAD_RECORDING_ENABLED = booleanPreferencesKey("privd_gamepad_recording_enabled")
internal val KEY_PRIVD_MIRROR_ENABLED = booleanPreferencesKey("privd_mirror_enabled")

// Privileged Mode — auto-connect on app start once the user has bootstrapped at least once.
internal val KEY_PRIVD_AUTO_CONNECT = booleanPreferencesKey("privd_auto_connect")

// Privileged Mode — per-stick evdev dead zone for physical gamepad recording (0.0–1.0, default 0.15).
internal val KEY_PRIVD_DEADZONE_LEFT  = floatPreferencesKey("privd_deadzone_left")
internal val KEY_PRIVD_DEADZONE_RIGHT = floatPreferencesKey("privd_deadzone_right")

// MacroPad ambient display settings
internal val KEY_MACROPAD_AMBIENT_DIM = floatPreferencesKey("macropad_ambient_dim")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED = booleanPreferencesKey("macropad_ambient_vignette_enabled")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA = floatPreferencesKey("macropad_ambient_vignette_visible_area")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION = floatPreferencesKey("macropad_ambient_vignette_transition")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY = floatPreferencesKey("macropad_ambient_vignette_opacity")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR = intPreferencesKey("macropad_ambient_vignette_color")
internal val KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE = stringPreferencesKey("macropad_ambient_vignette_shape")
internal val KEY_MACROPAD_AMBIENT_PREVIEW = booleanPreferencesKey("macropad_ambient_preview")
internal val KEY_MACROPAD_AMBIENT_APPLY_THEME = booleanPreferencesKey("macropad_ambient_apply_theme")

internal val KEY_SAVED_LOCKED = booleanPreferencesKey("mirror_saved_locked")
internal val KEY_SAVED_PROJECTION = booleanPreferencesKey("mirror_saved_projection")

// Internal backups storage key — isolated from SECTION_MAP export/import
internal val KEY_INTERNAL_BACKUPS = stringPreferencesKey("internal_backups")


// ── Section key groups for config export/import ───────────────────────────

private val GLOBAL_KEYS: Set<Preferences.Key<*>> = setOf(
    KEY_ACCENT_COLOR, KEY_OVERLAY_AT_BOTTOM, KEY_THEME_MODE,
    KEY_APP_LANGUAGE, KEY_LOG_LEVEL,
    KEY_SHOW_MIRROR_CONTROL_LABELS, KEY_SHOW_FULLSCREEN_EXIT_HINTS,
)
private val MIRROR_KEYS: Set<Preferences.Key<*>> = setOf(
    KEY_AUTO_START_CAPTURE, KEY_PINCH_WHILE_PROJECTING,
    KEY_REMEMBER_VIEWPORT, KEY_REMEMBER_LOCK, KEY_REMEMBER_PROJECTION,
    KEY_MIRROR_FOLLOW_SMOOTHING, KEY_MIRROR_FOLLOW_ACCELERATION, KEY_MIRROR_FOLLOW_ZOOM,
)
private val TOUCHPAD_KEYS: Set<Preferences.Key<*>> = setOf(
    KEY_TOUCHPAD_USE_MOUSE, KEY_TOUCHPAD_TAP_TO_CLICK, KEY_TOUCHPAD_TWO_FINGER_TAP,
)
private val KEYBOARD_KEYS: Set<Preferences.Key<*>> = setOf(
    KEY_KB_LAYOUT, KEY_KB_TRACKPOINT_ENABLED, KEY_KB_REPEAT_ENABLED,
    KEY_KB_FULLSCREEN, KEY_KB_MOUSE_BTN_POS,
)
private val MACROPAD_SETTINGS_KEYS: Set<Preferences.Key<*>> = setOf(
    KEY_MACROPAD_AMBIENT_DIM,
    KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED, KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA,
    KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION, KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY,
    KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR, KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE,
    KEY_MACROPAD_AMBIENT_PREVIEW, KEY_MACROPAD_AMBIENT_APPLY_THEME,
    KEY_GAMEPAD_SWAP_FACE_BUTTONS,
    KEY_PRIVD_GAMEPAD_MERGE_ENABLED,
    KEY_PRIVD_GAMEPAD_RECORDING_ENABLED,
    KEY_PRIVD_MIRROR_ENABLED,
    KEY_PRIVD_AUTO_CONNECT,
    KEY_PRIVD_DEADZONE_LEFT,
    KEY_PRIVD_DEADZONE_RIGHT,
)

internal val SECTION_MAP: Map<String, Set<Preferences.Key<*>>> = mapOf(
    "global" to GLOBAL_KEYS,
    "mirror" to MIRROR_KEYS,
    "touchpad" to TOUCHPAD_KEYS,
    "keyboard" to KEYBOARD_KEYS,
    "macropad_settings" to MACROPAD_SETTINGS_KEYS,
)

/** Reverse lookup: DataStore key name → section name. */
internal val KEY_TO_SECTION: Map<String, String> by lazy {
    SECTION_MAP.flatMap { (section, keys) -> keys.map { it.name to section } }.toMap()
}

/** Flat map from DataStore key name to the actual typed key instance, used by config import. */
internal val KEY_BY_NAME: Map<String, Preferences.Key<*>> by lazy {
    SECTION_MAP.values.flatten().associateBy { it.name }
}
