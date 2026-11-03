package com.instantreact.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad: su única función es ejecutar las pulsaciones (dispatchGesture) que el
 * usuario ha autorizado activándolo manualmente en Ajustes > Accesibilidad. No lee contenido de pantalla.
 */
class ReactAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            lastConnected = System.currentTimeMillis()
            val canGestures = serviceInfo?.capabilities?.and(AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
            Diagnostics.event("onServiceConnected OK · canPerformGestures=$canGestures · pid=${android.os.Process.myPid()}")
        } catch (t: Throwable) {
            Diagnostics.event("onServiceConnected FALLÓ", t)
            throw t
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Diagnostics.event("onUnbind")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Diagnostics.event("onDestroy")
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ReactAccessibilityService? = null

        @Volatile
        var lastConnected: Long? = null
    }
}
