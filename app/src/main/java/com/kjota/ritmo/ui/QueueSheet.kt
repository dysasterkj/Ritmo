package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kjota.ritmo.MainViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Block
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch

/** "A continuación" como en YouTube Music: panel que sube desde abajo siguiendo el dedo y se asienta con resorte. */
@Composable
fun QueuePanel(vm: MainViewModel, frac: Animatable<Float, AnimationVector1D>, heightPx: Float) {
    val q by vm.queue.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val known = remember(lib) {
        (lib.liked + lib.recent + lib.downloads).map { it.artist.lowercase().trim() }.toSet()
    }
    val cur = q.items.getOrNull(q.index)
    val rows = q.items.withIndex().filter { (i, t) ->
        i == q.index || when (filter) {
            1 -> t.artist.lowercase().trim() in known
            2 -> t.artist.lowercase().trim() !in known
            else -> true
        }
    }
    val settle: (Float) -> Unit = { v ->
        scope.launch {
            val target = if (v > 800f) 0f else if (v < -800f) 1f else if (frac.value > 0.5f) 1f else 0f
            frac.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 420f))
        }
    }
    BackHandler(enabled = frac.value > 0.5f) { scope.launch { frac.animateTo(0f, spring(stiffness = 500f)) } }
    LaunchedEffect(Unit) { if (q.index > 0) listState.scrollToItem(q.index) }

    val conn = remember(heightPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && heightPx > 0f) {
                    scope.launch { frac.snapTo((frac.value - available.y / heightPx).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (frac.value in 0.001f..0.999f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - frac.value) * heightPx }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFA140A12))
            .pointerInput(Unit) { detectTapGestures { } }
            .nestedScroll(conn)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Cabecera arrastrable (asa + título)
        Column(
            Modifier.pointerInput(heightPx) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { frac.snapTo((frac.value - dy / heightPx).coerceIn(0f, 1f)) }
                    },
                    onDragEnd = { settle(tracker.calculateVelocity().y) },
                    onDragCancel = { settle(0f) },
                )
            },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A continuación", color = Pal.Dim, fontSize = 12.sp)
                    Text(
                        "Mix de ${cur?.title ?: ""}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.saveQueueAsPlaylist() }) {
                    Icon(Icons.Rounded.PlaylistAdd, "Guardar playlist", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todo", "Conocidas", "Por descubrir").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (filter == i) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable { filter = i }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(label, color = if (filter == i) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(rows, key = { _, r -> r.index.toString() + r.value.id }) { _, r ->
                val i = r.index
                val t = r.value
                val isCur = i == q.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCur) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { vm.playAt(i) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(88.dp).height(50.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isCur) Pal.Green else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            t.artist + " • " + formatTime(t.durationSec), fontSize = 13.sp, color = Pal.Dim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isCur) {
                        IconButton(onClick = { vm.removeFromQueue(i) }) {
                            Icon(Icons.Rounded.Close, "Quitar", tint = Pal.Dim, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Flechita junto al título: más canciones del artista que suena. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSheet(vm: MainViewModel, artist: String, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var list by remember { androidx.compose.runtime.mutableStateOf<List<com.kjota.ritmo.data.Track>?>(null) }
    LaunchedEffect(artist) {
        val key = artist.lowercase().trim()
        list = try {
            com.kjota.ritmo.engine.YouTube.search(artist, 30)
                .filter { it.artist.lowercase().contains(key.take(12)) || key.contains(it.artist.lowercase().trim().take(12)) }
                .ifEmpty { com.kjota.ritmo.engine.YouTube.search(artist, 30) }
        } catch (_: Throwable) { emptyList() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = Color(0xF2140A12), contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Más de $artist", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { vm.blockCurrentArtist(); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Block, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("No recomendar este artista", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            val l = list
            if (l == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Pal.Green)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(l, key = { i, t -> "$i${t.id}" }) { i, t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.play(l, i, radio = true); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(88.dp).height(50.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(t.artist + " • " + formatTime(t.durationSec), fontSize = 13.sp, color = Pal.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
