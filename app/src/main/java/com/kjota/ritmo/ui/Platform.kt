package com.kjota.ritmo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private var awakeCount = 0

/** Mantiene la pantalla encendida mientras este composable esté visible (video, letras). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        awakeCount++
        view.keepScreenOn = true
        onDispose {
            awakeCount = (awakeCount - 1).coerceAtLeast(0)
            view.keepScreenOn = awakeCount > 0
        }
    }
}

/** Pantalla inmersiva (sin barras del sistema) y, si se pide, horizontal. Se restaura al salir. */
@Composable
fun SystemUi(hideBars: Boolean, landscape: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(hideBars, landscape) {
        val act = ctx.findActivity()
        val win = act?.window
        val ctl = if (win != null) WindowCompat.getInsetsController(win, win.decorView) else null
        if (hideBars) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        if (landscape) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (landscape) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/** Como clickable, pero con un borde blanco visible cuando se navega con el control remoto (TV). */
fun Modifier.tvClickable(shape: Shape = RoundedCornerShape(16.dp), onClick: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.5.dp, Color.White, shape) else Modifier)
        .clickable(onClick = onClick)
}

/** True cuando la app corre en una Android TV / Google TV. */
fun isTv(c: android.content.Context): Boolean {
    val um = c.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    return um?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        c.packageManager.hasSystemFeature("android.software.leanback")
}
