package com.kjota.ritmo.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/** Cola de descargas de canciones. El trabajo real lo hace [DownloadService]. */
object Downloads {
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** id de canción -> porcentaje (0..100) de las que están en cola o descargándose. */
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val queue = ConcurrentLinkedQueue<Track>()

    fun enqueue(ctx: Context, vararg tracks: Track): Int {
        var added = 0
        for (t in tracks) {
            if (Library.localFile(t.id) != null) continue
            if (_progress.value.containsKey(t.id)) continue
            setProgress(t.id, 0)
            queue.add(t)
            added++
        }
        if (added > 0) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }
        return added
    }

    internal fun poll(): Track? = queue.poll()

    internal fun hasPending(): Boolean = queue.isNotEmpty()

    internal fun setProgress(id: String, percent: Int) {
        _progress.value = _progress.value + (id to percent.coerceIn(0, 100))
    }

    internal fun finish(id: String) {
        _progress.value = _progress.value - id
    }
}
