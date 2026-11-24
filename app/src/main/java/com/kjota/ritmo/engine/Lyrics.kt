package com.kjota.ritmo.engine

import android.content.Context
import com.kjota.ritmo.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] = letra con tiempos (se resalta la línea que suena); si no, es solo texto. */
data class LyricsData(val lines: List<LyricLine>, val plain: String?, val synced: Boolean)

/**
 * Letras desde LRCLIB (lrclib.net): base de datos abierta, gratis y sin cuenta.
 * Se guardan en el teléfono (caché) para verlas también sin conexión.
 */
object Lyrics {
    private val NONE = LyricsData(emptyList(), null, false)
    private val mem = ConcurrentHashMap<String, LyricsData>()

    /** Devuelve la letra, o null si no existe / no hay conexión. */
    suspend fun get(ctx: Context, t: Track): LyricsData? = withContext(Dispatchers.IO) {
        mem[t.id]?.let { return@withContext if (it === NONE) null else it }

        val dir = File(ctx.cacheDir, "lyrics").also { it.mkdirs() }
        val cacheFile = File(dir, "${t.id}.json")
        if (cacheFile.exists()) {
            try {
                val o = JSONObject(cacheFile.readText())
                val d = build(o.optString("s").nullIfBlank(), o.optString("p").nullIfBlank())
                if (d != null) { mem[t.id] = d; return@withContext d }
            } catch (_: Exception) { }
        }

        try {
            val found = lookup(t)
            if (found == null) {
                // Solo se recuerda en memoria: si fue un fallo de red, se reintenta al reabrir la app.
                mem[t.id] = NONE
                return@withContext null
            }
            val synced = found.str("syncedLyrics")
            val plain = found.str("plainLyrics")
            val d = build(synced, plain)
            if (d == null) { mem[t.id] = NONE; return@withContext null }
            mem[t.id] = d
            try {
                cacheFile.writeText(JSONObject().put("s", synced ?: "").put("p", plain ?: "").toString())
            } catch (_: Exception) { }
            d
        } catch (_: Exception) {
            null // sin internet: no se guarda como "sin letra"
        }
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.str(key: String): String? = optString(key).nullIfBlank()

    private fun build(synced: String?, plain: String?): LyricsData? {
        if (synced != null) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return LyricsData(lines, plain, true)
        }
        if (plain != null) return LyricsData(emptyList(), plain, false)
        return null
    }

    private val lrcLine = Regex("^((?:\\[\\d+:\\d+(?:[.:]\\d+)?])+)(.*)$")
    private val lrcTime = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    internal fun parseLrc(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in text.lineSequence()) {
            val m = lrcLine.find(raw.trim()) ?: continue
            val words = m.groupValues[2].trim()
            for (t in lrcTime.findAll(m.groupValues[1])) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val fracStr = t.groupValues[3]
                val ms = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                out.add(LyricLine(min * 60_000 + sec * 1000 + ms, words))
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    // ---------------------------------------------------------------- Búsqueda

    private fun lookup(t: Track): JSONObject? {
        val (artist, title) = guessArtistAndTitle(t)
        // 1) Búsqueda precisa por título + artista.
        var arr = search("track_name=${enc(title)}&artist_name=${enc(artist)}")
        var best = arr?.let { pick(it, t.durationSec) }
        if (best != null) return best
        // 2) Búsqueda libre.
        arr = search("q=${enc("$artist $title")}")
        best = arr?.let { pick(it, t.durationSec) }
        if (best != null) return best
        // 3) Solo el título.
        arr = search("q=${enc(title)}")
        return arr?.let { pick(it, t.durationSec) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun search(params: String): JSONArray? {
        val body = httpGet("https://lrclib.net/api/search?$params") ?: return null
        return try { JSONArray(body) } catch (_: Exception) { null }
    }

    /** Elige el resultado cuya duración más se parece (±12 s) y que tenga letra con tiempos si es posible. */
    private fun pick(arr: JSONArray, durSec: Int): JSONObject? {
        var best: JSONObject? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hasSynced = o.str("syncedLyrics") != null
            val hasPlain = o.str("plainLyrics") != null
            if (!hasSynced && !hasPlain) continue
            val d = o.optDouble("duration", 0.0)
            var diff = 0
            if (durSec > 0 && d > 0) {
                diff = abs(d - durSec).toInt()
                if (diff > 12) continue
            }
            val score = diff * 2 + (if (hasSynced) 0 else 8)
            if (score < bestScore) { bestScore = score; best = o }
        }
        return best
    }

    private val noise = Regex(
        "\\s*[(\\[][^)\\]]*(official|video|audio|lyric|letra|visuali[sz]er|oficial|hd|4k|remaster|clip|live|en vivo)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val featuring = Regex("\\s*[(\\[]?\\s*(ft\\.?|feat\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)

    /** Los títulos de YouTube vienen sucios ("Artista - Canción (Official Video)"): se limpian para buscar. */
    internal fun guessArtistAndTitle(t: Track): Pair<String, String> {
        var title = t.title.replace(noise, "").replace(Regex("\\s*\\|.*$"), "").trim()
        var artist = t.artist
        val dash = title.indexOf(" - ")
        if (dash > 0) {
            artist = title.substring(0, dash).trim()
            title = title.substring(dash + 3).trim()
        }
        title = title.replace(featuring, "").trim().trim('"', '“', '”')
        return artist to title
    }

    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Ritmo/1.0 (app personal)")
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }
}
