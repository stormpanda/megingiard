package com.stormpanda.megingiard.ipc

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val TAG = "ProviderObserverFlow"

/**
 * Creates a reactive [Flow] that performs an initial query on [uri] using [parser],
 * and re-queries whenever a notification is received via a registered [ContentObserver].
 */
fun <T> observeContentProvider(
    context: Context,
    uri: Uri,
    parser: (ContentResolver, Uri) -> T,
): Flow<T> =
    callbackFlow {
        val appContext = context.applicationContext
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    AppLog.d(TAG, "ContentObserver onChange triggered for $uri")
                    launch(Dispatchers.IO) {
                        trySend(parser(appContext.contentResolver, uri))
                    }
                }
            }

        try {
            appContext.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: SecurityException) {
            AppLog.e(TAG, "SecurityException registering observer for $uri", e)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to register observer for $uri", e)
        }

        // Emit initial state on IO
        trySend(parser(appContext.contentResolver, uri))

        awaitClose {
            try {
                appContext.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error unregistering observer for $uri", e)
            }
        }
    }
