package com.stormpanda.megingiard.mirror

import android.os.IBinder
import android.os.Parcel
import android.view.Surface
import com.stormpanda.megingiard.AppLog
import java.lang.reflect.Method

private const val TAG = "DirectMirrorSurface"
private const val DIRECT_SURFACE_SERVICE_NAME = "megingiard.direct.surface"
private const val DIRECT_SURFACE_DESCRIPTOR = "com.stormpanda.megingiard.mirrorserver.IDirectSurfaceReceiver"
private const val TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION

internal object DirectMirrorSurfaceBridge {
    fun clearDirectSurfaces(): Boolean = transactSurface(null, 0, 0)

    fun sendToDirectServer(
        surface: Surface,
        width: Int,
        height: Int,
    ): Boolean = transactSurface(surface, width, height)

    private fun transactSurface(
        surface: Surface?,
        width: Int,
        height: Int,
    ): Boolean {
        val validSurface = surface?.takeIf { it.isValid }
        val binder =
            getService(DIRECT_SURFACE_SERVICE_NAME) ?: run {
                AppLog.w(TAG, "sendToDirectServer: direct service not registered")
                return false
            }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)
            if (validSurface != null) {
                data.writeInt(1)
                data.writeInt(width)
                data.writeInt(height)
                validSurface.writeToParcel(data, 0)
            } else {
                data.writeInt(0)
            }
            if (!binder.transact(TRANSACTION_SET_SURFACE, data, reply, 0)) {
                AppLog.w(TAG, "sendToDirectServer: transact returned false")
                false
            } else {
                reply.readException()
                val serverResult = reply.readInt()
                val ok = if (validSurface != null) serverResult == 1 else serverResult == 0
                AppLog.i(TAG, "sendToDirectServer: ok=$ok (hasSurface=${validSurface != null}, serverResult=$serverResult)")
                ok
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "sendToDirectServer failed: $e")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun getService(name: String): IBinder? =
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService: Method = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.isAccessible = true
            getService.invoke(null, name) as? IBinder
        } catch (e: Exception) {
            AppLog.w(TAG, "getService($name) failed: $e")
            null
        }
}
