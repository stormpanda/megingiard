package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "InjectorLifecycleWatcher"

/**
 * Canonical, single-source-of-truth watcher for the MacroPad injector lifecycle.
 *
 * Rules:
 *   - Stop all injectors immediately on IO whenever **any** blocking modal opens
 *     (Pill Menu, Layout Editor, Ambient Settings).
 *   - Restart injectors (respecting the active profile's enable flags) only when
 *     **all three** modal flags are simultaneously false, after a short debounce
 *     window that absorbs rapid open→close→open transitions.
 *
 * ## Why a shared class instead of inline coroutines
 * The identical `combine()+collectLatest+delay` pattern was previously duplicated
 * in [MacroPadViewModel] (primary display) and [AmbientMacroPadOverlay] (secondary
 * display). Having it in one place prevents the two sites from drifting out of
 * sync — which caused the original "injectors restart during editor open" bug.
 *
 * ## Threading
 * Both stop and start operations are dispatched on [Dispatchers.IO] because the
 * underlying Shell*Injector implementations perform blocking JVM I/O
 * (`writer.close()`, `process.destroy()`, `readLine()` for the ready signal).
 * Calling them on Main would jank the UI thread.
 *
 * ## Cancellation safety
 * `start()` sequences multiple blocking `readLine()` calls that are not
 * coroutine-cancellable. After each injector start we call `ensureActive()` — a
 * cheap suspend point that surfaces a pending [CancellationException]. If the
 * watcher's coroutine is cancelled mid-sequence (because a modal opened again),
 * the `catch` block immediately stops all injectors before re-throwing, preventing
 * partially-started injectors from remaining alive.
 *
 * @param scope   The [CoroutineScope] that owns this watcher's lifecycle.
 *                Pass `viewModelScope` from a ViewModel, or the coroutine scope
 *                of a `LaunchedEffect` block.
 * @param context Android [Context] used to deploy the native binaries.
 */
class InjectorLifecycleWatcher(
    private val scope: CoroutineScope,
    private val context: Context,
) {

    /** Debounce window that absorbs rapid modal open→close→open sequences. */
    private val restartDebounceMs = RESTART_DEBOUNCE_MS

    /**
     * Launches the watcher coroutine inside [scope].
     * Safe to call multiple times — each call adds an independent collector, so
     * call it exactly once per lifecycle owner.
     */
    fun start() {
        scope.launch {
            combine(
                AppStateManager.isPillMenuOpen,
                AppStateManager.isEditorActive,
                AppStateManager.isAmbientSettingsActive,
            ) { pillMenu, editor, ambient ->
                pillMenu || editor || ambient
            }.distinctUntilChanged()
            .collectLatest { anyOpen ->
                if (anyOpen) {
                    withContext(Dispatchers.IO) {
                        AppLog.i(TAG, "modal open → stopping injectors")
                        KeyInjector.stop()
                        GamepadInjector.stop()
                        MouseInjector.stop()
                    }
                } else {
                    // Debounce: if a new modal opens within this window, collectLatest
                    // cancels this branch before any start() is called.
                    delay(restartDebounceMs)
                    withContext(Dispatchers.IO) {
                        val ap = MacroPadState.activeProfile.value
                        AppLog.i(
                            TAG,
                            "all modals closed → starting injectors for profile '${ap?.name}'" +
                                " (kb=${ap?.enableKeyboard} gp=${ap?.enableGamepad} ms=${ap?.enableMouse})"
                        )
                        try {
                            if (ap?.enableKeyboard == true) {
                                KeyInjector.start(context)
                                ensureActive()
                            }
                            if (ap?.enableGamepad == true) {
                                GamepadInjector.start(context)
                                ensureActive()
                            }
                            if (ap?.enableMouse == true) {
                                MouseInjector.start(context)
                                ensureActive()
                            }
                        } catch (e: CancellationException) {
                            // A modal opened while we were starting binaries.
                            // Stop whatever was already started before propagating.
                            AppLog.i(TAG, "start sequence cancelled mid-flight → stopping injectors")
                            KeyInjector.stop()
                            GamepadInjector.stop()
                            MouseInjector.stop()
                            throw e
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Shared debounce constant — must match across all consumers. */
        const val RESTART_DEBOUNCE_MS = 150L
    }
}
