package com.stormpanda.megingiard.privd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.RememberSettingRow
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberQuickMenuBezelBrush
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val PR_CARD_PADDING = 16.dp
private val PR_ROW_V_PADDING = 12.dp
private val PR_STATUS_DOT_SIZE = 10.dp
private val PR_STATUS_DOT_GAP = 8.dp
private val PR_BUTTON_GAP = 8.dp
private val PR_PING_SPINNER_SIZE = 18.dp
private const val PR_PING_SPINNER_STROKE = 2
private const val PR_DIALOG_SCRIM_ALPHA = 0.5f
private const val PR_DIALOG_WIDTH_FRACTION = 0.85f
private val PR_DIALOG_CORNER = 16.dp
private val PR_DIALOG_PADDING = 20.dp
private val PR_ARROW_ICON_SIZE = 16.dp
private val PR_DIALOG_SLIDER_GAP = 8.dp
private val PR_DIALOG_PCT_WIDTH = 52.dp

/**
 * Privileged Mode settings card.
 *
 * Shows the current connection state (OFF / BOOTSTRAPPING / CONNECTING / RUNNING / FAILED),
 * a Connect / Disconnect button, a Test button (round-trips a `PING` to the
 * daemon), the show reconnect prompt toggle, the on-device bootstrap wizard, and the
 * per-feature sub-toggles.
 *
 * Bootstrap (Meilenstein B): the user opens the wizard, pairs the device with
 * its own ADB Wireless-Debugging service, and the wizard pushes the daemon
 * binary + spawns it. After a successful run, future app starts silently call
 * `PrivdManager.connect()` automatically, prompting the user if reconnection fails
 * (unless disabled via the reconnect prompt preference toggle).
 */
@Composable
internal fun PrivdSettingsCard(
    viewModel: GlobalSettingsViewModel,
    onShowWizard: () -> Unit,
) {
    val state by viewModel.privdState.collectAsState()
    val colors = LocalAppColors.current
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(PR_CARD_PADDING),
    ) {
        // ── Status row ──────────────────────────────────────────────────────
        Column {
            val (dotColor, textColor, label) =
                when (state) {
                    PrivdState.OFF -> {
                        Triple(colors.onSurfaceSecondary, colors.onSurfaceSecondary, stringResource(R.string.privd_status_off))
                    }

                    PrivdState.BOOTSTRAPPING -> {
                        Triple(colors.accent, colors.accent, stringResource(R.string.privd_status_bootstrapping))
                    }

                    PrivdState.CONNECTING -> {
                        Triple(colors.accent, colors.accent, stringResource(R.string.privd_status_connecting))
                    }

                    PrivdState.RUNNING -> {
                        Triple(colors.accent, colors.accent, stringResource(R.string.privd_status_running, PrivdConstants.PRIVD_VERSION))
                    }

                    PrivdState.FAILED -> {
                        Triple(colors.error, colors.error, stringResource(R.string.privd_status_failed))
                    }
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.privd_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(PR_STATUS_DOT_GAP))
                Box(
                    modifier =
                        Modifier
                            .size(PR_STATUS_DOT_SIZE)
                            .background(dotColor, CircleShape),
                )
            }
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(PR_BUTTON_GAP))
        Text(
            text = stringResource(R.string.privd_description),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        // ── Action buttons ──────────────────────────────────────────────────
        if (state != PrivdState.RUNNING) {
            Spacer(Modifier.height(PR_BUTTON_GAP))
            Row(horizontalArrangement = Arrangement.spacedBy(PR_BUTTON_GAP)) {
                Button(
                    onClick = { viewModel.privdConnect(context) },
                    enabled = state != PrivdState.BOOTSTRAPPING && state != PrivdState.CONNECTING,
                ) {
                    Text(stringResource(R.string.privd_action_connect))
                }
                TextButton(onClick = onShowWizard) {
                    Text(stringResource(R.string.privd_action_show_wizard))
                }
            }
        }
    }
}

@Composable
internal fun PrivdDeadzoneSettingsRow(
    deadzoneLeft: Float,
    deadzoneRight: Float,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    AppSettingsRow(onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.privd_deadzone_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                    stringResource(
                        R.string.privd_deadzone_desc,
                        (deadzoneLeft * 100).toInt(),
                        (deadzoneRight * 100).toInt(),
                    ),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(PR_ARROW_ICON_SIZE),
        )
    }
}

/**
 * In-tree dialog for configuring the per-stick dead zone used during physical
 * gamepad recording. Rendered at the GlobalSettingsScreen overlay level so it
 * covers the full Scaffold content.
 *
 * @param initialDeadzoneLeft  Current left-stick dead zone (0.0–1.0).
 * @param initialDeadzoneRight Current right-stick dead zone (0.0–1.0).
 * @param onConfirm            Called with the new (left, right) values when the user confirms.
 * @param onDismiss            Called when the dialog is dismissed without saving.
 */
@Composable
internal fun DeadzoneDialog(
    initialDeadzoneLeft: Float,
    initialDeadzoneRight: Float,
    onConfirm: (left: Float, right: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var leftPct by rememberSaveable { mutableIntStateOf((initialDeadzoneLeft * 100).roundToInt()) }
    var rightPct by rememberSaveable { mutableIntStateOf((initialDeadzoneRight * 100).roundToInt()) }
    val colors = LocalAppColors.current

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = PR_DIALOG_WIDTH_FRACTION,
        cornerRadius = PR_DIALOG_CORNER,
        contentPadding = PR_DIALOG_PADDING,
        scrimAlpha = PR_DIALOG_SCRIM_ALPHA,
    ) {
        Text(
            text = stringResource(R.string.privd_deadzone_dialog_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(PR_DIALOG_PADDING))
        Text(
            text = stringResource(R.string.privd_deadzone_dialog_hint),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(PR_DIALOG_PADDING))
        // ── Left stick ──────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.privd_deadzone_left),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = leftPct / 100f,
                onValueChange = { leftPct = (it * 100).roundToInt() },
                valueRange = 0f..1f,
                steps = 99,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$leftPct %",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .width(PR_DIALOG_PCT_WIDTH)
                        .padding(start = PR_DIALOG_SLIDER_GAP),
            )
        }
        Spacer(Modifier.height(PR_DIALOG_SLIDER_GAP))
        // ── Right stick ─────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.privd_deadzone_right),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = rightPct / 100f,
                onValueChange = { rightPct = (it * 100).roundToInt() },
                valueRange = 0f..1f,
                steps = 99,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$rightPct %",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .width(PR_DIALOG_PCT_WIDTH)
                        .padding(start = PR_DIALOG_SLIDER_GAP),
            )
        }
        Spacer(Modifier.height(PR_DIALOG_PADDING))
        // ── Action buttons ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.privd_deadzone_cancel))
            }
            Spacer(Modifier.width(PR_DIALOG_SLIDER_GAP))
            Button(onClick = { onConfirm(leftPct / 100f, rightPct / 100f) }) {
                Text(stringResource(R.string.privd_deadzone_ok))
            }
        }
    }
}
