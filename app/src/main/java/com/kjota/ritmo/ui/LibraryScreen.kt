package com.kjota.ritmo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Track

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Tu biblioteca", Modifier.weight(1f))
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, "Nueva playlist", tint = Color.White)
                }
            }
        }
        item {
            CollectionRow(
                title = "Canciones que te gustan", subtitle = "${lib.liked.size} canciones",
                icon = { GradientIcon(listOf(Pal.Violet, Pal.Blue), Icons.Rounded.Favorite, Modifier.size(60.dp), 14) },
                onClick = { onOpen("liked") },
            )
        }
        item {
            CollectionRow(
                title = "Descargadas", subtitle = "${lib.downloads.size} canciones · sin conexión",
                icon = { GradientIcon(listOf(Pal.Green, Pal.Mint), Icons.Rounded.Download, Modifier.size(60.dp), 14) },
                onClick = { onOpen("downloads") },
            )
        }
        item {
            CollectionRow(
                title = "Escuchadas recientemente", subtitle = "${lib.recent.size} canciones",
                icon = { GradientIcon(listOf(Pal.Orange, Pal.Pink), Icons.Rounded.QueueMusic, Modifier.size(60.dp), 14) },
                onClick = { onOpen("recent") },
            )
        }
        items(lib.playlists, key = { it.id }) { p ->
            CollectionRow(
                title = p.name, subtitle = "Playlist · ${p.tracks.size} canciones",
                icon = {
                    if (p.tracks.isNotEmpty()) Cover(p.tracks.first().thumb, Modifier.size(60.dp), 14)
                    else GradientIcon(listOf(Color(0xFF3A3F47), Color(0xFF22262C)), Icons.Rounded.QueueMusic, Modifier.size(60.dp), 14)
                },
                onClick = { onOpen(p.id) },
            )
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = Color(0xF2151A22),
            title = { Text("Nueva playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.createPlaylist(name); name = ""; creating = false }) {
                    Text("Crear", color = Pal.Green)
                }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar", color = Color.White) } },
        )
    }
}

@Composable
private fun CollectionRow(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = Pal.Dim)
        }
    }
}

/** Contenido de una colección: favoritas, descargadas, recientes o una playlist (por su id). */
@Composable
fun CollectionScreen(
    vm: MainViewModel,
    collectionId: String,
    currentId: String?,
    onBack: () -> Unit,
    onPickPlaylist: (Track) -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val playlist = lib.playlists.firstOrNull { it.id == collectionId }
    val (title, tracks) = when (collectionId) {
        "liked" -> "Canciones que te gustan" to lib.liked
        "downloads" -> "Descargadas" to lib.downloads
        "recent" -> "Escuchadas recientemente" to lib.recent
        else -> (playlist?.name ?: "Playlist") to (playlist?.tracks ?: emptyList())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    // En colecciones propias de la biblioteca no hace falta "radio" automática.
    val radio = false

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = Color.White) }
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (playlist != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Eliminar playlist", tint = Pal.Dim)
                    }
                }
            }
        }
        item {
            val extra = when (collectionId) {
                "downloads" -> if (Library.usedBytes() > 0) " · ${formatSize(Library.usedBytes())}" else ""
                else -> ""
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${tracks.size} canciones$extra", color = Pal.Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (collectionId != "downloads" && tracks.isNotEmpty()) {
                    IconButton(onClick = { vm.download(*tracks.toTypedArray()) }) {
                        Icon(Icons.Rounded.Download, "Descargar todo", tint = Color.White)
                    }
                }
                IconButton(onClick = { if (tracks.isNotEmpty()) vm.play(tracks, 0, radio, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = Color.White)
                }
                RoundPlay(onClick = { if (tracks.isNotEmpty()) vm.play(tracks, 0, radio) }, size = 52) {
                    Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color(0xFF00210B), modifier = Modifier.size(32.dp))
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                Text(
                    when (collectionId) {
                        "liked" -> "Toca ⋮ en cualquier canción y elige “Agregar a favoritas”."
                        "downloads" -> "Descarga canciones con ⋮ → Descargar para escucharlas sin conexión."
                        "recent" -> "Aquí aparecerá lo que vayas escuchando."
                        else -> "Esta playlist está vacía. Agrega canciones con ⋮ → Agregar a playlist."
                    },
                    color = Pal.Dim, modifier = Modifier.padding(16.dp),
                )
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(
                track = t, vm = vm, isCurrent = t.id == currentId,
                onClick = { vm.play(tracks, i, radio) },
                onPickPlaylist = onPickPlaylist,
                extra = if (playlist != null) listOf("Quitar de esta playlist" to { vm.removeFromPlaylist(playlist.id, t) })
                else emptyList(),
            )
        }
    }

    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xF2151A22),
            title = { Text("¿Eliminar “${playlist.name}”?", color = Color.White) },
            text = { Text("Las canciones no se borran de tu teléfono.", color = Pal.Dim) },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(playlist.id); confirmDelete = false; onBack() }) {
                    Text("Eliminar", color = Color(0xFFFFB4AB))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Color.White) } },
        )
    }
}
