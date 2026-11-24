package com.kjota.ritmo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel

/**
 * Importar una playlist: pega un enlace (YouTube, YouTube Music, Spotify, Deezer, SoundCloud, Bandcamp…)
 * o una lista de canciones en texto, una por línea ("Artista - Canción").
 */
@Composable
fun ImportDialog(vm: MainViewModel, initial: String, onDismiss: () -> Unit) {
    val st by vm.importState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(initial) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2151A22),
        title = { Text("Importar playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !st.running,
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("Enlace o lista de canciones") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = !st.running,
                    onClick = { clipboard.getText()?.text?.let { text = it } },
                ) { Text("Pegar del portapapeles", color = Pal.Green) }
                Text(
                    "Sirve con YouTube, YouTube Music, Spotify (listas públicas), Deezer, SoundCloud y Bandcamp. " +
                        "También puedes pegar canciones como texto (Artista - Canción, una por línea). " +
                        "Todo suena desde YouTube.",
                    color = Pal.Dim, fontSize = 12.sp,
                )
                if (st.running) {
                    if (st.total > 0) {
                        LinearProgressIndicator(
                            progress = { st.done.toFloat() / st.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = Pal.Green,
                        )
                        Text("${st.message} ${st.done}/${st.total}", color = Color.White, fontSize = 13.sp)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Pal.Green)
                        Text(st.message, color = Color.White, fontSize = 13.sp)
                    }
                    Text("Puedes cerrar esta ventana: la importación sigue en segundo plano.", color = Pal.Dim, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !st.running && text.isNotBlank(),
                onClick = { vm.importPlaylist(text); onDismiss() },
            ) { Text("Importar", color = Pal.Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (st.running) "Cerrar" else "Cancelar", color = Color.White) } },
    )
}
