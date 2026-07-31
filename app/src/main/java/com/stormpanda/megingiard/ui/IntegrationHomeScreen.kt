package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState

private const val TAG = "IntegrationHomeScreen"

private val IH_CARD_SHAPE = RoundedCornerShape(16.dp)
private val IH_PADDING_CARD = 16.dp
private val IH_PADDING_SCREEN = 24.dp
private val IH_SPACING_CARD = 12.dp
private val IH_SPACING_SECTION = 20.dp
private val IH_SPACING_STATUS = 8.dp
private val IH_STATUS_DOT_SIZE = 12.dp
private val IH_STATUS_ICON_SIZE = 24.dp

private const val IH_BATTERY_LOW_THRESHOLD = 20
private val IH_BATTERY_ICON_SIZE = 22.dp
private val IH_BATTERY_SPACING = 6.dp

@Composable
fun IntegrationHomeScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current

    val clientPackage by AppStateManager.externalClientPackage.collectAsState()
    val isClientActive by AppStateManager.isExternalClientActive.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val hoveredAppLabel by AppStateManager.hoveredAppLabel.collectAsState()

    val privdState by PrivdManager.state.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isAccessibilityActive by AppStateManager.isAccessibilityActive.collectAsState()

    val batteryState = rememberBatteryState()
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val formatter =
            java.time.format.DateTimeFormatter
                .ofPattern("HH:mm")
        while (true) {
            timeText =
                java.time.LocalDateTime
                    .now()
                    .format(formatter)
            kotlinx.coroutines.delay(1000)
        }
    }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "IntegrationHomeScreen: ON_START")
        onDispose {
            AppLog.d(TAG, "IntegrationHomeScreen: ON_DISPOSE")
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .padding(IH_PADDING_SCREEN),
        verticalArrangement = Arrangement.spacedBy(IH_SPACING_SECTION),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header Row (Clock on Left, Battery on Right)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = IH_SPACING_SECTION),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clock (Upper Left)
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )

            // Battery Indicator (Upper Right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IH_BATTERY_SPACING),
            ) {
                val batteryIcon =
                    when {
                        batteryState.isCharging -> Icons.Rounded.BatteryChargingFull
                        batteryState.percentage <= IH_BATTERY_LOW_THRESHOLD -> Icons.Rounded.BatteryAlert
                        else -> Icons.Rounded.BatteryFull
                    }
                Icon(
                    imageVector = batteryIcon,
                    contentDescription = null,
                    tint =
                        if (batteryState.percentage <= IH_BATTERY_LOW_THRESHOLD &&
                            !batteryState.isCharging
                        ) {
                            Color.Red
                        } else {
                            colors.onSurface
                        },
                    modifier = Modifier.size(IH_BATTERY_ICON_SIZE),
                )
                Text(
                    text = "${batteryState.percentage}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Connected Client Card
        InfoCard(
            title = stringResource(R.string.integration_home_connected_client),
            value = if (isClientActive) clientPackage ?: "Unknown" else "None",
            icon = Icons.Rounded.Link,
            colors = colors,
            isHighlight = isClientActive,
            isMonospace = true,
        )

        // Hovered Game/App Card (only when gamefocus is active)
        val isGameFocus = isClientActive && clientPackage?.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE) == true
        if (isGameFocus) {
            InfoCard(
                title = stringResource(R.string.integration_home_hovered_game),
                value = hoveredAppLabel ?: stringResource(R.string.integration_home_no_game_hovered),
                icon = Icons.Rounded.TouchApp,
                colors = colors,
                isHighlight = hoveredAppLabel != null,
            )
        }

        // Active Profile Card
        InfoCard(
            title = stringResource(R.string.integration_home_active_profile),
            value = activeProfile?.name ?: stringResource(R.string.integration_home_no_profile_active),
            icon = Icons.Rounded.Gamepad,
            colors = colors,
            isHighlight = activeProfile != null,
        )

        // Status Panel Card
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.controlOverlayBorder, IH_CARD_SHAPE),
            shape = IH_CARD_SHAPE,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Column(
                modifier = Modifier.padding(IH_PADDING_CARD),
                verticalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
            ) {
                Text(
                    text = stringResource(R.string.integration_home_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceSecondary,
                    fontWeight = FontWeight.SemiBold,
                )

                // Privileged Daemon Row
                StatusRow(
                    label = stringResource(R.string.integration_home_status_privd),
                    icon = Icons.Rounded.LockOpen,
                    isActive = privdState == PrivdState.RUNNING,
                    activeLabel = stringResource(R.string.integration_home_status_running),
                    inactiveLabel = stringResource(R.string.integration_home_status_stopped),
                    colors = colors,
                )

                // Mirror Session Row
                StatusRow(
                    label = stringResource(R.string.integration_home_status_mirror),
                    icon = Icons.Rounded.Airplay,
                    isActive = isCapturing,
                    activeLabel = stringResource(R.string.integration_home_status_active),
                    inactiveLabel = stringResource(R.string.integration_home_status_inactive),
                    colors = colors,
                )

                // Accessibility Service Row
                StatusRow(
                    label = stringResource(R.string.integration_home_status_accessibility),
                    icon = Icons.Rounded.Accessibility,
                    isActive = isAccessibilityActive,
                    activeLabel = stringResource(R.string.integration_home_status_active),
                    inactiveLabel = stringResource(R.string.integration_home_status_inactive),
                    colors = colors,
                )
            }
        }
    }
}

private data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
)

@Composable
private fun rememberBatteryState(): BatteryState {
    val context = androidx.compose.ui.platform.LocalContext.current
    var batteryState by remember { mutableStateOf(BatteryState(100, false)) }

    DisposableEffect(context) {
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: android.content.Context,
                    intent: android.content.Intent,
                ) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val isCharging =
                        status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL

                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                    batteryState = BatteryState(pct, isCharging)
                }
            }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = stickyIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging =
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            batteryState = BatteryState(pct, isCharging)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    colors: AppColors,
    isHighlight: Boolean,
    isMonospace: Boolean = false,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.controlOverlayBorder, IH_CARD_SHAPE),
        shape = IH_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Row(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isHighlight) colors.accent.copy(alpha = 0.15f) else colors.controlOverlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlight) colors.accent else colors.onSurfaceSecondary,
                    modifier = Modifier.size(IH_STATUS_ICON_SIZE),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = value,
                    style =
                        if (isMonospace) {
                            MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    color = if (isHighlight) colors.accent else colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    colors: AppColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_STATUS),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(IH_STATUS_ICON_SIZE),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_STATUS),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(IH_STATUS_DOT_SIZE)
                        .clip(CircleShape)
                        .background(if (isActive) Color.Green else colors.onSurfaceSecondary.copy(alpha = 0.4f)),
            )
            Text(
                text = if (isActive) activeLabel else inactiveLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) colors.accent else colors.onSurfaceSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
