package com.kjota.ritmo

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Prefs
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.download.Downloads
import com.kjota.ritmo.engine.Engine
import com.kjota.ritmo.engine.Importer
import com.kjota.ritmo.engine.Lyrics
import com.kjota.ritmo.engine.LyricsData
import com.kjota.ritmo.engine.YouTube
import com.kjota.ritmo.data.Taste
import com.kjota.ritmo.player.AudioFx
import com.kjota.ritmo.player.MixBus
import com.kjota.ritmo.player.PlaybackService
import com.kjota.ritmo.player.toMediaItem
import com.kjota.ritmo.player.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs
import kotlin.math.roundToInt

data class NowPlaying(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val done: Boolean = false,
    val results: List<Track> = emptyList(),
    val error: String? = null,
)

data class Shelf(val title: String, val tracks: List<Track>)

/** Cola de reproducción: todas las canciones y cuál suena. */
data class QueueState(val items: List<Track> = emptyList(), val index: Int = 0)

/** Letra de la canción [trackId]: cargando, o con datos (null = no se encontró). */
data class LyricsUi(val trackId: String? = null, val loading: Boolean = false, val data: LyricsData? = null)

data class ImportState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val message: String = "")

private class ShelfDef(val title: String, val query: String)

private val generalPool = listOf(
    ShelfDef("Éxitos del momento", "éxitos del momento música"),
    ShelfDef("Música chilena", "música chilena popular"),
    ShelfDef("Reguetón", "reguetón nuevo"),
    ShelfDef("Pop en español", "pop en español hits"),
    ShelfDef("Rock clásico", "rock clásico en español e inglés"),
    ShelfDef("Baladas románticas", "baladas románticas"),
    ShelfDef("Lo-fi para concentrarte", "lo-fi beats relax"),
    ShelfDef("Cumbia y salsa", "cumbia salsa clásicos"),
    ShelfDef("Hip hop latino", "hip hop latino"),
    ShelfDef("Indie y alternativo", "indie alternativo canciones"),
    ShelfDef("Electrónica para bailar", "electrónica house dance"),
    ShelfDef("Trap y flow urbano", "trap latino nuevo"),
    ShelfDef("Rock en español", "rock en español 90s"),
    ShelfDef("Bachata", "bachata románticas"),
    ShelfDef("Para manejar de noche", "música para manejar de noche"),
    ShelfDef("Clásicos de los 80", "éxitos de los 80"),
    ShelfDef("Clásicos de los 2000", "éxitos de los 2000"),
    ShelfDef("Jazz suave", "jazz suave relax"),
    ShelfDef("Acústico", "acústico versiones en vivo"),
    ShelfDef("Funk y soul", "funk soul clásicos"),
    ShelfDef("R&B", "r&b nuevo"),
    ShelfDef("Reggae", "reggae clásicos"),
    ShelfDef("Cantautores", "cantautores en español"),
    ShelfDef("Pop internacional", "pop internacional hits"),
)

val moodChips = listOf("Para ti", "Energía", "Relax", "Entrenar", "Fiesta", "Romance")

private val moodPools: Map<Int, List<ShelfDef>> = mapOf(
    1 to listOf(
        ShelfDef("Energía total", "música para subir el ánimo energía"),
        ShelfDef("Rock potente", "rock potente"),
        ShelfDef("Pop arriba", "pop alegre hits"),
        ShelfDef("Electrónica intensa", "electrónica energética"),
        ShelfDef("Hip hop con fuerza", "hip hop energía"),
        ShelfDef("Para despertar", "canciones para despertar con energía"),
    ),
    2 to listOf(
        ShelfDef("Calma", "música relajante canciones"),
        ShelfDef("Lo-fi tranquilo", "lo-fi chill beats"),
        ShelfDef("Acústico suave", "acústico suave"),
        ShelfDef("Jazz de fondo", "jazz café"),
        ShelfDef("Para dormir", "música para dormir suave"),
        ShelfDef("Piano relajante", "piano relajante"),
    ),
    3 to listOf(
        ShelfDef("Para entrenar", "música para entrenar gym"),
        ShelfDef("Trap para el gym", "trap gym motivación"),
        ShelfDef("Rock para correr", "rock para correr"),
        ShelfDef("Electrónica running", "electrónica para correr"),
        ShelfDef("Reguetón gym", "reguetón para entrenar"),
        ShelfDef("Motivación", "canciones motivación deporte"),
    ),
    4 to listOf(
        ShelfDef("Fiesta latina", "fiesta latina mix"),
        ShelfDef("Reguetón de fiesta", "reguetón viejo escuela fiesta"),
        ShelfDef("Cumbia para bailar", "cumbia bailable"),
        ShelfDef("Salsa y bachata", "salsa bachata bailable"),
        ShelfDef("Éxitos para el carrete", "éxitos para carretear"),
        ShelfDef("Electrónica de fiesta", "house fiesta mix"),
    ),
    5 to listOf(
        ShelfDef("Baladas", "baladas románticas en español"),
        ShelfDef("Boleros y clásicos", "boleros románticos"),
        ShelfDef("Pop romántico", "pop romántico"),
        ShelfDef("Bachata romántica", "bachata romántica"),
        ShelfDef("R&B suave", "r&b slow romántico"),
        ShelfDef("Para dedicar", "canciones para dedicar amor"),
    ),
)

private val searchTwists = listOf("", " mix", " nuevo", " lo mejor", " playlist", " top", " favoritos")

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null

    private val _now = MutableStateFlow(NowPlaying())
    val now: StateFlow<NowPlaying> = _now.asStateFlow()

    val lib = Library.state
    val downloading = Downloads.progress

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()

    private val _shelvesLoading = MutableStateFlow(false)
    val shelvesLoading: StateFlow<Boolean> = _shelvesLoading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _lyrics = MutableStateFlow(LyricsUi())
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    private val _mixOn = MutableStateFlow(Prefs.getLong(app, "mix_on", 0L) == 1L)
    val mixOn: StateFlow<Boolean> = _mixOn.asStateFlow()

    private val _autoDl = MutableStateFlow(Prefs.getLong(app, "auto_dl", 1L) == 1L)
    val autoDl: StateFlow<Boolean> = _autoDl.asStateFlow()

    private val _carMode = MutableStateFlow(Prefs.getLong(app, "car_mode", 0L) == 1L)
    val carMode: StateFlow<Boolean> = _carMode.asStateFlow()

    private val _mixSec = MutableStateFlow(Prefs.getLong(app, "mix_sec", 8L).toInt())
    val mixSec: StateFlow<Int> = _mixSec.asStateFlow()

    private val _mixCurve = MutableStateFlow(Prefs.getLong(app, "mix_curve", 3L).toInt())
    val mixCurve: StateFlow<Int> = _mixCurve.asStateFlow()
    val mixBus = MixBus.state

    // Ecualizador
    val eqBandInfo = AudioFx.bands
    private val _eqOn = MutableStateFlow(Prefs.getLong(app, "eq_on", 0L) == 1L)
    val eqOn: StateFlow<Boolean> = _eqOn.asStateFlow()
    private val _eqLevels = MutableStateFlow((0 until 8).map { Prefs.getLong(app, "eq_b$it", 0L).toInt() })
    val eqLevels: StateFlow<List<Int>> = _eqLevels.asStateFlow()
    private val _bass = MutableStateFlow(Prefs.getLong(app, "bass", 0L).toInt())
    val bass: StateFlow<Int> = _bass.asStateFlow()
    private val _loud = MutableStateFlow(Prefs.getLong(app, "loud", 0L).toInt())
    val loud: StateFlow<Int> = _loud.asStateFlow()
    private val _eqPreset = MutableStateFlow(Prefs.getLong(app, "eq_preset", -1L).toInt())
    val eqPreset: StateFlow<Int> = _eqPreset.asStateFlow()

    private val _mood = MutableStateFlow(0)
    val mood: StateFlow<Int> = _mood.asStateFlow()

    private val _videoHeight = MutableStateFlow(Prefs.getLong(app, "video_h", 1080L).toInt())
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    private val _openVideo = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openVideo: SharedFlow<Unit> = _openVideo.asSharedFlow()

    private val _import = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    /** Enlace recibido desde otra app con "Compartir" (se muestra en el diálogo de importar). */
    private val _pendingImport = MutableStateFlow<String?>(null)
    val pendingImport: StateFlow<String?> = _pendingImport.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private var radioEnabled = true
    private var radioFor: String? = null
    private var radioSeedTitle = ""
    private var skipWhenReady = false
    private var searchJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) publishQueue(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            Library.pushRecent(mediaItem.toTrack())
            maybeExtendWithRadio()
            prefetchNext()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            _messages.tryEmit("No se pudo reproducir esa canción")
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    init {
        connect()
        loadShelves()
        startPositionTicker()
        startBackgroundPrep()
    }

    // ------------------------------------------------------------------ Reproductor

    private fun connect() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                sync(c)
                publishQueue(c)
            } catch (_: Exception) {
                _messages.tryEmit("No se pudo iniciar el reproductor")
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val track = item?.toTrack()
        val dur = if (p.duration == C.TIME_UNSET || p.duration < 0) (track?.durationSec ?: 0) * 1000L else p.duration
        _now.value = NowPlaying(
            track = track,
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = dur,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
        )
    }

    private fun publishQueue(p: Player) {
        val items = ArrayList<Track>(p.mediaItemCount)
        for (i in 0 until p.mediaItemCount) items.add(p.getMediaItemAt(i).toTrack())
        _queue.value = QueueState(items, p.currentMediaItemIndex.coerceAtLeast(0))
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount || index == c.currentMediaItemIndex) return
        c.removeMediaItem(index)
        maybeExtendWithRadio()
    }

    /** Guarda lo que hay en la cola como playlist. */
    fun saveQueueAsPlaylist() {
        val q = _queue.value
        if (q.items.isEmpty()) return
        val name = "Mix de " + (q.items.getOrNull(q.index)?.title ?: "mi cola").take(40)
        Library.createPlaylistWith(name, q.items)
        _messages.tryEmit("Guardada como playlist: $name")
    }

    /** 👎: salta la canción, no vuelve a sugerir esa canción ni a ese artista y limpia lo parecido de la cola. */
    fun dislike(t: Track) {
        Taste.dislike(t)
        if (Library.isLiked(t.id)) Library.toggleLike(t)
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        for (i in c.mediaItemCount - 1 downTo cur + 1) {
            if (Taste.isBlocked(c.getMediaItemAt(i).toTrack(), radioSeedTitle)) c.removeMediaItem(i)
        }
        if (c.currentMediaItem?.mediaId == t.id) {
            if (c.hasNextMediaItem()) c.seekToNextMediaItem() else skipWhenReady = true
        }
        _messages.tryEmit("Listo, no te sugeriremos más canciones así")
        radioFor = null
        maybeExtendWithRadio()
        publishQueue(c)
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                delay(250)
                val c = controller ?: continue
                if (c.isPlaying) {
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    _now.update { it.copy(positionMs = pos) }
                }
            }
        }
    }

    /** Reproduce [tracks] empezando por [index]. Con [radio], al terminar la lista sigue con canciones parecidas. */
    fun play(tracks: List<Track>, index: Int, radio: Boolean = true, shuffle: Boolean = false, context: String = "") {
        val c = controller ?: run {
            _messages.tryEmit("El reproductor aún está iniciando, intenta de nuevo")
            return
        }
        if (tracks.isEmpty()) return
        radioEnabled = radio
        radioFor = null
        radioSeedTitle = context.ifEmpty { tracks[index.coerceIn(0, tracks.lastIndex)].title }
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    /** Desde la búsqueda: suena solo la elegida y sigue el mix de YouTube (parecidas, otros artistas, versiones). */
    fun playFromSearch(t: Track) {
        play(listOf(t), 0, radio = true)
        maybeExtendWithRadio()
    }

    private fun titleKey(t: String): String =
        t.lowercase().replace(Regex("[\\(\\[].*?[\\)\\]]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun maybeExtendWithRadio() {
        val c = controller ?: return
        if (!radioEnabled) return
        val remaining = c.mediaItemCount - 1 - c.currentMediaItemIndex
        if (remaining >= 15 || c.mediaItemCount == 0) return
        val seed = c.getMediaItemAt(c.mediaItemCount - 1).toTrack()
        if (radioFor == seed.id) return
        radioFor = seed.id
        val seedTitle = radioSeedTitle.ifEmpty { seed.title }
        viewModelScope.launch {
            try {
                val more = YouTube.radio(seed.id)
                val cc = controller ?: return@launch
                val have = HashSet<String>()
                val titles = HashSet<String>()
                val perArtist = HashMap<String, Int>()
                for (i in 0 until cc.mediaItemCount) {
                    val tr = cc.getMediaItemAt(i).toTrack()
                    have.add(tr.id); titles.add(titleKey(tr.title))
                    perArtist.merge(tr.artist.lowercase().trim(), 1, Int::plus)
                }
                val fresh = more.filter { !Taste.isBlocked(it, seedTitle) }
                    .filter { have.add(it.id) }
                    .filter { titles.add(titleKey(it.title)) }
                    .filter {
                        val k = it.artist.lowercase().trim()
                        val n = perArtist.getOrDefault(k, 0)
                        perArtist[k] = n + 1
                        n < 2
                    }
                    .take(25)
                if (fresh.isNotEmpty()) cc.addMediaItems(fresh.map { it.toMediaItem() })
                if (skipWhenReady && cc.hasNextMediaItem()) {
                    skipWhenReady = false
                    cc.seekToNextMediaItem()
                }
                publishQueue(cc)
                // Si aún quedan pocas, sigue con la última (máx. unas pocas veces seguidas)
                if (fresh.isNotEmpty() && cc.mediaItemCount - 1 - cc.currentMediaItemIndex < 15 && radioChain < 3) {
                    radioChain++
                    maybeExtendWithRadio()
                } else radioChain = 0
            } catch (_: Throwable) {
                radioFor = null
                radioChain = 0
            }
        }
    }
    private var radioChain = 0

    private fun prefetchNext() {
        val c = controller ?: return
        val i = c.currentMediaItemIndex
        // El controlador solo se toca en el hilo principal: aquí se leen los ids y luego se precargan en segundo plano.
        val ids = listOf(i + 1, i + 2)
            .filter { it < c.mediaItemCount }
            .map { c.getMediaItemAt(it).mediaId }
            .filter { Library.localFile(it) == null }
        if (ids.isEmpty()) return
        ids.forEach { YouTube.prefetchNow(it) }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _now.update { it.copy(positionMs = ms) }
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun addToQueue(t: Track) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            play(listOf(t), 0)
            return
        }
        c.addMediaItem(t.toMediaItem())
        _messages.tryEmit("Agregada a la cola")
    }

    fun playNext(t: Track) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            play(listOf(t), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
        _messages.tryEmit("Sonará a continuación")
    }

    // ------------------------------------------------------------------ Búsqueda y portada

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _search.value = SearchState(query = q, loading = true)
        searchJob = viewModelScope.launch {
            try {
                val r = YouTube.search(q, 25, maxSec = 4 * 3600)
                // Precarga las primeras para que suenen casi al instante al tocarlas
                r.take(3).forEach { YouTube.prefetch(it.id) }
                _search.value = SearchState(
                    query = q, done = true, results = r,
                    error = if (r.isEmpty()) "No encontré resultados para “$q”" else null,
                )
            } catch (e: Throwable) {
                _search.value = SearchState(
                    query = q, done = true,
                    error = "No se pudo buscar. Revisa tu conexión (tus descargas siguen disponibles en Biblioteca).",
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }


    fun setMood(i: Int) {
        if (_mood.value == i) return
        _mood.value = i
        loadShelves(force = true)
    }

    /** Pull-to-refresh: cada vez arma estantes distintas (otros géneros, otros términos, otro orden). */
    fun loadShelves(force: Boolean = false) {
        if (_shelvesLoading.value) return
        if (!force && _shelves.value.isNotEmpty()) return
        _shelvesLoading.value = true
        viewModelScope.launch {
            val fresh = ArrayList<Pair<Int, Shelf>>()
            val lock = Mutex()
            suspend fun publish(order: Int, shelf: Shelf) = lock.withLock {
                val clean = shelf.tracks.filter { !Taste.isBlocked(it) }
                if (clean.isEmpty()) return@withLock
                fresh.add(order to shelf.copy(tracks = clean))
                _shelves.value = fresh.sortedBy { it.first }.map { it.second }
            }
            val gate = Semaphore(3)
            val jobs = ArrayList<Job>()
            val mood = _mood.value
            val recent = Library.state.value.recent
            var order = 0

            // Personalizadas: "Porque escuchaste…" (solo en "Para ti")
            if (mood == 0 && recent.isNotEmpty()) {
                recent.shuffled().take(2).forEach { base ->
                    val o = order++
                    jobs.add(launch {
                        gate.withPermit {
                            try {
                                val r = YouTube.radio(base.id, 20).shuffled().take(16)
                                if (r.isNotEmpty()) publish(o, Shelf("Porque escuchaste “${base.title.take(34)}”", r))
                            } catch (_: Throwable) { }
                        }
                    })
                }
            }

            val pool = if (mood == 0) generalPool else (moodPools[mood].orEmpty() + generalPool.shuffled().take(2))
            val previous = _shelves.value.map { it.title }.toSet()
            // Prefiere géneros que no se vieron en la pantalla anterior.
            val picks = (pool.filter { it.title !in previous }.shuffled() + pool.filter { it.title in previous }.shuffled())
                .take(if (mood == 0) 7 else 6)
            picks.forEach { def ->
                val o = order++
                jobs.add(launch {
                    gate.withPermit {
                        try {
                            val q = def.query + searchTwists.random()
                            val r = YouTube.search(q, 25).shuffled().take(16)
                            if (r.isNotEmpty()) publish(o, Shelf(def.title, r))
                        } catch (_: Throwable) { }
                    }
                })
            }
            jobs.forEach { it.join() }
            _shelvesLoading.value = false
        }
    }

    // ------------------------------------------------------------------ Biblioteca

    fun toggleLike(t: Track) {
        Library.toggleLike(t)
        com.kjota.ritmo.widget.WidgetUpdater.refresh(app)
        autoDownload()
    }

    fun setAutoDl(on: Boolean) {
        Prefs.putLong(app, "auto_dl", if (on) 1L else 0L)
        _autoDl.value = on
        if (on) autoDownload()
    }

    /** Descarga sola las favoritas y lo último escuchado (con Wi-Fi, hasta 150 canciones) para que suenen al instante. */
    fun autoDownload() {
        if (!_autoDl.value) return
        try {
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (cm.isActiveNetworkMetered) return
        } catch (_: Throwable) { return }
        val s = Library.state.value
        val busy = Downloads.progress.value.keys
        val have = s.downloads.map { it.id }.toHashSet()
        val slots = 150 - s.downloads.size
        if (slots <= 0) return
        val todo = (s.liked + s.recent.take(30)).distinctBy { it.id }
            .filter { it.id !in have && it.id !in busy }
            .take(minOf(slots, 10))
        if (todo.isNotEmpty()) Downloads.enqueue(app, *todo.toTypedArray())
    }

    /** Preparación en segundo plano: direcciones de las primeras canciones de Inicio y descargas automáticas. */
    private fun startBackgroundPrep() {
        viewModelScope.launch {
            _shelvesLoading.collect { loading ->
                if (!loading) {
                    val ids = Library.state.value.recent.take(2).map { it.id } +
                        _shelves.value.take(5).flatMap { it.tracks.take(2) }.map { it.id }
                    ids.distinct().filter { Library.localFile(it) == null }.forEach { YouTube.prefetch(it) }
                }
            }
        }
        viewModelScope.launch {
            delay(30_000)
            while (true) {
                autoDownload()
                delay(10 * 60 * 1000L)
            }
        }
    }

    fun download(vararg t: Track) {
        val n = Downloads.enqueue(app, *t)
        if (n > 0) _messages.tryEmit(if (n == 1) "Descargando…" else "Descargando $n canciones…")
        else _messages.tryEmit("Ya está descargada")
    }

    fun removeDownload(t: Track) {
        Library.removeDownload(t.id)
        _messages.tryEmit("Descarga eliminada")
    }

    fun createPlaylist(name: String, first: Track? = null) {
        Library.createPlaylist(name, first)
        _messages.tryEmit("Playlist creada")
    }

    fun addToPlaylist(pid: String, t: Track) {
        Library.addToPlaylist(pid, t)
        _messages.tryEmit("Agregada a la playlist")
    }

    fun removeFromPlaylist(pid: String, t: Track) = Library.removeFromPlaylist(pid, t.id)

    fun deletePlaylist(pid: String) = Library.deletePlaylist(pid)

    fun updateEngine() {
        viewModelScope.launch {
            _messages.tryEmit("Actualizando motor…")
            try {
                _messages.tryEmit(Engine.update(app))
            } catch (e: Throwable) {
                _messages.tryEmit("No se pudo actualizar: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ Letras

    fun loadLyrics(t: Track) {
        val cur = _lyrics.value
        if (cur.trackId == t.id && (cur.loading || cur.data != null)) return
        lyricsJob?.cancel()
        _lyrics.value = LyricsUi(t.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val d = Lyrics.get(app, t)
            _lyrics.value = LyricsUi(t.id, loading = false, data = d)
        }
    }

    // ------------------------------------------------------------------ Mix DJ

    fun setCarMode(on: Boolean) {
        Prefs.putLong(app, "car_mode", if (on) 1L else 0L)
        _carMode.value = on
    }

    fun setMix(on: Boolean) {
        Prefs.putLong(app, "mix_on", if (on) 1L else 0L)
        _mixOn.value = on
        _messages.tryEmit(
            if (on) "Mix DJ activado: las canciones se mezclarán solas"
            else "Mix DJ desactivado"
        )
    }

    fun setMixSeconds(sec: Int) {
        Prefs.putLong(app, "mix_sec", sec.toLong())
        _mixSec.value = sec
    }

    fun setVideoHeight(h: Int) {
        Prefs.putLong(app, "video_h", h.toLong())
        _videoHeight.value = h
    }

    // ------------------------------------------------------------------ Video

    /** Abre el reproductor en modo Video: el audio queda en pausa mientras se ve. */
    private var videoAutoplay = false

    fun consumeVideoAutoplay(): Boolean = videoAutoplay.also { videoAutoplay = false }

    fun watchVideo(t: Track) {
        play(listOf(t), 0, radio = true)
        controller?.pause()
        videoAutoplay = true
        _openVideo.tryEmit(Unit)
    }

    fun pauseAudio() { controller?.pause() }

    fun prevTrack() { controller?.seekToPreviousMediaItem() }

    /** Al cerrar el video, el audio sigue desde donde quedó el video (si es la misma canción). */
    fun endVideo(trackId: String?, positionMs: Long, wasPlaying: Boolean) {
        val c = controller ?: return
        if (trackId != null && c.currentMediaItem?.mediaId == trackId) {
            c.seekTo(positionMs.coerceAtLeast(0L))
            if (wasPlaying) c.play()
        }
    }

    fun setMixCurve(c: Int) {
        Prefs.putLong(app, "mix_curve", c.toLong())
        _mixCurve.value = c
    }

    // ------------------------------------------------------------------ Ecualizador

    private fun fx() = AudioFx.apply(app)

    fun setEqOn(on: Boolean) {
        Prefs.putLong(app, "eq_on", if (on) 1L else 0L)
        _eqOn.value = on
        fx()
    }

    fun setEqBand(i: Int, mB: Int) {
        Prefs.putLong(app, "eq_b$i", mB.toLong())
        _eqLevels.update { l -> l.toMutableList().also { it[i] = mB } }
        Prefs.putLong(app, "eq_preset", -1L)
        _eqPreset.value = -1
        fx()
    }

    fun setBass(v: Int) {
        Prefs.putLong(app, "bass", v.toLong())
        _bass.value = v
        fx()
    }

    fun setLoud(mB: Int) {
        Prefs.putLong(app, "loud", mB.toLong())
        _loud.value = mB
        fx()
    }

    /** Presets: curva en dB según la posición de la banda (0 = graves, 1 = agudos). */
    fun applyPreset(index: Int) {
        val n = AudioFx.bands.value.size.coerceIn(1, 8).let { if (AudioFx.bands.value.isEmpty()) 5 else it }
        val maxDb = AudioFx.bands.value.firstOrNull()?.maxDb ?: 12f
        val levels = (0 until 8).map { i ->
            if (i >= n) 0 else {
                val f = if (n == 1) 0f else i.toFloat() / (n - 1)
                val db = when (index) {
                    1 -> 6f * (1f - f) * (1f - f)                       // Bajos
                    2 -> 6f * f * f                                      // Agudos
                    3 -> 5f * abs(2f * f - 1f) - 1.5f                    // Rock
                    4 -> 4f * (1f - abs(2f * f - 1f)) - 1f - 2f * f * f  // Voz
                    5 -> 3f * abs(2f * f - 1f) - 0.5f                    // Pop
                    else -> 0f                                           // Normal
                }
                (db.coerceIn(-maxDb, maxDb) * 100f).roundToInt()
            }
        }
        levels.forEachIndexed { i, v -> Prefs.putLong(app, "eq_b$i", v.toLong()) }
        _eqLevels.value = levels
        Prefs.putLong(app, "eq_preset", index.toLong())
        _eqPreset.value = index
        if (!_eqOn.value) setEqOn(true) else fx()
    }

    fun resetEq() {
        applyPreset(0)
        setBass(0)
        setLoud(0)
    }

    // ------------------------------------------------------------------ Importar playlists

    fun setPendingImport(text: String?) { _pendingImport.value = text }

    fun importPlaylist(input: String) {
        if (_import.value.running) return
        _import.value = ImportState(running = true, message = "Leyendo la lista…")
        viewModelScope.launch {
            try {
                val r = Importer.import(input) { d, t, m ->
                    _import.value = ImportState(running = true, done = d, total = t, message = m)
                }
                Library.createPlaylistWith(r.name, r.tracks)
                val extra = buildString {
                    if (r.notFound > 0) append(" (${r.notFound} no se encontraron)")
                    if (r.note != null) append(". ").append(r.note)
                }
                _messages.tryEmit("Playlist “${r.name}” creada con ${r.tracks.size} canciones$extra")
            } catch (e: Throwable) {
                _messages.tryEmit(e.message ?: "No se pudo importar la lista")
            } finally {
                _import.value = ImportState()
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
