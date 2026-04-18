package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "TouchpadViewModel"

/**
 * ViewModel for [TouchpadScreen] — manages injector lifecycle and
 * provides [TouchpadGestureProcessor] factory.
 */
class TouchpadViewModel(application: Application) : AndroidViewModel(application) {

    val touchpadUseMouse: StateFlow<Boolean> = SettingsManager.touchpadUseMouse
    val touchpadTapToClick: StateFlow<Boolean> = SettingsManager.touchpadTapToClick
    val touchpadTwoFingerTap: StateFlow<Boolean> = SettingsManager.touchpadTwoFingerTap
    val overlayVisible: StateFlow<Boolean> = AppStateManager.overlayVisible

    fun hideOverlay() = AppStateManager.hideOverlay()

    fun createGestureProcessor(useMouse: Boolean) =
        TouchpadGestureProcessor(useMouse, viewModelScope)

    fun startInjectors(context: Context, useMouse: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (useMouse) {
                AppLog.d(TAG, "starting MouseInjector (useMouse=true)")
                TouchInjector.stop()
                MouseInjector.start(context)
            } else {
                AppLog.d(TAG, "starting TouchInjector (useMouse=false)")
                MouseInjector.stop()
                TouchInjector.start(context)
            }
        }
    }

    fun stopInjectors() {
        AppLog.d(TAG, "stopInjectors called")
        TouchInjector.stop()
        MouseInjector.stop()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.d(TAG, "onCleared → injectors stopped")
        TouchInjector.stop()
        MouseInjector.stop()
    }
}
