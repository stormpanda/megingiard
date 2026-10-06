package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.stormpanda.megingiard.AppLog

private const val TAG = "GameFocusPreferences"
private const val PREFS_NAME = "gamefocus_preferences"
private const val KEY_BUTTON_PROMPTS_VISIBLE = "button_prompts_visible"
private const val DEFAULT_BUTTON_PROMPTS_VISIBLE = true

/**
 * Manages persisted user settings and UI preferences for Game Focus.
 */
object GameFocusPreferences {
    fun areButtonPromptsVisible(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val visible = prefs.getBoolean(KEY_BUTTON_PROMPTS_VISIBLE, DEFAULT_BUTTON_PROMPTS_VISIBLE)
        AppLog.d(TAG, "areButtonPromptsVisible: $visible")
        return visible
    }

    fun setButtonPromptsVisible(
        context: Context,
        visible: Boolean,
    ) {
        AppLog.d(TAG, "setButtonPromptsVisible: $visible")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BUTTON_PROMPTS_VISIBLE, visible).apply()
    }

    @VisibleForTesting
    fun resetForTesting(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
}
