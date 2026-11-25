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
import kotlin.math.abs
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

    companion object {
        private const val GRACE_MS = 3_000L
        private const val PREROLL_MS = 10_000L
    }

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
    private var mixCurve = 0
    private var prerollId: String? = null
    private var lastSeekAt = 0L

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
            .setUserAgent(com.kjota.ritmo.engine.InnerTube.PLAYER_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val upstream = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(upstream) { spec -> resolve(spec) }

        player = buildPlayer(resolving, mainPlayer = true)
        tail = buildPlayer(resolving, mainPlayer = false)
        // Los dos reproductores comparten sesión de audio: así el ecualizador afecta a ambos durante la mezcla.
        tail.setAudioSessionId(player.getAudioSessionId())
        AudioFx.attach(this, player.getAudioSessionId())

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
            .setBufferDurationsMs(30_000, 120_000, 700, 2_500)
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

    private fun clearPreroll() {
        if (prerollId == null || inMix) return
        try {
            tail.stop()
            tail.clearMediaItems()
        } catch (_: Throwable) { }
        prerollId = null
    }

    private fun tick() {
        if (inMix) {
            stepMix()
            return
        }
        val p = player
        if (!mixEnabled() || p.repeatMode == Player.REPEAT_MODE_ONE || !p.hasNextMediaItem()) {
            clearPreroll()
            return
        }
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur < 45_000L) {
            clearPreroll()
            return
        }
        val fade = Prefs.getLong(this, "mix_sec", 8L).coerceIn(3L, 20L) * 1000L
        val remaining = dur - p.currentPosition
        // Mucho antes del final no hace falta nada; la "cola" se prepara unos segundos antes.
        if (remaining > fade + GRACE_MS + PREROLL_MS) {
            clearPreroll()
            return
        }
        val id = p.currentMediaItem?.mediaId ?: return
        ensurePreroll(p, id)
        if (remaining in 2_500L..(fade + GRACE_MS) && prerollReady(p, id)) {
            startMix(fade, remaining)
        }
    }

    /**
     * Pre-roll: la "cola" carga la MISMA canción que suena y la sigue en silencio, sincronizada.
     * Así, cuando empieza la mezcla ya está lista y no hay cortes ni silencios.
     */
    private fun ensurePreroll(p: ExoPlayer, id: String) {
        val now = SystemClock.elapsedRealtime()
        if (prerollId != id) {
            val item = p.currentMediaItem ?: return
            tail.volume = 0f
            tail.setMediaItem(item, p.currentPosition + 1_500L)
            tail.prepare()
            tail.playWhenReady = p.playWhenReady
            prerollId = id
            lastSeekAt = now
            return
        }
        if (tail.playWhenReady != p.playWhenReady) tail.playWhenReady = p.playWhenReady
        if (tail.playbackState == Player.STATE_READY && tail.isPlaying && p.isPlaying) {
            val drift = tail.currentPosition - p.currentPosition
            if (abs(drift) > 150L && now - lastSeekAt > 900L) {
                tail.seekTo(p.currentPosition + 80L)
                lastSeekAt = now
            }
        }
    }

    private fun prerollReady(p: ExoPlayer, id: String): Boolean =
        prerollId == id && p.isPlaying && tail.playbackState == Player.STATE_READY && tail.isPlaying &&
            abs(tail.currentPosition - p.currentPosition) < 350L

    private fun startMix(fade: Long, remaining: Long) {
        val item = player.currentMediaItem ?: return
        mixMs = min(fade, (remaining - 1_200L).coerceAtLeast(1_500L))
        mixElapsed = 0L
        lastTick = SystemClock.elapsedRealtime()
        mixCurve = Prefs.getLong(this, "mix_curve", 0L).toInt()
        inMix = true
        expectTransition = true

        val outTitle = item.mediaMetadata.title?.toString().orEmpty()
        val next = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex) else null
        MixBus.state.value = MixBus.State(true, 0f, outTitle, next?.mediaMetadata?.title?.toString().orEmpty())

        // La "cola" ya suena en sincronía (en silencio): pasa a volumen completo y sigue la canción que termina.
        tail.volume = 1f
        tail.playWhenReady = true

        // El principal salta a la siguiente canción empezando en silencio.
        player.volume = 0f
        player.seekToNextMediaItem()
    }

    /** Ganancias (entra, sale) según la curva elegida. x va de 0 a 1. */
    private fun gains(x: Float): Pair<Float, Float> {
        return when (mixCurve) {
            1 -> { // corte rápido: cruce corto en el centro
                val t = ((x - 0.35f) / 0.30f).coerceIn(0f, 1f)
                sin(t * PI / 2).toFloat() to cos(t * PI / 2).toFloat()
            }
            2 -> x to (1f - x) // lineal
            else -> sin(x * PI / 2).toFloat() to cos(x * PI / 2).toFloat() // suave: potencia constante
        }
    }

    private fun stepMix() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        // La mezcla avanza solo mientras la nueva canción ya está sonando (así no se pierde en silencio).
        if (player.isPlaying) mixElapsed += dt
        val x = (mixElapsed.toFloat() / mixMs).coerceIn(0f, 1f)
        val (gin, gout) = gains(x)
        player.volume = gin
        tail.volume = gout
        MixBus.state.value = MixBus.state.value.copy(progress = x)
        val tailDead = tail.playbackState == Player.STATE_ENDED || tail.playbackState == Player.STATE_IDLE
        if (x >= 1f || tailDead) endMix()
    }

    private fun endMix() {
        inMix = false
        expectTransition = false
        mixElapsed = 0L
        prerollId = null
        try {
            tail.stop()
            tail.clearMediaItems()
        } catch (_: Throwable) { }
        player.volume = 1f
        MixBus.state.value = MixBus.State()
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
        AudioFx.release()
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
