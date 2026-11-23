package com.kjota.ritmo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Buenos días"
        h < 20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenCollection: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val loading by vm.shelvesLoading.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
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
            item { ShelfRow("Escuchado hace poco", lib.recent) { i -> vm.play(lib.recent, i) } }
        }

        shelves.forEach { shelf ->
            item(key = shelf.title) { ShelfRow(shelf.title, shelf.tracks) { i -> vm.play(shelf.tracks, i) } }
        }

        if (loading) item { Loader() }

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
            .clickable(onClick = onClick),
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
private fun ShelfRow(title: String, tracks: List<Track>, onPlay: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                Column(
                    Modifier
                        .width(150.dp)
                        .clickable { onPlay(i) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Cover(t.thumb, Modifier.size(150.dp), 18)
                    Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Pal.Dim)
                }
            }
        }
    }
}
