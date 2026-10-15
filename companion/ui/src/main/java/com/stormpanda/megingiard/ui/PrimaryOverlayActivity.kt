package com.stormpanda.megingiard.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import kotlinx.coroutines.launch

private const val TAG = "PrimaryOverlayActivity"

/**
 * Translucent, edge-to-edge Activity hosted on the primary display (Display 0).
 *
 * Hosts deep configuration dialogs, widescreen master-detail settings, button/layout
 * inspectors, setup wizards, and tutorials on the top screen while allowing the secondary
 * display to remain an unobstructed, live interactive action deck.
 */
class PrimaryOverlayActivity : ComponentActivity() {
    private var wasFrozenForModal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayId = runCatching { display?.displayId }.getOrNull()
        AppLog.i(TAG, "onCreate: started on display=$displayId")

        wasFrozenForModal = savedInstanceState?.getBoolean("wasFrozenForModal") ?: false
        if (savedInstanceState == null) {
            wasFrozenForModal = syncPrimaryOverlayMirrorFreeze(AppStateManager.activePrimaryModal.value, wasFrozenForModal)
        }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        lifecycleScope.launch {
            AppStateManager.activePrimaryModal.collect { modal ->
                if (modal == null) {
                    AppLog.i(TAG, "All primary overlays inactive -> finishing activity")
                    finish()
                    return@collect
                }
                wasFrozenForModal = syncPrimaryOverlayMirrorFreeze(modal, wasFrozenForModal)
            }
        }

        setContent {
            PrimaryOverlayRootContent(
                hostContext = this@PrimaryOverlayActivity,
                backDispatcherOwner = this@PrimaryOverlayActivity,
            )
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (dispatchPrimaryOverlayGenericMotionEvent(event, ::dispatchKeyEvent)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (dispatchPrimaryOverlayKeyEvent(KeyEvent.ACTION_DOWN, keyCode, this, ::dispatchKeyEvent)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (dispatchPrimaryOverlayKeyEvent(KeyEvent.ACTION_UP, keyCode, this, ::dispatchKeyEvent)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasFrozenForModal", wasFrozenForModal)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: isFinishing=$isFinishing")
        if (isFinishing) {
            AppStateManager.closePrimaryModal()
            AppStateManager.setActiveCropCutoutId(null)
            wasFrozenForModal = releasePrimaryOverlayMirrorFreeze(wasFrozenForModal)
        }
    }
}
