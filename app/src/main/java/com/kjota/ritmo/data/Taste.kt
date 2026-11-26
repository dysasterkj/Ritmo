package com.kjota.ritmo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Gustos: canciones/artistas que marcaste con "no me gusta" y palabras que no quieres ver en las sugerencias
 * (mix automático e Inicio). Las búsquedas que haces tú no se filtran.
 */
object Taste {
    val DEFAULT_WORDS = "niños, infantil, infantiles, kids, children, nursery, para bebés, canciones infantiles, cocomelon, baby shark"

    private var file: File? = null
    private val ids = HashSet<String>()
    private val artists = HashSet<String>()
    private var words: List<String> = emptyList()

    private val _wordsText = MutableStateFlow(DEFAULT_WORDS)
    val wordsText: StateFlow<String> = _wordsText

    private val _count = MutableStateFlow(0)
    val dislikedCount: StateFlow<Int> = _count

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "taste.json")
        file = f
        try {
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.optJSONArray("ids")?.let { a -> for (i in 0 until a.length()) ids.add(a.getString(i)) }
                o.optJSONArray("artists")?.let { a -> for (i in 0 until a.length()) artists.add(a.getString(i)) }
                if (o.has("words")) _wordsText.value = o.getString("words")
            }
        } catch (_: Throwable) { }
        words = parse(_wordsText.value)
        _count.value = ids.size
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    private fun parse(text: String): List<String> =
        text.split(',', '\n').map { norm(it) }.filter { it.length >= 3 }

    @Synchronized
    private fun save() {
        try {
            file?.writeText(
                JSONObject()
                    .put("ids", JSONArray(ids.toList()))
                    .put("artists", JSONArray(artists.toList()))
                    .put("words", _wordsText.value)
                    .toString()
            )
        } catch (_: Throwable) { }
    }

    @Synchronized
    fun dislike(t: Track) {
        ids.add(t.id)
        val a = norm(t.artist)
        if (a.length >= 3) artists.add(a)
        _count.value = ids.size
        save()
    }

    @Synchronized
    fun clearDislikes() {
        ids.clear(); artists.clear(); _count.value = 0; save()
    }

    @Synchronized
    fun setWords(text: String) {
        _wordsText.value = text
        words = parse(text)
        save()
    }

    /** Palabras extra según de qué trata la canción semilla (p. ej. "para despertar" no debe traer trompetas ni alarmas). */
    private fun contextWords(seedTitle: String): List<String> {
        val t = norm(seedTitle)
        return if (t.contains("despert") || t.contains("wake") || t.contains("morning") || t.contains("manana"))
            listOf("trompeta", "corneta", "toque de diana", "diana", "alarma", "bugle", "reveille", "despertador", "sonido de", "ringtone")
        else emptyList()
    }

    @Synchronized
    fun isBlocked(t: Track, seedTitle: String = ""): Boolean {
        if (t.id in ids) return true
        val a = norm(t.artist)
        if (a in artists) return true
        val text = norm(t.title + " " + t.artist)
        if (words.any { text.contains(it) }) return true
        return contextWords(seedTitle).any { text.contains(it) }
    }
}
