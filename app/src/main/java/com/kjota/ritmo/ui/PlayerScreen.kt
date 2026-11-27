package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Headphones
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
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
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
fun PlayerScreen(
    vm: MainViewModel,
    mode: Int,
    onMode: (Int) -> Unit,
    video: VideoState?,
    onClose: () -> Unit,
    onPickPlaylist: (Track) -> Unit,
    onOpenEq: () -> Unit,
    onFullLyrics: () -> Unit,
    onVideoFull: () -> Unit,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val prog by vm.downloading.collectAsStateWithLifecycle()
    val t = now.track
    var drag by remember { mutableStateOf<Float?>(null) }
    val vOn = video != null && mode == 2
    if (mode == 1 || mode == 2) KeepScreenOn()
    val qFrac = remember { androidx.compose.animation.core.Animatable(0f) }
    val qScope = androidx.compose.runtime.rememberCoroutineScope()
    var rootH by remember { mutableStateOf(0f) }
    var showArtist by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.openQueue.collect {
            qFrac.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f))
        }
    }
    val mixOn by vm.mixOn.collectAsStateWithLifecycle()
    val mixState by vm.mixBus.collectAsStateWithLifecycle()
    val eqOn by vm.eqOn.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Ink)
            .onSizeChanged { rootH = it.height.toFloat() }
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
        val curPos = if (vOn) video!!.pos else now.positionMs
        val dur = (if (vOn && video!!.dur > 0) video.dur else now.durationMs).coerceAtLeast(1L)
        val frac = drag ?: (curPos.toFloat() / dur).coerceIn(0f, 1f)
        val shownMs = if (drag != null) (drag!! * dur).toLong() else curPos
        val isPlayingNow = if (vOn) video!!.playing else now.isPlaying
        val isBufferingNow = if (vOn) (video!!.loading || video.buffering) else now.buffering

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.45f))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ModeToggle(mode, onMode)
                }
                if (mode == 1) {
                    IconButton(onClick = onFullLyrics) {
                        Icon(Icons.Rounded.Fullscreen, "Letra a pantalla completa", tint = Color.White)
                    }
                } else if (mode == 2) {
                    IconButton(onClick = onVideoFull) {
                        Icon(Icons.Rounded.Fullscreen, "Video a pantalla completa", tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            if (mode == 1) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LyricsPanel(
                        vm = vm,
                        track = t,
                        positionMs = now.positionMs,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(10.dp))
            } else if (mode == 2) {
                Spacer(Modifier.weight(0.5f))
                if (video != null) {
                    VideoInline(vm, video, onFullscreen = onVideoFull)
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Pal.Green, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.weight(0.5f))
            } else {
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


            }

            if (mixState.mixing) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(18.dp), tint = Pal.Green, strength = 1.6f)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Pal.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("MEZCLANDO", color = Pal.Green, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "${mixState.outTitle} → ${mixState.inTitle}",
                        color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(mixState.progress * 100).toInt()}%", color = Pal.Dim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                Modifier.fillMaxWidth().clickable { showArtist = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, fontSize = 15.sp, color = Pal.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, "Más del artista", tint = Pal.Dim, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.toggleLike(t) }) {
                        Icon(Icons.Rounded.ThumbUp, "Me gusta", tint = if (liked) Pal.Green else Color.White, modifier = Modifier.size(24.dp))
                    }
                    Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.25f)))
                    IconButton(onClick = { vm.dislike(t) }) {
                        Icon(Icons.Rounded.ThumbDown, "No me gusta", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                ActionChip("Guardar", false, { onPickPlaylist(t) }) { Icon(Icons.Rounded.PlaylistAdd, null, tint = it, modifier = Modifier.size(22.dp)) }
                ActionChip(
                    when {
                        downloaded -> "Descargada"
                        percent != null -> "Descargando $percent%"
                        else -> "Descargar"
                    },
                    downloaded,
                    { if (downloaded) vm.removeDownload(t) else if (percent == null) vm.download(t) },
                ) {
                    Icon(if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null, tint = it, modifier = Modifier.size(22.dp))
                }
                ActionChip(if (mixOn) "Mix DJ: sí" else "Mix DJ", mixOn, { vm.setMix(!mixOn) }) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = it, modifier = Modifier.size(22.dp))
                }
                ActionChip(if (eqOn) "Ecualizador: sí" else "Ecualizador", eqOn, onOpenEq) {
                    Icon(Icons.Rounded.Tune, null, tint = it, modifier = Modifier.size(22.dp))
                }
                val sleepMs by com.kjota.ritmo.player.SleepTimer.remainingMs.collectAsStateWithLifecycle()
                ActionChip(
                    when {
                        sleepMs == -1L -> "Al terminar la canción"
                        sleepMs > 0 -> "Apagado en ${(sleepMs / 60_000L) + 1} min"
                        else -> "Temporizador"
                    },
                    sleepMs != 0L,
                    { showSleep = true },
                ) { Icon(Icons.Rounded.Bedtime, null, tint = it, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(4.dp))

            Slider(
                value = frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { f ->
                        val ms = (f * dur).toLong()
                        if (vOn) video!!.seekTo(ms) else vm.seekTo(ms)
                    }
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
                        .clickable { if (vOn) video!!.togglePlay() else vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBufferingNow && !isPlayingNow) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            if (isPlayingNow) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlayingNow) "Pausa" else "Reproducir",
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

            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { qScope.launch { qFrac.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f)) } }
                    .pointerInput(rootH) {
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (rootH > 0f) qScope.launch { qFrac.snapTo((qFrac.value - dy / rootH).coerceIn(0f, 1f)) }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().y
                                qScope.launch {
                                    val target = if (v < -800f) 1f else if (v > 800f) 0f else if (qFrac.value > 0.3f) 1f else 0f
                                    qFrac.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f))
                                }
                            },
                        )
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.4f)))
                Spacer(Modifier.height(8.dp))
                Text("A continuación", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (qFrac.value > 0.001f) QueuePanel(vm, qFrac, rootH, onPickPlaylist)
        if (showSleep) {
            val st = com.kjota.ritmo.player.SleepTimer
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSleep = false },
                containerColor = Color(0xFF1C1C1E),
                title = { Text("Temporizador de apagado", color = Color.White) },
                text = {
                    Column {
                        listOf(15, 30, 45, 60, 90).forEach { m ->
                            Text(
                                "$m minutos", color = Color.White, fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth().clickable { st.startMinutes(m); showSleep = false }.padding(vertical = 12.dp),
                            )
                        }
                        Text(
                            "Al terminar la canción", color = Color.White, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { st.endOfSong(); showSleep = false }.padding(vertical = 12.dp),
                        )
                        Text(
                            "Desactivar", color = Pal.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { st.cancel(); showSleep = false }.padding(vertical = 12.dp),
                        )
                    }
                },
                confirmButton = {},
            )
        }
        if (showArtist) ArtistScreen(vm, t.mainArtist) { showArtist = false }
    }
}

@Composable
internal fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    val items = listOf(
        Icons.Rounded.Headphones to "Carátula",
        Icons.Rounded.Lyrics to "Letra",
        Icons.Rounded.Videocam to "Video",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(3.dp),
    ) {
        items.forEachIndexed { i, (ic, label) ->
            val sel = i == mode
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .tvClickable(RoundedCornerShape(50)) { onMode(i) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ic, label, tint = if (sel) Color.Black else Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, active: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (active) Pal.Green else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Pal.Green.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun GlassPill(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    active: Boolean = false,
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(26.dp), tint = if (active) Pal.Green else Color.White, strength = if (active) 1.8f else 1.2f)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.size(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Letra de la canción. Con tiempos: resalta la línea actual y al tocar una línea salta a ese momento. */
@Composable
internal fun LyricsPanel(vm: MainViewModel, track: Track, positionMs: Long, modifier: Modifier, big: Boolean = false) {
    val k = if (big) 1.4f else 1f
    val ly by vm.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(track.id) { vm.loadLyrics(track) }

    Box(
        modifier.glass(RoundedCornerShape(28.dp), strength = 1.5f),
        contentAlignment = Alignment.Center,
    ) {
        val mine = ly.trackId == track.id
        val data = if (mine) ly.data else null
        when {
            !mine || ly.loading -> CircularProgressIndicator(color = Pal.Green, strokeWidth = 3.dp)

            data == null -> Text(
                "No encontré la letra de esta canción.\nPuede ser instrumental o no estar en la base de datos.",
                color = Pal.Dim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )

            data.synced -> {
                val state = rememberLazyListState()
                val active = data.lines.indexOfLast { it.timeMs <= positionMs + 250L }
                LaunchedEffect(active) {
                    if (active >= 0) state.animateScrollToItem(active, -260)
                }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(data.lines) { i, line ->
                        val isActive = i == active
                        Text(
                            line.text.ifBlank { "♪" },
                            fontSize = (if (isActive) 24f * k else 20f * k).sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.42f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.seekTo(line.timeMs) },
                        )
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(22.dp),
            ) {
                item {
                    Text(
                        data.plain.orEmpty(),
                        fontSize = (18f * k).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        lineHeight = (28f * k).sp,
                    )
                }
            }
        }
    }
}

/** Letra a pantalla completa: sin barras, la pantalla no se apaga y se puede pausar desde aquí. */
@Composable
fun LyricsFullScreen(vm: MainViewModel, onClose: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val t = now.track
    KeepScreenOn()
    SystemUi(hideBars = true, landscape = false)

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Ink)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        if (t != null) {
            AsyncImage(
                model = t.thumbHq,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                    .blur(70.dp)
                    .alpha(0.5f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x55000000), Color(0xE607090C)))
                )
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.FullscreenExit, "Salir de pantalla completa", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = Pal.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.previous() }) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = { vm.togglePlay() }) {
                        Icon(
                            if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (now.isPlaying) "Pausa" else "Reproducir", tint = Color.White, modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(onClick = { vm.next() }) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                LyricsPanel(vm = vm, track = t, positionMs = now.positionMs, modifier = Modifier.weight(1f).fillMaxWidth(), big = true)
            }
        }
    }
}
