package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleFinderTest {
    private val step = 4

    @Test fun findsSixGrayCirclesOnSeveralResolutions() {
        for ((w, h) in listOf(720 to 1280, 1080 to 2400, 1440 to 3200, 2400 to 1080)) {
            val g = GameLayout(w, h)
            val s = TestScreen(w, h); g.draw(s)
            val (px, gw, gh) = s.downsample(step)
            val found = CircleFinder.find(px, gw, gh)
            assertEquals("$w x $h", 6, found.size)
            for (i in 0 until 6) {
                val fx = found[i].x * step + step / 2
                val fy = found[i].y * step + step / 2
                assertTrue("x $i $w x $h: $fx vs ${g.xs[i]}", Math.abs(fx - g.xs[i]) <= step)
                assertTrue("y $i $w x $h: $fy vs ${g.ys[i]}", Math.abs(fy - g.ys[i]) <= step)
                val fr = found[i].radius * step
                assertTrue("radio $i: $fr vs ${g.radius}", Math.abs(fr - g.radius) <= step * 2)
            }
        }
    }

    @Test fun ignoresDistractors() {
        val w = 1080; val h = 2400
        val g = GameLayout(w, h)
        val s = TestScreen(w, h); g.draw(s)
        s.circle(540, 200, 60, 40, 120, 255)      // círculo azul decorativo
        s.rect(100, 2000, 980, 2060, 128, 128, 128) // barra gris (no circular)
        val (px, gw, gh) = s.downsample(step)
        val found = CircleFinder.find(px, gw, gh)
        assertEquals(6, found.size)
    }

    @Test fun returnsFewerWhenNotAllVisible() {
        val g = GameLayout(1080, 2400)
        val s = TestScreen(1080, 2400)
        for (i in 0 until 4) s.circle(g.xs[i], g.ys[i], g.radius, 128, 128, 128)
        val (px, gw, gh) = s.downsample(step)
        assertEquals(4, CircleFinder.find(px, gw, gh).size)
    }

    @Test fun readingOrderIsRowsThenColumns() {
        val g = GameLayout(1080, 2400)
        val s = TestScreen(1080, 2400); g.draw(s)
        val (px, gw, gh) = s.downsample(step)
        val found = CircleFinder.find(px, gw, gh)
        assertTrue(found[0].x < found[1].x && found[1].x < found[2].x)
        assertTrue(found[3].x < found[4].x && found[4].x < found[5].x)
        assertTrue(found[0].y < found[3].y)
    }
}
