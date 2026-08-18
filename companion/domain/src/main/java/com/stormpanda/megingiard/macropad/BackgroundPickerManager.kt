package com.stormpanda.megingiard.macropad

import android.net.Uri
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "BackgroundPickerManager"

/**
 * Coordinator singleton for background image file picker requests between UI overlays
 * (which may run in non-Activity contexts) and [MainActivity].
 */
object BackgroundPickerManager {
    private val _pickRequest =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val pickRequest: SharedFlow<Unit> = _pickRequest.asSharedFlow()

    private val _pickedUri = MutableStateFlow<Uri?>(null)
    val pickedUri: StateFlow<Uri?> = _pickedUri.asStateFlow()

    fun requestImagePicker() {
        AppLog.d(TAG, "requestImagePicker")
        _pickRequest.tryEmit(Unit)
    }

    fun setPickedUri(uri: Uri?) {
        AppLog.d(TAG, "setPickedUri: $uri")
        _pickedUri.value = uri
    }

    fun clearPickedUri() {
        _pickedUri.value = null
    }
}
