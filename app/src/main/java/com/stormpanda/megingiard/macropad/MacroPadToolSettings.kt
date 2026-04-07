package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.SliderSettingRow
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tool-settings panel content for the MacroPad tool.
 * Shown inside [com.stormpanda.megingiard.settings.ToolSettingsPanel].
 *
 * @param onOpenEditor  Called when the user taps "Edit Layout…" to open the full-screen editor.
 */
@Composable
fun MacroPadToolSettings(onOpenEditor: () -> Unit) {
    val profiles    by MacroPadState.profiles.collectAsState()
    val activeId    by MacroPadState.activeProfileId.collectAsState()
    val colors      = LocalAppColors.current

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (profiles.isEmpty()) {
            Text(
                text     = stringResource(R.string.macropad_no_profile),
                color    = colors.onSurfaceSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            LayoutActionButtons(accentColor = colors.accent, onOpenEditor = onOpenEditor)
            return@Column
        }

        // ── Active profile picker ──────────────────────────────────────────
        SettingsLabel(stringResource(R.string.settings_macropad_profile), colors.accent)
        ProfileDropdown(
            profiles    = profiles,
            activeId    = activeId,
            accentColor = colors.accent,
            onSelect    = { MacroPadState.setActiveProfileId(it) },
        )

        HorizontalDivider(color = colors.divider)

        // ── Layout action buttons ───────────────────────────────────────────
        LayoutActionButtons(accentColor = colors.accent, onOpenEditor = onOpenEditor)

        HorizontalDivider(color = colors.divider)

        // ── Ambient Display settings ────────────────────────────────────────
        AmbientSettingsSection(accentColor = colors.accent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileDropdown(
    profiles:    List<PadProfile>,
    activeId:    String?,
    accentColor: Color,
    onSelect:    (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val colors = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = active?.name ?: "",
                color    = colors.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text  = p.name,
                            color = if (p.id == activeId) accentColor else colors.onSurface,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = { onSelect(p.id); expanded = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout action buttons (Edit + New, side by side)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutActionButtons(accentColor: Color, onOpenEditor: () -> Unit) {
    val defaultName = stringResource(R.string.macropad_editor_new_profile_name)
    Row(
        modifier             = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Edit current layout
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenEditor)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.Edit,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_edit_layout),
                color      = accentColor,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 6.dp),
            )
        }

        // New layout — creates a blank profile and opens the editor
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    val newProfile = PadProfile(
                        id   = UUID.randomUUID().toString(),
                        name = defaultName,
                    )
                    MacroPadState.addProfile(newProfile)
                    MacroPadState.setActiveProfileId(newProfile.id)
                    onOpenEditor()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.Add,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text       = stringResource(R.string.settings_macropad_new_layout),
                color      = accentColor,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ambient Display settings
// ─────────────────────────────────────────────────────────────────────────────

private const val MTS_BLUR_MAX = 25f
private const val MTS_BLUR_PERCENT_DIVISOR = 100f
private const val MTS_DIM_MAX = 0.9f

@Composable
private fun AmbientSettingsSection(accentColor: Color) {
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val ambientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()
    val ambientBlur    by SettingsManager.macropadAmbientBlur.collectAsState()
    val ambientDim     by SettingsManager.macropadAmbientDim.collectAsState()

    SettingsLabel(stringResource(R.string.settings_macropad_ambient), accentColor)

    // Toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_macropad_ambient),
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_macropad_ambient_hint),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = ambientEnabled,
            onCheckedChange = { scope.launch { SettingsManager.setMacropadAmbientEnabled(it) } },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }

    // Sliders – only shown when ambient is enabled
    if (ambientEnabled) {
        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_blur),
            value = ambientBlur,
            valueRange = 0f..MTS_BLUR_MAX,
            formatLabel = { "${(it / MTS_BLUR_MAX * MTS_BLUR_PERCENT_DIVISOR).toInt()}%" },
            accentColor = accentColor,
            onValueChange = { scope.launch { SettingsManager.setMacropadAmbientBlur(it) } }
        )

        SliderSettingRow(
            label = stringResource(R.string.settings_macropad_dim),
            value = ambientDim,
            valueRange = 0f..MTS_DIM_MAX,
            formatLabel = { "${(it * MTS_BLUR_PERCENT_DIVISOR).toInt()}%" },
            accentColor = accentColor,
            onValueChange = { scope.launch { SettingsManager.setMacropadAmbientDim(it) } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsLabel(text: String, accentColor: Color) {
    Text(text, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
