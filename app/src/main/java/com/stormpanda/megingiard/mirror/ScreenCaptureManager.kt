package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

object ScreenCaptureManager {
    val bitmapFlow = MutableStateFlow<Bitmap?>(null)
    val isCapturing = MutableStateFlow(false)
    val scale = MutableStateFlow(1f)
    val offsetX = MutableStateFlow(0f)
    val offsetY = MutableStateFlow(0f)
}
