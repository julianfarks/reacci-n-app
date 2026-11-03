package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colores medidos en la captura real del juego: círculo (11,12,16) sobre fondo (4,5,10). */
class PointCheckAndFinderTest {
    private val step = 4
    private val dark = Triple(11, 12, 16)

    private fun darkGame(n: Int, w: Int = 1116, h: Int = 2480): Pair<GameLayout, TestScreen> {
        val (xs, ys) = DefaultLayout.positions(n, w, h)
        val g = GameLayout(w, h, n, xs, ys)
        val s = TestScreen(w, h); s.fill(4, 5, 10)
        g.draw(s, gray = dark)
        return g to s
    }

    @Test fun autoDetectFindsVeryDarkCirclesOfTheRealGame() {
        for (n in listOf(8, 9, 6, 4)) {
            val (g, s) = darkGame(n)
            val (px, gw, gh) = s.downsample(step)
            val found = CircleFinder.find(px, gw, gh, n)
            assertEquals("n=$n", n, found.size)
            for (f in found) {
                val fx = f.x * step + step / 2; val fy = f.y * step + step / 2
                assertTrue("($fx,$fy) no coincide", (0 until n).any { Math.abs(g.xs[it] - fx) <= step * 2 && Math.abs(g.ys[it] - fy) <= step * 2 })
            }
        }
    }

    @Test fun markersOnCirclesAreOkAndOnBackgroundAreBad() {
        val (g, s) = darkGame(8)
        val (px, gw, gh) = s.downsample(step)
        for (i in 0 until 8) {
            assertEquals("punto $i", PointState.GRAY_OK, PointCheck.evaluate(px, gw, gh, step, g.xs[i], g.ys[i], 40))
        }
        assertEquals(PointState.BAD, PointCheck.evaluate(px, gw, gh, step, 558, 1527, 40)) // hueco del centro: fondo
        assertEquals(PointState.BAD, PointCheck.evaluate(px, gw, gh, step, 100, 300, 40))   // fondo
    }

    @Test fun markerNearEdgeOfCircleOrTooBigAreaIsStillJudgedByRegion() {
        val (g, s) = darkGame(8)
        val (px, gw, gh) = s.downsample(step)
        // área excesiva: desborda el círculo y el color dominante pasa a ser el fondo
        assertEquals(PointState.BAD, PointCheck.evaluate(px, gw, gh, step, g.xs[0], g.ys[0], 400))
    }

    @Test fun litBluePointIsRecognised() {
        val (g, s) = darkGame(8)
        s.circle(g.xs[3], g.ys[3], g.radius, 40, 120, 255)
        val (px, gw, gh) = s.downsample(step)
        assertEquals(PointState.BLUE_OK, PointCheck.evaluate(px, gw, gh, step, g.xs[3], g.ys[3], 40))
    }

    @Test fun textCrossingACircleDoesNotBreakTheCheck() {
        val (g, s) = darkGame(8)
        s.rect(g.xs[0] - g.radius, g.ys[0] + g.radius / 2, g.xs[0] + g.radius, g.ys[0] + g.radius / 2 + 20, 200, 200, 200)
        val (px, gw, gh) = s.downsample(step)
        assertEquals(PointState.GRAY_OK, PointCheck.evaluate(px, gw, gh, step, g.xs[0], g.ys[0], 40))
    }

    @Test fun outOfScreenMarkerIsBad() {
        val (_, s) = darkGame(8)
        val (px, gw, gh) = s.downsample(step)
        assertEquals(PointState.BAD, PointCheck.evaluate(px, gw, gh, step, -50, 10, 20))
        assertEquals(PointState.BAD, PointCheck.evaluate(px, gw, gh, step, 5000, 10, 20))
    }

    @Test fun darkGameBlueDetectionStillWorksWithDefaultEight() {
        val (g, s) = darkGame(8)
        val cfg = DetectorConfig(g.xs, g.ys, IntArray(8) { 50 }, 60, calibrated = true, calibW = 1116, calibH = 2480)
        val p = FrameProcessor(cfg)
        val fired = ArrayList<Int>()
        val l = TriggerListener { fired.add(it) }
        p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, true, l)
        val lit = TestScreen(1116, 2480); lit.fill(4, 5, 10); g.draw(lit, blue = setOf(6), gray = dark)
        p.process(lit.buf, lit.rowStride, lit.pixelStride, lit.w, lit.h, true, l)
        assertEquals(listOf(6), fired)
    }
}
