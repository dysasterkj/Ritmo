package com.kjota.ritmo.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.CallSplit
import com.kjota.ritmo.player.CarSplitService
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel

/**
 * Modo auto: pantalla horizontal con botones grandes, pensada para ir manejando.
 * Se adapta al ancho: completa en pantalla entera, o compacta cuando la ventana es la mitad
 * (pantalla dividida con Waze u otra app de mapas).
 */
@Composable
fun CarScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = now.track
    var drag by remember { mutableStateOf<Float?>(null) }

    KeepScreenOn()
    SystemUi(hideBars = false, landscape = true)

    fun openWaze() {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.waze")
        if (i == null) {
            Toast.makeText(ctx, "Waze no está instalado", Toast.LENGTH_SHORT).show()
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            try { ctx.startActivity(i) } catch (_: Throwable) {
                Toast.makeText(ctx, "No se pudo abrir Waze", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Al entrar al modo auto: divide la pantalla (si el permiso está activo) y abre Waze al lado.
    var weSplit by rememberSaveable { mutableStateOf(false) }
    var autoDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoDone) return@LaunchedEffect
        autoDone = true
        val act = ctx.findActivity()
        if (act != null && !act.isInMultiWindowMode && CarSplitService.enabled) {
            weSplit = CarSplitService.toggleSplit()
            delay(1200)
        }
        if (act != null && (act.isInMultiWindowMode || !CarSplitService.enabled)) openWaze()
    }

    fun exitCar() {
        val act = ctx.findActivity()
        if (weSplit && act != null && act.isInMultiWindowMode) CarSplitService.toggleSplit()
        vm.setCarMode(false)
    }
    androidx.activity.compose.BackHandler { exitCar() }

    AuroraBackground {
        if (t != null) {
            AsyncImage(
                model = t.thumbHq, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                    .androidxBlur()
                    .graphicsLayer { alpha = 0.45f },
            )
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        }

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val mh = this.maxHeight
            val wide = maxWidth >= 560.dp
            val dur = now.durationMs.coerceAtLeast(1L)
            val frac = drag ?: (now.positionMs.toFloat() / dur).coerceIn(0f, 1f)
            val shown = if (drag != null) (drag!! * dur).toLong() else now.positionMs
            val liked = t != null && lib.liked.any { it.id == t.id }

            @Composable
            fun Info(modifier: Modifier = Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        t?.title ?: "Modo auto", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = if (wide) 24.sp else 19.sp, lineHeight = if (wide) 28.sp else 23.sp,
                        maxLines = if (mh < 420.dp) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        t?.artist ?: "Elige qué escuchar", color = Pal.Dim,
                        fontSize = if (wide) 18.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            @Composable
            fun Progress() {
                if (t == null) return
                Column {
                    Slider(
                        value = frac,
                        onValueChange = { drag = it },
                        onValueChangeFinished = { drag?.let { vm.seekTo((it * dur).toLong()) }; drag = null },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime((shown / 1000).toInt()), fontSize = 13.sp, color = Pal.Dim, modifier = Modifier.weight(1f))
                        Text(formatTime((dur / 1000).toInt()), fontSize = 13.sp, color = Pal.Dim)
                    }
                }
            }

            @Composable
            fun Controls() {
                val big = (mh * 0.24f).coerceIn(64.dp, 100.dp)
                val side = big * 0.72f
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CarBtn(Icons.Rounded.SkipPrevious, "Anterior", side) { vm.previous() }
                    Box(
                        Modifier.size(big).clip(CircleShape).background(Color.White).clickable { vm.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (now.buffering && !now.isPlaying) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 3.5.dp, modifier = Modifier.size(40.dp))
                        } else {
                            Icon(
                                if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (now.isPlaying) "Pausa" else "Reproducir",
                                tint = Color.Black, modifier = Modifier.size(big * 0.58f),
                            )
                        }
                    }
                    CarBtn(Icons.Rounded.SkipNext, "Siguiente", side) { vm.next() }
                }
            }

            @Composable
            fun Quick() {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (t != null) {
                        item { CarChip(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorita", liked) { vm.toggleLike(t) } }
                        item { CarChip(Icons.Rounded.Shuffle, "Aleatorio", now.shuffle) { vm.toggleShuffle() } }
                        item {
                            CarChip(
                                if (now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                "Repetir", now.repeatMode != Player.REPEAT_MODE_OFF,
                            ) { vm.cycleRepeat() }
                        }
                    }
                    item {
                        CarChip(null, "Mis favoritas", false) {
                            if (lib.liked.isNotEmpty()) vm.play(lib.liked.shuffled(), 0, radio = true)
                            else Toast.makeText(ctx, "Aún no tienes favoritas", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        CarChip(null, "Escuchado hace poco", false) {
                            if (lib.recent.isNotEmpty()) vm.play(lib.recent, 0, radio = true)
                            else Toast.makeText(ctx, "Aún no hay historial", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        CarChip(null, "Mix de hoy", false) {
                            shelves.firstOrNull()?.tracks?.takeIf { it.isNotEmpty() }?.let { vm.play(it.shuffled(), 0, radio = true) }
                                ?: Toast.makeText(ctx, "Cargando… prueba en un momento", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            if (wide) {
                val cover = (mh - 32.dp).coerceAtLeast(120.dp)
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (t != null) {
                            Cover(t.thumbHq, Modifier.size(cover).glass(RoundedCornerShape(22.dp), strength = 1.6f), 22)
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Spacer(Modifier.height(40.dp))
                        Info()
                        Progress()
                        Controls()
                        Quick()
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(Modifier.height(36.dp))
                    if (t != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val side = (mh * 0.34f).coerceIn(80.dp, 220.dp)
                            Cover(t.thumbHq, Modifier.size(side).glass(RoundedCornerShape(20.dp), strength = 1.6f), 20)
                        }
                    }
                    Info()
                    Progress()
                    Controls()
                    Quick()
                }
            }

            // Salir y Waze siempre visibles arriba a la derecha
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CarRound(Icons.Rounded.Navigation, "Abrir Waze") { openWaze() }
                if (!CarSplitService.enabled) {
                    CarRound(Icons.Rounded.CallSplit, "Activar pantalla dividida automática") {
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                CarRound(Icons.Rounded.Close, "Salir del modo auto") { exitCar() }
            }
        }
    }
}

private fun Modifier.androidxBlur(): Modifier = this.then(Modifier.blur(70.dp))

@Composable
private fun CarRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun CarBtn(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun CarChip(icon: ImageVector?, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) Pal.Green.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (active) Pal.Green else Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}
