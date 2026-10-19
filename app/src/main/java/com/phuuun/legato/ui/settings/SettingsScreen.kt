package com.phuuun.legato.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.library.Back

enum class SettingsSection(val label: String) { Music("MUSIC"), Audiobooks("AUDIOBOOKS"), Converter("CONVERTER") }

/** Two panes like a file manager: sections on the left, the chosen section's settings scroll on the right. */
@Composable
fun SettingsScreen(onBack: () -> Unit, pane: @Composable (SettingsSection) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(SettingsSection.Music) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Column(Modifier.width(120.dp)) {
                SettingsSection.entries.forEach { s ->
                    Text(
                        s.label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (s == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (s == selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { selected = s }.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
            VerticalDivider(Modifier.fillMaxHeight(), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            Box(Modifier.weight(1f)) { pane(selected) }
        }
    }
}
