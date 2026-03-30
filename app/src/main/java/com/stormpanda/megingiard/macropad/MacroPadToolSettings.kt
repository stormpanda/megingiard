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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val MS_SURFACE         = Color(0xFF1C1C1E)
private val MS_TEXT            = Color.White
private val MS_TEXT_SECONDARY  = Color.White.copy(alpha = 0.6f)
private val MS_DIVIDER         = Color.White.copy(alpha = 0.12f)
private val MS_BORDER          = Color.White.copy(alpha = 0.20f)

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
    val accentColor by SettingsManager.accentColor.collectAsState()

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (profiles.isEmpty()) {
            Text(
                text     = stringResource(R.string.macropad_no_profile),
                color    = MS_TEXT_SECONDARY,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            EditLayoutButton(accentColor = accentColor, onClick = onOpenEditor)
            return@Column
        }

        // ── Active profile picker ──────────────────────────────────────────
        SettingsLabel(stringResource(R.string.settings_macropad_profile), accentColor)
        ProfileDropdown(
            profiles    = profiles,
            activeId    = activeId,
            accentColor = accentColor,
            onSelect    = { MacroPadState.setActiveProfileId(it) },
        )

        HorizontalDivider(color = MS_DIVIDER)

        // ── Edit Layout button ─────────────────────────────────────────────
        EditLayoutButton(accentColor = accentColor, onClick = onOpenEditor)
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

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MS_BORDER, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = active?.name ?: "",
                color    = MS_TEXT,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MS_TEXT_SECONDARY)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(MS_SURFACE),
        ) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text  = p.name,
                            color = if (p.id == activeId) accentColor else MS_TEXT,
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
// Edit Layout button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditLayoutButton(accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            text     = stringResource(R.string.settings_macropad_edit_layout),
            color    = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
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
