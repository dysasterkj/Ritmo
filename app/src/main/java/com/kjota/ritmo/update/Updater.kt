package com.kjota.ritmo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones desde la propia app: consulta la última versión publicada en GitHub (Releases),
 * descarga el APK y abre el instalador de Android. Cada APK está firmado con la misma llave,
 * así que se instala encima de la versión anterior sin perder datos.
 */
object Updater {
    private const val REPO = "dysasterkj/Ritmo"
    private const val UA = "Ritmo-Updater"

    data class Release(
        val tag: String,
        val build: Int,
        val apkUrl: String,
        val sizeBytes: Long,
        val notes: String,
    )

    data class State(
        val installed: String = "",
        val checking: Boolean = false,
        val release: Release? = null,
        val downloading: Boolean = false,
        val progress: Int = 0,
        val downloaded: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun installedBuild(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    private fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    private fun apkFile(ctx: Context): File =
        File(File(ctx.cacheDir, "updates").also { it.mkdirs() }, "update.apk")

    /** Busca una versión nueva. [manual] = el usuario lo pidió (muestra también "ya estás al día" y errores). */
    fun checkAsync(ctx: Context, manual: Boolean = false) {
        val app = ctx.applicationContext
        val s = _state.value
        if (s.checking || s.downloading) return
        _state.update { it.copy(installed = installedName(app), checking = true, message = null) }
        scope.launch {
            try {
                val conn = URL("https://api.github.com/repos/$REPO/releases?per_page=15").openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", UA)
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                // Se toma la versión con el número más alto (GitHub puede marcar como "latest" una anterior si dos compilaciones terminan juntas).
                var json = JSONObject()
                var bestBuild = -1
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
                    val b = o.optString("tag_name").trimStart('v', 'V').substringAfterLast('.').toIntOrNull() ?: 0
                    if (b > bestBuild) { bestBuild = b; json = o }
                }
                val tag = json.optString("tag_name")
                val build = tag.trimStart('v', 'V').substringAfterLast('.').toIntOrNull() ?: 0
                var url = ""
                var size = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.optString("browser_download_url")
                            size = a.optLong("size")
                            break
                        }
                    }
                }
                val newer = url.isNotEmpty() && build > installedBuild(app)
                _state.update {
                    it.copy(
                        checking = false,
                        release = if (newer) Release(tag, build, url, size, json.optString("body")) else null,
                        downloaded = if (newer) it.downloaded && it.release?.tag == tag else false,
                        message = if (!newer && manual) "Ya tienes la última versión" else null,
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        checking = false,
                        message = if (manual) "No se pudo buscar actualizaciones. Revisa tu conexión." else null,
                    )
                }
            }
        }
    }

    fun downloadAsync(ctx: Context) {
        val app = ctx.applicationContext
        val rel = _state.value.release ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0, downloaded = false, message = null) }
        scope.launch {
            try {
                val file = apkFile(app)
                file.delete()
                val conn = URL(rel.apkUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", UA)
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.sizeBytes
                var done = 0L
                var last = -1
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val p = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                            if (p != last) {
                                last = p
                                _state.update { it.copy(progress = p) }
                            }
                        }
                    }
                }
                _state.update { it.copy(downloading = false, downloaded = true, progress = 100) }
                install(app)
            } catch (e: Throwable) {
                _state.update {
                    it.copy(downloading = false, message = "No se pudo descargar la actualización. Intenta de nuevo.")
                }
            }
        }
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val file = apkFile(app)
        if (!file.exists() || file.length() == 0L) {
            _state.update { it.copy(downloaded = false, message = "Falta descargar la actualización") }
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.update {
                it.copy(message = "Activa «Permitir de esta fuente» para esta app, vuelve y toca Instalar otra vez.")
            }
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
