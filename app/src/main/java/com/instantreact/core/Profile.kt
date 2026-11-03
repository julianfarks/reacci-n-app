package com.instantreact.core

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Perfil de calibración: coordenadas, tamaño de cada área y configuración de color. */
data class Profile(
    val id: String,
    val name: String,
    val xs: IntArray,
    val ys: IntArray,
    val radii: IntArray,
    val blueThreshold: Int,
    val calibW: Int,
    val calibH: Int,
) {
    val count: Int get() = xs.size

    override fun equals(other: Any?): Boolean =
        other is Profile && id == other.id && name == other.name && xs.contentEquals(other.xs) &&
            ys.contentEquals(other.ys) && radii.contentEquals(other.radii) &&
            blueThreshold == other.blueThreshold && calibW == other.calibW && calibH == other.calibH

    override fun hashCode(): Int = id.hashCode() * 31 + xs.contentHashCode()
}

/**
 * Disposición inicial de los puntos (en vertical). Sigue la rejilla 3x3 del minijuego
 * (x ≈ 25/50/75 %, y ≈ 50/62/73 % de la pantalla). Con 8 puntos son los 8 alrededor del centro.
 * Es solo un punto de partida: cualquier punto se puede arrastrar y se guarda en el perfil.
 */
object DefaultLayout {
    private val FX = doubleArrayOf(0.2464, 0.5, 0.7527)
    private val FY = doubleArrayOf(0.502, 0.6157, 0.7298)

    /** Casillas de la rejilla 3x3 (fila*3+columna) usadas para cada cantidad. */
    private val SLOTS: Map<Int, IntArray> = mapOf(
        1 to intArrayOf(4),
        2 to intArrayOf(3, 5),
        3 to intArrayOf(3, 4, 5),
        4 to intArrayOf(0, 2, 6, 8),
        5 to intArrayOf(0, 2, 4, 6, 8),
        6 to intArrayOf(0, 1, 2, 6, 7, 8),
        7 to intArrayOf(0, 1, 2, 3, 5, 6, 8),
        8 to intArrayOf(0, 1, 2, 3, 5, 6, 7, 8),
        9 to intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
    )

    fun positions(n: Int, w: Int, h: Int): Pair<IntArray, IntArray> {
        val xs = IntArray(n)
        val ys = IntArray(n)
        val slots = SLOTS[n]
        if (slots != null) {
            for (i in 0 until n) {
                xs[i] = (FX[slots[i] % 3] * w).roundToInt()
                ys[i] = (FY[slots[i] / 3] * h).roundToInt()
            }
            return Pair(xs, ys)
        }
        // Más de 9 (o 0): rejilla de 4 columnas, última fila centrada.
        val cols = 4
        val rows = ceil(n / cols.toDouble()).toInt().coerceAtLeast(1)
        var i = 0
        for (r in 0 until rows) {
            val k = minOf(cols, n - r * cols)
            val y = if (rows == 1) 0.6 else 0.36 + (0.84 - 0.36) * r / (rows - 1)
            for (c in 0 until k) {
                xs[i] = ((0.5 + (c - (k - 1) / 2.0) * 0.175) * w).roundToInt()
                ys[i] = (y * h).roundToInt()
                i++
            }
        }
        return Pair(xs, ys)
    }

    fun defaultRadius(w: Int): Int = (w * 0.05).roundToInt().coerceIn(8, 80)

    fun profile(id: String, name: String, n: Int, w: Int, h: Int, blue: Int = 60): Profile {
        val (xs, ys) = positions(n, w, h)
        return Profile(id, name, xs, ys, IntArray(n) { defaultRadius(w) }, blue, w, h)
    }

    /** true si el perfil sigue con la disposición por defecto (sin mover ningún punto). */
    fun isPristine(p: Profile): Boolean {
        val (xs, ys) = positions(p.count, p.calibW, p.calibH)
        return xs.contentEquals(p.xs) && ys.contentEquals(p.ys)
    }
}

object Profiles {
    /**
     * Cambia la cantidad de puntos creando o eliminando marcadores automáticamente.
     * Si el perfil estaba sin tocar se regenera la disposición por defecto de la nueva cantidad;
     * si no, se conservan los puntos existentes y los nuevos nacen en la disposición por defecto.
     */
    fun resize(p: Profile, n: Int): Profile {
        val target = n.coerceIn(1, DetectorConfig.MAX_POINTS)
        if (target == p.count) return p
        if (DefaultLayout.isPristine(p)) {
            return DefaultLayout.profile(p.id, p.name, target, p.calibW, p.calibH, p.blueThreshold)
        }
        if (target < p.count) {
            return p.copy(xs = p.xs.copyOf(target), ys = p.ys.copyOf(target), radii = p.radii.copyOf(target))
        }
        val (dx, dy) = DefaultLayout.positions(target, p.calibW, p.calibH)
        val avgR = if (p.count == 0) DefaultLayout.defaultRadius(p.calibW) else p.radii.average().roundToInt()
        return p.copy(
            xs = IntArray(target) { if (it < p.count) p.xs[it] else dx[it] },
            ys = IntArray(target) { if (it < p.count) p.ys[it] else dy[it] },
            radii = IntArray(target) { if (it < p.count) p.radii[it] else avgR },
        )
    }

    fun toConfig(p: Profile, delayMs: Long, tapMs: Long) = DetectorConfig(
        xs = p.xs.copyOf(), ys = p.ys.copyOf(), radii = p.radii.copyOf(),
        blueThreshold = p.blueThreshold, delayMs = delayMs, tapDurationMs = tapMs,
        calibrated = p.count > 0, calibW = p.calibW, calibH = p.calibH,
    )
}

object ProfileCodec {
    private fun ints(a: IntArray) = a.joinToString(",")
    private fun parse(s: String): IntArray =
        if (s.isBlank()) IntArray(0) else s.split(',').map { it.trim().toInt() }.toIntArray()

    fun cleanName(s: String): String = s.replace('|', '/').replace('\n', ' ').trim().ifEmpty { "Perfil" }

    fun encode(p: Profile): String =
        listOf(cleanName(p.name), p.blueThreshold, p.calibW, p.calibH, ints(p.xs), ints(p.ys), ints(p.radii))
            .joinToString("|")

    fun decode(id: String, s: String): Profile? = try {
        val f = s.split('|')
        if (f.size < 7) null else {
            val xs = parse(f[4]); val ys = parse(f[5]); val rs = parse(f[6])
            if (xs.size != ys.size || xs.size != rs.size) null
            else Profile(id, f[0], xs, ys, rs, f[1].toInt(), f[2].toInt(), f[3].toInt())
        }
    } catch (e: Exception) {
        null
    }
}
