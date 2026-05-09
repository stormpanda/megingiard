package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents

private const val TAG = "PhysGamepadRecordSheet"

private const val PR_SHEET_PADDING = 24
private const val PR_DOT_SIZE = 12
private const val PR_SECTION_SPACING = 16
private const val PR_BTN_SPACING = 12

/**
 * Full-screen overlay shown while a [PhysicalGamepadRecordingManager] session is active.
 *
 * Unlike [GamepadRecordingOverlay], this sheet is read-only — it displays live physical
 * controller state (buttons pressed, stick deflection) and provides Stop/Cancel actions.
 * There are no virtual buttons to tap; input comes directly from the physical gamepad.
 *
 * @param state    Current recording state — [GamepadRecordingState.Recording] while active,
 *                 any other value while transitioning (sheet remains visible until the
 *                 parent composable hides it).
 * @param swapFaceButtons Whether A/B and X/Y labels should be swapped (NINTENDO layout).
 * @param onStop   Called when the user taps "Stop & Save".
 * @param onCancel Called when the user taps "Cancel".
 */
@Composable
internal fun PhysicalGamepadRecordingSheet(
    state: GamepadRecordingState,
    swapFaceButtons: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val recording = state as? GamepadRecordingState.Recording

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground.copy(alpha = 0.96f))
            .blockPointerEvents(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PR_SHEET_PADDING.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PR_SECTION_SPACING.dp),
        ) {
            /* ── Recording indicator ─────────────────────────────────────── */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(PR_DOT_SIZE.dp)
                        .clip(CircleShape)
                        .background(colors.error),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.privd_recording_physical_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.privd_recording_physical_hint),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(color = colors.divider)

            /* ── Live controller state ───────────────────────────────────── */
            if (recording != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (recording.pressedButtons.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.privd_recording_physical_pressed,
                                recording.pressedButtons
                                    .mapNotNull { code ->
                                        gamepadCodeDisplayLabel(code, swapFaceButtons, context)
                                            .takeIf { it.isNotBlank() }
                                    }
                                    .joinToString(" + "),
                            ),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    val leftMag = kotlin.math.sqrt(
                        recording.leftStickX * recording.leftStickX +
                            recording.leftStickY * recording.leftStickY,
                    )
                    val rightMag = kotlin.math.sqrt(
                        recording.rightStickX * recording.rightStickX +
                            recording.rightStickY * recording.rightStickY,
                    )

                    if (leftMag > 0.05f) {
                        Text(
                            text = stringResource(
                                R.string.privd_recording_physical_stick_left,
                                "%.2f".format(recording.leftStickX),
                                "%.2f".format(recording.leftStickY),
                            ),
                            color = colors.actionColorGamepad,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (rightMag > 0.05f) {
                        Text(
                            text = stringResource(
                                R.string.privd_recording_physical_stick_right,
                                "%.2f".format(recording.rightStickX),
                                "%.2f".format(recording.rightStickY),
                            ),
                            color = colors.actionColorGamepad,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                HorizontalDivider(color = colors.divider)
            }

            /* ── Actions ─────────────────────────────────────────────────── */
            Row(horizontalArrangement = Arrangement.spacedBy(PR_BTN_SPACING.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.privd_recording_physical_cancel),
                        color = colors.onSurfaceSecondary,
                    )
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.privd_recording_physical_stop))
                }
            }
        }
    }
}
