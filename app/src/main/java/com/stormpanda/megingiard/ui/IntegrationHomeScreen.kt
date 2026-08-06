package com.stormpanda.megingiard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.focus.rom.EmulatorDetectionFunnel
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.mirror.ScreenCutout
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val TAG = "IntegrationHomeScreen"

private val IH_CARD_SHAPE = RoundedCornerShape(16.dp)
private val IH_DECK_CARD_SHAPE = RoundedCornerShape(14.dp)
private val IH_PADDING_CARD = 16.dp
private val IH_PADDING_SCREEN = 20.dp
private val IH_PADDING_HEADER_TOP = 12.dp
private val IH_PADDING_HEADER_BOTTOM = 8.dp
private val IH_SPACING_CARD = 12.dp
private val IH_SPACING_SECTION = 16.dp
private val IH_SPACING_GRID = 12.dp
private val IH_HERO_ICON_SIZE = 32.dp
private val IH_HERO_ICON_BG_SIZE = 56.dp
private val IH_DECK_ICON_BG_SIZE = 44.dp
private val IH_DECK_ICON_SIZE = 24.dp

private const val IH_BATTERY_LOW_THRESHOLD = 20
private val IH_BATTERY_ICON_SIZE = 20.dp
private val IH_BATTERY_SPACING = 6.dp

private const val IH_AMBIENT_PRIMARY_ALPHA = 0.22f
private const val IH_AMBIENT_SECONDARY_ALPHA = 0.12f
private const val IH_BG_LOGO_ALPHA = 0.085f
private const val IH_BG_LOGO_WIDTH_FRACTION = 0.65f
private const val IH_BG_LOGO_ASPECT_RATIO = 2038f / 1076f
private const val IH_CARD_BG_ALPHA = 0.30f
private const val IH_COLOR_TRANSITION_DURATION_MS = 800
private const val IH_CLOCK_UPDATE_INTERVAL_MS = 1000L
private const val IH_BATTERY_MAX = 100

private val IH_BORDER_WIDTH = 1.dp
private val IH_BUTTON_CORNER_RADIUS = 10.dp
private val IH_BUTTON_ICON_SIZE = 16.dp
private val IH_BUTTON_ICON_SPACING = 6.dp
private const val IH_HIGHLIGHT_ALPHA = 0.15f
private val IH_BUTTON_SPACING = 10.dp

private val IH_BATTERY_LOW_COLOR = Color(0xFFE57373)

@Composable
fun IntegrationHomeScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current

    val clientPackage by AppStateManager.externalClientPackage.collectAsState()
    val isClientActive by AppStateManager.isExternalClientActive.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val hoveredAppLabel by AppStateManager.hoveredAppLabel.collectAsState()

    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isGlobalSettingsOpen by AppStateManager.isGlobalSettingsOpen.collectAsState()

    val hoveredPrimaryColor by AppStateManager.hoveredAppPrimaryColor.collectAsState()
    val hoveredSecondaryColor by AppStateManager.hoveredAppSecondaryColor.collectAsState()
    val hoveredPackage by AppStateManager.hoveredAppPackageName.collectAsState()
    val hoveredRomPath by AppStateManager.hoveredRomPath.collectAsState()
    val hoveredSystemId by AppStateManager.hoveredSystemId.collectAsState()
    val activeSession by EmulatorDetectionFunnel.activeSession.collectAsState()
    val lastDetectedSession by EmulatorDetectionFunnel.lastDetectedSession.collectAsState()

    val isGameFocus = isClientActive && clientPackage?.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE) == true

    val targetPrimary =
        remember(isGameFocus, hoveredPrimaryColor) {
            if (isGameFocus && hoveredPrimaryColor != null) {
                Color(hoveredPrimaryColor!!).copy(alpha = IH_AMBIENT_PRIMARY_ALPHA)
            } else {
                colors.appBackground
            }
        }
    val targetSecondary =
        remember(isGameFocus, hoveredSecondaryColor) {
            if (isGameFocus && hoveredSecondaryColor != null) {
                Color(hoveredSecondaryColor!!).copy(alpha = IH_AMBIENT_SECONDARY_ALPHA)
            } else {
                colors.appBackground
            }
        }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = IH_COLOR_TRANSITION_DURATION_MS),
        label = "ambientPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = IH_COLOR_TRANSITION_DURATION_MS),
        label = "ambientSecondary",
    )

    val batteryState = rememberBatteryState()
    var timeText by remember { mutableStateOf("") }
    var showHubHelp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            timeText = LocalDateTime.now().format(formatter)
            delay(IH_CLOCK_UPDATE_INTERVAL_MS)
        }
    }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "IntegrationHomeScreen: ON_START")
        onDispose {
            AppLog.d(TAG, "IntegrationHomeScreen: ON_DISPOSE")
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(colors.appBackground, animatedSecondary, animatedPrimary),
                            ),
                    )
                },
    ) {
        // Background Logo Watermark (sits between ambient background and card boxes)
        Icon(
            painter = painterResource(R.drawable.ic_megingiard_logo),
            contentDescription = null,
            tint = colors.onSurface.copy(alpha = IH_BG_LOGO_ALPHA),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(IH_BG_LOGO_WIDTH_FRACTION)
                    .aspectRatio(IH_BG_LOGO_ASPECT_RATIO),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top Header Row (Clock on Left, "Megingiard" in Dead Center, Battery on Right)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = IH_PADDING_SCREEN,
                            top = IH_PADDING_HEADER_TOP,
                            end = IH_PADDING_SCREEN,
                            bottom = IH_PADDING_HEADER_BOTTOM,
                        ),
            ) {
                // Clock (Upper Left)
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                // "Megingiard" (Dead Center)
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Battery Indicator (Upper Right)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
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
                                IH_BATTERY_LOW_COLOR
                            } else {
                                colors.onSurfaceSecondary
                            },
                        modifier = Modifier.size(IH_BATTERY_ICON_SIZE),
                    )
                    Text(
                        text = "${batteryState.percentage}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurfaceSecondary,
                        fontWeight = FontWeight.Bold,
                    )

                    HelpIconButton(onClick = { showHubHelp = true })
                }
            }

            // Main Non-Scrollable Content Layout
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = IH_PADDING_SCREEN,
                            end = IH_PADDING_SCREEN,
                            top = IH_PADDING_HEADER_BOTTOM,
                            bottom = IH_PADDING_SCREEN,
                        ),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Featured Hero Game / Companion Card
                val profiles by MacroPadState.profiles.collectAsState()

                val targetPkg = hoveredPackage ?: activeSession?.packageName ?: lastDetectedSession?.packageName
                val targetLabel =
                    hoveredAppLabel ?: activeSession?.gameTitle ?: lastDetectedSession?.let { s ->
                        s.romPath?.let { File(it).name } ?: s.gameTitle
                    }
                val targetRom = hoveredRomPath ?: activeSession?.romPath ?: lastDetectedSession?.romPath
                val targetSystem = hoveredSystemId ?: activeSession?.systemId ?: lastDetectedSession?.systemId

                val associatedProfile =
                    remember(profiles, targetPkg, targetRom, targetSystem) {
                        if (targetPkg == null) {
                            null
                        } else {
                            profiles.firstOrNull { profile ->
                                profile.association?.romFileName != null &&
                                    profile.matches(targetPkg, targetRom, targetSystem)
                            } ?: profiles.firstOrNull { profile ->
                                profile.association?.romFileName == null &&
                                    profile.matches(targetPkg, targetRom, targetSystem)
                            }
                        }
                    }

                HeroCompanionCard(
                    targetPackage = targetPkg,
                    targetLabel = targetLabel,
                    targetRomPath = targetRom,
                    targetSystemId = targetSystem,
                    associatedProfile = associatedProfile,
                    activeProfile = activeProfile,
                    profiles = profiles,
                    colors = colors,
                )

                Spacer(modifier = Modifier.weight(1f))

                // 2. Companion Tools 2x2 Grid (Anchored at Bottom)
                CompanionToolsDeck(
                    activeProfile = activeProfile,
                    isFullscreenKeyboardActive = isFullscreenKeyboardActive,
                    isGlobalSettingsOpen = isGlobalSettingsOpen,
                    isFullscreenMouseActive = isFullscreenMouseActive,
                    colors = colors,
                )
            }
        }

        CompanionHubHelpModal(
            visible = showHubHelp,
            onDismiss = { showHubHelp = false },
        )
    }
}

@Composable
private fun CompanionHubHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_companion_hub_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_companion_hub_intro))

        HelpSection(stringResource(R.string.help_companion_hub_sec_hero))
        HelpEntry(
            icon = Icons.Rounded.Visibility,
            label = stringResource(R.string.integration_home_show_macropad),
            description = stringResource(R.string.help_companion_hub_hero_show_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.integration_home_edit_layout),
            description = stringResource(R.string.help_companion_hub_hero_edit_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Link,
            label = stringResource(R.string.integration_home_link_existing),
            description = stringResource(R.string.help_companion_hub_hero_link_desc),
        )

        HelpSection(stringResource(R.string.help_companion_hub_sec_tools))
        HelpEntry(
            icon = Icons.Rounded.Gamepad,
            label = stringResource(R.string.integration_home_tool_macropad),
            description = stringResource(R.string.help_companion_hub_macropad_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.integration_home_tool_settings),
            description = stringResource(R.string.help_companion_hub_settings_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Keyboard,
            label = stringResource(R.string.integration_home_tool_keyboard),
            description = stringResource(R.string.help_companion_hub_keyboard_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.TouchApp,
            label = stringResource(R.string.integration_home_tool_touchpad),
            description = stringResource(R.string.help_companion_hub_touchpad_desc),
        )
    }
}

@Composable
private fun HeroCompanionCard(
    targetPackage: String?,
    targetLabel: String?,
    targetRomPath: String?,
    targetSystemId: String?,
    associatedProfile: PadProfile?,
    activeProfile: PadProfile?,
    profiles: List<PadProfile>,
    colors: AppColors,
) {
    val context = LocalContext.current
    var expandedDropdown by remember { mutableStateOf(false) }
    val unassignedProfiles = remember(profiles) { profiles.filter { it.association == null } }

    val hasActiveGame = targetLabel != null || targetPackage != null

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(IH_BORDER_WIDTH, brush = rememberQuickMenuBezelBrush(), shape = IH_CARD_SHAPE),
        shape = IH_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = IH_CARD_BG_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(IH_HERO_ICON_BG_SIZE)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = IH_HIGHLIGHT_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (hasActiveGame) Icons.Rounded.TrackChanges else Icons.Rounded.Gamepad,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(IH_HERO_ICON_SIZE),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (hasActiveGame) {
                                (
                                    targetLabel ?: stringResource(
                                        R.string.integration_home_hovered_game,
                                    )
                                )
                            } else {
                                stringResource(R.string.integration_home_hero_idle_title)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.appMarquee(enabled = hasActiveGame),
                    )

                    if (hasActiveGame) {
                        val sysInfo =
                            listOfNotNull(
                                targetSystemId?.uppercase(),
                                targetRomPath?.substringAfterLast('/'),
                            ).joinToString(" • ")
                        if (sysInfo.isNotEmpty()) {
                            Text(
                                text = sysInfo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceSecondary,
                                maxLines = 1,
                                modifier = Modifier.appMarquee(),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.integration_home_hero_idle_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }
            }

            if (hasActiveGame && targetPackage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.integration_home_linked_profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                        Text(
                            text = associatedProfile?.name ?: stringResource(R.string.integration_home_no_profile_active),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (associatedProfile != null) colors.accent else colors.onSurfaceSecondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.appMarquee(),
                        )
                    }

                    if (associatedProfile != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_SPACING)) {
                            Button(
                                onClick = {
                                    MacroPadState.setActiveProfileId(associatedProfile.id)
                                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_ICON_SPACING),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(IH_BUTTON_ICON_SIZE),
                                    )
                                    Text(
                                        text = stringResource(R.string.integration_home_launch_controls),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    MacroPadState.setActiveProfileId(associatedProfile.id)
                                    AppStateManager.setEditorActive(true)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = colors.onSurface,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_ICON_SPACING),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(IH_BUTTON_ICON_SIZE),
                                    )
                                    Text(
                                        text = stringResource(R.string.integration_home_edit_layout),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_SPACING)) {
                            Button(
                                onClick = {
                                    val newProfileId = UUID.randomUUID().toString()
                                    val defaultLayoutId = UUID.randomUUID().toString()
                                    val assoc =
                                        ProfileAssociation(
                                            packageName = targetPackage,
                                            systemId = targetSystemId,
                                            romFileName = targetRomPath?.substringAfterLast('/'),
                                        )
                                    val newProfile =
                                        PadProfile(
                                            id = newProfileId,
                                            name = targetLabel ?: context.getString(R.string.integration_home_new_profile),
                                            association = assoc,
                                            layouts =
                                                listOf(
                                                    PadLayout(
                                                        id = defaultLayoutId,
                                                        name = context.getString(R.string.integration_home_default_layout),
                                                        mirrorCutouts = listOf(ScreenCutout.createDefault()),
                                                    ),
                                                ),
                                            activeLayoutId = defaultLayoutId,
                                        )
                                    MacroPadState.addProfile(newProfile)
                                    MacroPadState.setActiveProfileId(newProfileId)
                                    AppStateManager.setEditorActive(true)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_ICON_SPACING),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(IH_BUTTON_ICON_SIZE),
                                    )
                                    Text(
                                        text = stringResource(R.string.integration_home_create_profile),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = colors.onSurface,
                                        ),
                                    shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_ICON_SPACING),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(IH_BUTTON_ICON_SIZE),
                                        )
                                        Text(
                                            text = stringResource(R.string.integration_home_link_existing),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false },
                                    modifier = Modifier.background(colors.surface),
                                ) {
                                    if (unassignedProfiles.isEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.integration_home_no_unassigned_profiles),
                                                    color = colors.onSurfaceSecondary,
                                                )
                                            },
                                            onClick = { expandedDropdown = false },
                                        )
                                    } else {
                                        unassignedProfiles.forEach { profile ->
                                            DropdownMenuItem(
                                                text = { Text(profile.name, color = colors.onSurface) },
                                                onClick = {
                                                    expandedDropdown = false
                                                    val assoc =
                                                        ProfileAssociation(
                                                            packageName = targetPackage,
                                                            systemId = targetSystemId,
                                                            romFileName = targetRomPath?.substringAfterLast('/'),
                                                        )
                                                    val updatedProfile = profile.copy(association = assoc)
                                                    MacroPadState.updateProfile(updatedProfile)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeProfile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.integration_home_active_profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.appMarquee(),
                        )
                    }

                    Button(
                        onClick = {
                            AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                            ),
                        shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_ICON_SPACING),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(IH_BUTTON_ICON_SIZE),
                            )
                            Text(
                                text = stringResource(R.string.integration_home_launch_controls),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionToolsDeck(
    activeProfile: PadProfile?,
    isFullscreenKeyboardActive: Boolean,
    isGlobalSettingsOpen: Boolean,
    isFullscreenMouseActive: Boolean,
    colors: AppColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
    ) {
        // Top Row: Show Active Profile (Top Left) & Global Settings (Top Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
        ) {
            // Top Left: Show Active Profile (MacroPad)
            ToolCard(
                title = stringResource(R.string.integration_home_tool_macropad),
                subtitle = activeProfile?.name ?: stringResource(R.string.integration_home_macropad_desc),
                icon = Icons.Rounded.Gamepad,
                isActive = activeProfile != null,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD) },
            )

            // Top Right: Global Settings
            ToolCard(
                title = stringResource(R.string.integration_home_tool_settings),
                subtitle = stringResource(R.string.integration_home_settings_desc),
                icon = Icons.Rounded.Settings,
                isActive = isGlobalSettingsOpen,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setGlobalSettingsOpen(true) },
            )
        }

        // Bottom Row: Keyboard (Bottom Left) & Touchpad (Bottom Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
        ) {
            // Bottom Left: Keyboard
            ToolCard(
                title = stringResource(R.string.integration_home_tool_keyboard),
                subtitle = stringResource(R.string.integration_home_keyboard_desc),
                icon = Icons.Rounded.Keyboard,
                isActive = isFullscreenKeyboardActive,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setFullscreenKeyboardActive(!isFullscreenKeyboardActive) },
            )

            // Bottom Right: Touchpad
            ToolCard(
                title = stringResource(R.string.integration_home_tool_touchpad),
                subtitle = stringResource(R.string.integration_home_touchpad_desc),
                icon = Icons.Rounded.TouchApp,
                isActive = isFullscreenMouseActive,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setFullscreenMouseActive(!isFullscreenMouseActive) },
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            modifier
                .border(IH_BORDER_WIDTH, brush = rememberQuickMenuBezelBrush(), shape = IH_DECK_CARD_SHAPE)
                .clickable { onClick() },
        shape = IH_DECK_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = IH_CARD_BG_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(IH_DECK_ICON_BG_SIZE)
                        .clip(CircleShape)
                        .background(if (isActive) colors.accent.copy(alpha = IH_HIGHLIGHT_ALPHA) else colors.controlOverlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) colors.accent else colors.onSurfaceSecondary,
                    modifier = Modifier.size(IH_DECK_ICON_SIZE),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) colors.accent else colors.onSurfaceSecondary,
                    maxLines = 1,
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
    val context = LocalContext.current
    var batteryState by remember { mutableStateOf(BatteryState(IH_BATTERY_MAX, false)) }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val pct = if (level >= 0 && scale > 0) (level * IH_BATTERY_MAX / scale) else IH_BATTERY_MAX
                    batteryState = BatteryState(pct, isCharging)
                }
            }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * IH_BATTERY_MAX / scale) else IH_BATTERY_MAX
            batteryState = BatteryState(pct, isCharging)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}
