package com.kjota.ritmo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track
import com.kjota.ritmo.moodChips
import com.kjota.ritmo.update.Updater

private val railItems: List<Pair<ImageVector, String>> = listOf(
    Icons.Rounded.Home to "Inicio",
    Icons.Rounded.Search to "Buscar",
    Icons.Rounded.LibraryMusic to "Biblioteca",
    Icons.Rounded.Settings to "Ajustes",
    Icons.Rounded.Person to "Perfil",
)

/**
 * Interfaz para Android TV, parecida a YouTube en la tele: riel lateral con iconos,
 * filas de tarjetas 16:9 y reproductor a pantalla completa. Todo se maneja con el control remoto.
 * Solo música, video y letras (sin canales, suscripciones, directos ni comentarios).
 */
@Composable
fun TvRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val now by vm.now.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var playerMode by rememberSaveable { mutableIntStateOf(2) }
    var videoFull by rememberSaveable { mutableStateOf(false) }
    var lyricsFull by rememberSaveable { mutableStateOf(false) }
    var eqOpen by remember { mutableStateOf(false) }
    var screenOff by remember { mutableStateOf(false) }
    var profileChosen by rememberSaveable { mutableStateOf(com.kjota.ritmo.data.Profiles.list(ctx, "Televisión").size <= 1) }
    var pick by remember { mutableStateOf<Track?>(null) }
    var importOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.openVideo.collect { playerMode = 2; playerOpen = true } }
    LaunchedEffect(Unit) { vm.userPlay.collect { playerMode = 2; playerOpen = true } }
    LaunchedEffect(Unit) { Updater.checkAsync(ctx, manual = false) }

    val videoActive = ((playerOpen && playerMode == 2) || videoFull) && !screenOff
    val video = if (videoActive) rememberVideoState(vm) else null

    BackHandler(enabled = !profileChosen) { profileChosen = true }
    BackHandler(enabled = screenOff) { screenOff = false }
    BackHandler(enabled = videoFull || lyricsFull || eqOpen || playerOpen || collection != null || tab != 0) {
        when {
            videoFull -> videoFull = false
            lyricsFull -> lyricsFull = false
            eqOpen -> eqOpen = false
            playerOpen -> playerOpen = false
            collection != null -> collection = null
            else -> tab = 0
        }
    }

    val overlay = playerOpen || eqOpen || lyricsFull || videoFull || screenOff || !profileChosen
    AuroraBackground {
        Row(Modifier.fillMaxSize().then(if (overlay) Modifier.focusProperties { canFocus = false } else Modifier)) {
            TvRail(tab) { if (it == 4) profileChosen = false else { tab = it; collection = null } }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val open = collection
                Box(Modifier.fillMaxSize().padding(end = 24.dp)) {
                    when {
                        open != null -> CollectionScreen(vm, open, now.track?.id, onBack = { collection = null }, onPickPlaylist = { pick = it })
                        tab == 0 -> TvHome(vm, now.track?.id, onOpenCollection = { collection = it }, onOpenSettings = { tab = 3 }, onPick = { pick = it })
                        tab == 1 -> TvSearch(vm, now.track?.id, onPick = { pick = it })
                        tab == 2 -> LibraryScreen(vm, onOpen = { collection = it }, onImport = { importOpen = true })
                        else -> SettingsScreen(vm, onOpenEq = { eqOpen = true })
                    }
                }
                now.track?.let { t ->
                    TvNowBar(vm, t, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 16.dp)) {
                        playerMode = 2
                        playerOpen = true
                    }
                }
            }
        }

        AnimatedVisibility(visible = playerOpen) {
            TvPlayer(
                vm, playerMode, { playerMode = it }, video,
                onClose = { playerOpen = false },
                onScreenOff = { screenOff = true },
                onPickPlaylist = { pick = it },
                onFullLyrics = { lyricsFull = true },
                onVideoFull = { videoFull = true },
            )
        }
        AnimatedVisibility(visible = eqOpen) { EqualizerScreen(vm, onClose = { eqOpen = false }) }
        if (lyricsFull) LyricsFullScreen(vm, onClose = { lyricsFull = false })
        if (videoFull && video != null) VideoFullScreen(vm, video, onClose = { videoFull = false })
        if (screenOff) ScreenOffOverlay { screenOff = false }
        if (!profileChosen) TvProfilePicker { id ->
            if (id == com.kjota.ritmo.data.Profiles.currentId(ctx)) profileChosen = true
            else com.kjota.ritmo.data.Profiles.switchTo(ctx, id)
        }
    }

    pick?.let { PlaylistPicker(it, vm) { pick = null } }
    if (importOpen) ImportDialog(vm, initial = "") { importOpen = false }
}

/** Elegir perfil al abrir la app en el TV (solo si hay más de uno). */
@Composable
private fun TvProfilePicker(onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val list = remember { com.kjota.ritmo.data.Profiles.list(ctx, "Televisión") }
    val cur = com.kjota.ritmo.data.Profiles.currentId(ctx)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(6) {
            kotlinx.coroutines.delay(250)
            try { focus.requestFocus(); return@LaunchedEffect } catch (_: Throwable) { }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Pal.Ink).pointerInput(Unit) { detectTapGestures { } },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("¿Quién escucha?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            list.forEach { p ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .then(if (p.id == cur) Modifier.focusRequester(focus) else Modifier)
                        .tvClickable(RoundedCornerShape(18.dp)) { onPick(p.id) }.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(Pal.Green.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1).uppercase(), color = Color(0xFF00210B), fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(p.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .tvClickable(RoundedCornerShape(18.dp)) {
                        val np = com.kjota.ritmo.data.Profiles.add(ctx, "Perfil ${list.size + 1}")
                        onPick(np.id)
                    }.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "Nuevo perfil", tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Nuevo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Pantalla negra mientras suena la música; cualquier tecla del control la vuelve a encender. */
@Composable
private fun ScreenOffOverlay(onWake: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(6) {
            kotlinx.coroutines.delay(200)
            try { focus.requestFocus(); return@LaunchedEffect } catch (_: Throwable) { }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onWake() } }
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) { onWake(); true } else false
            },
    )
}

@Composable
private fun TvRail(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(104.dp).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        railItems.forEachIndexed { i, (icon, label) ->
            val sel = i == selected
            Column(
                Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .tvClickable(RoundedCornerShape(18.dp)) { onSelect(i) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, label, tint = if (sel) Pal.Green else Color.White, modifier = Modifier.size(30.dp))
                Text(label, color = if (sel) Color.White else Pal.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TvNowBar(vm: MainViewModel, t: Track, modifier: Modifier, onOpen: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    Row(
        modifier
            .width(440.dp)
            .glass(RoundedCornerShape(20.dp), strength = 1.8f)
            .tvClickable(RoundedCornerShape(20.dp), onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(t.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f), 10)
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Dim, fontSize = 13.sp)
        }
        Icon(
            if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
            tint = Color.White, modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun TvChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color(0xFF00210B) else Color.White,
        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(Pal.Green) else Modifier.background(Color.White.copy(alpha = 0.14f)))
            .tvClickable(RoundedCornerShape(50), onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun TvHome(
    vm: MainViewModel,
    currentId: String?,
    onOpenCollection: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPick: (Track) -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val loading by vm.shelvesLoading.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val upd by Updater.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, top = 28.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        upd.release?.let { rel ->
            item(key = "update") {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Pal.Green.copy(alpha = 0.22f))
                        .tvClickable(RoundedCornerShape(18.dp)) { onOpenSettings() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Pal.Green)
                    Text("Nueva versión ${rel.tag}: abrir Ajustes para actualizar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                item { TvChip("Favoritas") { onOpenCollection("liked") } }
                item { TvChip("Descargadas") { onOpenCollection("downloads") } }
                itemsIndexed(moodChips) { i, name -> TvChip(name, mood == i) { vm.setMood(i) } }
            }
        }
        if (lib.recent.isNotEmpty()) {
            item { TvShelf("Escuchado hace poco", lib.recent, vm, currentId, onPick) { i -> vm.play(lib.recent, i) } }
        }
        shelves.forEachIndexed { idx, shelf ->
            item(key = "${shelf.title}#$idx") {
                TvShelf(shelf.title, shelf.tracks, vm, currentId, onPick) { i -> vm.play(shelf.tracks, i) }
            }
        }
        if (loading && shelves.isEmpty()) item { Loader() }
        if (!loading && shelves.isEmpty()) {
            item {
                TvChip("Sin conexión: pulsa OK para reintentar") { vm.loadShelves(force = true) }
            }
        }
    }
}

@Composable
private fun TvShelf(
    title: String,
    tracks: List<Track>,
    vm: MainViewModel,
    currentId: String?,
    onPick: (Track) -> Unit,
    onPlay: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                VideoCard(
                    t, vm, onClick = { onPlay(i) }, onPickPlaylist = onPick,
                    isCurrent = t.id == currentId, tv = true, modifier = Modifier.width(300.dp),
                )
            }
        }
    }
}

@Composable
private fun TvSearch(vm: MainViewModel, currentId: String?, onPick: (Track) -> Unit) {
    val st by vm.search.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(st.query) }
    var typing by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(text, typing) {
        if (!typing || text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(180)
        suggestions = com.kjota.ritmo.engine.Suggest.forQuery(text)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 8.dp, top = 28.dp, bottom = 120.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GlassSearchField(
                value = text,
                onValueChange = { text = it; typing = true },
                onSearch = { typing = false; vm.search(text) },
                onClear = { text = ""; typing = false; vm.clearSearch() },
            )
        }
        when {
            typing && suggestions.isNotEmpty() -> {
                items(suggestions, span = { GridItemSpan(maxLineSpan) }) { sug ->
                    SuggestionRow(sug, onPick = { text = sug; typing = false; vm.search(sug) }, onFill = { text = sug })
                }
            }
            st.loading -> item(span = { GridItemSpan(maxLineSpan) }) { Loader() }
            st.done && st.results.isNotEmpty() -> {
                itemsIndexed(st.results, key = { _, t -> t.id }) { i, t ->
                    VideoCard(
                        t, vm, onClick = { vm.playFromSearch(t) }, onPickPlaylist = onPick,
                        isCurrent = t.id == currentId, tv = true,
                    )
                }
            }
            st.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(st.error!!, color = Pal.Dim, modifier = Modifier.padding(vertical = 24.dp))
            }
            else -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Explorar", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
                items(genres) { g ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .glass(RoundedCornerShape(18.dp), tint = g.color, strength = 2.2f)
                            .tvClickable(RoundedCornerShape(18.dp)) { text = g.name; typing = false; vm.search(g.query) }
                            .padding(18.dp),
                    ) {
                        Text(g.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvButton(icon: ImageVector, label: String, active: Boolean = false, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = if (big) 72.dp else 52.dp
    Box(
        modifier
            .size(s)
            .clip(CircleShape)
            .background(if (big) Color.White else Color.White.copy(alpha = 0.14f))
            .tvClickable(CircleShape, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = when { big -> Color.Black; active -> Pal.Green; else -> Color.White },
            modifier = Modifier.size(if (big) 42.dp else 28.dp),
        )
    }
}

@Composable
private fun TvPlayer(
    vm: MainViewModel,
    mode: Int,
    onMode: (Int) -> Unit,
    video: VideoState?,
    onClose: () -> Unit,
    onScreenOff: () -> Unit,
    onPickPlaylist: (Track) -> Unit,
    onFullLyrics: () -> Unit,
    onVideoFull: () -> Unit,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val mixOn by vm.mixOn.collectAsStateWithLifecycle()
    val eqOn by vm.eqOn.collectAsStateWithLifecycle()
    val t = now.track
    val vOn = video != null && mode == 2
    val focus = remember { FocusRequester() }
    if (mode != 0) KeepScreenOn()
    LaunchedEffect(Unit) {
        repeat(6) {
            kotlinx.coroutines.delay(250)
            try { focus.requestFocus(); return@LaunchedEffect } catch (_: Throwable) { }
        }
    }
    LaunchedEffect(now.track?.id, mode) {
        kotlinx.coroutines.delay(500)
        try { focus.requestFocus() } catch (_: Throwable) { }
    }

    Box(Modifier.fillMaxSize().background(Pal.Ink).pointerInput(Unit) { detectTapGestures { } }.focusRequester(focus).focusable()) {
        if (t == null) return@Box
        AsyncImage(
            model = t.thumbHq, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(80.dp).alpha(0.55f),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x66000000), Color(0xE607090C)))))

        val liked = lib.liked.any { it.id == t.id }
        val pos = if (vOn) video!!.pos else now.positionMs
        val dur = (if (vOn && video!!.dur > 0) video.dur else now.durationMs).coerceAtLeast(1L)
        val playing = if (vOn) video!!.playing else now.isPlaying
        val buffering = if (vOn) (video!!.loading || video.buffering) else now.buffering

        val q by vm.queue.collectAsStateWithLifecycle()
        val upNext = remember(q) { q.items.withIndex().drop(q.index + 1).take(25).toList() }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 16.dp)) {
            Text("A continuación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 56.dp, bottom = 6.dp))
            if (upNext.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp).padding(start = 56.dp), contentAlignment = Alignment.CenterStart) {
                    CircularProgressIndicator(color = Pal.Green, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(upNext.size) { i ->
                        val (idx, tr) = upNext[i]
                        Column(Modifier.width(172.dp).tvClickable(RoundedCornerShape(10.dp)) { vm.playAt(idx) }.padding(2.dp)) {
                            Cover(tr.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f), 8)
                            Text(tr.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 28.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // Izquierda: carátula, letra o video
            Box(Modifier.weight(1.45f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when (mode) {
                    1 -> LyricsPanel(vm, t, now.positionMs, Modifier.fillMaxSize(), big = true)
                    2 -> if (video != null) {
                        Box(Modifier.tvClickable(RoundedCornerShape(22.dp)) { onVideoFull() }) {
                            VideoInline(vm, video, onFullscreen = onVideoFull, overlayControls = false)
                        }
                    } else {
                        CircularProgressIndicator(color = Pal.Green, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                    }
                    else -> Cover(t.thumbHq, Modifier.fillMaxHeight(0.85f).aspectRatio(16f / 9f), 22)
                }
            }

            // Derecha: info y controles
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.Top,
            ) {
                val vh by vm.videoHeight.collectAsStateWithLifecycle()
                Text(t.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(t.artist, color = Pal.Dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(formatTime((pos / 1000).toInt()), color = Pal.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(formatTime((dur / 1000).toInt()), color = Pal.Dim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(Icons.Rounded.SkipPrevious, "Anterior") { vm.previous() }
                    TvButton(Icons.Rounded.Replay10, "Retroceder 10 s") { if (vOn) video!!.seekBy(-10_000) else vm.seekTo((now.positionMs - 10_000).coerceAtLeast(0L)) }
                    TvButton(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pausa" else "Reproducir", big = true,
                    ) { if (vOn) video!!.togglePlay() else vm.togglePlay() }
                    TvButton(Icons.Rounded.Forward10, "Adelantar 10 s") { if (vOn) video!!.seekBy(10_000) else vm.seekTo(now.positionMs + 10_000) }
                    TvButton(Icons.Rounded.SkipNext, "Siguiente") { vm.next() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorita", active = liked) { vm.toggleLike(t) }
                    TvButton(Icons.Rounded.PlaylistAdd, "Guardar en playlist") { onPickPlaylist(t) }
                    TvButton(Icons.Rounded.Shuffle, "Aleatorio", active = now.shuffle) { vm.toggleShuffle() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvChip("${vh}p") {
                        val l = listOf(360, 480, 720, 1080)
                        vm.setVideoHeight(l[(l.indexOf(vh) + 1).mod(l.size)])
                    }
                    TvButton(
                        if (now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir", active = now.repeatMode != Player.REPEAT_MODE_OFF,
                    ) { vm.cycleRepeat() }
                    TvButton(Icons.Rounded.GraphicEq, "Mix DJ", active = mixOn) { vm.setMix(!mixOn) }
                    TvButton(Icons.Rounded.PowerSettingsNew, "Pantalla apagada") { onScreenOff() }
                    if (mode != 0) {
                        TvButton(Icons.Rounded.Fullscreen, "Pantalla completa") { if (mode == 1) onFullLyrics() else onVideoFull() }
                    }
                }
            }
        }
    }
}
