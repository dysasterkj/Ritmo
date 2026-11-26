package com.kjota.ritmo.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
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
    var playerMode by rememberSaveable { mutableIntStateOf(0) }
    var videoFull by rememberSaveable { mutableStateOf(false) }
    var lyricsFull by rememberSaveable { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Track?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // Enlace compartido desde otra app (Spotify, YouTube…): abre el diálogo de importar.
    LaunchedEffect(pending) {
        if (pending != null) {
            importOpen = true
            tab = 2
            collection = null
            playerOpen = false
        }
    }

    LaunchedEffect(Unit) {
        vm.openVideo.collect { playerMode = 2; playerOpen = true }
    }
    LaunchedEffect(playerOpen) { if (!playerOpen && !videoFull) playerMode = 0 }

    LaunchedEffect(Unit) {
        com.kjota.ritmo.update.Updater.checkAsync(ctx, manual = false)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    var undoEv by remember { mutableStateOf<com.kjota.ritmo.UndoEvent?>(null) }
    LaunchedEffect(Unit) { vm.undo.collect { undoEv = it } }
    LaunchedEffect(undoEv) { if (undoEv != null) { delay(10_000); undoEv = null } }

    var eqOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 0 = reproductor abierto, 1 = minimizado (queda solo el mini reproductor).
    var closed by remember { mutableFloatStateOf(if (playerOpen) 0f else 1f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    fun animateTo(target: Float, velocity: Float = 0f) {
        settle?.cancel()
        settle = scope.launch {
            animate(
                initialValue = closed, targetValue = target, initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            ) { v, _ -> closed = v.coerceIn(0f, 1f) }
        }
    }
    LaunchedEffect(playerOpen) { animateTo(if (playerOpen) 0f else 1f) }

    val videoActive = (playerOpen && playerMode == 2) || videoFull
    val video = if (videoActive) rememberVideoState(vm) else null

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

    AuroraBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
            ) {
            val open = collection
            when {
                open != null -> CollectionScreen(
                    vm, open, now.track?.id,
                    onBack = { collection = null },
                    onPickPlaylist = { pick = it },
                )
                tab == 0 -> HomeScreen(vm, onOpenCollection = { collection = it }, onOpenSettings = { tab = 3; collection = null }, onPickPlaylist = { pick = it })
                tab == 1 -> SearchScreen(vm, now.track?.id, onPickPlaylist = { pick = it })
                tab == 2 -> LibraryScreen(vm, onOpen = { collection = it }, onImport = { importOpen = true })
                else -> SettingsScreen(vm, onOpenEq = { eqOpen = true })
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (now.track != null) MiniPlayer(vm) {
                    // Si el estado quedó "abierto" pero el panel está abajo, hay que volver a subirlo igual.
                    if (playerOpen) animateTo(0f) else playerOpen = true
                }
                NavBar(tab) { tab = it; collection = null }
            }

            if (closed < 0.999f) {
                // Soltar: si fue rápido o bajó más de un tercio, se minimiza; si no, vuelve a abrirse.
                fun release(velocityY: Float) {
                    val vf = velocityY / hPx // fracción de pantalla por segundo
                    val close = vf > 1.4f || (closed > 0.33f && vf > -1.2f)
                    if (close) playerOpen = false else animateTo(0f, vf)
                }
                val connection = remember(hPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y < 0f && closed > 0f) {
                                settle?.cancel()
                                val newV = (closed + available.y / hPx).coerceIn(0f, 1f)
                                val used = (newV - closed) * hPx
                                closed = newV
                                return Offset(0f, used)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (available.y > 0f) {
                                settle?.cancel()
                                closed = (closed + available.y / hPx).coerceIn(0f, 1f)
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            if (closed > 0f) { release(available.y); return available }
                            return Velocity.Zero
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = closed * hPx
                            val sc = 1f - 0.05f * closed
                            scaleX = sc; scaleY = sc
                            alpha = (1f - closed * 0.35f).coerceIn(0f, 1f)
                        }
                        .nestedScroll(connection)
                        .pointerInput(hPx) {
                            val tracker = VelocityTracker()
                            var up = 0f
                            detectVerticalDragGestures(
                                onDragStart = { tracker.resetTracking(); settle?.cancel(); up = 0f },
                                onVerticalDrag = { change, dy ->
                                    up += dy
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    closed = (closed + dy / hPx).coerceIn(0f, 1f)
                                    // Al llegar abajo el panel se quita de la composición y se perdería el "soltar".
                                    if (closed >= 0.999f) playerOpen = false
                                },
                                onDragEnd = {
                                    if (up < -90f && closed < 0.03f) vm.requestQueue()
                                    release(tracker.calculateVelocity().y)
                                },
                                onDragCancel = { release(0f) },
                            )
                        },
                ) {
                    PlayerScreen(
                        vm,
                        mode = playerMode,
                        onMode = { playerMode = it },
                        video = video,
                        onClose = { playerOpen = false },
                        onPickPlaylist = { pick = it },
                        onOpenEq = { eqOpen = true },
                        onFullLyrics = { lyricsFull = true },
                        onVideoFull = { videoFull = true },
                    )
                }
            }

            AnimatedVisibility(
                visible = eqOpen,
                enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(260)) { it },
            ) {
                EqualizerScreen(vm, onClose = { eqOpen = false })
            }
            }

            if (lyricsFull) LyricsFullScreen(vm, onClose = { lyricsFull = false })
            if (videoFull && video != null) VideoFullScreen(vm, video, onClose = { videoFull = false })

            AnimatedVisibility(
                visible = undoEv != null,
                enter = slideInVertically(tween(260)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val ev = undoEv
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF232A33))
                        .border(1.dp, Color(0xFF3A4452), RoundedCornerShape(16.dp))
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ev?.message ?: "", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { ev?.undo?.invoke(); undoEv = null }) {
                        Text("DESHACER", color = Pal.Green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }

        }
    }

    pick?.let { PlaylistPicker(it, vm) { pick = null } }
    if (importOpen) {
        ImportDialog(vm, initial = pending.orEmpty()) {
            importOpen = false
            vm.setPendingImport(null)
        }
    }
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
            .glassBar(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy -> if (dy < -10f) onOpen() }
            }
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

/**
 * Barra inferior de "vidrio líquido": una lente de luz (la gota) marca la pestaña activa.
 * Al tocar, la gota se infla y brilla; al soltar viaja con efecto resorte, se estira en el camino
 * y suelta una onda de agua. La acción se dispara al soltar.
 */
@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(-1) }
    var ripples by remember { mutableStateOf(0) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(ripples) {
        if (ripples > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .glassBar(RoundedCornerShape(34.dp)),
    ) {
        val pad = 5.dp
        val tabW = (maxWidth - pad * 2) / 4
        val target = if (pressed >= 0) pressed else selected
        val pos by animateFloatAsState(
            target.toFloat(),
            spring(dampingRatio = 0.55f, stiffness = 320f),
            label = "lens-pos",
        )
        val press by animateFloatAsState(
            if (pressed >= 0) 1f else 0f,
            spring(dampingRatio = 0.5f, stiffness = 520f),
            label = "lens-press",
        )
        val stretch = 1f + min(0.32f, abs(pos - target) * 0.6f)

        // Onda de agua que sale de la gota
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = ripple.value
                    if (p < 1f) {
                        val cx = (pad + tabW * (pos + 0.5f)).toPx()
                        val cy = size.height / 2f
                        val r0 = 24.dp.toPx()
                        val r1 = 120.dp.toPx()
                        for (k in 0..1) {
                            val pk = (p - k * 0.18f).coerceIn(0f, 1f)
                            if (pk > 0f) drawCircle(
                                Color.White.copy(alpha = (1f - pk) * 0.40f),
                                radius = r0 + (r1 - r0) * pk,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.dp.toPx() * (1f - pk) + 0.5f),
                            )
                        }
                    }
                },
        )

        // La gota (lente)
        Box(
            Modifier
                .offset { IntOffset((pad + tabW * pos).roundToPx(), 5.dp.roundToPx()) }
                .size(tabW, 58.dp)
                .graphicsLayer {
                    scaleX = (1f + 0.13f * press) * stretch
                    scaleY = 1f + 0.20f * press
                }
                .clip(RoundedCornerShape(29.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.34f + 0.26f * press),
                            Color.White.copy(alpha = 0.10f + 0.08f * press),
                        ),
                        center = Offset(40f, 8f),
                        radius = 260f,
                    )
                )
                .background(Pal.Green.copy(alpha = 0.10f + 0.16f * press))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.12f))),
                    RoundedCornerShape(29.dp),
                ),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pad)
                .pointerInput(Unit) {
                    fun idx(x: Float) = (x / (size.width / 4f)).toInt().coerceIn(0, 3)
                    detectTapGestures(
                        onPress = { o ->
                            pressed = idx(o.x)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tryAwaitRelease()
                            pressed = -1
                        },
                        onTap = { o ->
                            onSelect(idx(o.x))
                            ripples++
                        },
                    )
                },
        ) {
            NavItem("Inicio", Icons.Rounded.Home, selected == 0, Modifier.weight(1f)) { onSelect(0); ripples++ }
            NavItem("Buscar", Icons.Rounded.Search, selected == 1, Modifier.weight(1f)) { onSelect(1); ripples++ }
            NavItem("Biblioteca", Icons.Rounded.LibraryMusic, selected == 2, Modifier.weight(1f)) { onSelect(2); ripples++ }
            NavItem("Ajustes", Icons.Rounded.Settings, selected == 3, Modifier.weight(1f)) { onSelect(3); ripples++ }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                    onSelect(); true
                } else false
            }
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(29.dp)) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = if (active) Pal.Green else Pal.Dim, modifier = Modifier.size(25.dp))
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Pal.Green else Pal.Dim,
        )
    }
}
