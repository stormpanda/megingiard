package com.stormpanda.megingiard.input

/**
 * Injects mouse button presses and relative pointer movement by piping
 * commands to the native `mouseinjector_arm64` binary, which creates a
 * virtual mouse via `/dev/uinput`.
 *
 * ### Protocol
 * - `MB L D\n` / `MB L U\n` — left button down / up (R=right, M=middle, 4=BTN_SIDE, 5=BTN_EXTRA)
 * - `MM <dx> <dy>\n`         — relative pointer move (trackpoint)
 * - `MW <delta>\n`           — scroll wheel (positive = up)
 *
 * Consecutive MM commands are coalesced (keep-latest). Button and wheel
 * events are never dropped.
 */
internal sealed class MouseCommand {
    data class Button(val side: Char, val down: Boolean) : MouseCommand()
    data class Move(val dx: Int, val dy: Int) : MouseCommand()
    data class Wheel(val delta: Int) : MouseCommand()
}

internal object ShellMouseInjector : NativeBinaryInjector<MouseCommand>(
    workerThreadName = "MouseInjectorWriter",
) {
    override val tag = "ShellMouseInjector"
    override val assetName = "mouseinjector_arm64"

    override fun isCoalescible(cmd: MouseCommand): Boolean = cmd is MouseCommand.Move

    override fun formatCommand(cmd: MouseCommand): String = when (cmd) {
        is MouseCommand.Button -> "MB ${cmd.side} ${if (cmd.down) 'D' else 'U'}\n"
        is MouseCommand.Move   -> "MM ${cmd.dx} ${cmd.dy}\n"
        is MouseCommand.Wheel  -> "MW ${cmd.delta}\n"
    }

    fun buttonDown(side: Char) = enqueue(MouseCommand.Button(side = side.uppercaseChar(), down = true))
    fun buttonUp(side: Char)   = enqueue(MouseCommand.Button(side = side.uppercaseChar(), down = false))

    fun moveMouse(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        enqueue(MouseCommand.Move(dx = dx, dy = dy))
    }

    fun scrollWheel(delta: Int) {
        if (delta == 0) return
        enqueue(MouseCommand.Wheel(delta = delta))
    }
}
