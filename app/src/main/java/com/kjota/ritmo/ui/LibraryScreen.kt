package com.kjota.ritmo.ui

import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
fun LibraryScreen(vm: MainViewModel, onOpen: (String) -> Unit, onImport: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Tu biblioteca", Modifier.weight(1f))
                Box {
                    IconButton(onClick = { addMenu = true }) {
                        Icon(Icons.Rounded.Add, "Agregar", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = addMenu,
                        onDismissRequest = { addMenu = false },
                        modifier = Modifier.background(Color(0xF2151A22)),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nueva playlist", color = Color.White) },
                            onClick = { addMenu = false; creating = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar playlist", color = Color.White) },
                            onClick = { addMenu = false; onImport() },
                        )
                    }
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
    var renaming by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOff by remember { mutableStateOf(0f) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
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
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Rounded.Edit, "Cambiar nombre", tint = Pal.Dim)
                    }
                    IconButton(onClick = { reorder = !reorder }) {
                        Icon(Icons.Rounded.SwapVert, "Ordenar", tint = if (reorder) Pal.Green else Pal.Dim)
                    }
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
        if (reorder && playlist != null) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                val idx by androidx.compose.runtime.rememberUpdatedState(i)
                val dragging = dragKey == t.id
                Row(
                    Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .fillMaxWidth().height(64.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOff else 0f }
                        .background(if (dragging) Color(0xFF2A3342) else Color.Transparent)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coil.compose.AsyncImage(
                        model = t.thumb, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(width = 80.dp, height = 45.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(t.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(48.dp).pointerInput(t.id) {
                            androidx.compose.foundation.gestures.detectDragGestures(
                                onDragStart = { dragKey = t.id; dragOff = 0f },
                                onDragEnd = { dragKey = null; dragOff = 0f },
                                onDragCancel = { dragKey = null; dragOff = 0f },
                            ) { change, amt ->
                                change.consume()
                                dragOff += amt.y
                                if (dragOff > rowPx * 0.5f && idx < tracks.lastIndex) {
                                    Library.movePlaylistTrack(playlist.id, idx, idx + 1); dragOff -= rowPx
                                } else if (dragOff < -rowPx * 0.5f && idx > 0) {
                                    Library.movePlaylistTrack(playlist.id, idx, idx - 1); dragOff += rowPx
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.DragHandle, "Mover", tint = Pal.Dim) }
                }
            }
        } else
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

    if (renaming && playlist != null) {
        var text by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Color(0xF2151A22),
            title = { Text("Nombre de la playlist", color = Color.White) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                )
            },
            confirmButton = {
                TextButton(onClick = { Library.renamePlaylist(playlist.id, text); renaming = false }) { Text("Guardar", color = Pal.Green) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar", color = Color.White) } },
        )
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
