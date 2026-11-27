package com.kjota.ritmo.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Perfiles locales, sin cuenta ni contraseña. Cada perfil tiene su propia biblioteca, recientes,
 * gustos y última sesión. El primer perfil usa las carpetas de siempre (no se pierde nada).
 * Las descargas de audio se comparten entre perfiles.
 */
object Profiles {
    class P(val id: String, val name: String)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("ritmo_profiles", Context.MODE_PRIVATE)

    fun list(ctx: Context, defaultName: String = "Principal"): List<P> {
        val raw = sp(ctx).getString("list", null)
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                val out = (0 until a.length()).map { a.getJSONObject(it).let { o -> P(o.getString("id"), o.getString("name")) } }
                if (out.isNotEmpty()) return out
            } catch (_: Throwable) { }
        }
        val first = listOf(P("p0", defaultName))
        save(ctx, first)
        return first
    }

    private fun save(ctx: Context, l: List<P>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        sp(ctx).edit().putString("list", a.toString()).apply()
    }

    fun currentId(ctx: Context): String = sp(ctx).getString("current", "p0") ?: "p0"

    fun current(ctx: Context): P = list(ctx).firstOrNull { it.id == currentId(ctx) } ?: list(ctx).first()

    /** Carpeta de datos del perfil activo. */
    fun dir(ctx: Context): File {
        val base = ctx.applicationContext.filesDir
        val id = currentId(ctx)
        if (id == "p0") return base
        return File(base, "profiles/$id").also { it.mkdirs() }
    }

    fun add(ctx: Context, name: String): P {
        val l = list(ctx)
        val p = P("p" + System.currentTimeMillis().toString(36), name.trim().ifBlank { "Perfil ${l.size + 1}" })
        save(ctx, l + p)
        return p
    }

    fun rename(ctx: Context, id: String, name: String) {
        if (name.isBlank()) return
        save(ctx, list(ctx).map { if (it.id == id) P(id, name.trim()) else it })
    }

    fun delete(ctx: Context, id: String) {
        if (id == "p0" || id == currentId(ctx)) return
        save(ctx, list(ctx).filterNot { it.id == id })
        File(ctx.applicationContext.filesDir, "profiles/$id").deleteRecursively()
    }

    /** Cambia de perfil y reinicia la app para cargar sus datos. */
    fun switchTo(ctx: Context, id: String) {
        if (id == currentId(ctx)) return
        sp(ctx).edit().putString("current", id).commit()
        val app = ctx.applicationContext
        val i = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(i)
        Handler(Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 250)
    }
}
