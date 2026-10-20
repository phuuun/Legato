package com.phuuun.legato.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The five bands Legato offers, in Hz. Each drives the phone equalizer's nearest band, whatever bands it has. */
val EqBands = listOf(60, 230, 910, 3_600, 14_000)

/** Gains per band, in dB, from -12 to +12. */
enum class EqPreset(val label: String, val gains: List<Float>) {
    Flat("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    Bass("Bass boost", listOf(6f, 4f, 0f, 0f, 0f)),
    Treble("Treble", listOf(0f, 0f, 0f, 3f, 6f)),
    Vocal("Vocal", listOf(-2f, 0f, 4f, 3f, 0f)),
    Rock("Rock", listOf(4f, 2f, -2f, 2f, 4f)),
    Pop("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
}

data class EqSettings(val on: Boolean = false, val gains: List<Float> = EqPreset.Flat.gains) {
    /** The preset these gains match, or null for a custom curve. */
    val preset: EqPreset? get() = EqPreset.entries.firstOrNull { it.gains == gains }
}

/** Equalizer settings: the Sound pane writes them, the playback service applies them (to music only). */
class EqStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    private fun read() = EqSettings(
        prefs.getBoolean("on", false),
        prefs.getString("gains", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == EqBands.size } ?: EqPreset.Flat.gains,
    )

    val settings: Flow<EqSettings> get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun set(s: EqSettings) = prefs.edit { putBoolean("on", s.on); putString("gains", s.gains.joinToString(",")) }
}
