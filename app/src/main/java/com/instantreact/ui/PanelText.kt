package com.instantreact.ui

/** Textos del panel (separados de las vistas para poder probarlos en JVM). */
object PanelText {
    fun delay(ms: Long) = "Retraso configurado: $ms ms"

    fun last(reactionMs: Double, systemMs: Double, taps: Int): String =
        if (taps == 0) "Último: —" else "Último: ${reactionMs.toInt()} ms · sistema ${systemMs.toInt()} ms"

    fun taps(n: Int) = "Pulsaciones: $n"

    fun state(running: Boolean, calibrated: Boolean, mismatch: Boolean): String = when {
        !calibrated -> "Sin calibrar"
        mismatch -> "Gira la pantalla (orientación distinta)"
        running -> "Detección activa"
        else -> "Detenido"
    }
}
