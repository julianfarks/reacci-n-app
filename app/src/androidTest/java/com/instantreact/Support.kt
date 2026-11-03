package com.instantreact

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.Image
import android.util.DisplayMetrics
import android.view.Display
import com.instantreact.core.DetectorConfig

fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (cond()) return true
        Thread.sleep(5)
    }
    return cond()
}

@Suppress("DEPRECATION")
fun realScreen(ctx: Context): DisplayMetrics {
    val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val m = DisplayMetrics()
    dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
    return m
}

/** Disposición 3x2 de seis círculos para un tamaño de pantalla. */
class Layout6(val w: Int, val h: Int) {
    val radius = minOf(w, h) * 7 / 100
    val xs = IntArray(6) { w * (it % 3 + 1) / 4 }
    val ys = IntArray(6) { h * (4 + 2 * (it / 3)) / 10 }

    fun config() = DetectorConfig(
        xs = xs.copyOf(), ys = ys.copyOf(), radius = radius * 6 / 10, blueThreshold = 60,
        calibrated = true, calibW = w, calibH = h,
    )

    /** Rellena un Image RGBA_8888 (buffer R,G,B,A) con fondo oscuro, círculos grises y los azules indicados. */
    fun fill(image: Image, blue: Set<Int>) {
        val plane = image.planes[0]
        val rs = plane.rowStride
        val bytes = ByteArray(rs * h)
        for (y in 0 until h) for (x in 0 until w) put(bytes, y * rs + x * 4, 12, 14, 18)
        for (i in 0 until 6) {
            val (r, g, b) = if (i in blue) Triple(40, 120, 255) else Triple(128, 128, 128)
            for (y in maxOf(0, ys[i] - radius)..minOf(h - 1, ys[i] + radius)) {
                for (x in maxOf(0, xs[i] - radius)..minOf(w - 1, xs[i] + radius)) {
                    val dx = x - xs[i]; val dy = y - ys[i]
                    if (dx * dx + dy * dy <= radius * radius) put(bytes, y * rs + x * 4, r, g, b)
                }
            }
        }
        plane.buffer.put(bytes, 0, minOf(bytes.size, plane.buffer.remaining()))
    }

    private fun put(a: ByteArray, i: Int, r: Int, g: Int, b: Int) {
        a[i] = r.toByte(); a[i + 1] = g.toByte(); a[i + 2] = b.toByte(); a[i + 3] = 0xFF.toByte()
    }
}
