package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale

private const val TAG = "TimelineBaseComponents"

private const val MTE_PADDING = 16
private val MTE_ACTION_BTN_HEIGHT = 44.dp
private val MTE_SECTION_HEADER_V_PADDING = 10.dp

@Composable
internal fun MtSectionHeader(textRes: Int) {
    val colors = LocalAppColors.current
    Text(
        text     = stringResource(textRes).uppercase(Locale.ROOT),
        color    = colors.sectionHeaderColor,
        style    = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = MTE_PADDING.dp, vertical = MTE_SECTION_HEADER_V_PADDING),
    )
}

@Composable
internal fun StepActionRow(
    steps: List<MacroStep>,
    accentColor: Color,
    onAdd: () -> Unit,
    onRecordGamepad: () -> Unit,
    onRecordTouch: () -> Unit,
    onTest: () -> Unit,
) {
    val btnShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MTE_PADDING.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(MTE_ACTION_BTN_HEIGHT)
                .clip(btnShape)
                .border(1.dp, accentColor.copy(alpha = 0.5f), btnShape)
                .clickable { onAdd() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.macropad_macro_add_step), color = accentColor, style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(MTE_ACTION_BTN_HEIGHT)
                .clip(btnShape)
                .border(1.dp, accentColor.copy(alpha = 0.5f), btnShape)
                .clickable { onRecordGamepad() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MaterialSymbol(name = "sports_esports", size = 18.dp, tint = accentColor, filled = true)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.macropad_macro_record_gamepad), color = accentColor, style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(MTE_ACTION_BTN_HEIGHT)
                .clip(btnShape)
                .border(1.dp, accentColor.copy(alpha = 0.5f), btnShape)
                .clickable { onRecordTouch() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = stringResource(R.string.cd_record_touch), tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.macropad_macro_record_touch), color = accentColor, style = MaterialTheme.typography.bodyMedium)
        }

        if (steps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(MTE_ACTION_BTN_HEIGHT)
                    .clip(btnShape)
                    .border(1.dp, accentColor.copy(alpha = 0.5f), btnShape)
                    .clickable { onTest() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_test_macro), tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.macropad_macro_test_run), color = accentColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
