package com.kjota.ritmo.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.kjota.ritmo.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ecualizador del sistema (Equalizer + BassBoost + LoudnessEnhancer) sobre la sesión de audio de los dos
 * reproductores. Los valores se guardan en Prefs: eq_on, eq_b<i> (milibelios), bass (0..1000), loud (milibelios).
 */
object AudioFx {
    data class Band(val centerHz: Int, val minDb: Float, val maxDb: Float)

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands: StateFlow<List<Band>> = _bands

    fun attach(ctx: Context, sessionId: Int) {
        release()
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val range = e.bandLevelRange
            val list = ArrayList<Band>()
            for (i in 0 until e.numberOfBands.toInt()) {
                list.add(Band(e.getCenterFreq(i.toShort()) / 1000, range[0] / 100f, range[1] / 100f))
            }
            _bands.value = list
        } catch (_: Throwable) {
            eq = null
        }
        try { bass = BassBoost(0, sessionId) } catch (_: Throwable) { bass = null }
        try { loud = LoudnessEnhancer(sessionId) } catch (_: Throwable) { loud = null }
        apply(ctx)
    }

    /** Lee los valores guardados y los aplica a los efectos. */
    fun apply(ctx: Context) {
        val on = Prefs.getLong(ctx, "eq_on", 0L) == 1L
        try {
            eq?.let { e ->
                val range = e.bandLevelRange
                for (i in 0 until e.numberOfBands.toInt()) {
                    val v = Prefs.getLong(ctx, "eq_b$i", 0L).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(i.toShort(), v.toShort())
                }
                e.enabled = on
            }
        } catch (_: Throwable) { }
        try {
            bass?.let {
                if (it.strengthSupported) it.setStrength(Prefs.getLong(ctx, "bass", 0L).toInt().coerceIn(0, 1000).toShort())
                it.enabled = on && Prefs.getLong(ctx, "bass", 0L) > 0L
            }
        } catch (_: Throwable) { }
        try {
            loud?.let {
                it.setTargetGain(Prefs.getLong(ctx, "loud", 0L).toInt().coerceIn(0, 1500))
                it.enabled = on && Prefs.getLong(ctx, "loud", 0L) > 0L
            }
        } catch (_: Throwable) { }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) { }
        try { bass?.release() } catch (_: Throwable) { }
        try { loud?.release() } catch (_: Throwable) { }
        eq = null; bass = null; loud = null
    }
}
