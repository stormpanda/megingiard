package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

object ScreenCaptureManager {
    val bitmapFlow = MutableStateFlow<Bitmap?>(null)
    val isCapturing = MutableStateFlow(false)
}
