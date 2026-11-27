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
        // Cada reproductor tiene su propia sesión y sus propios efectos (ver AudioFx).
        AudioFx.attach(this, player.getAudioSessionId(), tail.getAudioSessionId())
        SleepTimer.attach(player)

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

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) com.kjota.ritmo.widget.WidgetUpdater.fromPlayer(this@PlaybackService, p)
            }
        })

        session = MediaSession.Builder(this, player).build()

        // Como YouTube Music: al abrir (aunque la app se haya cerrado del todo) queda en la última canción, en pausa.
        restoreLastSession()
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) saveLastSession()
            }
        })
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!ticking) return
                if (player.isPlaying) saveLastSession()
                handler.postDelayed(this, 5000L)
            }
        }, 5000L)

        ticking = true
        handler.postDelayed(ticker, 250L)
    }

    private fun buildPlayer(resolving: DataSource.Factory, mainPlayer: Boolean): ExoPlayer {
        // La "cola" solo suena unos segundos: poco búfer = menos datos en paralelo durante la mezcla.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (mainPlayer) 30_000 else 12_000,
                if (mainPlayer) 120_000 else 25_000,
                if (mainPlayer) 700 else 500,
                if (mainPlayer) 2_500 else 1_500,
            )
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

    private var tailStallSince = 0L

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
        // La siguiente canción debe tener su dirección lista ANTES de la mezcla, o habría un hueco de silencio.
        if (p.hasNextMediaItem()) YouTube.prefetchNow(p.getMediaItemAt(p.nextMediaItemIndex).mediaId)
        // BPM de ambas canciones (para igualar el ritmo en la mezcla)
        p.currentMediaItem?.let { com.kjota.ritmo.engine.Bpm.ensure(it.mediaId, it.mediaMetadata.title?.toString().orEmpty(), it.mediaMetadata.artist?.toString().orEmpty()) }
        if (p.hasNextMediaItem()) p.getMediaItemAt(p.nextMediaItemIndex).let {
            com.kjota.ritmo.engine.Bpm.ensure(it.mediaId, it.mediaMetadata.title?.toString().orEmpty(), it.mediaMetadata.artist?.toString().orEmpty())
        }
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
            if (abs(drift) > 300L && now - lastSeekAt > 2_500L) {
                tail.seekTo(p.currentPosition + 80L)
                lastSeekAt = now
            }
        }
    }

    private fun prerollReady(p: ExoPlayer, id: String): Boolean =
        prerollId == id && p.isPlaying && tail.playbackState == Player.STATE_READY && tail.isPlaying &&
            abs(tail.currentPosition - p.currentPosition) < 350L

    private var mixRatio = 1f

    private fun startMix(fade: Long, remaining: Long) {
        val item = player.currentMediaItem ?: return
        mixMs = min(fade, (remaining - 1_200L).coerceAtLeast(1_500L))
        mixElapsed = 0L
        lastTick = SystemClock.elapsedRealtime()
        mixCurve = Prefs.getLong(this, "mix_curve", 3L).toInt()
        inMix = true
        expectTransition = true

        val outTitle = item.mediaMetadata.title?.toString().orEmpty()
        val next = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex) else null
        MixBus.state.value = MixBus.State(true, 0f, outTitle, next?.mediaMetadata?.title?.toString().orEmpty())

        // La "cola" ya suena en sincronía (en silencio): pasa a volumen completo y sigue la canción que termina.
        // Primero se silencia el principal (así nunca suena la misma canción dos veces con desfase = eco)...
        player.volume = 0f
        tail.volume = 1f
        tail.playWhenReady = true

        // Igualar el ritmo: la canción que entra suena al tempo de la que sale y vuelve poco a poco al suyo.
        mixRatio = 1f
        if (next != null && Prefs.getLong(this, "mix_beat", 1L) == 1L) {
            val bo = com.kjota.ritmo.engine.Bpm.get(item.mediaId)
            val bi = com.kjota.ritmo.engine.Bpm.get(next.mediaId)
            if (bo > 0 && bi > 0) {
                var r = bo.toFloat() / bi
                while (r > 1.4f) r /= 2f
                while (r < 0.7f) r *= 2f
                if (r in 0.9f..1.1f) mixRatio = r
            }
        }
        if (mixRatio != 1f) player.setPlaybackSpeed(mixRatio)

        // ...y salta a la siguiente canción empezando en silencio.
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
            3 -> { // limpia: la que sale baja primero y la nueva sube después; nunca suenan fuerte las dos juntas
                val out = Math.pow((1f - x).toDouble(), 1.5).toFloat()
                val t = ((x - 0.15f) / 0.85f).coerceIn(0f, 1f)
                val s = sin(t * PI / 2).toFloat()
                (s * s) to out
            }
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
        if (mixRatio != 1f) player.setPlaybackSpeed(mixRatio + (1f - mixRatio) * x)
        MixBus.state.value = MixBus.state.value.copy(progress = x)
        val tailDead = tail.playbackState == Player.STATE_ENDED || tail.playbackState == Player.STATE_IDLE
        // Si la canción que sale se trabó (cargando), se corta la mezcla y sigue solo la nueva: mejor eso que sonar entrecortado.
        if (tail.playbackState == Player.STATE_BUFFERING) {
            if (tailStallSince == 0L) tailStallSince = now
        } else tailStallSince = 0L
        val tailStalled = tailStallSince != 0L && now - tailStallSince > 600L
        if (x >= 1f || tailDead || tailStalled) endMix()
    }

    private fun endMix() {
        tailStallSince = 0L
        inMix = false
        expectTransition = false
        mixElapsed = 0L
        prerollId = null
        try {
            tail.stop()
            tail.clearMediaItems()
        } catch (_: Throwable) { }
        player.volume = 1f
        if (mixRatio != 1f) { player.setPlaybackSpeed(1f); mixRatio = 1f }
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

    private val sessionFile get() = java.io.File(com.kjota.ritmo.data.Profiles.dir(this), "last_session.json")

    private fun saveLastSession() {
        try {
            val p = session?.player ?: return
            if (p.mediaItemCount == 0) return
            val from = (p.currentMediaItemIndex - 5).coerceAtLeast(0)
            val to = (p.currentMediaItemIndex + 40).coerceAtMost(p.mediaItemCount - 1)
            val arr = org.json.JSONArray()
            for (i in from..to) arr.put(p.getMediaItemAt(i).toTrack().toJson())
            sessionFile.writeText(
                org.json.JSONObject()
                    .put("index", p.currentMediaItemIndex - from)
                    .put("pos", p.currentPosition.coerceAtLeast(0L))
                    .put("items", arr)
                    .toString()
            )
        } catch (_: Throwable) { }
    }

    private fun restoreLastSession() {
        try {
            val p = session?.player ?: return
            if (p.mediaItemCount > 0 || !sessionFile.exists()) return
            val o = org.json.JSONObject(sessionFile.readText())
            val a = o.getJSONArray("items")
            val items = ArrayList<com.kjota.ritmo.data.Track>()
            for (i in 0 until a.length()) items.add(com.kjota.ritmo.data.Track.fromJson(a.getJSONObject(i)))
            if (items.isEmpty()) return
            val idx = o.optInt("index", 0).coerceIn(0, items.lastIndex)
            p.setMediaItems(items.map { it.toMediaItem() }, idx, o.optLong("pos", 0L))
            p.prepare()
            p.pause()
        } catch (_: Throwable) { }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveLastSession()
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        SleepTimer.detach()
        saveLastSession()
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
