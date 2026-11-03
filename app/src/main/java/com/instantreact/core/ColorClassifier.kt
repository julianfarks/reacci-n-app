package com.instantreact.core

/**
 * Clasificación de color en el espacio HSV (tono, saturación, valor), con enteros y sin asignaciones.
 *
 *  - AZUL: tono 190°–255°, saturación ≥ mínimo configurable y valor ≥ 25 %. Un gris, aunque sea claro,
 *    oscuro, brillante o con un tinte azulado leve, tiene saturación baja y NUNCA es azul. Cambiar el
 *    brillo solo mueve el valor, no el tono ni la saturación.
 *  - GRIS: saturación ≤ 25 %, o tan oscuro (valor < 25 %) que el tono carece de significado.
 *  - Todo lo demás es OTRO (rojo, verde, morado, mezclas de animación...), que nunca dispara.
 */
object ColorClassifier {
    const val OTHER = 0
    const val GRAY = 1
    const val BLUE = 2

    const val BLUE_HUE_MIN = 190
    const val BLUE_HUE_MAX = 255
    const val MIN_VALUE = 25
    const val GRAY_MAX_SAT = 25

    /** Umbral de azul de la interfaz (10..150, menor = más sensible) -> saturación mínima en %. */
    fun minSaturation(threshold: Int): Int = (threshold * 3 / 4).coerceIn(15, 90)

    fun value(r: Int, g: Int, b: Int): Int = maxOf(r, maxOf(g, b)) * 100 / 255

    fun saturation(r: Int, g: Int, b: Int): Int {
        val max = maxOf(r, maxOf(g, b))
        if (max == 0) return 0
        return (max - minOf(r, minOf(g, b))) * 100 / max
    }

    /** Tono 0..359 (0 si no hay color). */
    fun hue(r: Int, g: Int, b: Int): Int {
        val max = maxOf(r, maxOf(g, b))
        val delta = max - minOf(r, minOf(g, b))
        if (delta == 0) return 0
        val h = when (max) {
            r -> 60 * (g - b) / delta
            g -> 120 + 60 * (b - r) / delta
            else -> 240 + 60 * (r - g) / delta
        }
        return (h + 360) % 360
    }

    fun classify(r: Int, g: Int, b: Int, minSat: Int): Int {
        val v = value(r, g, b)
        if (v < MIN_VALUE) return GRAY
        val s = saturation(r, g, b)
        if (s <= GRAY_MAX_SAT) return GRAY
        if (s >= minSat) {
            val h = hue(r, g, b)
            if (h in BLUE_HUE_MIN..BLUE_HUE_MAX) return BLUE
        }
        return OTHER
    }

    fun isBlue(r: Int, g: Int, b: Int, threshold: Int): Boolean = classify(r, g, b, minSaturation(threshold)) == BLUE

    fun isGray(r: Int, g: Int, b: Int): Boolean = classify(r, g, b, 100) == GRAY

    fun pack(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
