package com.instantreact.core

import java.nio.ByteBuffer

/** Resultado reutilizable del muestreo de una región (evita asignaciones en el bucle caliente). */
class SampleResult {
    var r = 0
    var g = 0
    var b = 0
    var blueVotes = 0
    var total = 0

    /** Un círculo se considera azul si al menos la mitad de los puntos muestreados lo son. */
    val isBlue: Boolean get() = total > 0 && blueVotes * 2 >= total

    val color: Int get() = ColorClassifier.pack(r, g, b)
}

/**
 * Muestrea únicamente unos pocos píxeles dentro del círculo (rejilla 5x5 recortada al disco),
 * leyendo directamente del buffer de la imagen capturada (RGBA_8888). Nunca se analiza la pantalla
 * completa: el coste es ~20 lecturas por círculo.
 */
object RegionSampler {
    private const val GRID = 5

    fun sample(
        buf: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        threshold: Int,
        out: SampleResult,
    ) {
        var sr = 0
        var sg = 0
        var sb = 0
        var votes = 0
        var n = 0
        val r = if (radius < 2) 2 else radius
        val step = maxOf(1, r * 45 / 100)
        val r2 = r * r
        val limit = buf.limit()
        for (iy in 0 until GRID) {
            val dy = (iy - GRID / 2) * step
            val py = cy + dy
            if (py < 0 || py >= height) continue
            for (ix in 0 until GRID) {
                val dx = (ix - GRID / 2) * step
                if (dx * dx + dy * dy > r2) continue
                val px = cx + dx
                if (px < 0 || px >= width) continue
                val idx = py * rowStride + px * pixelStride
                if (idx + 2 >= limit) continue
                val rr = buf.get(idx).toInt() and 0xFF
                val gg = buf.get(idx + 1).toInt() and 0xFF
                val bb = buf.get(idx + 2).toInt() and 0xFF
                sr += rr
                sg += gg
                sb += bb
                if (ColorClassifier.isBlue(rr, gg, bb, threshold)) votes++
                n++
            }
        }
        out.total = n
        out.blueVotes = votes
        if (n > 0) {
            out.r = sr / n
            out.g = sg / n
            out.b = sb / n
        } else {
            out.r = 0; out.g = 0; out.b = 0
        }
    }
}
