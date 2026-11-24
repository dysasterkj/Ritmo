package com.kjota.ritmo.engine

import com.kjota.ritmo.data.Track
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Acceso a YouTube a través de yt-dlp: búsqueda, "radio" (canciones parecidas) y dirección directa del audio.
 * No usa la app ni la API oficial de YouTube: sin cuenta, sin anuncios, sin seguimiento.
 */
object YouTube {
    private const val WATCH = "https://www.youtube.com/watch?v="
    private const val URL_TTL_MS = 3L * 60 * 60 * 1000

    private class Cached(val url: String, val at: Long)
    private val urlCache = ConcurrentHashMap<String, Cached>()

    suspend fun search(query: String, count: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        val req = YoutubeDLRequest("ytsearch$count:$query")
        flatOptions(req)
        parse(YoutubeDL.getInstance().execute(req, null, null).out)
    }

    /** Canciones parecidas (el "mix" automático de YouTube). */
    suspend fun radio(id: String, count: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        val req = YoutubeDLRequest("${WATCH}$id&list=RD$id")
        flatOptions(req)
        req.addOption("--playlist-end", count)
        parse(YoutubeDL.getInstance().execute(req, null, null).out).filter { it.id != id }
    }

    private fun flatOptions(req: YoutubeDLRequest) {
        req.addOption("--flat-playlist")
        req.addOption("-J")
        req.addOption("--no-warnings")
        req.addOption("--ignore-errors")
        req.addOption("--socket-timeout", 20)
    }

    private fun parse(json: String): List<Track> {
        val start = json.indexOf('{')
        if (start < 0) return emptyList()
        val entries = JSONObject(json.substring(start)).optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        for (i in 0 until entries.length()) {
            val t = entryToTrack(entries.optJSONObject(i) ?: continue) ?: continue
            if (seen.add(t.id)) out.add(t)
        }
        return out
    }

    private fun JSONObject.str(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    /** Convierte una entrada de yt-dlp en canción. Devuelve null si no es un video de YouTube utilizable. */
    fun entryToTrack(e: JSONObject): Track? {
        val id = e.optString("id")
        if (id.length != 11) return null
        val dur = e.optDouble("duration", 0.0).toInt()
        val live = e.optString("live_status")
        if (live == "is_live" || live == "is_upcoming") return null
        if (dur > 25 * 60) return null // evita mixes larguísimos y transmisiones
        val artist = (e.str("channel") ?: e.str("uploader") ?: "YouTube").removeSuffix(" - Topic")
        return Track(id, e.optString("title", "Sin título"), artist, dur)
    }

    /** Lista plana de cualquier enlace que yt-dlp entienda: (título de la lista, entradas crudas). */
    suspend fun flatList(url: String, max: Int = 300): Pair<String, List<JSONObject>> = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        val req = YoutubeDLRequest(url)
        flatOptions(req)
        req.addOption("--playlist-end", max)
        val json = YoutubeDL.getInstance().execute(req, null, null).out
        val start = json.indexOf('{')
        if (start < 0) return@withContext "" to emptyList()
        val root = JSONObject(json.substring(start))
        val arr = root.optJSONArray("entries")
        val list = ArrayList<JSONObject>()
        if (arr != null) {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
        } else if (root.has("id")) {
            list.add(root) // era un solo video
        }
        (root.str("title") ?: "") to list
    }

    /** Dirección directa del audio (bloqueante: se llama desde el hilo de carga del reproductor). */
    fun streamUrl(id: String): String {
        val c = urlCache[id]
        if (c != null && System.currentTimeMillis() - c.at < URL_TTL_MS) return c.url
        runBlocking { Engine.awaitReady() }
        val req = YoutubeDLRequest("$WATCH$id")
        req.addOption("-f", "bestaudio/best")
        req.addOption("-g")
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        req.addOption("--socket-timeout", 20)
        val out = YoutubeDL.getInstance().execute(req, null, null).out
        val url = out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            ?: throw IllegalStateException("No se encontró el audio")
        urlCache[id] = Cached(url, System.currentTimeMillis())
        return url
    }

    /** Resuelve la siguiente canción con anticipación para que el cambio sea instantáneo. */
    fun prefetch(id: String) {
        try { streamUrl(id) } catch (_: Throwable) { }
    }
}
