package com.instantreact.core

/** Estado por punto y razones de rechazo, visibles en el modo de diagnóstico. */
object PointStatus {
    /** Bloqueado: esperando a que el círculo vuelva a gris (ESPERA). */
    const val WAIT = 0
    /** Gris estable y armado: listo para reaccionar (GRIS). */
    const val GRAY = 1
    /** Cambio gris->azul confirmado, pulsación en curso (AZUL). */
    const val DETECTED = 2
    /** Pulsación ejecutada (PULSADO). */
    const val PRESSED = 3

    fun name(s: Int) = when (s) {
        GRAY -> "GRIS"
        DETECTED -> "AZUL"
        PRESSED -> "PULSADO"
        else -> "ESPERA"
    }
}

object Reject {
    const val NONE = 0
    const val NOT_ARMED = 1
    const val MULTI = 2
    const val GLOBAL = 3
    const val NOT_GRAY_BEFORE = 4
    const val LOST_BASELINE = 5

    fun text(r: Int) = when (r) {
        NOT_ARMED -> "rechazado: ya pulsado, espera a gris"
        MULTI -> "rechazado: varios azules a la vez"
        GLOBAL -> "rechazado: cambio global de pantalla"
        NOT_GRAY_BEFORE -> "rechazado: no venía de gris estable"
        LOST_BASELINE -> "perdió la base gris (animación)"
        else -> "—"
    }
}

/** Datos de depuración de un punto; los escribe el hilo de captura y los lee la interfaz. */
class PointDebug(val n: Int) {
    val status = IntArray(n)
    val cls = IntArray(n)
    val rgb = IntArray(n)
    val hue = IntArray(n)
    val sat = IntArray(n)
    val value = IntArray(n)
    val bluePct = IntArray(n)
    val grayPct = IntArray(n)
    val reject = IntArray(n)
    val cx = IntArray(n)
    val cy = IntArray(n)
    val radius = IntArray(n)
    val realChange = BooleanArray(n)
    /** Tiempo entre detección y envío de la pulsación (ms); -1 si no hay. */
    val detectToTapMs = DoubleArray(n) { -1.0 }
    @Volatile var lastChosen = -1
    @Volatile var lastFrameW = 0
    @Volatile var lastFrameH = 0
}
