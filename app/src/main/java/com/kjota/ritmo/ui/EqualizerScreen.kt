package com.kjota.ritmo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kjota.ritmo.MainViewModel
import kotlin.math.roundToInt

private val presetNames = listOf("Normal", "Bajos", "Agudos", "Rock", "Voz", "Pop")

@Composable
fun EqualizerScreen(vm: MainViewModel, onClose: () -> Unit) {
    val on by vm.eqOn.collectAsStateWithLifecycle()
    val bands by vm.eqBandInfo.collectAsStateWithLifecycle()
    val levels by vm.eqLevels.collectAsStateWithLifecycle()
    val bass by vm.bass.collectAsStateWithLifecycle()
    val loud by vm.loud.collectAsStateWithLifecycle()
    val preset by vm.eqPreset.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2080B0F))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Ecualizador", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                Switch(
                    checked = on, onCheckedChange = { vm.setEqOn(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Pal.Green, checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.2f), uncheckedThumbColor = Color.White,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(presetNames) { i, name ->
                        val sel = preset == i
                        Text(
                            name,
                            color = if (sel) Color(0xFF00210B) else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Pal.Green else Color.White.copy(alpha = 0.14f))
                                .clickable { vm.applyPreset(i) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    item {
                        val sel = preset < 0
                        Text(
                            "Mi ajuste",
                            color = if (sel) Color(0xFF00210B) else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Pal.Green else Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }

                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), strength = 1.3f).padding(vertical = 16.dp, horizontal = 8.dp),
                ) {
                    if (bands.isEmpty()) {
                        Text(
                            "Reproduce una canción para activar el ecualizador.",
                            color = Pal.Dim, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            bands.forEachIndexed { i, b ->
                                val mB = levels.getOrElse(i) { 0 }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    val db = mB / 100f
                                    Text(
                                        (if (db > 0f) "+" else "") + String.format(java.util.Locale.US, "%.0f", db) + " dB",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (mB != 0) Pal.Green else Pal.Dim,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    BandSlider(
                                        level = mB, minMb = (b.minDb * 100).roundToInt(), maxMb = (b.maxDb * 100).roundToInt(),
                                        enabled = on, onChange = { vm.setEqBand(i, it) },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(freqLabel(b.centerHz), fontSize = 11.5.sp, color = Pal.Dim)
                                }
                            }
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row {
                        Text("Refuerzo de bajos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${bass / 10} %", color = Pal.Dim, fontSize = 13.sp)
                    }
                    Slider(
                        value = bass / 1000f, onValueChange = { vm.setBass((it * 1000).roundToInt()) },
                        colors = sliderColors(),
                    )
                    Row {
                        Text("Volumen más fuerte y parejo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("+" + String.format(java.util.Locale.US, "%.1f", loud / 100f) + " dB", color = Pal.Dim, fontSize = 13.sp)
                    }
                    Slider(
                        value = loud / 1000f, onValueChange = { vm.setLoud((it * 1000).roundToInt()) },
                        colors = sliderColors(),
                    )
                    if (!on) {
                        Text("Activa el ecualizador (arriba a la derecha) para que estos ajustes suenen.", color = Pal.Dim, fontSize = 12.sp)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Calidad de audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                        Text(
                            "MÁXIMA SIEMPRE", color = Pal.Green, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.Green.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        "Siempre se elige la mejor calidad que entrega YouTube (Opus de unos 160 kbps; AAC de 128 kbps si no hay Opus). " +
                            "Las descargas guardan esa misma calidad.",
                        color = Pal.Dim, fontSize = 12.5.sp, lineHeight = 17.sp,
                    )
                }

                Text(
                    "Restablecer",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp))
                        .clickable { vm.resetEq() }
                        .padding(vertical = 14.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f).replace(".0", "") else "$hz Hz"

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Pal.Green,
    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
)

/** Control vertical de una banda: arrastra o toca para fijar el nivel. */
@Composable
private fun BandSlider(level: Int, minMb: Int, maxMb: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val height = 190.dp
    fun toLevel(y: Float, h: Float): Int {
        val frac = 1f - (y / h).coerceIn(0f, 1f)
        val raw = minMb + frac * (maxMb - minMb)
        var v = (raw / 50f).roundToInt() * 50 // pasos de 0,5 dB
        if (kotlin.math.abs(v) < 60) v = 0     // imán en 0 dB
        return v.coerceIn(minMb, maxMb)
    }
    Box(
        Modifier
            .width(44.dp)
            .height(height)
            .pointerInput(minMb, maxMb) {
                detectTapGestures { o -> onChange(toLevel(o.y, size.height.toFloat())) }
            }
            .pointerInput(minMb, maxMb) {
                detectVerticalDragGestures(
                    onDragStart = { o -> onChange(toLevel(o.y, size.height.toFloat())) },
                    onVerticalDrag = { change, _ -> onChange(toLevel(change.position.y, size.height.toFloat())) },
                )
            }
            .drawBehind {
                val cx = size.width / 2f
                val frac = ((level - minMb).toFloat() / (maxMb - minMb).coerceAtLeast(1)).coerceIn(0f, 1f)
                val ty = size.height * (1f - frac)
                val zero = size.height * (1f - (0 - minMb).toFloat() / (maxMb - minMb).coerceAtLeast(1))
                val track = 6.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.18f), Offset(cx, 0f), Offset(cx, size.height), track, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.30f), Offset(cx - 14.dp.toPx(), zero), Offset(cx + 14.dp.toPx(), zero), 1.5.dp.toPx())
                drawLine(if (enabled) Pal.Green else Color.White.copy(alpha = 0.4f), Offset(cx, zero), Offset(cx, ty), track, StrokeCap.Round)
                drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx(), Offset(cx, ty + 2.dp.toPx()))
                drawCircle(Color.White, 12.dp.toPx(), Offset(cx, ty))
            },
    )
}
