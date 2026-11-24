package com.kjota.ritmo.player

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Prefs
import com.kjota.ritmo.engine.YouTube
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reproductor en segundo plano (notificación + pantalla de bloqueo).
 * Cada canción se identifica como "ritmo:<id de YouTube>". Al cargarla:
 *  - si está descargada, se reproduce el archivo del teléfono (sin internet);
 *  - si no, se pide a yt-dlp la dirección directa del audio y se reproduce en streaming.
 *
 * MODO MIX DJ: cuando está activado y a la canción le quedan unos segundos, el reproductor principal
 * salta a la siguiente canción (sube el volumen) mientras un segundo reproductor ("cola") sigue
 * sonando el final de la anterior (baja el volumen). Es una mezcla con curva de potencia constante.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var tail: ExoPlayer

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    // Estado de la mezcla
    private var inMix = false
    private var expectTransition = false
    private var mixMs = 8_000L
    private var mixElapsed = 0L
    private var lastTick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            try { tick() } catch (_: Throwable) { endMix() }
            handler.postDelayed(this, if (inMix) 80L else 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Library.init(this)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val upstream = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(upstream) { spec -> resolve(spec) }

        player = buildPlayer(resolving, mainPlayer = true)
        tail = buildPlayer(resolving, mainPlayer = false)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!inMix) return
                // La primera transición después de empezar la mezcla es la nuestra; otra = el usuario saltó.
                if (expectTransition) expectTransition = false else endMix()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (inMix) tail.playWhenReady = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                if (inMix) endMix()
            }
        })

        session = MediaSession.Builder(this, player).build()

        ticking = true
        handler.postDelayed(ticker, 250L)
    }

    private fun buildPlayer(resolving: DataSource.Factory, mainPlayer: Boolean): ExoPlayer {
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_000, 4_000)
            .build()
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                mainPlayer, // solo el principal maneja el foco de audio
            )
            .setHandleAudioBecomingNoisy(mainPlayer)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    // ------------------------------------------------------------------ Mix DJ

    private fun mixEnabled() = Prefs.getLong(this, "mix_on", 0L) == 1L

    private fun tick() {
        if (inMix) {
            stepMix()
            return
        }
        if (!mixEnabled()) return
        val p = player
        if (!p.isPlaying) return
        if (p.repeatMode == Player.REPEAT_MODE_ONE) return
        if (!p.hasNextMediaItem()) return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur < 45_000L) return
        val fade = Prefs.getLong(this, "mix_sec", 8L).coerceIn(3L, 20L) * 1000L
        val remaining = dur - p.currentPosition
        if (remaining in 2_500L..fade) startMix(min(fade, remaining - 800L))
    }

    private fun startMix(ms: Long) {
        val item = player.currentMediaItem ?: return
        mixMs = ms.coerceAtLeast(1_500L)
        mixElapsed = 0L
        lastTick = SystemClock.elapsedRealtime()
        inMix = true
        expectTransition = true

        // La "cola": sigue sonando el final de la canción que termina.
        tail.setMediaItem(item, player.currentPosition + 300L)
        tail.volume = 1f
        tail.prepare()
        tail.playWhenReady = true

        // El principal pasa a la siguiente canción, empezando en silencio.
        player.volume = 0f
        player.seekToNextMediaItem()
    }

    private fun stepMix() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        // Solo avanza la mezcla mientras la nueva canción ya está sonando (así no se pierde en silencio).
        if (player.isPlaying) mixElapsed += dt
        val x = (mixElapsed.toFloat() / mixMs).coerceIn(0f, 1f)
        player.volume = sin(x * PI / 2).toFloat()
        tail.volume = cos(x * PI / 2).toFloat()
        if (x >= 1f) endMix()
    }

    private fun endMix() {
        inMix = false
        expectTransition = false
        mixElapsed = 0L
        try {
            tail.stop()
            tail.clearMediaItems()
        } catch (_: Throwable) { }
        player.volume = 1f
    }

    // ------------------------------------------------------------------ Resolución de canciones

    private fun resolve(spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != "ritmo") return spec
        val id = uri.schemeSpecificPart
        val local = Library.localFile(id)
        if (local != null) return spec.withUri(Uri.fromFile(local))
        try {
            return spec.withUri(Uri.parse(YouTube.streamUrl(id)))
        } catch (e: Throwable) {
            throw IOException("No se pudo obtener el audio: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacksAndMessages(null)
        try { tail.release() } catch (_: Throwable) { }
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
