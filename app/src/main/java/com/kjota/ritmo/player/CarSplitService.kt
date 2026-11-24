package com.kjota.ritmo.player

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad mínimo: su único uso es pedirle al sistema "dividir pantalla"
 * cuando se activa el modo auto. No escucha eventos ni lee el contenido de la pantalla.
 */
class CarSplitService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var instance: CarSplitService? = null
        val enabled: Boolean get() = instance != null

        /** Activa o desactiva la pantalla dividida del sistema. */
        fun toggleSplit(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false
    }
}
