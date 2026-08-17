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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberBezelBrush
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
private val PR_ARROW_ICON_SIZE = 16.dp

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

    AppSettingsRow {
        Column(modifier = Modifier.weight(1f)) {
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privd_description),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state != PrivdState.RUNNING) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onShowWizard,
                enabled = state != PrivdState.BOOTSTRAPPING && state != PrivdState.CONNECTING,
            ) {
                Text(stringResource(R.string.privd_action_show_wizard))
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
                text = stringResource(R.string.privd_deadzone_desc),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.privd_deadzone_values,
                        (deadzoneLeft * 100).roundToInt(),
                        (deadzoneRight * 100).roundToInt(),
                    ),
                color = colors.accent,
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
