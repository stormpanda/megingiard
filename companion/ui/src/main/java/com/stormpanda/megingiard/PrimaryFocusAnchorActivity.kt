package com.stormpanda.megingiard

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display

private const val TAG = "PrimaryFocusAnchorActivity"

/**
 * Lightweight, translucent trampoline Activity that anchors Android WindowManager
 * and InputDispatcher focus to the primary display (Display 0).
 *
 * When Megingiard (MainActivity) is launched or deployed on the secondary display (Display 4)
 * with FLAG_NOT_FOCUSABLE, this anchor ensures the system never leaves FocusedDisplayId
 * pointing at Display 4 without a focused window, preventing InputDispatcher ANRs while
 * strictly preserving MainActivity's unfocusable touch surface for gamepad macros.
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
        /**
         * Dispatches a transient launch request to the primary display (Display 0) to
         * ensure Android WindowManager and InputDispatcher maintain focus on Display 0.
         */
        fun anchorPrimaryFocus(context: Context) {
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
    }
}
