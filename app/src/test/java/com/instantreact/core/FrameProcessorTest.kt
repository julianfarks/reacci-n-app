package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProcessorTest {
    private val resolutions = listOf(
        720 to 1280, 1080 to 1920, 1080 to 2400, 1440 to 3200, 2400 to 1080, 1920 to 1080, 1280 to 720,
    )

    private class Collector : TriggerListener {
        val fired = ArrayList<Int>()
        override fun onTrigger(index: Int) { fired.add(index) }
    }

    private fun run(p: FrameProcessor, s: TestScreen, c: Collector, fire: Boolean = true): Int =
        p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, fire, c)

    @Test fun detectsGraySixCirclesWithoutFiring() {
        for ((w, h) in resolutions) {
            val g = GameLayout(w, h)
            val p = FrameProcessor(g.config())
            val s = TestScreen(w, h); g.draw(s)
            val c = Collector()
            repeat(5) { run(p, s, c) }
            assertTrue("$w x $h sin disparos en gris", c.fired.isEmpty())
            for (i in 0 until 6) assertFalse("círculo $i gris en $w x $h", p.lastBlue[i])
        }
    }

    @Test fun detectsGrayToBlueOnEachOfTheSixCircles() {
        for ((w, h) in resolutions) {
            val g = GameLayout(w, h)
            for (k in 0 until 6) {
                val p = FrameProcessor(g.config())
                val c = Collector()
                val gray = TestScreen(w, h); g.draw(gray)
                run(p, gray, c)
                val blue = TestScreen(w, h); g.draw(blue, blue = setOf(k))
                run(p, blue, c)
                assertEquals("$w x $h círculo $k", listOf(k), c.fired)
            }
        }
    }

    @Test fun noDuplicateTapsWhileCircleStaysBlue() {
        val g = GameLayout(1080, 2400)
        val p = FrameProcessor(g.config())
        val c = Collector()
        val s = TestScreen(1080, 2400); g.draw(s, blue = setOf(4))
        repeat(120) { run(p, s, c) } // 2 s a 60 fps
        assertEquals(listOf(4), c.fired)
    }

    @Test fun detectsReturnToOriginalStateAndNextStimulus() {
        val g = GameLayout(1080, 2400)
        val p = FrameProcessor(g.config())
        val c = Collector()
        val gray = TestScreen(1080, 2400); g.draw(gray)
        val blue = TestScreen(1080, 2400); g.draw(blue, blue = setOf(1))
        run(p, gray, c); run(p, blue, c); run(p, blue, c); run(p, gray, c); run(p, blue, c)
        assertEquals(listOf(1, 1), c.fired)
    }

    @Test fun rapidChangesAreAllDetected() {
        val g = GameLayout(1080, 2400)
        val p = FrameProcessor(g.config())
        val c = Collector()
        val gray = TestScreen(1080, 2400); g.draw(gray)
        // un círculo distinto en cada fotograma consecutivo, sin pasar por gris entre medias
        for (k in 0 until 6) {
            val s = TestScreen(1080, 2400); g.draw(s, blue = setOf(k))
            run(p, s, c)
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), c.fired)
        // dos círculos azules en el mismo fotograma
        val p2 = FrameProcessor(g.config())
        val c2 = Collector()
        run(p2, gray, c2)
        val both = TestScreen(1080, 2400); g.draw(both, blue = setOf(0, 3))
        run(p2, both, c2)
        assertEquals(listOf(0, 3), c2.fired)
        // azul-gris-azul en fotogramas consecutivos en el mismo círculo
        val p3 = FrameProcessor(g.config())
        val c3 = Collector()
        val b = TestScreen(1080, 2400); g.draw(b, blue = setOf(2))
        run(p3, b, c3); run(p3, gray, c3); run(p3, b, c3)
        assertEquals(listOf(2, 2), c3.fired)
    }

    @Test fun toleratesBrightnessChanges() {
        val g = GameLayout(1080, 2400)
        for (scale in listOf(1.0, 0.8, 0.6)) {
            val p = FrameProcessor(g.config())
            val c = Collector()
            val gray = TestScreen(1080, 2400); g.draw(gray, grayLevel = (128 * scale).toInt())
            val blue = TestScreen(1080, 2400); g.draw(blue, blue = setOf(3), grayLevel = (128 * scale).toInt(), blueScale = scale)
            run(p, gray, c); run(p, blue, c)
            assertEquals("brillo $scale", listOf(3), c.fired)
        }
    }

    @Test fun worksWithPaddedRowStride() {
        val g = GameLayout(1080, 2400)
        val p = FrameProcessor(g.config())
        val c = Collector()
        val s = TestScreen(1080, 2400, padPixels = 24); g.draw(s, blue = setOf(5))
        run(p, s, c)
        assertEquals(listOf(5), c.fired)
    }

    @Test fun coordinatesAreExact() {
        for ((w, h) in resolutions) {
            val g = GameLayout(w, h)
            val p = FrameProcessor(g.config())
            val s = TestScreen(w, h); g.draw(s)
            run(p, s, Collector())
            for (i in 0 until 6) {
                assertEquals("x $i $w x $h", g.xs[i], p.tapX[i])
                assertEquals("y $i $w x $h", g.ys[i], p.tapY[i])
            }
        }
    }

    @Test fun rescalesWhenResolutionChanges() {
        // calibrado en 1080x2400, el juego se renderiza ahora en 720x1600
        val calibrated = GameLayout(1080, 2400).config()
        val now = GameLayout(720, 1600)
        val p = FrameProcessor(calibrated)
        val c = Collector()
        val gray = TestScreen(720, 1600); now.draw(gray)
        val blue = TestScreen(720, 1600); now.draw(blue, blue = setOf(2))
        run(p, gray, c); run(p, blue, c)
        assertEquals(listOf(2), c.fired)
        for (i in 0 until 6) {
            assertTrue("x $i", Math.abs(p.tapX[i] - now.xs[i]) <= 1)
            assertTrue("y $i", Math.abs(p.tapY[i] - now.ys[i]) <= 1)
        }
    }

    @Test fun orientationMismatchDoesNotFire() {
        val p = FrameProcessor(GameLayout(1080, 2400).config())
        val c = Collector()
        val land = TestScreen(2400, 1080); GameLayout(2400, 1080).draw(land, blue = setOf(0))
        assertEquals(0, run(p, land, c))
        assertTrue(p.orientationMismatch)
        assertTrue(c.fired.isEmpty())
    }

    @Test fun uncalibratedDoesNothing() {
        val p = FrameProcessor(DetectorConfig())
        val s = TestScreen(720, 1280)
        assertEquals(0, run(p, s, Collector()))
    }

    @Test fun stoppedModeDoesNotFireButKeepsState() {
        val g = GameLayout(1080, 2400)
        val p = FrameProcessor(g.config())
        val c = Collector()
        val blue = TestScreen(1080, 2400); g.draw(blue, blue = setOf(0))
        run(p, blue, c, fire = false)
        assertTrue(p.lastBlue[0])
        run(p, blue, c, fire = true) // ya estaba azul antes de INICIAR: no dispara
        assertTrue(c.fired.isEmpty())
    }

    @Test fun processingLatencyIsTiny() {
        val w = 2400; val h = 1080
        val g = GameLayout(w, h)
        val p = FrameProcessor(g.config())
        val s = TestScreen(w, h); g.draw(s, blue = setOf(1, 4))
        val c = Collector()
        repeat(3000) { run(p, s, c) } // calentamiento JIT
        val n = 5000
        val t0 = System.nanoTime()
        repeat(n) { run(p, s, c) }
        val avgUs = (System.nanoTime() - t0) / 1000.0 / n
        println("Latencia media de procesado de un fotograma ($w x $h, 6 regiones): %.2f us".format(avgUs))
        assertTrue("procesado demasiado lento: $avgUs us", avgUs < 1000.0)
    }
}
