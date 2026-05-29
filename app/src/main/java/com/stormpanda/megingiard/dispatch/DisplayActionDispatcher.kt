package com.stormpanda.megingiard.dispatch

import android.content.Context
import com.stormpanda.megingiard.AppLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DisplayActionDispatcher"

/**
 * A coordinator that allows executing display-bound actions by temporarily
 * targeting and acquiring focus on a specified display.
 */
object DisplayActionDispatcher {

    private data class DispatchTask(
        val action: (() -> Unit)?,
        val onComplete: (() -> Unit)?
    )

    private val activeDispatches = ConcurrentHashMap<String, DispatchTask>()

    /**
     * Queues an action task and shifts input focus to the specified display ID to execute it.
     *
     * @param context Application context.
     * @param displayId The target display ID to execute the action on.
     * @param action The action to run once focus is acquired on the target display.
     * @param onComplete Optional callback to invoke when the task is finished.
     */
    fun dispatchActionOnDisplay(
        context: Context,
        displayId: Int,
        action: (() -> Unit)?,
        onComplete: (() -> Unit)? = null
    ) {
        val taskId = UUID.randomUUID().toString()
        AppLog.i(TAG, "dispatchActionOnDisplay: displayId=$displayId taskId=$taskId")

        activeDispatches[taskId] = DispatchTask(
            action = action,
            onComplete = onComplete
        )

        val launched = DisplayActionDispatcherActivity.launchOnDisplay(context, displayId, taskId)
        if (!launched) {
            AppLog.e(TAG, "Failed to launch DispatcherActivity, executing tasks inline as fallback")
            val pending = activeDispatches.remove(taskId) ?: return
            try {
                pending.action?.invoke()
            } finally {
                pending.onComplete?.invoke()
            }
        }
    }
    
    internal fun retrieveAndRemoveTask(taskId: String?): Pair<(() -> Unit)?, (() -> Unit)?> {
        val task = taskId?.let { activeDispatches.remove(it) }
        return (task?.action) to (task?.onComplete)
    }
}
