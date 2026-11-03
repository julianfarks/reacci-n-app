package com.instantreact.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad: su única función es ejecutar las pulsaciones (dispatchGesture) que el
 * usuario ha autorizado activándolo manualmente en Ajustes > Accesibilidad. No lee contenido de pantalla.
 */
class ReactAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ReactAccessibilityService? = null
    }
}
