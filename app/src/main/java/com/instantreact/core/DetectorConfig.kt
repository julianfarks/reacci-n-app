package com.instantreact.core

/**
 * Configuración inmutable del detector con un número VARIABLE de puntos independientes.
 * Las coordenadas están en píxeles de pantalla de la resolución de calibración ([calibW] x [calibH]);
 * [FrameProcessor] las reescala si la resolución cambia. Cada punto tiene su propio radio de detección.
 */
data class DetectorConfig(
    val xs: IntArray = IntArray(0),
    val ys: IntArray = IntArray(0),
    /** Radio (px) del área de detección de cada punto. */
    val radii: IntArray = IntArray(0),
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
    init {
        require(xs.size == ys.size && xs.size == radii.size) { "xs, ys y radii deben tener el mismo tamaño" }
    }

    val count: Int get() = xs.size

    companion object {
        const val DEFAULT_COUNT = 8
        /** Tope de seguridad muy alto: cada punto cuesta ~20 lecturas de píxel por fotograma. */
        const val MAX_POINTS = 200
        val COUNT_PRESETS = intArrayOf(2, 3, 4, 5, 6, 8, 10, 12, 16)
        val DELAY_PRESETS = longArrayOf(0, 10, 20, 30, 50, 75, 100, 150, 200)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectorConfig) return false
        return xs.contentEquals(other.xs) && ys.contentEquals(other.ys) && radii.contentEquals(other.radii) &&
            blueThreshold == other.blueThreshold && delayMs == other.delayMs &&
            tapDurationMs == other.tapDurationMs && calibrated == other.calibrated &&
            calibW == other.calibW && calibH == other.calibH
    }

    override fun hashCode(): Int {
        var h = xs.contentHashCode()
        h = 31 * h + ys.contentHashCode()
        h = 31 * h + radii.contentHashCode()
        h = 31 * h + blueThreshold
        h = 31 * h + delayMs.hashCode()
        h = 31 * h + tapDurationMs.hashCode()
        h = 31 * h + calibrated.hashCode()
        h = 31 * h + calibW
        h = 31 * h + calibH
        return h
    }
}
