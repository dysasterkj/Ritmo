package com.kjota.ritmo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Fondo "aurora": manchas de luz verde, turquesa, violeta y azul que se mueven muy despacio
 * detrás de todo. Es lo que el vidrio "desenfoca" y deja entrever.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora-t",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Ink)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, Color.Transparent), Offset(cx, cy), r),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
                blob(Color(0x7712B556), w * (0.10f + 0.25f * t), h * (0.10f + 0.06f * t), w * 0.95f)
                blob(Color(0x5538E8C8), w * (0.95f - 0.30f * t), h * (0.35f + 0.08f * t), w * 0.80f)
                blob(Color(0x558B7BFF), w * (0.15f + 0.20f * t), h * (0.68f - 0.08f * t), w * 0.85f)
                blob(Color(0x443B82F6), w * (0.90f - 0.12f * t), h * (0.92f - 0.10f * t), w * 0.90f)
            },
        content = content,
    )
}

private val GlassBorder
    get() = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f)))

/** Vidrio: relleno translúcido con degradado y borde de luz. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color.White,
    strength: Float = 1f,
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(tint.copy(alpha = 0.17f * strength), tint.copy(alpha = 0.05f * strength))
        )
    )
    .border(1.dp, GlassBorder, shape)

/** Vidrio para barras flotantes (mini reproductor y navegación): más oscuro para que el texto se lea. */
fun Modifier.glassBar(shape: Shape = RoundedCornerShape(26.dp)): Modifier = this
    .clip(shape)
    .background(Color(0xD90B0F14))
    .background(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))
    )
    .border(1.dp, GlassBorder, shape)
