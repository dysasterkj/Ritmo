package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.engine.Engine
import com.kjota.ritmo.update.Updater
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(vm: MainViewModel, onOpenEq: () -> Unit) {
    val version by Engine.version.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val mixOn by vm.mixOn.collectAsStateWithLifecycle()
    val carOn by vm.carMode.collectAsStateWithLifecycle()
    val mixSec by vm.mixSec.collectAsStateWithLifecycle()
    val curve by vm.mixCurve.collectAsStateWithLifecycle()
    val eqOn by vm.eqOn.collectAsStateWithLifecycle()
    val vh by vm.videoHeight.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val upd by Updater.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Ajustes")

        SettingsCard(
            "Actualizaciones",
            "Versión instalada: " + (upd.installed.ifEmpty { "…" }),
        ) {
            val rel = upd.release
            when {
                upd.downloading -> {
                    Text("Descargando… ${upd.progress} %", color = Color.White, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { upd.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Pal.Green,
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
                rel != null -> {
                    Text(
                        "Hay una versión nueva: ${rel.tag}" +
                            (if (rel.sizeBytes > 0) " (${formatSize(rel.sizeBytes)})" else ""),
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                    )
                    if (upd.downloaded) {
                        GreenPill("Instalar ${rel.tag}") { Updater.install(ctx) }
                    } else {
                        GreenPill("Descargar e instalar") { Updater.downloadAsync(ctx) }
                    }
                }
                else -> GreenPill(if (upd.checking) "Buscando…" else "Buscar actualizaciones") {
                    if (!upd.checking) Updater.checkAsync(ctx, manual = true)
                }
            }
            upd.message?.let { Text(it, color = Pal.Dim, fontSize = 13.sp) }
        }

        SettingsCard(
            "Modo auto",
            "Pantalla horizontal con botones grandes, dividida con Waze al lado (como Android Auto).",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activar modo auto", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = carOn, onCheckedChange = { vm.setCarMode(it) }, colors = switchColors())
            }
            Text(
                "Para que se divida sola y abra Waze al lado, activa \"Ritmo: pantalla dividida\" en Accesibilidad (una sola vez).",
                color = Pal.Dim, fontSize = 13.sp,
            )
            GreenPill("Abrir Accesibilidad") {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        SettingsCard("Mix DJ", "Las canciones se mezclan solas, sin cortes.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activar Mix DJ", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = mixOn, onCheckedChange = { vm.setMix(it) }, colors = switchColors())
            }
            Label("Duración de la mezcla")
            ChipRow(listOf("5 s", "8 s", "12 s", "16 s"), listOf(5, 8, 12, 16).indexOf(mixSec)) {
                vm.setMixSeconds(listOf(5, 8, 12, 16)[it])
            }
            Label("Curva")
            ChipRow(listOf("Suave", "Corte rápido", "Lineal"), curve) { vm.setMixCurve(it) }
        }

        SettingsCard("Audio", "Siempre la mejor calidad que entrega YouTube.") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .tvClickable(RoundedCornerShape(16.dp)) { onOpenEq() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ecualizador", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (eqOn) "Activado" else "Apagado", color = if (eqOn) Pal.Green else Pal.Dim, fontSize = 13.sp)
            }
        }

        SettingsCard("Video", "Calidad máxima al ver videos. Si va entrecortado, baja la calidad.") {
            ChipRow(listOf("360p", "480p", "720p", "1080p"), listOf(360, 480, 720, 1080).indexOf(vh)) {
                vm.setVideoHeight(listOf(360, 480, 720, 1080)[it])
            }
        }

        SettingsCard("Motor de YouTube", "yt-dlp: ${version ?: "iniciando…"} · ruta rápida: ${com.kjota.ritmo.engine.InnerTube.status}") {
            Text(
                "Si las búsquedas, los videos o la reproducción fallan, actualiza el motor: YouTube cambia seguido.",
                color = Pal.Dim, fontSize = 13.sp,
            )
            Text(
                "Actualizar motor ahora",
                color = Color(0xFF00210B), fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pal.Green)
                    .tvClickable(RoundedCornerShape(50)) { vm.updateEngine() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        SettingsCard("Almacenamiento", "${lib.downloads.size} canciones · ${formatSize(Library.usedBytes())} en el dispositivo") {
            Text("Sin anuncios, sin cuentas y sin seguimiento. Todo se guarda solo en este dispositivo.", color = Pal.Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), strength = 1.25f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(subtitle, color = Pal.Dim, fontSize = 13.sp)
        }
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Pal.Dim, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            val on = i == selected
            Text(
                name,
                color = if (on) Color(0xFF00210B) else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Pal.Green else Color.White.copy(alpha = 0.14f))
                    .tvClickable(RoundedCornerShape(50)) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = Pal.Green, checkedThumbColor = Color.White,
    uncheckedTrackColor = Color.White.copy(alpha = 0.2f), uncheckedThumbColor = Color.White,
    uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun GreenPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color(0xFF00210B), fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Pal.Green)
            .tvClickable(RoundedCornerShape(50), onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
