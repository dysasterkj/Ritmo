package com.kjota.ritmo.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.engine.Engine

@Composable
fun RitmoRoot(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Track?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler(enabled = playerOpen || collection != null || tab != 0) {
        when {
            playerOpen -> playerOpen = false
            collection != null -> collection = null
            else -> tab = 0
        }
    }

    AuroraBackground {
        Box(Modifier.fillMaxSize()) {
            val open = collection
            when {
                open != null -> CollectionScreen(
                    vm, open, now.track?.id,
                    onBack = { collection = null },
                    onPickPlaylist = { pick = it },
                )
                tab == 0 -> HomeScreen(vm, onOpenCollection = { collection = it }, onOpenSettings = { showSettings = true })
                tab == 1 -> SearchScreen(vm, now.track?.id, onPickPlaylist = { pick = it })
                else -> LibraryScreen(vm, onOpen = { collection = it })
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (now.track != null) MiniPlayer(vm) { playerOpen = true }
                NavBar(tab) { tab = it; collection = null }
            }

            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                PlayerScreen(vm, onClose = { playerOpen = false }, onPickPlaylist = { pick = it })
            }
        }
    }

    pick?.let { PlaylistPicker(it, vm) { pick = null } }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val t = now.track ?: return
    val liked = lib.liked.any { it.id == t.id }
    val frac = (now.positionMs.toFloat() / now.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxWidth()
            .glassBar(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(t.thumb, Modifier.size(44.dp), 10)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = Color.White)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Pal.Dim)
            }
            IconButton(onClick = { vm.toggleLike(t) }) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorita", tint = if (liked) Pal.Green else Color.White,
                )
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(
                    if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (now.isPlaying) "Pausa" else "Reproducir",
                    tint = Color.White, modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White)
            }
        }
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Pal.Green,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassBar(RoundedCornerShape(28.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("Inicio", Icons.Rounded.Home, selected == 0) { onSelect(0) }
        NavItem("Buscar", Icons.Rounded.Search, selected == 1) { onSelect(1) }
        NavItem("Biblioteca", Icons.Rounded.LibraryMusic, selected == 2) { onSelect(2) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip28()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) Pal.Green else Pal.Dim, modifier = Modifier.size(26.dp))
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Pal.Green else Pal.Dim,
        )
    }
}

private fun Modifier.clip28(): Modifier = this.then(Modifier.clip(RoundedCornerShape(20.dp)))

@Composable
private fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val version by Engine.version.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2151A22),
        title = { Text("Ajustes", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Motor de YouTube (yt-dlp): ${version ?: "iniciando…"}", color = Color.White)
                Text(
                    "Si las búsquedas o la reproducción fallan, actualiza el motor: YouTube cambia seguido.",
                    color = Pal.Dim, fontSize = 13.sp,
                )
                TextButton(onClick = { vm.updateEngine() }) { Text("Actualizar motor ahora", color = Pal.Green) }
                Text(
                    "Descargas: ${lib.downloads.size} canciones · ${formatSize(Library.usedBytes())} en el teléfono",
                    color = Color.White, fontSize = 14.sp,
                )
                Text(
                    "Sin anuncios, sin cuentas y sin seguimiento. Todo se guarda solo en este teléfono.",
                    color = Pal.Dim, fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) } },
    )
}
