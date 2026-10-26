package com.phuuun.legato.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import com.phuuun.legato.ui.components.ChoicePill
import com.phuuun.legato.ui.theme.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.ui.library.Back

enum class SettingsSection(val label: String) { Music("Music"), Audiobooks("Audiobooks"), Converter("Converter"), Sound("Sound"), Backup("Backup") }

/** Two panes like a file manager: sections on the left, the chosen section's settings scroll on the right. */
@Composable
fun SettingsScreen(onBack: () -> Unit, pane: @Composable (SettingsSection) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(SettingsSection.Music) }
    Column(Modifier.screen()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Column(Modifier.width(136.dp).padding(start = 16.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection.entries.forEach { s -> ChoicePill(s.label, s == selected, { selected = s }, Modifier.fillMaxWidth()) }
            }
            VerticalDivider(Modifier.fillMaxHeight(), thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            // The chosen section's pane slides in from above or below, by where it sits in the list.
            AnimatedContent(selected, Modifier.weight(1f), transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInVertically(Motion.settle()) { dir * it / 6 } + fadeIn(Motion.settle())) togetherWith
                    (slideOutVertically(Motion.settle()) { -dir * it / 6 } + fadeOut(Motion.settle()))
            }, label = "settings pane") { pane(it) }
        }
    }
}
