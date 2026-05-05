package com.stormpanda.megingiard.gyro

/**
 * Selects which virtual input device receives gyroscope-derived movement events.
 *
 * [OFF]               — gyroscope input is disabled.
 * [GAMEPAD_LEFT_STICK] — maps gyro X/Y rotation to the left analog stick (ABS_X / ABS_Y).
 * [GAMEPAD_RIGHT_STICK]— maps gyro X/Y rotation to the right analog stick (ABS_Z / ABS_RZ).
 * [MOUSE]             — maps gyro X/Y rotation to relative mouse (REL_X / REL_Y) movement.
 */
enum class GyroOutput {
    OFF,
    GAMEPAD_LEFT_STICK,
    GAMEPAD_RIGHT_STICK,
    MOUSE,
}
