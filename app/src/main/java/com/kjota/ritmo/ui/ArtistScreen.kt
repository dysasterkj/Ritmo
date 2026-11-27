package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.engine.YouTube

/** Página del artista: canciones populares y álbumes / playlists. Se abre desde "Ir al artista". */
@Composable
fun ArtistScreen(vm: MainViewModel, artist: String, onClose: () -> Unit) {
    var songs by remember { mutableStateOf<List<Track>?>(null) }
    var albums by remember { mutableStateOf<List<YouTube.AlbumRef>?>(null) }
    var album by remember { mutableStateOf<YouTube.AlbumRef?>(null) }
    var albumTracks by remember { mutableStateOf<List<Track>?>(null) }

    LaunchedEffect(artist) {
        val key = artist.lowercase().trim()
        songs = try {
            val r = YouTube.search("$artist canciones", 40, 12 * 60)
            val mine = r.filter { it.mainArtist.lowercase().contains(key.take(12)) || key.contains(it.mainArtist.lowercase().take(12)) }
            (mine.ifEmpty { r }).take(20)
        } catch (_: Throwable) { emptyList() }
    }
    LaunchedEffect(artist) {
        albums = try { YouTube.artistAlbums(artist) } catch (_: Throwable) { emptyList() }
    }
    LaunchedEffect(album) {
        val a = album
        albumTracks = null
        if (a != null) albumTracks = try { YouTube.playlistTracks(a.id) } catch (_: Throwable) { emptyList() }
    }

    Dialog(onDismissRequest = { if (album != null) album = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.Ink).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (album != null) album = null else onClose() }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    album?.title ?: artist, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            val list = if (album != null) albumTracks else songs
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.Green) }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip("Reproducir", Icons.Rounded.PlayArrow, true) { if (list.isNotEmpty()) { vm.play(list, 0, radio = album == null); onClose() } }
                            Chip("Mezclar", Icons.Rounded.Shuffle, false) { if (list.isNotEmpty()) { vm.play(list.shuffled(), 0, radio = album == null); onClose() } }
                            if (album == null) Chip("No recomendar", Icons.Rounded.Block, false) {
                                list.firstOrNull()?.let { vm.dislike(it) }
                                onClose()
                            }
                        }
                    }
                    if (album == null) {
                        item { Header("Canciones populares") }
                    }
                    itemsIndexed(list, key = { i, t -> "$i${t.id}" }) { i, t ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { vm.play(list, i, radio = album == null); onClose() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(88.dp).height(50.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(t.artist + " • " + formatTime(t.durationSec), color = Pal.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (album == null) {
                        item { Header("Álbumes y playlists") }
                        item {
                            val al = albums
                            when {
                                al == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.Green, strokeWidth = 2.dp) }
                                al.isEmpty() -> Text("No encontré álbumes.", color = Pal.Dim, modifier = Modifier.padding(16.dp))
                                else -> LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(al, key = { it.id }) { a ->
                                        Column(Modifier.width(150.dp).clickable { album = a }) {
                                            AsyncImage(
                                                model = a.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(a.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Chip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) Pal.Green else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
