package com.kjota.ritmo.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Sugerencias de búsqueda de YouTube mientras se escribe (como en YouTube Music). */
object Suggest {
    suspend fun forQuery(q: String): List<String> = withContext(Dispatchers.IO) {
        val text = q.trim()
        if (text.isEmpty()) return@withContext emptyList()
        try {
            val url = URL(
                "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=es&q=" +
                    URLEncoder.encode(text, "UTF-8")
            )
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body).getJSONArray(1)
            List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }.take(8)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
