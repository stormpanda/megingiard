package com.stormpanda.megingiard

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Display

private const val TAG = "PrimaryFocusAnchorActivity"
private const val ANCHOR_DEBOUNCE_INTERVAL_MS = 250L

/**
 * Lightweight, translucent trampoline Activity that anchors Android WindowManager
 * and InputDispatcher focus to the primary display (Display 0).
 *
 * When Megingiard (MainActivity) is launched, resumed, or left active on the secondary
 * display (Display 4) with FLAG_NOT_FOCUSABLE, this anchor ensures the system never leaves
 * FocusedDisplayId pointing at Display 4 without a focused window, preventing InputDispatcher
 * ANRs while strictly preserving MainActivity's unfocusable touch surface for gamepad macros.
 */
class PrimaryFocusAnchorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(TAG, "onCreate: primary focus anchor invoked -> finishing immediately")
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        @Volatile
        private var lastAnchorDispatchTimeMs = -ANCHOR_DEBOUNCE_INTERVAL_MS

        /**
         * Dispatches a transient launch request to the primary display (Display 0) to
         * ensure Android WindowManager and InputDispatcher maintain focus on Display 0.
         *
         * Calls are debounced by [ANCHOR_DEBOUNCE_INTERVAL_MS] unless [force] is true.
         * If [AppStateManager.isPrivdSetupWizardActive] is true, dispatch is suppressed
         * so the IME on the secondary display is not stripped of user input focus.
         */
        fun anchorPrimaryFocus(
            context: Context,
            force: Boolean = false,
        ) {
            if (AppStateManager.isPrivdSetupWizardActive.value) {
                AppLog.d(TAG, "anchorPrimaryFocus: suppressed while PrivdSetupWizard is active")
                return
            }

            val now = SystemClock.uptimeMillis()
            if (!force && (now - lastAnchorDispatchTimeMs) < ANCHOR_DEBOUNCE_INTERVAL_MS) {
                AppLog.d(
                    TAG,
                    "anchorPrimaryFocus: throttled (elapsed=${now - lastAnchorDispatchTimeMs}ms < ${ANCHOR_DEBOUNCE_INTERVAL_MS}ms)",
                )
                return
            }
            lastAnchorDispatchTimeMs = now

            try {
                val options =
                    ActivityOptions.makeBasic().apply {
                        setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    }
                val intent =
                    Intent(context, PrimaryFocusAnchorActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        )
                    }
                context.startActivity(intent, options.toBundle())
                AppLog.d(TAG, "anchorPrimaryFocus: dispatched to Display.DEFAULT_DISPLAY")
            } catch (e: Exception) {
                AppLog.e(TAG, "anchorPrimaryFocus: failed to dispatch anchor intent", e)
            }
        }

        internal fun resetDebounceForTesting() {
            lastAnchorDispatchTimeMs = -ANCHOR_DEBOUNCE_INTERVAL_MS
        }
    }
}
