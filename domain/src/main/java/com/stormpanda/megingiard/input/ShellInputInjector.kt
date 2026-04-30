package com.stormpanda.megingiard.input

/**
 * Injects touch events by piping text commands to the native `touchinjector_arm64`
 * binary, which writes Linux Multi-Touch Protocol Type B events directly to
 * `/dev/input/event6` (the primary touchscreen node on the AYN Thor).
 *
 * ### Why a native binary
 * The native binary opens the device node once and writes `struct input_event`
 * (24-byte kernel structs) directly — no Binder IPC, yielding < 1 ms per MOVE
 * vs ~7 ms for `cmd input motionevent`.
 *
 * ### Protocol
 * `D x y\n` — finger down, `M x y\n` — move, `U x y\n` — up.
 * Coordinates are in the sensor's physical portrait space: X ∈ [0, 1080], Y ∈ [0, 1920].
 *
 * Consecutive MOVE commands are coalesced (keep-latest) to prevent backlog.
 * DOWN and UP are never dropped.
 */
private data class TouchCommand(val action: TouchAction, val x: Int, val y: Int)

object ShellInputInjector : NativeBinaryInjector<TouchCommand>(
    workerThreadName = "TouchInjectorWriter",
) {
    override val tag = "ShellInputInjector"
    override val assetName = "touchinjector_arm64"

    private const val EVENT_NODE = "/dev/input/event6"

    override fun buildProcessArgs(binaryPath: String): List<String> =
        listOf(binaryPath, EVENT_NODE)

    override fun isCoalescible(cmd: TouchCommand): Boolean = cmd.action == TouchAction.MOVE

    override fun formatCommand(cmd: TouchCommand): String {
        val char = when (cmd.action) {
            TouchAction.DOWN -> "D"
            TouchAction.MOVE -> "M"
            TouchAction.UP   -> "U"
        }
        return "$char ${cmd.x} ${cmd.y}\n"
    }

    /**
     * Enqueues a touch event. Never blocks — MOVE coalescing happens in the
     * writer thread to eliminate any producer/consumer backlog.
     *
     * @param action DOWN / MOVE / UP
     * @param px Physical X in the touchscreen's portrait space [0, 1080]
     * @param py Physical Y in the touchscreen's portrait space [0, 1920]
     */
    fun injectTouch(action: TouchAction, px: Int, py: Int) {
        enqueue(TouchCommand(action, px, py))
    }
}
