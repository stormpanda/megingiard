package com.stormpanda.megingiard.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal allowing UI components (such as [GamepadTextFieldCard]) to request
 * or dismiss the host app's dedicated fullscreen virtual keyboard without depending on host-specific
 * state holders (e.g. Companion's AppStateManager).
 *
 * Defaults to null (no-op). Host apps or overlays provide an implementation where fullscreen keyboard
 * switching is supported.
 */
val LocalFullscreenKeyboardRequester = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }
