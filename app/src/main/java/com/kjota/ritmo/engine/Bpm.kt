package com.kjota.ritmo.engine

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * BPM de las canciones (API pública de Deezer, sin cuenta). Se usa en el Mix DJ para igualar el ritmo
 * de la canción que entra con la que sale. 0 = no se encontró.
 */
object Bpm {
    private val cache = ConcurrentHashMap<String, Int>()
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    /** BPM conocido, o -1 si todavía no se ha consultado. */
    fun get(id: String): Int = cache[id] ?: -1

    fun ensure(id: String, title: String, artist: String) {
        if (cache.containsKey(id) || !inflight.add(id)) return
        Thread {
            try { cache[id] = fetch(title, artist) } catch (_: Throwable) { cache[id] = 0 }
            finally { inflight.remove(id) }
        }.start()
    }

    private fun clean(t: String): String = t
        .replace(Regex("[\\(\\[].*?[\\)\\]]"), " ")
        .replace(Regex("(?i)official|video|lyrics?|letra|audio|hd|4k|remix|mix|ft\\.?.*|feat\\.?.*"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun getJson(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000
        return try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }

    private fun fetch(title: String, artist: String): Int {
        val a = com.kjota.ritmo.data.mainArtistOf(artist)
        var name = clean(title)
        // Muchos títulos son "Artista - Canción": se queda con la parte de la canción
        if (name.contains(" - ")) name = name.substringAfter(" - ").trim()
        val q = URLEncoder.encode("$a $name", "UTF-8")
        val data = getJson("https://api.deezer.com/search?q=$q&limit=1").optJSONArray("data") ?: return 0
        val id = data.optJSONObject(0)?.optLong("id", 0L) ?: return 0
        if (id == 0L) return 0
        return getJson("https://api.deezer.com/track/$id").optDouble("bpm", 0.0).toInt().coerceAtLeast(0)
    }
}
