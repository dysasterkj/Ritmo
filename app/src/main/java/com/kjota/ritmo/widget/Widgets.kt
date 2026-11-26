package com.kjota.ritmo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.kjota.ritmo.MainActivity
import com.kjota.ritmo.R
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.player.PlaybackService
import com.kjota.ritmo.player.toMediaItem
import com.kjota.ritmo.player.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RitmoWidgetSmall : WidgetBase()
class RitmoWidgetMedium : WidgetBase()
class RitmoWidgetLarge : WidgetBase()

abstract class WidgetBase : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        WidgetUpdater.refresh(context)
    }
}

/** Estado de lo que suena y construcción de los tres tamaños de widget. */
object WidgetUpdater {
    private class Snap(val id: String, val title: String, val artist: String, val playing: Boolean)

    @Volatile private var snap: Snap? = null
    private var lastCoverId: String? = null
    private var lastCover: Bitmap? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Lo llama el reproductor cuando cambia la canción o el play/pausa. */
    fun fromPlayer(ctx: Context, p: Player) {
        val item = p.currentMediaItem
        snap = if (item == null) null else Snap(
            item.mediaId,
            item.mediaMetadata.title?.toString().orEmpty(),
            item.mediaMetadata.artist?.toString().orEmpty(),
            p.isPlaying,
        )
        refresh(ctx)
    }

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                Library.init(app)
                val s = snap
                val bmp = s?.let { cover(app, it.id) }
                val liked = s != null && Library.isLiked(s.id)
                val mgr = AppWidgetManager.getInstance(app)
                for (cls in listOf(RitmoWidgetSmall::class.java, RitmoWidgetMedium::class.java, RitmoWidgetLarge::class.java)) {
                    push(app, mgr, cls, s, bmp, liked)
                }
            } catch (_: Throwable) { }
        }
    }

    private suspend fun cover(app: Context, id: String): Bitmap? {
        if (id == lastCoverId && lastCover != null) return lastCover
        return try {
            val req = ImageRequest.Builder(app)
                .data(Track(id, "", "", 0).thumb)
                .size(360, 203)
                .allowHardware(false)
                .build()
            val bmp = (app.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
            if (bmp != null) { lastCoverId = id; lastCover = bmp }
            bmp
        } catch (_: Throwable) { null }
    }

    private fun push(
        app: Context, mgr: AppWidgetManager, cls: Class<*>,
        s: Snap?, bmp: Bitmap?, liked: Boolean,
    ) {
        val ids = mgr.getAppWidgetIds(ComponentName(app, cls))
        for (id in ids) {
            // El diseño se elige por el tamaño real al que el usuario estiró el widget.
            val o = mgr.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 70)
            val layout = when {
                h >= 210 -> R.layout.widget_large
                h >= 110 -> R.layout.widget_medium
                w < 170 -> R.layout.widget_mini
                else -> R.layout.widget_small
            }
            mgr.updateAppWidget(id, build(app, layout, s, bmp, liked))
        }
    }

    private fun build(app: Context, layout: Int, s: Snap?, bmp: Bitmap?, liked: Boolean): RemoteViews {
        val rv = RemoteViews(app.packageName, layout)
        rv.setTextViewText(R.id.w_title, s?.title ?: "Ritmo")
        rv.setTextViewText(R.id.w_artist, s?.artist ?: "Toca para elegir música")
        if (bmp != null) rv.setImageViewBitmap(R.id.w_cover, bmp) else rv.setImageViewResource(R.id.w_cover, R.drawable.ic_w_note)
        rv.setImageViewResource(R.id.w_play, if (s?.playing == true) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        rv.setImageViewResource(R.id.w_like, if (liked) R.drawable.ic_w_heart_on else R.drawable.ic_w_heart)

        val open = PendingIntent.getActivity(
            app, 100, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.w_root, open)
        rv.setOnClickPendingIntent(R.id.w_cover, open)
        rv.setOnClickPendingIntent(R.id.w_btn_open, open)
        fun act(view: Int, action: String, code: Int) =
            rv.setOnClickPendingIntent(view, action(app, action, code))
        act(R.id.w_play, WidgetReceiver.PLAY_PAUSE, 1)
        act(R.id.w_next, WidgetReceiver.NEXT, 2)
        act(R.id.w_prev, WidgetReceiver.PREV, 3)
        act(R.id.w_like, WidgetReceiver.LIKE, 4)
        act(R.id.w_dislike, WidgetReceiver.DISLIKE, 7)
        act(R.id.w_btn_liked, WidgetReceiver.PLAY_LIKED, 5)
        act(R.id.w_btn_recent, WidgetReceiver.PLAY_RECENT, 6)
        return rv
    }

    private fun action(app: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            app, code,
            Intent(app, WidgetReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Recibe los toques de los botones del widget y controla el reproductor. */
class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = ctx.applicationContext
        Library.init(app)
        val pending = goAsync()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                when (action) {
                    PLAY_PAUSE -> when {
                        c.isPlaying -> c.pause()
                        c.mediaItemCount > 0 -> { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
                        else -> {
                            val s = Library.state.value
                            start(app, c, s.recent.ifEmpty { s.liked.shuffled() }, "Aún no hay música: elige una en la app")
                        }
                    }
                    NEXT -> c.seekToNextMediaItem()
                    PREV -> c.seekToPreviousMediaItem()
                    DISLIKE -> c.currentMediaItem?.toTrack()?.let { t ->
                        com.kjota.ritmo.data.Taste.init(app)
                        com.kjota.ritmo.data.Taste.dislike(t)
                        if (Library.isLiked(t.id)) Library.toggleLike(t)
                        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
                        Toast.makeText(app, "No te sugeriremos más a ${t.artist}", Toast.LENGTH_SHORT).show()
                    }
                    LIKE -> c.currentMediaItem?.toTrack()?.let { Library.toggleLike(it) }
                    PLAY_LIKED -> start(app, c, Library.state.value.liked.shuffled(), "Aún no tienes favoritas")
                    PLAY_RECENT -> start(app, c, Library.state.value.recent, "Aún no hay historial")
                }
            } catch (_: Throwable) { }
            if (action == LIKE || action == DISLIKE) WidgetUpdater.refresh(app)
            // Se suelta el control un instante después para que los comandos lleguen al servicio.
            Handler(Looper.getMainLooper()).postDelayed({
                try { MediaController.releaseFuture(future) } catch (_: Throwable) { }
                pending.finish()
            }, 1200)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun start(app: Context, c: MediaController, tracks: List<Track>, emptyMsg: String) {
        if (tracks.isEmpty()) {
            Toast.makeText(app, emptyMsg, Toast.LENGTH_SHORT).show()
            return
        }
        c.shuffleModeEnabled = false
        c.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0L)
        c.prepare()
        c.play()
    }

    companion object {
        const val PLAY_PAUSE = "com.kjota.ritmo.widget.PLAY_PAUSE"
        const val NEXT = "com.kjota.ritmo.widget.NEXT"
        const val PREV = "com.kjota.ritmo.widget.PREV"
        const val DISLIKE = "com.kjota.ritmo.widget.DISLIKE"
        const val LIKE = "com.kjota.ritmo.widget.LIKE"
        const val PLAY_LIKED = "com.kjota.ritmo.widget.PLAY_LIKED"
        const val PLAY_RECENT = "com.kjota.ritmo.widget.PLAY_RECENT"
    }
}
