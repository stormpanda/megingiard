package com.stormpanda.megingiard.mirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MegingiardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var capturing = false
    private val bufferQueue = mutableListOf<HardwareBuffer>()

    companion object {
        var isServiceConnected = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        this.serviceInfo = info

        scope.launch {
            ScreenCaptureManager.isCapturing.collectLatest { isCapturing ->
                capturing = isCapturing
                if (isCapturing) {
                    requestNextFrame()
                }
            }
        }
    }

    private fun requestNextFrame() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!capturing) return

        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                try {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                    ScreenCaptureManager.bitmapFlow.value = bitmap

                    bufferQueue.add(hardwareBuffer)
                    if (bufferQueue.size > 2) {
                        val old = bufferQueue.removeAt(0)
                        if (!old.isClosed) old.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Immediately chain next frame — no idle wait!
                requestNextFrame()
            }

            override fun onFailure(errorCode: Int) {
                // Short back-off on failure then retry
                scope.launch {
                    delay(16)
                    requestNextFrame()
                }
            }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        capturing = false
        for (buffer in bufferQueue) {
            if (!buffer.isClosed) buffer.close()
        }
        bufferQueue.clear()
        return super.onUnbind(intent)
    }
}
