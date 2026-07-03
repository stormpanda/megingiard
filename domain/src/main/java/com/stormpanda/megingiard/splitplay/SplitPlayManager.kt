package com.stormpanda.megingiard.splitplay

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.view.Surface
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SplitPlayManager"
private const val DIRECT_SURFACE_SERVICE_NAME = "megingiard.direct.surface"
private const val DIRECT_SURFACE_DESCRIPTOR = "com.stormpanda.megingiard.mirrorserver.IDirectSurfaceReceiver"

sealed class SplitPlayState {
    object Inactive : SplitPlayState()
    object Starting : SplitPlayState()
    data class Running(val displayId: Int, val packageName: String) : SplitPlayState()
    data class Error(val message: String) : SplitPlayState()
}

object SplitPlayManager {
    private val _state = MutableStateFlow<SplitPlayState>(SplitPlayState.Inactive)
    val state: StateFlow<SplitPlayState> = _state.asStateFlow()

    private var activeDisplayId: Int = -1
    private var splitDisplayCallback: IBinder? = null
    
    private var imageReader: ImageReader? = null
    private var activeImage: Image? = null
    private var activeBitmap: Bitmap? = null

    var onFrameAvailable: ((Bitmap) -> Unit)? = null

    val isRunning: Boolean
        get() = _state.value is SplitPlayState.Running

    val currentDisplayId: Int
        get() = activeDisplayId

    fun setStarting() {
        _state.value = SplitPlayState.Starting
    }

    fun setError(message: String) {
        _state.value = SplitPlayState.Error(message)
    }

    fun setInactive() {
        stopSplitPlaySession()
        _state.value = SplitPlayState.Inactive
    }

    /**
     * Spawns a trusted portrait virtual display on the privileged direct server,
     * routing the display content into an internal ImageReader for offscreen rendering.
     */
    fun startSplitPlayDisplay(packageName: String, width: Int, height: Int, dpi: Int): Int {
        if (activeDisplayId >= 0) {
            AppLog.i(TAG, "startSplitPlayDisplay: already running with displayId=$activeDisplayId")
            return activeDisplayId
        }

        val binder = getService(DIRECT_SURFACE_SERVICE_NAME) ?: run {
            AppLog.w(TAG, "startSplitPlayDisplay: direct service not registered")
            _state.value = SplitPlayState.Error("Privileged daemon direct service not registered")
            return -1
        }

        try {
            // Create the offscreen ImageReader to capture the virtual display's pixels.
            // Using 3 buffers for double/triple buffering to prevent frame drops.
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader = reader

            reader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val hb = img.hardwareBuffer
                    if (hb == null) {
                        img.close()
                        return@setOnImageAvailableListener
                    }
                    val bitmap = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                    if (bitmap != null) {
                        onFrameAvailable?.invoke(bitmap)
                        
                        val oldImg = activeImage
                        activeImage = img
                        activeBitmap = bitmap
                        oldImg?.close() // release old buffer back to reader queue
                    } else {
                        img.close()
                    }
                } catch (t: Throwable) {
                    img.close()
                }
            }, Handler(Looper.getMainLooper()))

            val surface = reader.surface

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)
                data.writeInt(width)
                data.writeInt(height)
                data.writeInt(dpi)
                // display flags: public (1) | own content (8) | trusted (1024)
                val flags = 1 or 8 or 1024
                data.writeInt(flags)
                data.writeString("megingiard-splitplay")
                data.writeInt(1) // Surface is not null
                surface.writeToParcel(data, 0)

                // TRANSACTION_CREATE_SPLIT_DISPLAY = FIRST_CALL_TRANSACTION + 1
                if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, reply, 0)) {
                    AppLog.w(TAG, "startSplitPlayDisplay: transact returned false")
                    _state.value = SplitPlayState.Error("Failed to communicate display transaction to daemon")
                    reader.close()
                    imageReader = null
                    return -1
                }

                reply.readException()
                val displayId = reply.readInt()
                val callbackBinder = reply.readStrongBinder()

                if (displayId >= 0 && callbackBinder != null) {
                    activeDisplayId = displayId
                    splitDisplayCallback = callbackBinder
                    _state.value = SplitPlayState.Running(displayId, packageName)
                    AppLog.i(TAG, "startSplitPlayDisplay: trusted virtual display created with id=$displayId")
                    return displayId
                } else {
                    AppLog.w(TAG, "startSplitPlayDisplay: creation failed displayId=$displayId")
                    _state.value = SplitPlayState.Error("Daemon failed to create trusted virtual display")
                    reader.close()
                    imageReader = null
                    return -1
                }
            } finally {
                reply.recycle()
                data.recycle()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "startSplitPlayDisplay exception", e)
            _state.value = SplitPlayState.Error("Exception creating display: ${e.message}")
            imageReader?.close()
            imageReader = null
            return -1
        }
    }

    /**
     * Requests the privileged server to launch the resolved launcher activity component
     * on the specific virtual display.
     */
    fun launchGameOnDisplay(componentName: String, displayId: Int): Boolean {
        val binder = getService(DIRECT_SURFACE_SERVICE_NAME) ?: run {
            AppLog.w(TAG, "launchGameOnDisplay: direct service not registered")
            return false
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)
            data.writeString(componentName)
            data.writeInt(displayId)

            // TRANSACTION_LAUNCH_GAME = FIRST_CALL_TRANSACTION + 2
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 2, data, reply, 0)) {
                AppLog.w(TAG, "launchGameOnDisplay: transact returned false")
                return false
            }

            reply.readException()
            val ok = reply.readInt() == 1
            AppLog.i(TAG, "launchGameOnDisplay component=$componentName displayId=$displayId ok=$ok")
            return ok
        } catch (e: Exception) {
            AppLog.e(TAG, "launchGameOnDisplay exception", e)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * Injects a touch event into the virtual display.
     *
     * @param action 0 = DOWN, 1 = MOVE, 2 = UP
     */
    fun injectTouch(displayId: Int, slot: Int, action: Int, x: Float, y: Float): Boolean {
        val binder = getService(DIRECT_SURFACE_SERVICE_NAME) ?: return false

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)
            data.writeInt(displayId)
            data.writeInt(slot)
            data.writeInt(action)
            data.writeFloat(x)
            data.writeFloat(y)

            // TRANSACTION_INJECT_TOUCH = FIRST_CALL_TRANSACTION + 3
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 3, data, reply, 0)) {
                return false
            }
            reply.readException()
            return reply.readInt() == 1
        } catch (e: Exception) {
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * Queries the privileged daemon for the taskId and package name of the currently resumed foreground activity.
     * @return "taskId:packageName" or "" if failed
     */
    fun getTopTaskInfo(): String {
        val binder = getService(DIRECT_SURFACE_SERVICE_NAME) ?: run {
            AppLog.w(TAG, "getTopTaskInfo: direct service not registered")
            return ""
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)

            // TRANSACTION_GET_TOP_PACKAGE = FIRST_CALL_TRANSACTION + 4
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 4, data, reply, 0)) {
                AppLog.w(TAG, "getTopTaskInfo: transact returned false")
                return ""
            }

            reply.readException()
            val info = reply.readString() ?: ""
            AppLog.i(TAG, "getTopTaskInfo returned: '$info'")
            return info
        } catch (e: Exception) {
            AppLog.e(TAG, "getTopTaskInfo exception", e)
            return ""
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    suspend fun awaitDirectService(timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (getService(DIRECT_SURFACE_SERVICE_NAME) == null) {
            if (System.currentTimeMillis() - start >= timeoutMs) {
                return false
            }
            kotlinx.coroutines.delay(50)
        }
        return true
    }

    private fun stopSplitPlaySession() {
        activeDisplayId = -1
        splitDisplayCallback = null
        imageReader?.close()
        imageReader = null
        activeImage?.close()
        activeImage = null
        activeBitmap = null
        onFrameAvailable = null
        AppLog.i(TAG, "stopSplitPlaySession: state reset & resources released")
    }

    private fun getService(name: String): IBinder? {
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.isAccessible = true
            getService.invoke(null, name) as? IBinder
        } catch (e: Exception) {
            AppLog.w(TAG, "getService($name) failed: $e")
            null
        }
    }
}
