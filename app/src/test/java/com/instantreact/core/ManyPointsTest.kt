package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Detección independiente con cualquier cantidad y cualquier disposición de puntos. */
class ManyPointsTest {
    private class Collector : TriggerListener {
        val fired = ArrayList<Int>()
        override fun onTrigger(index: Int) { fired.add(index) }
    }

    private fun run(p: FrameProcessor, s: TestScreen, c: Collector, fire: Boolean = true) =
        p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, fire, c)

    @Test fun eachCountDetectsEveryPointIndependently() {
        for (n in listOf(2, 3, 4, 5, 6, 8, 10, 12, 16, 24)) {
            val g = GameLayout(1080, 2400, n)
            for (k in 0 until n) {
                val p = FrameProcessor(g.config())
                val c = Collector()
                val gray = TestScreen(1080, 2400); g.draw(gray)
                val blue = TestScreen(1080, 2400); g.draw(blue, blue = setOf(k))
                run(p, gray, c); run(p, blue, c)
                assertEquals("n=$n punto $k", listOf(k), c.fired)
                repeat(5) { run(p, blue, c) }
                assertEquals("sin duplicados n=$n punto $k", listOf(k), c.fired)
            }
        }
    }

    @Test fun arbitraryNonGridLayoutsWork() {
        val rnd = Random(7)
        for (trial in 0 until 5) {
            val n = 3 + rnd.nextInt(10)
            val w = 1080; val h = 2400
            val r = 40
            val xs = IntArray(n); val ys = IntArray(n)
            var i = 0
            while (i < n) { // posiciones aleatorias sin solaparse
                val x = 100 + rnd.nextInt(w - 200); val y = 300 + rnd.nextInt(h - 600)
                if ((0 until i).all { Math.hypot((xs[it] - x).toDouble(), (ys[it] - y).toDouble()) > r * 3 }) { xs[i] = x; ys[i] = y; i++ }
            }
            val g = GameLayout(w, h, n, xs, ys)
            val cfg = DetectorConfig(xs, ys, IntArray(n) { 25 }, 60, calibrated = true, calibW = w, calibH = h)
            val p = FrameProcessor(cfg); val c = Collector()
            val gray = TestScreen(w, h); g.draw(gray)
            run(p, gray, c)
            val lit = (0 until n).filter { rnd.nextBoolean() }.toSet()
            val blue = TestScreen(w, h); g.draw(blue, blue = lit)
            run(p, blue, c)
            assertEquals("trial $trial", lit.toList().sorted(), c.fired.sorted())
        }
    }

    @Test fun perPointRadiusIsHonoured() {
        val g = GameLayout(1080, 2400, 3)
        val cfg = DetectorConfig(g.xs, g.ys, intArrayOf(10, 25, 40), 60, calibrated = true, calibW = 1080, calibH = 2400)
        val p = FrameProcessor(cfg)
        val s = TestScreen(1080, 2400); g.draw(s)
        // el centro del punto 2 se vuelve azul solo en un disco pequeño (radio 8): radio de detección 10 lo ve, 40 no
        s.circle(g.xs[0], g.ys[0], 8, 40, 120, 255)
        s.circle(g.xs[2], g.ys[2], 8, 40, 120, 255)
        val c = Collector()
        run(p, s, c, fire = false)
        assertTrue("radio 10 ve el azul", p.lastBlue[0])
        assertTrue("radio 40 no lo ve", !p.lastBlue[2])
    }

    @Test fun changingPointCountOnTheFlyKeepsWorking() {
        val g8 = GameLayout(1080, 2400, 8)
        val p = FrameProcessor(g8.config())
        val c = Collector()
        val gray8 = TestScreen(1080, 2400); g8.draw(gray8)
        run(p, gray8, c)
        val g12 = GameLayout(1080, 2400, 12)
        p.updateConfig(g12.config())
        assertEquals(12, p.count)
        val gray12 = TestScreen(1080, 2400); g12.draw(gray12)
        run(p, gray12, c)
        val blue12 = TestScreen(1080, 2400); g12.draw(blue12, blue = setOf(11))
        run(p, blue12, c)
        assertEquals(listOf(11), c.fired)
        p.updateConfig(g8.config())
        assertEquals(8, p.count)
    }

    @Test fun twoHundredPointsStayFast() {
        val g = GameLayout(1080, 2400, 200, IntArray(200) { 60 + (it % 10) * 100 }, IntArray(200) { 100 + (it / 10) * 100 })
        val p = FrameProcessor(g.config(detectRadius = 20))
        val s = TestScreen(1080, 2400); g.draw(s, blue = setOf(5, 150))
        val c = Collector()
        repeat(500) { run(p, s, c) }
        val t0 = System.nanoTime()
        repeat(1000) { run(p, s, c) }
        val us = (System.nanoTime() - t0) / 1000.0 / 1000
        println("200 puntos: %.1f us por fotograma".format(us))
        assertTrue("200 puntos demasiado lentos: $us us", us < 5000)
    }
}
