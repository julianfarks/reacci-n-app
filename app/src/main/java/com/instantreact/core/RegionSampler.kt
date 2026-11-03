package com.instantreact.core

import java.nio.ByteBuffer

/** Resultado reutilizable del muestreo de una región (evita asignaciones en el bucle caliente). */
class SampleResult {
    var r = 0
    var g = 0
    var b = 0
    var blueN = 0
    var grayN = 0
    var total = 0

    /** Porcentaje (0..100) de muestras azules / grises. */
    val bluePct: Int get() = if (total == 0) 0 else blueN * 100 / total
    val grayPct: Int get() = if (total == 0) 0 else grayN * 100 / total

    val color: Int get() = ColorClassifier.pack(r, g, b)

    /** Clasificación del círculo completo: exige que la inmensa mayoría de la zona interior coincida. */
    fun classify(): Int = when {
        total < MIN_SAMPLES -> ColorClassifier.OTHER
        bluePct >= AGREE_PCT -> ColorClassifier.BLUE
        grayPct >= AGREE_PCT -> ColorClassifier.GRAY
        else -> ColorClassifier.OTHER
    }

    companion object {
        const val MIN_SAMPLES = 8
        const val AGREE_PCT = 85
    }
}

/**
 * Analiza únicamente la ZONA INTERIOR del círculo (60 % del radio de detección): rejilla 7x7 recortada
 * al disco (~37 lecturas), lo que deja fuera bordes, sombras, números y marcadores. Un solo píxel azul
 * en cualquier parte del área NO basta: hace falta que al menos el 85 % de la zona interior sea azul.
 */
object RegionSampler {
    private const val GRID = 7
    const val INNER_PCT = 60

    fun sample(
        buf: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        minSat: Int,
        out: SampleResult,
    ) {
        var sr = 0
        var sg = 0
        var sb = 0
        var blue = 0
        var gray = 0
        var n = 0
        val inner = maxOf(2, radius * INNER_PCT / 100)
        val step = maxOf(1, inner / (GRID / 2))
        val r2 = inner * inner + inner
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
                sr += rr; sg += gg; sb += bb
                when (ColorClassifier.classify(rr, gg, bb, minSat)) {
                    ColorClassifier.BLUE -> blue++
                    ColorClassifier.GRAY -> gray++
                }
                n++
            }
        }
        out.total = n
        out.blueN = blue
        out.grayN = gray
        if (n > 0) {
            out.r = sr / n; out.g = sg / n; out.b = sb / n
        } else {
            out.r = 0; out.g = 0; out.b = 0
        }
    }
}
