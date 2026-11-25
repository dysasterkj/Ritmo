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

    suspend fun search(query: String, count: Int = 25, maxSec: Int = 25 * 60): List<Track> = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        val req = YoutubeDLRequest("ytsearch$count:$query")
        flatOptions(req)
        parse(YoutubeDL.getInstance().execute(req, null, null).out, maxSec)
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

    private fun parse(json: String, maxSec: Int = 25 * 60): List<Track> {
        val start = json.indexOf('{')
        if (start < 0) return emptyList()
        val entries = JSONObject(json.substring(start)).optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        for (i in 0 until entries.length()) {
            val t = entryToTrack(entries.optJSONObject(i) ?: continue, maxSec) ?: continue
            if (seen.add(t.id)) out.add(t)
        }
        return out
    }

    private fun JSONObject.str(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    /** Convierte una entrada de yt-dlp en canción. Devuelve null si no es un video de YouTube utilizable. */
    fun entryToTrack(e: JSONObject, maxSec: Int = 25 * 60): Track? {
        val id = e.optString("id")
        if (id.length != 11) return null
        val dur = e.optDouble("duration", 0.0).toInt()
        val live = e.optString("live_status")
        if (live == "is_live" || live == "is_upcoming") return null
        if (dur > maxSec) return null // evita mixes larguísimos y transmisiones
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
    private val locks = ConcurrentHashMap<String, Any>()

    fun streamUrl(id: String, quickOnly: Boolean = false): String {
        fresh(id)?.let { return it }
        // Un solo cálculo por canción: si ya se está resolviendo (precarga), se espera ese resultado.
        synchronized(locks.getOrPut(id) { Any() }) {
            fresh(id)?.let { return it }
            // 1) Rápido: petición directa (sin Python). 2) Respaldo: yt-dlp.
            val quick = InnerTube.audioUrl(id)
            if (quick != null) {
                urlCache[id] = Cached(quick, System.currentTimeMillis())
                return quick
            }
            if (quickOnly) throw IllegalStateException("sin ruta rápida")
            runBlocking { Engine.awaitReady() }
            val req = YoutubeDLRequest("$WATCH$id")
            req.addOption("-f", "bestaudio/best")
            req.addOption("-g")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            // Menos descargas por canción: sin manifiestos DASH/HLS ni subtítulos (solo se necesita el audio)
            req.addOption("--extractor-args", "youtube:skip=dash,hls,translated_subs")
            req.addOption("--no-write-subs")
            req.addOption("--socket-timeout", 15)
            val out = YoutubeDL.getInstance().execute(req, null, null).out
            val url = out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
                ?: throw IllegalStateException("No se encontró el audio")
            urlCache[id] = Cached(url, System.currentTimeMillis())
            return url
        }
    }

    private fun fresh(id: String): String? {
        val c = urlCache[id] ?: return null
        return if (System.currentTimeMillis() - c.at < URL_TTL_MS) c.url else null
    }

    private class CachedVideo(val video: String, val audio: String?, val at: Long)
    private val videoCache = ConcurrentHashMap<String, CachedVideo>()

    /**
     * Direcciones directas del VIDEO: (video, audio). Con calidades altas YouTube separa imagen y sonido,
     * así que devuelve dos direcciones y el reproductor las une. Prefiere H.264 + AAC (lo entienden todos los teléfonos y TV).
     */
    suspend fun videoUrls(id: String, maxHeight: Int): Pair<String, String?> = withContext(Dispatchers.IO) {
        val key = "$id@$maxHeight"
        val c = videoCache[key]
        if (c != null && System.currentTimeMillis() - c.at < 2L * 60 * 60 * 1000) return@withContext c.video to c.audio
        Engine.awaitReady()
        val h = maxHeight
        val req = YoutubeDLRequest("$WATCH$id")
        req.addOption(
            "-f",
            "bv*[height<=$h][vcodec^=avc1]+ba[acodec^=mp4a]/b[height<=$h][ext=mp4]/bv*[height<=$h]+ba/b[height<=$h]/b",
        )
        req.addOption("-g")
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        req.addOption("--socket-timeout", 20)
        val out = YoutubeDL.getInstance().execute(req, null, null).out
        val urls = out.lineSequence().map { it.trim() }.filter { it.startsWith("http") }.toList()
        if (urls.isEmpty()) throw IllegalStateException("No se encontró el video")
        videoCache[key] = CachedVideo(urls[0], urls.getOrNull(1), System.currentTimeMillis())
        urls[0] to urls.getOrNull(1)
    }

    // Precarga de a UNA a la vez (varios yt-dlp en paralelo harían todo más lento)
    private val prefetcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "ritmo-prefetch").apply { isDaemon = true }
    }

    /** Resuelve canciones con anticipación, en orden, para que suenen casi al instante al tocarlas. */
    fun prefetch(id: String) {
        if (fresh(id) != null) return
        prefetcher.execute { try { streamUrl(id) } catch (_: Throwable) { } }
    }
}
