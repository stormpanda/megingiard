package com.stormpanda.megingiard.ipc

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "ContentProviderObserverFlow"

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
        // Initial fetch (pull)
        trySend(parser(context.contentResolver, uri))

        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    AppLog.d(TAG, "ContentObserver onChange triggered for $uri")
                    trySend(parser(context.contentResolver, uri))
                }
            }

        try {
            context.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to register ContentObserver for $uri: ${e.message}")
        }

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to unregister ContentObserver for $uri: ${e.message}")
            }
        }
    }
