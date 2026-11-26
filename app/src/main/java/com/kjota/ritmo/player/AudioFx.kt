package com.kjota.ritmo.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.kjota.ritmo.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ecualizador del sistema (Equalizer + BassBoost + LoudnessEnhancer). Cada reproductor (el principal y la "cola"
 * del Mix DJ) tiene su PROPIA sesión de audio con sus propios efectos: así cada canción se procesa por separado.
 * (Antes compartían sesión: el limitador del volumen extra reaccionaba a la suma de las dos y el volumen
 * subía y bajaba solo durante la mezcla.) Valores en Prefs: eq_on, eq_b<i>, bass (0..1000), loud (milibelios).
 */
object AudioFx {
    data class Band(val centerHz: Int, val minDb: Float, val maxDb: Float)

    private class Chain(val eq: Equalizer?, val bass: BassBoost?, val loud: LoudnessEnhancer?) {
        fun release() {
            try { eq?.release() } catch (_: Throwable) { }
            try { bass?.release() } catch (_: Throwable) { }
            try { loud?.release() } catch (_: Throwable) { }
        }
    }

    private val chains = ArrayList<Chain>()

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands: StateFlow<List<Band>> = _bands

    @Synchronized
    fun attach(ctx: Context, vararg sessionIds: Int) {
        release()
        for (id in sessionIds) {
            var eq: Equalizer? = null
            try {
                val e = Equalizer(0, id)
                eq = e
                if (_bands.value.isEmpty()) {
                    val range = e.bandLevelRange
                    val list = ArrayList<Band>()
                    for (i in 0 until e.numberOfBands.toInt()) {
                        list.add(Band(e.getCenterFreq(i.toShort()) / 1000, range[0] / 100f, range[1] / 100f))
                    }
                    _bands.value = list
                }
            } catch (_: Throwable) {
                eq = null
            }
            val bass = try { BassBoost(0, id) } catch (_: Throwable) { null }
            val loud = try { LoudnessEnhancer(id) } catch (_: Throwable) { null }
            chains.add(Chain(eq, bass, loud))
        }
        apply(ctx)
    }

    /** Lee los valores guardados y los aplica a los efectos de todos los reproductores. */
    @Synchronized
    fun apply(ctx: Context) {
        val on = Prefs.getLong(ctx, "eq_on", 0L) == 1L
        for (ch in chains) {
            try {
                ch.eq?.let { e ->
                    val range = e.bandLevelRange
                    for (i in 0 until e.numberOfBands.toInt()) {
                        val v = Prefs.getLong(ctx, "eq_b$i", 0L).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                        e.setBandLevel(i.toShort(), v.toShort())
                    }
                    e.enabled = on
                }
            } catch (_: Throwable) { }
            try {
                ch.bass?.let {
                    if (it.strengthSupported) it.setStrength(Prefs.getLong(ctx, "bass", 0L).toInt().coerceIn(0, 1000).toShort())
                    it.enabled = on && Prefs.getLong(ctx, "bass", 0L) > 0L
                }
            } catch (_: Throwable) { }
            try {
                ch.loud?.let {
                    it.setTargetGain(Prefs.getLong(ctx, "loud", 0L).toInt().coerceIn(0, 1500))
                    it.enabled = on && Prefs.getLong(ctx, "loud", 0L) > 0L
                }
            } catch (_: Throwable) { }
        }
    }

    @Synchronized
    fun release() {
        chains.forEach { it.release() }
        chains.clear()
    }
}
