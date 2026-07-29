package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Specification describing how a [GamePadButton] is rendered (either as a text letter or Material Symbol icon).
 */
sealed class GamePadButtonIconSpec {
    data class Letter(
        val text: String,
    ) : GamePadButtonIconSpec()

    data class Symbol(
        val name: String,
    ) : GamePadButtonIconSpec()
}

/**
 * Standard gamepad controls and their visual icon/letter representation mapping.
 */
enum class GamePadButton(
    val keyCode: Int,
) {
    BUTTON_A(KeyEvent.KEYCODE_BUTTON_A),
    BUTTON_B(KeyEvent.KEYCODE_BUTTON_B),
    BUTTON_X(KeyEvent.KEYCODE_BUTTON_X),
    BUTTON_Y(KeyEvent.KEYCODE_BUTTON_Y),
    BUTTON_L1(KeyEvent.KEYCODE_BUTTON_L1),
    BUTTON_R1(KeyEvent.KEYCODE_BUTTON_R1),
    BUTTON_L2(KeyEvent.KEYCODE_BUTTON_L2),
    BUTTON_R2(KeyEvent.KEYCODE_BUTTON_R2),
    DPAD_UP(KeyEvent.KEYCODE_DPAD_UP),
    DPAD_DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    DPAD_LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    DPAD_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT),
    START(KeyEvent.KEYCODE_BUTTON_START),
    ;

    /**
     * The fixed visual representation specification for this button.
     * - Face buttons use bold letters ("A", "B", "X", "Y").
     * - D-Pad directions use Material Symbol icons ("gamepad_up", "gamepad_down", "gamepad_left", "gamepad_right").
     * - Shoulder buttons use Material Symbol icons ("game_button_l1", "game_button_r1", "game_button_l2", "game_button_r2").
     * - Special buttons (SELECT, START) use pill cutout letters ("SELECT", "START").
     */
    val iconSpec: GamePadButtonIconSpec
        get() =
            when (this) {
                BUTTON_A -> GamePadButtonIconSpec.Letter("A")
                BUTTON_B -> GamePadButtonIconSpec.Letter("B")
                BUTTON_X -> GamePadButtonIconSpec.Letter("X")
                BUTTON_Y -> GamePadButtonIconSpec.Letter("Y")
                BUTTON_L1 -> GamePadButtonIconSpec.Symbol("game_button_l1")
                BUTTON_R1 -> GamePadButtonIconSpec.Symbol("game_button_r1")
                BUTTON_L2 -> GamePadButtonIconSpec.Symbol("game_button_l2")
                BUTTON_R2 -> GamePadButtonIconSpec.Symbol("game_button_r2")
                DPAD_UP -> GamePadButtonIconSpec.Symbol("gamepad_up")
                DPAD_DOWN -> GamePadButtonIconSpec.Symbol("gamepad_down")
                DPAD_LEFT -> GamePadButtonIconSpec.Symbol("gamepad_left")
                DPAD_RIGHT -> GamePadButtonIconSpec.Symbol("gamepad_right")
                SELECT -> GamePadButtonIconSpec.Letter("SELECT")
                START -> GamePadButtonIconSpec.Letter("START")
            }
}

/**
 * Reusable Composable rendering a solid filled circle with a [GamePadButton]'s letter or icon cutout.
 *
 * @param button The gamepad button to display.
 */
@Composable
fun GamePadButtonIcon(
    button: GamePadButton,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
) {
    when (val spec = button.iconSpec) {
        is GamePadButtonIconSpec.Letter -> {
            CutoutLetterCircleIcon(
                letter = spec.text,
                modifier = modifier,
                size = size,
                tint = tint,
                cutoutColor = cutoutColor,
            )
        }

        is GamePadButtonIconSpec.Symbol -> {
            CutoutSymbolCircleIcon(
                symbolName = spec.name,
                modifier = modifier,
                size = size,
                tint = tint,
                cutoutColor = cutoutColor,
            )
        }
    }
}

/**
 * Reusable subdued TextButton containing a [GamePadButtonIcon] + label text.
 *
 * @param button The gamepad button to display.
 * @param text Label text accompanying the gamepad button icon.
 * @param onClick Triggered when the button is clicked.
 */
@Composable
fun GamePadButtonAction(
    button: GamePadButton,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    iconSize: Dp = 18.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = tint,
        interactionSource = interactionSource,
        modifier = modifier.focusProperties { canFocus = false },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(contentPadding),
        ) {
            GamePadButtonIcon(
                button = button,
                size = iconSize,
                tint = tint,
                cutoutColor = cutoutColor,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}
