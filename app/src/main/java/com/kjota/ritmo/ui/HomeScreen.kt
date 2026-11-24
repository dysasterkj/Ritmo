package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.moodChips
import com.kjota.ritmo.update.Updater
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Buenos días"
        h < 20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenCollection: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPickPlaylist: (Track) -> Unit = {},
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val loading by vm.shelvesLoading.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val upd by Updater.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { vm.loadShelves(force = true) },
        modifier = Modifier.statusBarsPadding(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(greeting(), Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, "Ajustes", tint = Color.White)
                }
            }
        }

        upd.release?.let { rel ->
            item(key = "update") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(18.dp), tint = Pal.Green, strength = 1.6f)
                        .tvClickable(RoundedCornerShape(18.dp)) { onOpenSettings() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Nueva versión ${rel.tag}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Toca para actualizar desde Ajustes", color = Pal.Dim, fontSize = 12.5.sp)
                    }
                    Icon(Icons.Rounded.Download, "Actualizar", tint = Pal.Green)
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                itemsIndexed(moodChips) { i, name ->
                    val sel = mood == i
                    Text(
                        name,
                        color = if (sel) Color(0xFF00210B) else Color.White,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(if (sel) Modifier.background(Pal.Green) else Modifier.glass(RoundedCornerShape(50), strength = 0.9f))
                            .tvClickable(RoundedCornerShape(50)) { vm.setMood(i) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Accesos rápidos estilo Spotify
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        "Favoritas", Modifier.weight(1f), onClick = { onOpenCollection("liked") },
                        leading = { GradientIcon(listOf(Pal.Violet, Pal.Blue), Icons.Rounded.Favorite, Modifier.size(56.dp), 0) },
                    )
                    QuickTile(
                        "Descargadas", Modifier.weight(1f), onClick = { onOpenCollection("downloads") },
                        leading = { GradientIcon(listOf(Pal.Green, Pal.Mint), Icons.Rounded.Download, Modifier.size(56.dp), 0) },
                    )
                }
                lib.recent.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t ->
                            QuickTile(
                                t.title, Modifier.weight(1f),
                                onClick = { vm.play(lib.recent, lib.recent.indexOf(t), radio = true) },
                                leading = { Cover(t.thumb, Modifier.size(56.dp), 0) },
                            )
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        if (lib.recent.size > 4) {
            item { ShelfRow("Escuchado hace poco", lib.recent, vm, now.track?.id, onPickPlaylist) { i -> vm.play(lib.recent, i) } }
        }

        shelves.firstOrNull()?.let { first ->
            item(key = "hero") { MixHero(first.tracks) { vm.play(first.tracks.shuffled(), 0, radio = true) } }
        }

        shelves.forEachIndexed { idx, shelf ->
            item(key = "${shelf.title}#$idx") { ShelfRow(shelf.title, shelf.tracks, vm, now.track?.id, onPickPlaylist) { i -> vm.play(shelf.tracks, i) } }
        }

        if (loading && shelves.isEmpty()) item { Loader() }

        if (!loading && shelves.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sin conexión por ahora", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Tus canciones descargadas siguen disponibles en Biblioteca. Toca aquí para reintentar.",
                        color = Pal.Dim, fontSize = 14.sp,
                        modifier = Modifier.clickable { vm.loadShelves(force = true) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun MixHero(tracks: List<Track>, onPlay: () -> Unit) {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val name = when { h < 12 -> "mañana"; h < 20 -> "tarde"; else -> "noche" }
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .glass(RoundedCornerShape(26.dp), strength = 1.4f),
    ) {
        val covers = tracks.take(3)
        covers.forEachIndexed { i, t ->
            Cover(
                t.thumb,
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-(2 - i) * 46 + 10).dp, y = (i * 12 - 10).dp)
                    .size(116.dp)
                    .graphicsLayer { rotationZ = (i - 1) * 7f },
                18,
            )
        }
        Column(Modifier.padding(18.dp).fillMaxWidth(0.55f)) {
            Text("MIX DE HOY", color = Pal.Green, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Text("Tu mix de la $name", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 25.sp)
            Text("Para escuchar sin pensar", color = Pal.Dim, fontSize = 12.5.sp)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Pal.Green)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "Reproducir mix", tint = Color(0xFF00210B), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    modifier: Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(14.dp))
            .tvClickable(RoundedCornerShape(14.dp), onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            title,
            modifier = Modifier.padding(horizontal = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun ShelfRow(
    title: String,
    tracks: List<Track>,
    vm: MainViewModel,
    currentId: String?,
    onPickPlaylist: (Track) -> Unit,
    onPlay: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                VideoCard(
                    t, vm, onClick = { onPlay(i) }, onPickPlaylist = onPickPlaylist,
                    isCurrent = t.id == currentId, modifier = Modifier.width(250.dp),
                )
            }
        }
    }
}
