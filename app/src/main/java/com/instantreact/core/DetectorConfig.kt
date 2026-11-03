package com.instantreact.core

/**
 * Configuración inmutable del detector. Se comparte entre la UI y el hilo de captura.
 * Las coordenadas están en píxeles de pantalla reales de la resolución en la que se calibró
 * ([calibW] x [calibH]); [FrameProcessor] las reescala si la resolución cambia.
 */
data class DetectorConfig(
    val xs: IntArray = IntArray(CIRCLE_COUNT),
    val ys: IntArray = IntArray(CIRCLE_COUNT),
    /** Radio (px) del área de detección alrededor de cada círculo. */
    val radius: Int = 18,
    /** Umbral de dominancia del azul (10..150). Menor = más sensible. */
    val blueThreshold: Int = 60,
    /** Retraso configurado antes de pulsar, en ms (0 = inmediato). */
    val delayMs: Long = 0L,
    /** Duración del toque en ms. */
    val tapDurationMs: Long = 10L,
    val calibrated: Boolean = false,
    val calibW: Int = 0,
    val calibH: Int = 0,
) {
    companion object {
        const val CIRCLE_COUNT = 6
        val DELAY_PRESETS = longArrayOf(0, 10, 20, 30, 50, 75, 100, 150, 200)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectorConfig) return false
        return xs.contentEquals(other.xs) && ys.contentEquals(other.ys) &&
            radius == other.radius && blueThreshold == other.blueThreshold &&
            delayMs == other.delayMs && tapDurationMs == other.tapDurationMs &&
            calibrated == other.calibrated && calibW == other.calibW && calibH == other.calibH
    }

    override fun hashCode(): Int {
        var h = xs.contentHashCode()
        h = 31 * h + ys.contentHashCode()
        h = 31 * h + radius
        h = 31 * h + blueThreshold
        h = 31 * h + delayMs.hashCode()
        h = 31 * h + tapDurationMs.hashCode()
        h = 31 * h + calibrated.hashCode()
        h = 31 * h + calibW
        h = 31 * h + calibH
        return h
    }
}
