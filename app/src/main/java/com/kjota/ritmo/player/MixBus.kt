package com.kjota.ritmo.player

import kotlinx.coroutines.flow.MutableStateFlow

/** Estado de la mezcla Mix DJ, compartido entre el servicio de reproducción y la pantalla (mismo proceso). */
object MixBus {
    data class State(
        val mixing: Boolean = false,
        val progress: Float = 0f,
        val outTitle: String = "",
        val inTitle: String = "",
    )

    val state = MutableStateFlow(State())
}
