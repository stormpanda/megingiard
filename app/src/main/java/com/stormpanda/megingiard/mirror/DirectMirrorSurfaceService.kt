package com.stormpanda.megingiard.mirror

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.view.Surface
import com.stormpanda.megingiard.AppLog

private const val TAG = "DirectMirrorSurface"
private const val SHELL_UID = 2000
private const val DIRECT_SURFACE_DESCRIPTOR = "com.stormpanda.megingiard.mirror.IDirectMirrorSurfaceService"
private const val TRANSACTION_ACQUIRE_SURFACE = IBinder.FIRST_CALL_TRANSACTION

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
}

class DirectMirrorSurfaceService : Service() {
    private val directSurfaceBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel, flags: Int): Boolean {
            if (code != TRANSACTION_ACQUIRE_SURFACE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR)
            reply.writeNoException()

            if (getCallingUid() != SHELL_UID) {
                AppLog.w(TAG, "reject acquireSurface from uid=${getCallingUid()}")
                reply.writeInt(0)
                return true
            }

            val surface = DirectMirrorSurfaceRegistry.currentSurface()
            if (surface == null) {
                AppLog.w(TAG, "acquireSurface: no valid surface")
                reply.writeInt(0)
                return true
            }

            AppLog.i(TAG, "acquireSurface: returning valid surface")
            reply.writeInt(1)
            surface.writeToParcel(reply, 0)
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = directSurfaceBinder
}