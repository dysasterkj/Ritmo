package com.kjota.ritmo.engine

import android.app.Application
import com.kjota.ritmo.data.Prefs
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Loading : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

/** Motor yt-dlp (empaquetado dentro de la app). La primera vez tarda unos segundos en descomprimirse. */
object Engine {
    private const val KEY_LAST_UPDATE = "last_engine_update"
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    private val _state = MutableStateFlow<EngineState>(EngineState.Loading)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    @Volatile
    private var started = false

    fun start(app: Application) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(app)
                _version.value = YoutubeDL.getInstance().versionName(app)
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(t.message ?: t.javaClass.simpleName)
                return@launch
            }
            autoUpdate(app)
        }
    }

    /** YouTube cambia seguido: yt-dlp se actualiza solo, como máximo una vez al día. */
    private fun autoUpdate(app: Application) {
        val last = Prefs.getLong(app, KEY_LAST_UPDATE)
        if (System.currentTimeMillis() - last < ONE_DAY_MS) return
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app)
            Prefs.putLong(app, KEY_LAST_UPDATE, System.currentTimeMillis())
            _version.value = YoutubeDL.getInstance().versionName(app)
        } catch (_: Throwable) {
        }
    }

    suspend fun awaitReady() {
        val s = state.first { it !is EngineState.Loading }
        if (s is EngineState.Failed) throw IllegalStateException("El motor no pudo iniciar: ${s.message}")
    }

    suspend fun update(app: Application): String = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(app)
        Prefs.putLong(app, KEY_LAST_UPDATE, System.currentTimeMillis())
        _version.value = YoutubeDL.getInstance().versionName(app)
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "Motor actualizado"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Ya tienes la última versión"
            null -> "Listo"
        }
    }
}
