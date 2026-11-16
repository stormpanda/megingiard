package com.stormpanda.megingiard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.toImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CompanionAppIconCache"
private const val MAX_ICON_CACHE_BYTES = 8 * 1024 * 1024
private const val BYTES_PER_ARGB_PIXEL = 4
private const val MAX_LABEL_CACHE_ENTRIES = 256
private const val PACKAGE_DATA_SCHEME = "package"

/**
 * Process-wide in-memory cache for installed application launcher icons and labels in Companion UI.
 *
 * Eliminates repeated [android.content.pm.PackageManager] Binder RPCs, Drawable rasterization,
 * and 1-frame placeholder flashes across MacroPad launcher buttons, app pickers, and floating overlays.
 */
internal object CompanionAppIconCache {
    @Volatile
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val receiverLock = Any()
    private var registeredAppContext: Context? = null

    private val systemBroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                handleSystemBroadcast(intent)
            }
        }

    private val iconCache =
        object : LruCache<String, ImageBitmap>(MAX_ICON_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ): Int = (value.width * value.height * BYTES_PER_ARGB_PIXEL).coerceAtLeast(1)
        }

    private val labelCache = LruCache<String, String>(MAX_LABEL_CACHE_ENTRIES)

    private fun ensureReceiverRegistered(appContext: Context) {
        synchronized(receiverLock) {
            if (registeredAppContext != null) return
            try {
                val pkgFilter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_REPLACED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addAction(Intent.ACTION_PACKAGE_CHANGED)
                        addDataScheme(PACKAGE_DATA_SCHEME)
                    }
                appContext.registerReceiver(systemBroadcastReceiver, pkgFilter)
                appContext.registerReceiver(systemBroadcastReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
                registeredAppContext = appContext
                AppLog.d(TAG, "Registered package and locale invalidation receivers")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to register CompanionAppIconCache receivers: ${e.message}")
            }
        }
    }

    internal fun handleSystemBroadcast(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
            -> {
                val pkg = intent.data?.schemeSpecificPart.orEmpty()
                invalidatePackage(pkg)
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    val pkg = intent.data?.schemeSpecificPart.orEmpty()
                    invalidatePackage(pkg)
                }
            }

            Intent.ACTION_LOCALE_CHANGED -> {
                synchronized(labelCache) { labelCache.evictAll() }
                AppLog.d(TAG, "Evicted cached app labels on system locale change")
            }
        }
    }

    fun getCachedIcon(packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        return synchronized(iconCache) { iconCache.get(packageName) }
    }

    fun getCachedLabel(packageName: String): String? {
        if (packageName.isBlank()) return null
        return synchronized(labelCache) { labelCache.get(packageName) }
    }

    suspend fun loadIcon(
        context: Context,
        packageName: String,
    ): ImageBitmap? {
        if (packageName.isBlank()) return null
        val appContext = context.applicationContext
        ensureReceiverRegistered(appContext)
        getCachedIcon(packageName)?.let { return it }

        return withContext(ioDispatcher) {
            getCachedIcon(packageName)?.let { return@withContext it }
            try {
                val drawable = appContext.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toImageBitmap()
                if (bitmap != null) {
                    synchronized(iconCache) {
                        iconCache.put(packageName, bitmap)
                    }
                    AppLog.d(TAG, "Cached app icon for $packageName (${bitmap.width}x${bitmap.height})")
                }
                bitmap
            } catch (e: Exception) {
                AppLog.d(TAG, "Failed to load app icon for $packageName: ${e.message}")
                null
            }
        }
    }

    suspend fun loadLabel(
        context: Context,
        packageName: String,
    ): String? {
        if (packageName.isBlank()) return null
        val appContext = context.applicationContext
        ensureReceiverRegistered(appContext)
        getCachedLabel(packageName)?.let { return it }

        return withContext(ioDispatcher) {
            getCachedLabel(packageName)?.let { return@withContext it }
            try {
                val pm = appContext.packageManager
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
                val label = pm.getApplicationLabel(appInfo).toString()
                synchronized(labelCache) {
                    labelCache.put(packageName, label)
                }
                AppLog.d(TAG, "Cached app label for $packageName -> '$label'")
                label
            } catch (e: Exception) {
                AppLog.d(TAG, "Failed to load app label for $packageName: ${e.message}")
                null
            }
        }
    }

    fun invalidatePackage(packageName: String) {
        if (packageName.isBlank()) return
        synchronized(iconCache) { iconCache.remove(packageName) }
        synchronized(labelCache) { labelCache.remove(packageName) }
        AppLog.d(TAG, "Invalidated cached app icon/label for $packageName")
    }

    fun resetForTesting() {
        ioDispatcher = Dispatchers.IO
        synchronized(receiverLock) {
            registeredAppContext?.let { ctx ->
                try {
                    ctx.unregisterReceiver(systemBroadcastReceiver)
                } catch (_: Exception) {
                }
            }
            registeredAppContext = null
        }
        synchronized(iconCache) { iconCache.evictAll() }
        synchronized(labelCache) { labelCache.evictAll() }
    }
}
