package com.kjota.ritmo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track

/** Menú de acciones de una canción (favorita, descargar, video, playlist, cola). */
@Composable
fun TrackMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    track: Track,
    vm: MainViewModel,
    onPickPlaylist: (Track) -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val prog by vm.downloading.collectAsStateWithLifecycle()
    val liked = lib.liked.any { it.id == track.id }
    val downloaded = lib.downloads.any { it.id == track.id }
    val percent = prog[track.id]
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(Color(0xF2151A22)),
    ) {
        MenuItem(
            if (liked) "Quitar de favoritas" else "Agregar a favoritas",
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        ) { onDismiss(); vm.toggleLike(track) }
        if (downloaded) {
            MenuItem("Eliminar descarga", Icons.Rounded.Delete) { onDismiss(); vm.removeDownload(track) }
        } else if (percent == null) {
            MenuItem("Descargar", Icons.Rounded.Download) { onDismiss(); vm.download(track) }
        }
        MenuItem("Ver video", Icons.Rounded.Videocam) { onDismiss(); vm.watchVideo(track) }
        MenuItem("Agregar a playlist", Icons.Rounded.PlaylistAdd) { onDismiss(); onPickPlaylist(track) }
        MenuItem("Reproducir a continuación", Icons.Rounded.QueueMusic) { onDismiss(); vm.playNext(track) }
        MenuItem("Agregar a la cola", Icons.Rounded.Add) { onDismiss(); vm.addToQueue(track) }
    }
}

/**
 * Tarjeta estilo YouTube: miniatura 16:9 con duración, título y artista.
 * En el teléfono tiene un botón ⋮; en la TV el menú se abre con una pulsación larga del OK.
 * Con el control remoto, la tarjeta enfocada se agranda y se marca con un borde.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    track: Track,
    vm: MainViewModel,
    onClick: () -> Unit,
    onPickPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    tv: Boolean = false,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val prog by vm.downloading.collectAsStateWithLifecycle()
    val downloaded = lib.downloads.any { it.id == track.id }
    val percent = prog[track.id]
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(if (tv) 16.dp else 14.dp)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape)) {
            Cover(track.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f), 0)
            if (track.durationSec > 0) {
                Text(
                    formatTime(track.durationSec),
                    color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (downloaded) {
                Icon(
                    Icons.Rounded.DownloadDone, "Descargada", tint = Pal.Green,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
            if (percent != null) {
                CircularProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0.03f, 1f) },
                    color = Pal.Green, strokeWidth = 2.5.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp),
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp, start = 2.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, fontSize = if (tv) 16.sp else 14.sp,
                    lineHeight = if (tv) 20.sp else 18.sp,
                    color = if (isCurrent) Pal.Green else Color.White,
                )
                Text(
                    track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = if (tv) 14.sp else 12.5.sp, color = Pal.Dim,
                )
            }
            if (!tv) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, "Más opciones", tint = Pal.Dim)
                    }
                }
            }
            Box { TrackMenu(menu, { menu = false }, track, vm, onPickPlaylist) }
        }
    }
}
