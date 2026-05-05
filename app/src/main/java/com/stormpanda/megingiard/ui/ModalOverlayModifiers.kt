package com.stormpanda.megingiard.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes an in-tree full-screen overlay modal by participating in Compose's
 * hit-test, preventing events from reaching Compose nodes rendered behind it
 * in the same Box.
 *
 * Background: Compose does not automatically block touches on nodes behind a
 * visually-covering sibling. A `background()` modifier is purely visual — it
 * does not make the composable a hit-test target. Adding any `pointerInput`
 * modifier does: Compose's hit-testing delivers events to the deepest node in
 * z-order that has a pointer-input handler, so once the overlay claims the hit,
 * the sibling below is never in the dispatch path at all. No event consumption
 * is needed or desired — consuming would interfere with nested scrollable
 * children (e.g. `verticalScroll`).
 *
 * This matters for full-screen editor overlays that are kept in the same window
 * (rather than using AlertDialog / Dialog) in order to share the window's IME
 * focus and Compose state. Without this modifier, tapping the dialog background
 * falls through to interactive elements below (e.g. the EditorTopBar profile
 * selector behind PadButtonEditDialog).
 *
 * Usage: apply to the root Composable of any full-screen in-tree overlay:
 * ```
 * Column(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .background(colors.surface)
 *         .blockPointerEvents()
 * ) { … }
 * ```
 */
fun Modifier.blockPointerEvents(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
            // Intentionally not consuming — hit-test opacity alone is sufficient
            // to prevent events from reaching z-order siblings below.
        }
    }
}
