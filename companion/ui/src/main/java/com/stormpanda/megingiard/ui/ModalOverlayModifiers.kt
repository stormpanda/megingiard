package com.stormpanda.megingiard.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

internal const val MODAL_OVERLAY_ANIMATION_DURATION_MS = 250

/**
 * Standard enter transition for modal scrims.
 */
internal fun modalOverlayScrimEnter(): EnterTransition = fadeIn(animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS))

/**
 * Standard exit transition for modal scrims.
 */
internal fun modalOverlayScrimExit(): ExitTransition = fadeOut(animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS))

/**
 * Standard bottom-sheet slide-up and fade-in enter transition.
 */
internal fun modalOverlaySheetEnter(): EnterTransition =
    slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
    ) + fadeIn(animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS))

/**
 * Standard bottom-sheet slide-down and fade-out exit transition.
 */
internal fun modalOverlaySheetExit(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(MODAL_OVERLAY_ANIMATION_DURATION_MS))

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
fun Modifier.blockPointerEvents(): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
                // Intentionally not consuming — hit-test opacity alone is sufficient
                // to prevent events from reaching z-order siblings below.
            }
        }
    }

/**
 * App-wide bezel light refraction border brush.
 *
 * Features a primary top-left white highlight (0.0f) and an accent bottom-right
 * white highlight (1.0f) fading through semi-transparent midtones.
 */
@Composable
internal fun rememberBezelBrush(): Brush =
    remember {
        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0.0f to Color.White.copy(alpha = 0.25f),
                    0.25f to Color.White.copy(alpha = 0.05f),
                    0.5f to Color.Transparent,
                    0.833f to Color.White.copy(alpha = 0.05f),
                    1.0f to Color.White.copy(alpha = 0.25f),
                ),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        )
    }

/**
 * Draws a 3-sided bezel border (left, top-left arc, top, top-right arc, right) leaving the
 * bottom edge open. Used so bottom sheets sit flush with the bottom screen edge.
 */
internal fun Modifier.topAndSideBezelBorder(
    strokeWidth: Dp,
    brush: Brush,
    topCornerRadius: Dp,
): Modifier =
    this.drawWithContent {
        drawContent()
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        val r = topCornerRadius.toPx()
        val path =
            Path().apply {
                moveTo(inset, size.height)
                lineTo(inset, r)
                arcTo(
                    rect = Rect(inset, inset, 2 * r - inset, 2 * r - inset),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - r, inset)
                arcTo(
                    rect = Rect(size.width - 2 * r + inset, inset, size.width - inset, 2 * r - inset),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - inset, size.height)
            }
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = sw),
        )
    }
