package com.instantreact.ui

/** Textos del panel (separados de las vistas para poder probarlos en JVM). */
object PanelText {
    fun delay(ms: Long) = "Retraso configurado: $ms ms"

    fun last(reactionMs: Double, systemMs: Double, taps: Int, point: Int = -1): String =
        if (taps == 0) "Último: —" else {
            val who = if (point >= 0) "punto ${point + 1} · " else ""
            "Último: $who${reactionMs.toInt()} ms · sistema ${systemMs.toInt()} ms"
        }

    fun points(n: Int, profile: String) = if (profile.isEmpty()) "Puntos: $n" else "Puntos: $n · $profile"

    fun taps(n: Int) = "Pulsaciones: $n"

    fun state(running: Boolean, calibrated: Boolean, mismatch: Boolean): String = when {
        !calibrated -> "Sin calibrar"
        mismatch -> "Gira la pantalla (orientación distinta)"
        running -> "Detección activa"
        else -> "Detenido"
    }
}
