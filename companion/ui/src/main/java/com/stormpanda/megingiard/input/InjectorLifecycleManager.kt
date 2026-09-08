package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.keyboard.KeyInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "InjectorLifecycleManager"

internal data class InjectorStates(
    val startKeyboard: Boolean,
    val startMouse: Boolean,
    val startTouch: Boolean,
)

/**
 * Centralized single source of truth for input injector lifecycles across the application.
 *
 * Keeps standard injectors ([KeyInjector], [MouseInjector], [TouchInjector]) active whenever
 * Megingiard is in the foreground ([AppStateManager.isActivityResumed]), pausing only when
 * backgrounded or when Android's software IME is needed ([AppStateManager.isPrivdSetupWizardActive]).
 * Gamepad input routes directly via Privileged Mode socket merge.
 */
object InjectorLifecycleManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcherJob: Job? = null

    @Synchronized
    fun watch(context: Context) {
        if (watcherJob?.isActive == true) return
        val appContext = context.applicationContext

        watcherJob =
            scope.launch {
                combine(
                    AppStateManager.isActivityResumed,
                    AppStateManager.isPrivdSetupWizardActive,
                ) { isActivityResumed, isPrivdSetupWizardActive ->
                    calculateInjectorStates(
                        isActivityResumed = isActivityResumed,
                        isPrivdSetupWizardActive = isPrivdSetupWizardActive,
                    )
                }.distinctUntilChanged()
                    .collectLatest { states ->
                        if (!states.startKeyboard) {
                            AppLog.d(TAG, "stopping KeyInjector")
                            KeyInjector.stop()
                        }
                        if (!states.startMouse) {
                            AppLog.d(TAG, "stopping MouseInjector")
                            MouseInjector.stop()
                        }
                        if (!states.startTouch) {
                            TouchInjector.stop("InjectorLifecycleManager")
                        }

                        withContext(Dispatchers.IO) {
                            if (states.startKeyboard) {
                                KeyInjector.start(appContext)
                            }
                            if (states.startMouse) {
                                MouseInjector.start(appContext)
                            }
                            if (states.startTouch) {
                                TouchInjector.start(appContext, "InjectorLifecycleManager")
                            }
                        }
                    }
            }
    }

    internal fun calculateInjectorStates(
        isActivityResumed: Boolean,
        isPrivdSetupWizardActive: Boolean = false,
    ): InjectorStates {
        val startKeyboard = isActivityResumed && !isPrivdSetupWizardActive
        val startMouse = isActivityResumed
        val startTouch = isActivityResumed

        return InjectorStates(
            startKeyboard = startKeyboard,
            startMouse = startMouse,
            startTouch = startTouch,
        )
    }

    @Synchronized
    fun stopAll() {
        AppLog.i(TAG, "stopAll called")
        watcherJob?.cancel()
        watcherJob = null
        KeyInjector.stop()
        MouseInjector.stop()
        TouchInjector.stop("InjectorLifecycleManager")
    }
}
