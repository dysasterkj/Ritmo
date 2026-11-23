package com.kjota.ritmo.data

import org.json.JSONArray
import org.json.JSONObject

/** Una canción de YouTube. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
) {
    /** Miniatura 16:9 sin franjas negras (para listas). */
    val thumb: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    /** Miniatura grande (tiene franjas negras arriba y abajo; el reproductor las recorta). */
    val thumbHq: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("dur", durationSec)

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            durationSec = o.optInt("dur"),
        )
    }
}

data class Playlist(val id: String, val name: String, val tracks: List<Track>)

fun List<Track>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

fun JSONArray?.toTracks(): List<Track> {
    if (this == null) return emptyList()
    val out = ArrayList<Track>(length())
    for (i in 0 until length()) {
        try { out.add(Track.fromJson(getJSONObject(i))) } catch (_: Exception) { }
    }
    return out
}
