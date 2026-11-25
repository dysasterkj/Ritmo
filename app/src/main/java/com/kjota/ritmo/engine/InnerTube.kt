package com.kjota.ritmo.engine

import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolución RÁPIDA de la dirección del audio: una sola petición HTTP al reproductor de YouTube
 * (cliente "Android VR", el mismo que usa yt-dlp), sin arrancar Python. Tarda ~0,5 s en vez de varios segundos.
 * Si algo falla (video con restricción, cambio de YouTube…) devuelve null y se usa yt-dlp como respaldo.
 */
object InnerTube {
    /** Debe coincidir con el User-Agent del reproductor (PlaybackService). */
    const val PLAYER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

    private const val VERSION = "1.60.19"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/$VERSION (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    @Volatile var status: String = "sin usar"
    @Volatile private var fails = 0
    @Volatile private var pausedUntil = 0L

    /** Si falla varias veces seguidas, se desactiva 10 minutos para no hacer esperar al reproductor. */
    fun audioUrl(id: String): String? {
        if (System.currentTimeMillis() < pausedUntil) return null
        val t0 = System.currentTimeMillis()
        val r = resolve(id)
        if (r.first != null) {
            fails = 0
            status = "ok (${System.currentTimeMillis() - t0} ms)"
        } else {
            status = "falló: ${r.second}"
            if (++fails >= 2) { pausedUntil = System.currentTimeMillis() + 10 * 60 * 1000; fails = 0 }
        }
        return r.first
    }

    private fun resolve(id: String): Pair<String?, String> {
        return try {
            val body = JSONObject()
                .put(
                    "context", JSONObject().put(
                        "client", JSONObject()
                            .put("clientName", "ANDROID_VR")
                            .put("clientVersion", VERSION)
                            .put("deviceMake", "Oculus")
                            .put("deviceModel", "Quest 3")
                            .put("androidSdkVersion", 32)
                            .put("osName", "Android")
                            .put("osVersion", "12L")
                            .put("hl", "es")
                    )
                )
                .put("videoId", id)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
                .toString()

            val c = URL("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 3000
            c.readTimeout = 4000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", VR_UA)
            c.setRequestProperty("X-YouTube-Client-Name", "28")
            c.setRequestProperty("X-YouTube-Client-Version", VERSION)
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) return null to "HTTP ${c.responseCode}"
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })

            val ps = json.optJSONObject("playabilityStatus")?.optString("status")
            if (ps != "OK") return null to "estado $ps"
            val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null to "sin formatos"

            var best: JSONObject? = null
            var bestScore = -1L
            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                val mime = f.optString("mimeType")
                if (!mime.startsWith("audio/") || f.optString("url").isEmpty()) continue
                // Prefiere Opus (webm) y, dentro del tipo, la mayor tasa de bits
                val score = f.optLong("bitrate") + if (mime.contains("opus")) 1_000_000L else 0L
                if (score > bestScore) { bestScore = score; best = f }
            }
            val url = best?.optString("url")?.takeIf { it.startsWith("http") } ?: return null to "sin audio directo"
            if (!reachable(url)) return null to "URL no abre"
            url to "ok"
        } catch (e: Throwable) {
            null to (e.javaClass.simpleName)
        }
    }

    /** Comprueba con una petición mínima que el reproductor podrá abrir esa dirección. */
    private fun reachable(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        c.setRequestProperty("User-Agent", PLAYER_UA)
        c.setRequestProperty("Range", "bytes=0-1")
        val code = c.responseCode
        c.disconnect()
        code == 200 || code == 206
    } catch (_: Throwable) {
        false
    }
}
