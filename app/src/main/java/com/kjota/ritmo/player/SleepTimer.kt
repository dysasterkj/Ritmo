package com.kjota.ritmo.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Temporizador de apagado (vive en el proceso del reproductor, así que sigue aunque cierres la pantalla).
 * [remainingMs]: 0 = apagado, -1 = "al terminar la canción", >0 = milisegundos que faltan.
 */
object SleepTimer {
    private val h = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var endAt = 0L

    private val _remaining = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remaining

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && _remaining.value == -1L) cancel()
        }
    }

    fun attach(p: ExoPlayer) {
        player = p
        p.addListener(listener)
    }

    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
    }

    private val tick = object : Runnable {
        override fun run() {
            val left = endAt - SystemClock.elapsedRealtime()
            if (left <= 0) { fadeOutAndPause(); return }
            _remaining.value = left
            h.postDelayed(this, 1000L)
        }
    }

    fun startMinutes(min: Int) {
        cancel()
        endAt = SystemClock.elapsedRealtime() + min * 60_000L
        _remaining.value = min * 60_000L
        h.postDelayed(tick, 1000L)
    }

    fun endOfSong() {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        _remaining.value = -1L
    }

    fun cancel() {
        h.removeCallbacksAndMessages(null)
        player?.pauseAtEndOfMediaItems = false
        _remaining.value = 0L
    }

    private fun fadeOutAndPause() {
        val p = player ?: run { cancel(); return }
        val start = p.volume
        var step = 0
        val steps = 16
        h.removeCallbacksAndMessages(null)
        h.post(object : Runnable {
            override fun run() {
                step++
                p.volume = (start * (1f - step / steps.toFloat())).coerceAtLeast(0f)
                if (step >= steps) {
                    p.pause()
                    p.volume = start
                    cancel()
                } else h.postDelayed(this, 500L)
            }
        })
    }
}
