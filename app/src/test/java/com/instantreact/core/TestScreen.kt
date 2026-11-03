package com.instantreact.core

import java.nio.ByteBuffer

/** Pantalla sintética RGBA_8888 con la misma disposición de memoria que un ImageReader (con relleno de fila opcional). */
class TestScreen(val w: Int, val h: Int, padPixels: Int = 0) {
    val pixelStride = 4
    val rowStride = (w + padPixels) * 4
    val buf: ByteBuffer = ByteBuffer.allocateDirect(rowStride * h)

    init {
        fill(12, 14, 18)
    }

    fun fill(r: Int, g: Int, b: Int) {
        for (y in 0 until h) for (x in 0 until w) put(x, y, r, g, b)
    }

    fun put(x: Int, y: Int, r: Int, g: Int, b: Int) {
        val i = y * rowStride + x * pixelStride
        buf.put(i, r.toByte()); buf.put(i + 1, g.toByte()); buf.put(i + 2, b.toByte()); buf.put(i + 3, 0xFF.toByte())
    }

    fun circle(cx: Int, cy: Int, rad: Int, r: Int, g: Int, b: Int) {
        for (y in maxOf(0, cy - rad)..minOf(h - 1, cy + rad)) {
            for (x in maxOf(0, cx - rad)..minOf(w - 1, cx + rad)) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rad * rad) put(x, y, r, g, b)
            }
        }
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, r: Int, g: Int, b: Int) {
        for (y in y0 until y1) for (x in x0 until x1) put(x, y, r, g, b)
    }

    /** Downsample (paso [step]) a rejilla ARGB, como hace el servicio para la autodetección. */
    fun downsample(step: Int): Triple<IntArray, Int, Int> {
        val gw = w / step
        val gh = h / step
        val out = IntArray(gw * gh)
        var o = 0
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val i = gy * step * rowStride + gx * step * pixelStride
            out[o++] = ColorClassifier.pack(
                buf.get(i).toInt() and 0xFF, buf.get(i + 1).toInt() and 0xFF, buf.get(i + 2).toInt() and 0xFF,
            )
        }
        return Triple(out, gw, gh)
    }
}

/** Disposición del minijuego: 6 círculos en 3 columnas x 2 filas. */
class GameLayout(val w: Int, val h: Int) {
    val radius = minOf(w, h) * 7 / 100
    val xs = IntArray(6) { w * (it % 3 + 1) / 4 }
    val ys = IntArray(6) { h * (4 + 2 * (it / 3)) / 10 }

    fun draw(screen: TestScreen, blue: Set<Int> = emptySet(), grayLevel: Int = 128, blueScale: Double = 1.0) {
        for (i in 0 until 6) {
            if (i in blue) {
                screen.circle(xs[i], ys[i], radius, (40 * blueScale).toInt(), (120 * blueScale).toInt(), (255 * blueScale).toInt())
            } else {
                screen.circle(xs[i], ys[i], radius, grayLevel, grayLevel, grayLevel)
            }
        }
    }

    fun config(detectRadius: Int = radius * 6 / 10, threshold: Int = 60, delay: Long = 0L) = DetectorConfig(
        xs = xs.copyOf(), ys = ys.copyOf(), radius = detectRadius, blueThreshold = threshold,
        delayMs = delay, calibrated = true, calibW = w, calibH = h,
    )
}
