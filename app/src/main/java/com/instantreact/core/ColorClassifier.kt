package com.instantreact.core

/**
 * Clasificación de color basada en enteros (sin asignaciones), pensada para ejecutarse
 * decenas de veces por fotograma.
 *
 * El azul se define por *dominancia*: cuánto supera el canal B al mayor de R y G. El umbral se
 * escala con el brillo del propio píxel, así un azul oscuro (poca luz) y un azul brillante se
 * detectan igual de bien aunque el juego cambie ligeramente de brillo. Además se exige un matiz
 * entre cian-azulado y azul-violáceo claro para no confundir el azul con morado, verde o rojo.
 */
object ColorClassifier {
    const val MIN_BLUE_CHANNEL = 50
    const val MIN_DOMINANCE = 12
    const val GRAY_TOLERANCE = 30

    /** Matiz admitido expresado como (R-G)/(B-min(R,G)) * 100. */
    private const val HUE_MIN = -85
    private const val HUE_MAX = 35

    fun isBlue(r: Int, g: Int, b: Int, threshold: Int): Boolean {
        if (b < MIN_BLUE_CHANNEL) return false
        val dominance = b - (if (r > g) r else g)
        val needed = maxOf(MIN_DOMINANCE, threshold * b / 255)
        if (dominance < needed) return false
        val delta = b - (if (r < g) r else g)
        if (delta <= 0) return false
        val hue = (r - g) * 100 / delta
        return hue in HUE_MIN..HUE_MAX
    }

    /** Gris = poca diferencia entre canales. */
    fun isGray(r: Int, g: Int, b: Int): Boolean {
        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        return max - min <= GRAY_TOLERANCE
    }

    fun pack(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
