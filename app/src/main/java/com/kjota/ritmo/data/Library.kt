package com.kjota.ritmo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LibraryState(
    val liked: List<Track> = emptyList(),
    val downloads: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

/**
 * Tu biblioteca: favoritas, descargas, historial y playlists.
 * Todo se guarda en un archivo dentro del teléfono (filesDir/library.json). Nada sale a internet.
 */
object Library {
    private lateinit var file: File
    private lateinit var musicDir: File
    private var ready = false

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        val base = ctx.applicationContext.filesDir
        file = File(base, "library.json")
        musicDir = File(base, "music").also { it.mkdirs() }
        _state.value = load()
        // Quita de la lista las descargas cuyo archivo ya no existe.
        val ok = _state.value.downloads.filter { localFile(it.id) != null }
        if (ok.size != _state.value.downloads.size) update { it.copy(downloads = ok) }
        ready = true
    }

    fun musicDir(): File = musicDir

    /** Archivo de audio descargado para esta canción (si existe). */
    fun localFile(id: String): File? =
        musicDir.listFiles()?.firstOrNull { it.isFile && it.nameWithoutExtension == id && it.length() > 0 }

    fun usedBytes(): Long = musicDir.listFiles()?.sumOf { it.length() } ?: 0L

    // ---- Favoritas ----
    fun isLiked(id: String) = _state.value.liked.any { it.id == id }

    fun toggleLike(t: Track) = update { s ->
        if (s.liked.any { it.id == t.id }) s.copy(liked = s.liked.filterNot { it.id == t.id })
        else s.copy(liked = listOf(t) + s.liked)
    }

    // ---- Descargas ----
    fun addDownload(t: Track) = update { s ->
        s.copy(downloads = listOf(t) + s.downloads.filterNot { it.id == t.id })
    }

    fun removeDownload(id: String) {
        musicDir.listFiles()?.filter { it.nameWithoutExtension == id || it.name.startsWith("$id.") }
            ?.forEach { it.delete() }
        update { s -> s.copy(downloads = s.downloads.filterNot { it.id == id }) }
    }

    // ---- Historial ----
    fun pushRecent(t: Track) = update { s ->
        s.copy(recent = (listOf(t) + s.recent.filterNot { it.id == t.id }).take(30))
    }

    // ---- Playlists ----
    fun createPlaylist(name: String, first: Track? = null): String {
        val id = UUID.randomUUID().toString()
        update { s ->
            s.copy(playlists = s.playlists + Playlist(id, name.trim().ifEmpty { "Mi playlist" }, listOfNotNull(first)))
        }
        return id
    }

    fun addToPlaylist(pid: String, t: Track) = update { s ->
        s.copy(playlists = s.playlists.map { p ->
            if (p.id == pid && p.tracks.none { it.id == t.id }) p.copy(tracks = p.tracks + t) else p
        })
    }

    fun removeFromPlaylist(pid: String, tid: String) = update { s ->
        s.copy(playlists = s.playlists.map { p ->
            if (p.id == pid) p.copy(tracks = p.tracks.filterNot { it.id == tid }) else p
        })
    }

    fun deletePlaylist(pid: String) = update { s ->
        s.copy(playlists = s.playlists.filterNot { it.id == pid })
    }

    // ---- Guardado ----
    @Synchronized
    private fun update(block: (LibraryState) -> LibraryState) {
        _state.value = block(_state.value)
        save(_state.value)
    }

    private fun save(s: LibraryState) {
        try {
            val o = JSONObject()
                .put("liked", s.liked.toJsonArray())
                .put("downloads", s.downloads.toJsonArray())
                .put("recent", s.recent.toJsonArray())
                .put("playlists", JSONArray().also { arr ->
                    s.playlists.forEach { p ->
                        arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", p.tracks.toJsonArray()))
                    }
                })
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        } catch (_: Exception) {
        }
    }

    private fun load(): LibraryState {
        if (!file.exists()) return LibraryState()
        return try {
            val o = JSONObject(file.readText())
            val pls = o.optJSONArray("playlists")
            val playlists = ArrayList<Playlist>()
            if (pls != null) {
                for (i in 0 until pls.length()) {
                    val p = pls.getJSONObject(i)
                    playlists.add(Playlist(p.getString("id"), p.optString("name"), p.optJSONArray("tracks").toTracks()))
                }
            }
            LibraryState(
                liked = o.optJSONArray("liked").toTracks(),
                downloads = o.optJSONArray("downloads").toTracks(),
                recent = o.optJSONArray("recent").toTracks(),
                playlists = playlists,
            )
        } catch (_: Exception) {
            LibraryState()
        }
    }
}
