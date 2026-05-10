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

internal object DirectMirrorSurfaceRegistry {
    private var surface: Surface? = null

    fun publish(surface: Surface) {
        AppLog.i(TAG, "publish surface valid=${surface.isValid}")
        this.surface = surface
    }

    fun clear(surface: Surface? = null) {
        if (surface == null || this.surface === surface) {
            AppLog.i(TAG, "clear surface")
            this.surface = null
        }
    }

    fun currentSurface(): Surface? = surface?.takeIf { it.isValid }

    fun sendToDirectServer(surface: Surface): Boolean {
        if (!surface.isValid) {
            AppLog.w(TAG, "sendToDirectServer: invalid surface")
            return false
        }
        val binder = getService(DIRECT_SURFACE_SERVICE_NAME) ?: run {
            AppLog.w(TAG, "sendToDirectServer: direct service not registered")
            return false
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DIRECT_SURFACE_DESCRIPTOR)
            data.writeInt(1)
            surface.writeToParcel(data, 0)
            if (!binder.transact(TRANSACTION_SET_SURFACE, data, reply, 0)) {
                AppLog.w(TAG, "sendToDirectServer: transact returned false")
                false
            } else {
                reply.readException()
                val ok = reply.readInt() == 1
                AppLog.i(TAG, "sendToDirectServer: ok=$ok")
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

    private fun getService(name: String): IBinder? {
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService: Method = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.isAccessible = true
            getService.invoke(null, name) as? IBinder
        } catch (e: Exception) {
            AppLog.w(TAG, "getService($name) failed: $e")
            null
        }
    }
}