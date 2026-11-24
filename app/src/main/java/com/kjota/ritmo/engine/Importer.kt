package com.kjota.ritmo.engine

import com.kjota.ritmo.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Importa listas de otros servicios. Las canciones siempre se reproducen desde YouTube:
 *  - YouTube / YouTube Music: se usan directamente.
 *  - Spotify (listas públicas) y Deezer: se lee la lista de títulos y se busca cada uno en YouTube.
 *  - SoundCloud, Bandcamp y cualquier sitio que yt-dlp entienda: igual (título -> búsqueda en YouTube).
 *  - Texto pegado ("Artista - Canción", una por línea): igual.
 */
object Importer {

    data class Result(val name: String, val tracks: List<Track>, val notFound: Int, val note: String? = null)

    /** Una canción a buscar en YouTube. */
    private class Query(val text: String, val durSec: Int = 0)

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    suspend fun import(input: String, onProgress: (done: Int, total: Int, message: String) -> Unit): Result {
        val text = input.trim()
        val urlMatch = Regex("https?://\\S+").find(text)
        if (urlMatch == null) {
            val queries = textQueries(text)
            if (queries.isEmpty()) throw IllegalArgumentException("No encontré ningún enlace ni canciones en el texto")
            return matchAll("Lista importada", queries, null, onProgress)
        }

        var url = urlMatch.value.trimEnd(')', ']', '.', ',', '"')
        onProgress(0, 0, "Leyendo la lista…")
        var host = hostOf(url)

        if (host.endsWith("spotify.link") || host.endsWith("page.link") || host.endsWith("app.link")) {
            url = withContext(Dispatchers.IO) { resolveRedirect(url) }
            host = hostOf(url)
        }

        return when {
            host.endsWith("spotify.com") -> importSpotify(url, onProgress)
            host.endsWith("deezer.com") || host.endsWith("deezer.page.link") -> importDeezer(url, onProgress)
            host.endsWith("youtube.com") || host == "youtu.be" -> importYoutube(url, onProgress)
            else -> importGeneric(url, onProgress)
        }
    }

    // ------------------------------------------------------------------ YouTube

    private suspend fun importYoutube(url: String, onProgress: (Int, Int, String) -> Unit): Result {
        val list = Regex("[?&]list=([\\w-]+)").find(url)?.groupValues?.get(1)
        val target = when {
            list == null -> url
            list.startsWith("RD") -> url // mixes automáticos: se piden con el video original
            else -> "https://www.youtube.com/playlist?list=$list"
        }
        val (title, entries) = YouTube.flatList(target)
        val tracks = entries.mapNotNull { YouTube.entryToTrack(it) }.distinctBy { it.id }
        onProgress(tracks.size, tracks.size, "Listo")
        if (tracks.isEmpty()) throw IllegalStateException("No encontré canciones en ese enlace de YouTube")
        return Result(title.ifBlank { "Playlist de YouTube" }, tracks, 0)
    }

    // ------------------------------------------------------------------ Genérico (SoundCloud, Bandcamp…)

    private suspend fun importGeneric(url: String, onProgress: (Int, Int, String) -> Unit): Result {
        val (title, entries) = YouTube.flatList(url)
        if (entries.isEmpty()) throw IllegalStateException("No pude leer ese enlace. Prueba pegando la lista de canciones como texto.")
        val direct = ArrayList<Track>()
        val queries = ArrayList<Query>()
        for (e in entries) {
            val isYoutube = e.optString("ie_key").equals("Youtube", true) ||
                e.optString("extractor_key").equals("Youtube", true)
            val asTrack = if (isYoutube) YouTube.entryToTrack(e) else null
            if (asTrack != null) {
                direct.add(asTrack)
            } else {
                val t = cleanNull(e.optString("title")) ?: continue
                val who = cleanNull(e.optString("artist")) ?: cleanNull(e.optString("uploader"))
                    ?: cleanNull(e.optString("channel"))
                val text = if (who != null && !t.contains(" - ")) "$who - $t" else t
                queries.add(Query(text, e.optDouble("duration", 0.0).toInt()))
            }
        }
        val name = title.ifBlank { "Playlist importada" }
        if (queries.isEmpty()) return Result(name, direct.distinctBy { it.id }, 0)
        val r = matchAll(name, queries, null, onProgress)
        return r.copy(tracks = (direct + r.tracks).distinctBy { it.id })
    }

    // ------------------------------------------------------------------ Spotify

    private suspend fun importSpotify(url: String, onProgress: (Int, Int, String) -> Unit): Result {
        val m = Regex("/(playlist|album|track)/([A-Za-z0-9]+)").find(url)
            ?: throw IllegalArgumentException("Ese enlace de Spotify no es de una playlist, álbum o canción")
        val (type, id) = m.destructured
        val html = withContext(Dispatchers.IO) { httpGet("https://open.spotify.com/embed/$type/$id") }
            ?: throw IllegalStateException("No pude abrir la lista de Spotify. ¿Es pública y tienes internet?")
        val json = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Spotify cambió su página y no pude leer la lista. Pega las canciones como texto.")
        val root = JSONObject(json)
        val entity = findKey(root, "entity") as? JSONObject
        val name = entity?.let { cleanNull(it.optString("name")) ?: cleanNull(it.optString("title")) } ?: "Playlist de Spotify"
        val list = (entity?.optJSONArray("trackList") ?: findKey(root, "trackList") as? JSONArray)

        val queries = ArrayList<Query>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val t = list.optJSONObject(i) ?: continue
                val title = cleanNull(t.optString("title")) ?: continue
                val artist = cleanNull(t.optString("subtitle"))?.replace(' ', ' ')
                val durMs = t.optDouble("duration", 0.0)
                queries.add(Query(if (artist != null) "$artist - $title" else title, (durMs / 1000).toInt()))
            }
        } else if (entity != null) {
            // Una sola canción
            val title = cleanNull(entity.optString("title")) ?: cleanNull(entity.optString("name"))
            if (title != null) {
                val artists = entity.optJSONArray("artists")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> cleanNull(o.optString("name")) } }
                        .joinToString(", ")
                } ?: cleanNull(entity.optString("subtitle"))
                queries.add(Query(if (artists != null) "$artists - $title" else title,
                    (entity.optDouble("duration", 0.0) / 1000).toInt()))
            }
        }
        if (queries.isEmpty()) throw IllegalStateException("No encontré canciones en esa lista de Spotify (¿es privada?)")
        val note = if (queries.size >= 100 && type != "track") "Spotify entrega máximo 100 canciones por enlace." else null
        return matchAll(name, queries, note, onProgress)
    }

    /** Busca recursivamente una clave en el JSON (la estructura de la página puede cambiar). */
    private fun findKey(node: Any?, key: String): Any? {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) return node.opt(key)
                val it = node.keys()
                while (it.hasNext()) {
                    val r = findKey(node.opt(it.next()), key)
                    if (r != null) return r
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val r = findKey(node.opt(i), key)
                if (r != null) return r
            }
        }
        return null
    }

    // ------------------------------------------------------------------ Deezer

    private suspend fun importDeezer(url0: String, onProgress: (Int, Int, String) -> Unit): Result {
        var url = url0
        if (hostOf(url).endsWith("page.link")) url = withContext(Dispatchers.IO) { resolveRedirect(url) }
        val m = Regex("/(playlist|album)/(\\d+)").find(url)
            ?: throw IllegalArgumentException("Ese enlace de Deezer no es de una playlist o álbum")
        val (type, id) = m.destructured
        val info = withContext(Dispatchers.IO) { httpGet("https://api.deezer.com/$type/$id") }
            ?: throw IllegalStateException("No pude abrir la lista de Deezer")
        val name = cleanNull(JSONObject(info).optString("title")) ?: "Playlist de Deezer"
        val body = withContext(Dispatchers.IO) { httpGet("https://api.deezer.com/$type/$id/tracks?limit=300") }
            ?: throw IllegalStateException("No pude leer las canciones de Deezer")
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val queries = ArrayList<Query>()
        for (i in 0 until data.length()) {
            val t = data.optJSONObject(i) ?: continue
            val title = cleanNull(t.optString("title")) ?: continue
            val artist = t.optJSONObject("artist")?.let { cleanNull(it.optString("name")) }
            queries.add(Query(if (artist != null) "$artist - $title" else title, t.optInt("duration")))
        }
        if (queries.isEmpty()) throw IllegalStateException("La lista de Deezer está vacía o es privada")
        return matchAll(name, queries, null, onProgress)
    }

    // ------------------------------------------------------------------ Texto

    private fun textQueries(text: String): List<Query> =
        text.lineSequence()
            .map { it.trim().replace(Regex("^\\d+\\s*[.)\\-:]\\s*"), "").trim() }
            .filter { it.length > 1 }
            .map { Query(it) }
            .toList()

    // ------------------------------------------------------------------ Búsqueda en YouTube

    /** Busca cada canción en YouTube (3 a la vez) y elige el resultado cuya duración más se parece. */
    private suspend fun matchAll(
        name: String,
        queries: List<Query>,
        note: String?,
        onProgress: (Int, Int, String) -> Unit,
    ): Result = coroutineScope {
        val total = queries.size
        val done = AtomicInteger(0)
        val gate = Semaphore(3)
        onProgress(0, total, "Buscando canciones en YouTube…")
        val found: List<Track?> = queries.map { q ->
            async {
                gate.withPermit {
                    val track = try {
                        val results = YouTube.search(q.text, 4)
                        pickClosest(results, q.durSec)
                    } catch (_: Throwable) {
                        null
                    }
                    onProgress(done.incrementAndGet(), total, "Buscando canciones en YouTube…")
                    track
                }
            }
        }.awaitAll()
        val tracks = found.filterNotNull().distinctBy { it.id }
        if (tracks.isEmpty()) throw IllegalStateException("No pude encontrar esas canciones en YouTube (revisa tu conexión)")
        Result(name, tracks, found.count { it == null }, note)
    }

    private fun pickClosest(results: List<Track>, durSec: Int): Track? {
        if (results.isEmpty()) return null
        if (durSec <= 0) return results.first()
        val close = results.filter { it.durationSec > 0 && abs(it.durationSec - durSec) <= 15 }
        return close.minByOrNull { abs(it.durationSec - durSec) } ?: results.first()
    }

    // ------------------------------------------------------------------ Utilidades

    private fun cleanNull(s: String?): String? = s?.takeIf { it.isNotBlank() && it != "null" }?.trim()

    private fun hostOf(url: String): String =
        try { URI(url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }

    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "es,en;q=0.8")
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    private fun resolveRedirect(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        return try {
            c.responseCode
            c.url.toString()
        } catch (_: Exception) {
            url
        } finally {
            c.disconnect()
        }
    }
}
