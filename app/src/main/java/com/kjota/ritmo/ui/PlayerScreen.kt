package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track

@Composable
fun PlayerScreen(vm: MainViewModel, onClose: () -> Unit, onPickPlaylist: (Track) -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val prog by vm.downloading.collectAsStateWithLifecycle()
    val t = now.track
    var drag by remember { mutableStateOf<Float?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Ink)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        if (t != null) {
            // Fondo: la propia carátula, muy desenfocada, como vidrio esmerilado.
            AsyncImage(
                model = t.thumbHq,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                    .blur(70.dp)
                    .alpha(0.6f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x33000000), Color(0xE607090C)))
            )
        )

        if (t == null) return@Box

        val liked = lib.liked.any { it.id == t.id }
        val downloaded = lib.downloads.any { it.id == t.id }
        val percent = prog[t.id]
        val dur = now.durationMs.coerceAtLeast(1L)
        val frac = drag ?: (now.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        val shownMs = if (drag != null) (drag!! * dur).toLong() else now.positionMs

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Reproduciendo ahora",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.Dim,
                )
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(0.4f))

            // Carátula en marco de vidrio (recorta las franjas negras de la miniatura de YouTube)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .glass(RoundedCornerShape(30.dp), strength = 1.6f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AsyncImage(
                    model = t.thumbHq,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.34f; scaleY = 1.34f },
                )
            }

            Spacer(Modifier.weight(0.4f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, fontSize = 15.sp, color = Pal.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.toggleLike(t) }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorita", tint = if (liked) Pal.Green else Color.White, modifier = Modifier.size(28.dp),
                    )
                }
            }

            Slider(
                value = frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { vm.seekTo((it * dur).toLong()) }
                    drag = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime((shownMs / 1000).toInt()), fontSize = 12.sp, color = Pal.Dim, modifier = Modifier.weight(1f))
                Text(formatTime((dur / 1000).toInt()), fontSize = 12.sp, color = Pal.Dim)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (now.shuffle) Pal.Green else Color.White)
                }
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (now.buffering && !now.isPlaying) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (now.isPlaying) "Pausa" else "Reproducir",
                            tint = Color.Black, modifier = Modifier.size(42.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir",
                        tint = if (now.repeatMode == Player.REPEAT_MODE_OFF) Color.White else Pal.Green,
                    )
                }
            }

            Spacer(Modifier.weight(0.3f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                GlassPill(
                    modifier = Modifier.weight(1f),
                    label = when {
                        downloaded -> "Descargada"
                        percent != null -> "Descargando $percent%"
                        else -> "Descargar"
                    },
                    onClick = { if (downloaded) vm.removeDownload(t) else if (percent == null) vm.download(t) },
                    icon = {
                        Icon(
                            if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            null, tint = if (downloaded) Pal.Green else Color.White,
                        )
                    },
                )
                GlassPill(
                    modifier = Modifier.weight(1f),
                    label = "Playlist",
                    onClick = { onPickPlaylist(t) },
                    icon = { Icon(Icons.Rounded.PlaylistAdd, null, tint = Color.White) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GlassPill(modifier: Modifier, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(28.dp), strength = 1.2f)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.size(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
