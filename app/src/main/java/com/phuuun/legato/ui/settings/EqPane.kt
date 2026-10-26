package com.phuuun.legato.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.phuuun.legato.ui.components.ChoicePill
import com.phuuun.legato.ui.components.boldSliderColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuuun.legato.playback.EqBands
import com.phuuun.legato.playback.EqPreset
import com.phuuun.legato.playback.EqSettings
import com.phuuun.legato.playback.EqStore
import kotlin.math.roundToInt

/** On/off, presets, and a slider per band. Changes apply live to whatever's playing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqPane(store: EqStore) {
    val eq by remember { store.settings }.collectAsState(EqSettings())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Equalizer", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(eq.on, { store.set(eq.copy(on = it)) })
        }
        Text("Music only: audiobooks always play as recorded.", style = MaterialTheme.typography.bodyMedium, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqPreset.entries.forEach { p -> ChoicePill(p.label, eq.preset == p, { store.set(eq.copy(on = true, gains = p.gains)) }, Modifier.padding(bottom = 8.dp)) }
            if (eq.preset == null) ChoicePill("Custom", true, {}, Modifier.padding(bottom = 8.dp))
        }
        EqBands.forEachIndexed { i, hz ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hz >= 1000) "${hz / 1000f}".removeSuffix(".0") + " kHz" else "$hz Hz", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(56.dp))
                // Half-dB steps, so a nudge back to 0 lands on 0 and the curve can match a preset again.
                Slider(
                    eq.gains[i], { v -> store.set(eq.copy(on = true, gains = eq.gains.toMutableList().also { it[i] = (v * 2).roundToInt() / 2f })) },
                    valueRange = -12f..12f, enabled = eq.on, modifier = Modifier.weight(1f),
                    colors = boldSliderColors(),
                )
                Text("%+.1f".format(eq.gains[i]), style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(44.dp).padding(start = 8.dp))
            }
        }
    }
}
