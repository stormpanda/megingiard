package com.stormpanda.megingiard.privd

/**
 * A single raw evdev event streamed from the `megingiard_privd` daemon via the
 * `SUB GAMEPAD` subscription.
 *
 * Wire format (ASCII, newline-terminated):
 *   `EVT <type> <code> <value>\n`
 *
 * [type] is the Linux input event type (EV_KEY = 1, EV_ABS = 3).
 * [code] is the event code (BTN_* or ABS_*).
 * [value] is the event value (1/0 for keys, raw int16 for axes).
 */
data class EvdevEvent(val type: Int, val code: Int, val value: Int)
