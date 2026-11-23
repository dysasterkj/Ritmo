package com.kjota.ritmo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Track

private data class Genre(val name: String, val query: String, val color: Color)

private val genres = listOf(
    Genre("Pop", "pop hits", Pal.Pink),
    Genre("Reguetón", "reguetón", Pal.Orange),
    Genre("Rock", "rock", Color(0xFFE5484D)),
    Genre("Hip hop", "hip hop rap", Pal.Violet),
    Genre("Lo-fi", "lo-fi beats", Pal.Mint),
    Genre("Baladas", "baladas románticas", Color(0xFFE879F9)),
    Genre("Cumbia", "cumbia", Pal.Green),
    Genre("Electrónica", "electronic dance music", Pal.Blue),
    Genre("Salsa", "salsa clásicos", Color(0xFFF5B83D)),
    Genre("Jazz", "jazz relax", Color(0xFF8D6E63)),
    Genre("Clásica", "música clásica", Color(0xFF64B5F6)),
    Genre("Indie", "indie alternativo", Color(0xFF81C784)),
)

@Composable
fun SearchScreen(
    vm: MainViewModel,
    currentId: String?,
    onPickPlaylist: (Track) -> Unit,
) {
    val st by vm.search.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(st.query) }
    val focus = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Buscar") }
        item {
            GlassSearchField(
                value = text,
                onValueChange = { text = it },
                onSearch = { vm.search(text); focus.clearFocus() },
                onClear = { text = ""; vm.clearSearch() },
            )
        }

        when {
            st.loading -> item { Loader() }

            st.done && st.results.isNotEmpty() -> {
                itemsIndexed(st.results, key = { _, t -> t.id }) { i, t ->
                    TrackRow(
                        track = t, vm = vm, isCurrent = t.id == currentId,
                        onClick = { vm.play(st.results, i) },
                        onPickPlaylist = onPickPlaylist,
                    )
                }
            }

            st.error != null -> item {
                Text(st.error!!, color = Pal.Dim, modifier = Modifier.padding(vertical = 24.dp))
            }

            else -> {
                item {
                    Text("Explorar todo", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = Color.White, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                genres.chunked(2).forEach { pair ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { g ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .glass(RoundedCornerShape(18.dp), tint = g.color, strength = 2.2f)
                                        .clickable { text = g.name; vm.search(g.query) }
                                        .padding(14.dp),
                                ) {
                                    Text(g.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), strength = 1.3f)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Pal.Dim)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)) {
            if (value.isEmpty()) {
                Text("¿Qué quieres escuchar?", color = Pal.Dim, fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Pal.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Clear, "Borrar", tint = Pal.Dim) }
        }
    }
}
