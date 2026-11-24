@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.kjota.ritmo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Stable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.engine.YouTube
import kotlinx.coroutines.delay

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

private const val SEEK_MS = 10_000L

/** Estado del video compartido entre la vista pequeña (dentro del reproductor) y la pantalla completa. */
@Stable
class VideoState(val player: ExoPlayer, val autoplay: Boolean) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var retryKey by mutableIntStateOf(0)
    var loadedId: String? = null

    fun seekBy(ms: Long) {
        val max = if (dur > 0) dur else Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, max))
    }

    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0L))

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        player.playWhenReady = !player.playWhenReady
    }
}

/**
 * Crea el reproductor de video mientras el modo "Video" está activo y lo libera al salir.
 * El audio queda en pausa mientras se ve y continúa desde el mismo punto al volver.
 * Sigue la cola de reproducción: al terminar pasa al siguiente.
 */
@Composable
fun rememberVideoState(vm: MainViewModel): VideoState {
    val ctx = LocalContext.current
    val height by vm.videoHeight.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val track = now.track
    val st = remember {
        VideoState(ExoPlayer.Builder(ctx).build(), vm.consumeVideoAutoplay() || vm.now.value.isPlaying)
    }

    DisposableEffect(Unit) {
        vm.pauseAudio()
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) vm.next()
            }
        }
        st.player.addListener(l)
        onDispose {
            val p = st.player.currentPosition
            val was = st.player.playWhenReady
            st.player.removeListener(l)
            st.player.release()
            vm.endVideo(st.loadedId, p, was)
        }
    }

    // Carga el video de la canción actual (y otra vez si cambia la calidad o se reintenta)
    LaunchedEffect(track?.id, height, st.retryKey) {
        val t = track ?: return@LaunchedEffect
        val player = st.player
        val startAt = when (st.loadedId) {
            null -> vm.now.value.positionMs
            t.id -> player.currentPosition
            else -> 0L
        }
        val wasPlaying = if (st.loadedId == null) st.autoplay else player.playWhenReady
        st.loadedId = t.id
        st.loading = true
        st.error = null
        try {
            val (v, a) = YouTube.videoUrls(t.id, height)
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
            val factory = ProgressiveMediaSource.Factory(http)
            val vs = factory.createMediaSource(MediaItem.fromUri(v))
            val src = if (a != null) MergingMediaSource(vs, factory.createMediaSource(MediaItem.fromUri(a))) else vs
            player.setMediaSource(src, startAt.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = wasPlaying
        } catch (e: Throwable) {
            st.error = "No se pudo cargar el video. Revisa tu conexión o actualiza el motor en Ajustes."
        }
        st.loading = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            st.playing = st.player.isPlaying
            st.buffering = st.player.playbackState == Player.STATE_BUFFERING
            st.pos = st.player.currentPosition.coerceAtLeast(0L)
            val d = st.player.duration
            st.dur = if (d == C.TIME_UNSET || d < 0) 0L else d
            delay(250)
        }
    }
    return st
}

@Composable
private fun VideoSurface(v: VideoState, modifier: Modifier = Modifier) {
    val exo = v.player
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = exo
            }
        },
    )
}

@Composable
private fun QualityChip(height: Int, vm: MainViewModel, onTouch: () -> Unit) {
    var qMenu by remember { mutableStateOf(false) }
    Box {
        Text(
            "${height}p", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .tvClickable(RoundedCornerShape(50)) { qMenu = true; onTouch() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        DropdownMenu(
            expanded = qMenu, onDismissRequest = { qMenu = false },
            modifier = Modifier.background(Color(0xF2151A22)),
        ) {
            listOf(360, 480, 720, 1080).forEach { h ->
                DropdownMenuItem(
                    text = { Text("${h}p", color = if (h == height) Pal.Green else Color.White) },
                    onClick = { qMenu = false; vm.setVideoHeight(h) },
                )
            }
        }
    }
}

@Composable
private fun LoadingAndError(v: VideoState, boxScope: androidx.compose.foundation.layout.BoxScope) {
    with(boxScope) {
        if (v.loading || (v.buffering && !v.playing)) {
            CircularProgressIndicator(
                color = Pal.Green, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
        v.error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(msg, color = Color.White, textAlign = TextAlign.Center, fontSize = 13.sp)
                Text(
                    "Reintentar", color = Color(0xFF00210B), fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Pal.Green)
                        .tvClickable(RoundedCornerShape(50)) { v.retryKey++ }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Video dentro del reproductor (en el lugar de la carátula, como en YouTube Music). */
@Composable
fun VideoInline(vm: MainViewModel, v: VideoState, onFullscreen: () -> Unit, modifier: Modifier = Modifier) {
    val height by vm.videoHeight.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }

    LaunchedEffect(controls, touch, v.playing) {
        if (controls && v.playing) {
            delay(3000)
            controls = false
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black),
    ) {
        VideoSurface(v, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls; touch++ },
                        onDoubleTap = { o ->
                            v.seekBy(if (o.x < size.width / 2f) -SEEK_MS else SEEK_MS)
                            controls = true
                            touch++
                        },
                    )
                },
        )
        LoadingAndError(v, this)
        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { QualityChip(height, vm) { touch++ } }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .tvClickable(CircleShape) { v.togglePlay(); touch++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (v.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (v.playing) "Pausa" else "Reproducir", tint = Color.Black, modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.BottomEnd)) {
                    Icon(Icons.Rounded.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/**
 * Video a pantalla completa (horizontal en el teléfono). Mantiene la pantalla encendida.
 * Con el control remoto (TV): izquierda/derecha adelantan o retroceden 10 s, OK pausa y reanuda.
 */
@Composable
fun VideoFullScreen(vm: MainViewModel, v: VideoState, onClose: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val height by vm.videoHeight.collectAsStateWithLifecycle()
    val track = now.track
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var landscape by remember { mutableStateOf(true) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val focus = remember { FocusRequester() }

    KeepScreenOn()
    SystemUi(hideBars = true, landscape = landscape)
    BackHandler { onClose() }

    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Throwable) { } }
    LaunchedEffect(controls, touch, v.playing) {
        if (controls && v.playing) {
            delay(3500)
            controls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    controls = true
                    touch++
                    when (e.key) {
                        Key.DirectionRight -> { v.seekBy(SEEK_MS); true }
                        Key.DirectionLeft -> { v.seekBy(-SEEK_MS); true }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> { v.togglePlay(); true }
                        else -> false
                    }
                }
            },
    ) {
        VideoSurface(v, Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls; touch++ },
                        onDoubleTap = { o ->
                            v.seekBy(if (o.x < size.width / 2f) -SEEK_MS else SEEK_MS)
                            controls = true
                            touch++
                        },
                    )
                },
        )
        LoadingAndError(v, this)

        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).background(
                        Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Salir de pantalla completa", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(
                                track?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(track?.artist.orEmpty(), color = Pal.Dim, fontSize = 13.sp, maxLines = 1)
                        }
                        QualityChip(height, vm) { touch++ }
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.prevTrack(); touch++ }) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = { v.seekBy(-SEEK_MS); touch++ }) {
                        Icon(Icons.Rounded.Replay10, "Retroceder 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f))
                            .tvClickable(CircleShape) { v.togglePlay(); touch++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (v.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (v.playing) "Pausa" else "Reproducir",
                            tint = Color.Black, modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { v.seekBy(SEEK_MS); touch++ }) {
                        Icon(Icons.Rounded.Forward10, "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { vm.next(); touch++ }) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }

                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    val frac = dragFrac ?: if (v.dur > 0) (v.pos.toFloat() / v.dur).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = frac,
                        onValueChange = { dragFrac = it; touch++ },
                        onValueChangeFinished = {
                            dragFrac?.let { v.seekTo((it * v.dur).toLong()) }
                            dragFrac = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Pal.Green,
                            activeTrackColor = Pal.Green,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = dragFrac?.let { (it * v.dur).toLong() } ?: v.pos
                        Text(
                            formatTime((shown / 1000).toInt()) + " / " + formatTime((v.dur / 1000).toInt()),
                            color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { landscape = !landscape; touch++ }) {
                            Icon(
                                if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Girar pantalla", tint = Color.White, modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
