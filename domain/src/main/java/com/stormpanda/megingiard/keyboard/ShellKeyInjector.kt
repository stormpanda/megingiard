package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.NativeBinaryInjector

/**
 * Injects keyboard events by piping commands to the native `keyinjector_arm64`
 * binary, which creates a virtual keyboard via `/dev/uinput` and writes Linux
 * `EV_KEY` events into the kernel input subsystem.
 *
 * ### Protocol
 * - `KD <linux_keycode>\n` — key down
 * - `KU <linux_keycode>\n` — key up
 *
 * Every key-down and key-up is delivered in order — no coalescing applied.
 * The binary registers KEY bits 1–255 only; codes ≥ 256 (BTN_* device buttons)
 * are rejected so Android classifies the virtual device as KEYBOARD.
 */
internal data class KeyCommand(val action: KeyAction, val linuxKeycode: Int)

object ShellKeyInjector : NativeBinaryInjector<KeyCommand>(
    workerThreadName = "KeyInjectorWriter",
) {
    override val tag = "ShellKeyInjector"
    override val assetName = "keyinjector_arm64"

    override fun formatCommand(cmd: KeyCommand): String {
        val prefix = when (cmd.action) {
            KeyAction.DOWN -> "KD"
            KeyAction.UP   -> "KU"
        }
        return "$prefix ${cmd.linuxKeycode}\n"
    }

    fun injectKey(action: KeyAction, linuxKeycode: Int) {
        // The native binary only registers KEY bits 1–255. Codes 256+ are BTN_*
        // (mouse/gamepad/stylus buttons) and are intentionally excluded so that
        // the virtual device is classified as KEYBOARD (not EXTERNAL_STYLUS) by Android.
        if (linuxKeycode !in 1..255) {
            AppLog.w(tag, "Ignoring out-of-range linuxKeycode: $linuxKeycode for action=$action")
            return
        }
        enqueue(KeyCommand(action, linuxKeycode))
    }
}
