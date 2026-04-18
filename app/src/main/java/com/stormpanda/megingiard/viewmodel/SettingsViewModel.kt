package com.stormpanda.megingiard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "SettingsViewModel"

/**
 * ViewModel for [GlobalSettingsScreen] — bridges [SettingsManager] to the UI
 * and converts ARGB Int accent color to Compose [Color].
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    /** Accent color as ARGB Int from domain; UI converts to Compose Color. */
    val accentColorArgb: StateFlow<Int> = SettingsManager.accentColor

    fun setAccentColor(argb: Int) {
        AppLog.d(TAG, "setAccentColor 0x${argb.toString(16)}")
        SettingsManager.setAccentColor(argb)
    }
}
