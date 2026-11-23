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
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.download.Downloads
import com.kjota.ritmo.engine.Engine
import com.kjota.ritmo.engine.YouTube
import com.kjota.ritmo.player.PlaybackService
import com.kjota.ritmo.player.toMediaItem
import com.kjota.ritmo.player.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

private val shelfQueries = listOf(
    "Éxitos del momento" to "éxitos del momento música",
    "Música chilena" to "música chilena popular",
    "Reguetón" to "reguetón nuevo",
    "Pop en español" to "pop en español hits",
    "Rock clásico" to "rock clásico en español e inglés",
    "Baladas románticas" to "baladas románticas",
    "Lo-fi para concentrarte" to "lo-fi beats relax",
    "Cumbia y salsa" to "cumbia salsa clásicos",
)

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

    private var radioEnabled = true
    private var radioFor: String? = null
    private var searchJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
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

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    _now.update { it.copy(positionMs = pos) }
                }
            }
        }
    }

    /** Reproduce [tracks] empezando por [index]. Con [radio], al terminar la lista sigue con canciones parecidas. */
    fun play(tracks: List<Track>, index: Int, radio: Boolean = true, shuffle: Boolean = false) {
        val c = controller ?: run {
            _messages.tryEmit("El reproductor aún está iniciando, intenta de nuevo")
            return
        }
        if (tracks.isEmpty()) return
        radioEnabled = radio
        radioFor = null
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    private fun maybeExtendWithRadio() {
        val c = controller ?: return
        if (!radioEnabled) return
        if (c.currentMediaItemIndex < c.mediaItemCount - 1) return
        val current = c.currentMediaItem?.toTrack() ?: return
        if (radioFor == current.id) return
        radioFor = current.id
        viewModelScope.launch {
            try {
                val more = YouTube.radio(current.id)
                val cc = controller ?: return@launch
                val have = HashSet<String>()
                for (i in 0 until cc.mediaItemCount) have.add(cc.getMediaItemAt(i).mediaId)
                val fresh = more.filter { have.add(it.id) }
                if (fresh.isNotEmpty()) cc.addMediaItems(fresh.map { it.toMediaItem() })
            } catch (_: Throwable) {
                radioFor = null
            }
        }
    }

    private fun prefetchNext() {
        val c = controller ?: return
        val i = c.currentMediaItemIndex
        if (i + 1 >= c.mediaItemCount) return
        val id = c.getMediaItemAt(i + 1).mediaId
        if (Library.localFile(id) != null) return
        viewModelScope.launch(Dispatchers.IO) { YouTube.prefetch(id) }
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
                val r = YouTube.search(q)
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


    fun loadShelves(force: Boolean = false) {
        if (_shelvesLoading.value) return
        if (!force && _shelves.value.isNotEmpty()) return
        _shelvesLoading.value = true
        viewModelScope.launch {
            val gate = Semaphore(2)
            val jobs = shelfQueries.map { (title, q) ->
                launch {
                    gate.withPermit {
                        try {
                            val r = YouTube.search(q, 15)
                            if (r.isNotEmpty()) {
                                _shelves.update { old ->
                                    // Mantiene el orden original de las estanterías.
                                    (old.filterNot { it.title == title } + Shelf(title, r))
                                        .sortedBy { s -> shelfQueries.indexOfFirst { it.first == s.title } }
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            _shelvesLoading.value = false
        }
    }

    // ------------------------------------------------------------------ Biblioteca

    fun toggleLike(t: Track) = Library.toggleLike(t)

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

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
