package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track
import java.util.Locale

fun formatTime(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.0f MB", mb)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}

@Composable
fun Cover(url: String, modifier: Modifier = Modifier, radius: Int = 12) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
fun Loader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Pal.Green, strokeWidth = 3.dp)
    }
}

/**
 * Fila de canción. Muestra el estado de descarga y un menú con todas las acciones.
 * [extra] permite agregar opciones propias de la pantalla (quitar de la playlist, etc.).
 */
@Composable
fun TrackRow(
    track: Track,
    vm: MainViewModel,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPickPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier,
    extra: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val prog by vm.downloading.collectAsStateWithLifecycle()
    val liked = lib.liked.any { it.id == track.id }
    val downloaded = lib.downloads.any { it.id == track.id }
    val percent = prog[track.id]
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .tvClickable(RoundedCornerShape(16.dp), onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.thumb, Modifier.size(54.dp), 12)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (isCurrent) Pal.Green else Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(Icons.Rounded.DownloadDone, null, tint = Pal.Green, modifier = Modifier.size(15.dp))
                    Box(Modifier.size(4.dp))
                }
                Text(
                    buildString {
                        append(track.artist)
                        if (track.durationSec > 0) append(" · ").append(formatTime(track.durationSec))
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = Pal.Dim,
                )
            }
        }
        if (percent != null) {
            CircularProgressIndicator(
                progress = { (percent / 100f).coerceIn(0.03f, 1f) },
                modifier = Modifier.size(22.dp),
                color = Pal.Green,
                strokeWidth = 2.5.dp,
            )
            Box(Modifier.size(10.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Más opciones", tint = Pal.Dim)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.background(Color(0xF2151A22)),
            ) {
                MenuItem(if (liked) "Quitar de favoritas" else "Agregar a favoritas",
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder) {
                    menu = false; vm.toggleLike(track)
                }
                if (downloaded) {
                    MenuItem("Eliminar descarga", Icons.Rounded.Delete) { menu = false; vm.removeDownload(track) }
                } else if (percent == null) {
                    MenuItem("Descargar", Icons.Rounded.Download) { menu = false; vm.download(track) }
                }
                MenuItem("Ver video", Icons.Rounded.Videocam) { menu = false; vm.watchVideo(track) }
                MenuItem("Agregar a playlist", Icons.Rounded.PlaylistAdd) { menu = false; onPickPlaylist(track) }
                MenuItem("Reproducir a continuación", Icons.Rounded.QueueMusic) { menu = false; vm.playNext(track) }
                MenuItem("Agregar a la cola", Icons.Rounded.Add) { menu = false; vm.addToQueue(track) }
                extra.forEach { (label, action) ->
                    MenuItem(label, Icons.Rounded.Delete) { menu = false; action() }
                }
            }
        }
    }
}

@Composable
internal fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Color.White) },
        leadingIcon = { Icon(icon, null, tint = Pal.Dim) },
        onClick = onClick,
    )
}

/** Diálogo para elegir a qué playlist agregar una canción (o crear una nueva). */
@Composable
fun PlaylistPicker(track: Track, vm: MainViewModel, onDismiss: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2151A22),
        title = { Text("Agregar a playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Nombre de la playlist") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        vm.createPlaylist(name, track)
                        onDismiss()
                    }) { Text("Crear y agregar", color = Pal.Green) }
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, null, tint = Pal.Green)
                        Box(Modifier.size(8.dp))
                        Text("Nueva playlist", color = Pal.Green)
                    }
                }
                lib.playlists.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.addToPlaylist(p.id, track); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("${p.tracks.size}", color = Pal.Dim, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) } },
    )
}

@Composable
fun GradientIcon(
    colors: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    radius: Int = 12,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun RoundPlay(onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 56, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Pal.Green)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
