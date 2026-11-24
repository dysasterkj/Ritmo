package com.kjota.ritmo.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.engine.Engine
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Servicio en primer plano que baja las canciones en cola, una por una. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var working = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Descargas de música", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            NOTIF_ID,
            buildNotification("Preparando descargas…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!working) {
            working = true
            scope.launch {
                try {
                    while (true) {
                        val t = Downloads.poll()
                        if (t == null) {
                            working = false
                            // Si justo entró otra canción a la cola, se sigue trabajando.
                            if (Downloads.hasPending()) { working = true; continue } else break
                        }
                        downloadOne(t)
                    }
                } finally {
                    working = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadOne(t: Track) {
        try {
            Engine.awaitReady()
            notify("Descargando: ${t.title}")
            val req = YoutubeDLRequest("https://www.youtube.com/watch?v=${t.id}")
            req.addOption("-f", "bestaudio/best")
            req.addOption("-o", Library.musicDir().absolutePath + "/%(id)s.%(ext)s")
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
            req.addOption("--newline")
            req.addOption("--no-warnings")
            req.addOption("--retries", 5)
            req.addOption("--socket-timeout", 30)
            val onProgress: (Float, Long, String) -> Unit = { p, _, _ ->
                Downloads.setProgress(t.id, p.toInt())
            }
            YoutubeDL.getInstance().execute(req, t.id, onProgress)
            if (Library.localFile(t.id) != null) Library.addDownload(t)
        } catch (_: Throwable) {
            // Falló esta canción: se limpia y se sigue con la siguiente.
        } finally {
            Downloads.finish(t.id)
        }
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Ritmo")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ritmo_downloads"
        private const val NOTIF_ID = 42
    }
}
