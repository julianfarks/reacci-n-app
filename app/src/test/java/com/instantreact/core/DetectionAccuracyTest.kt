package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Pruebas de PRECISIÓN del detector con imágenes sintéticas de círculos grises y azules, a varias
 * resoluciones. Cada prueba cuenta exactamente las pulsaciones y a qué punto van dirigidas.
 */
class DetectionAccuracyTest {
    private val W = 1080
    private val H = 2400

    private class Rig(val g: GameLayout, cfg: DetectorConfig, val w: Int, val h: Int) {
        val p = FrameProcessor(cfg)
        val fired = ArrayList<Int>()
        private val l = TriggerListener { fired.add(it) }
        fun feed(s: TestScreen, fire: Boolean = true) = p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, fire, l)
        fun gray(level: Int = 128, times: Int = 3) { repeat(times) { feed(screen { g.draw(it, grayLevel = level) }) } }
        fun screen(draw: (TestScreen) -> Unit) = TestScreen(w, h).also(draw)
        fun blue(vararg pts: Int) = screen { g.draw(it, blue = pts.toSet()) }
    }

    private fun rig(n: Int = 8, w: Int = W, h: Int = H): Rig {
        val g = GameLayout(w, h, n)
        return Rig(g, g.config(), w, h)
    }

    @Test fun eightGrayCirclesZeroTaps() {
        for (level in listOf(20, 60, 100, 128, 170, 220, 250)) {
            val r = rig()
            r.gray(level, 200)
            assertTrue("gris $level", r.fired.isEmpty())
        }
        // gris oscuro del juego real (≈11,12,16) sobre fondo casi negro
        val g = GameLayout(W, H, 8)
        val r = Rig(g, g.config(), W, H)
        repeat(100) { r.feed(TestScreen(W, H).also { s -> s.fill(4, 5, 10); g.draw(s, gray = Triple(11, 12, 16)) }) }
        assertTrue(r.fired.isEmpty())
    }

    @Test fun oneBlueCircleExactlyOneTapOnThatPoint() {
        for ((w, h) in listOf(720 to 1280, 1080 to 2400, 1440 to 3200, 2400 to 1080)) {
            for (k in 0 until 8) {
                val r = rig(8, w, h)
                r.gray()
                r.feed(r.blue(k))
                assertEquals("$w x $h punto ${k + 1}", listOf(k), r.fired)
                assertEquals(k, r.p.debug.lastChosen)
            }
        }
    }

    @Test fun staysBlueNoExtraTaps() {
        val r = rig()
        r.gray()
        repeat(300) { r.feed(r.blue(3)) }
        assertEquals(listOf(3), r.fired)
    }

    @Test fun backToGrayThenBlueGivesOneNewTap() {
        val r = rig()
        r.gray()
        r.feed(r.blue(5)); r.feed(r.blue(5))
        r.gray(times = 3)
        r.feed(r.blue(5)); r.feed(r.blue(5))
        assertEquals(listOf(5, 5), r.fired)
    }

    @Test fun brightnessChangesNeverFire() {
        val r = rig()
        // la pantalla entera sube y baja de brillo (grises de 30 a 245 y vuelta), también con tinte frío leve
        for (cycle in 0 until 3) {
            for (lv in (30..245 step 5) + (245 downTo 30 step 5)) r.gray(lv, 1)
        }
        for (lv in 40..230 step 10) r.feed(r.screen { s -> r.g.draw(s, gray = Triple(lv, lv + 3, lv + 10)) })
        // sombra: oscurecimiento progresivo de unos círculos y no de otros
        for (k in 0 until 8) {
            val lv = 128 - k * 12
            r.feed(r.screen { s -> for (i in 0 until 8) s.circle(r.g.xs[i], r.g.ys[i], r.g.radius, if (i % 2 == 0) lv else 128, if (i % 2 == 0) lv else 128, if (i % 2 == 0) lv else 128) })
        }
        assertTrue("brillo: ${r.fired}", r.fired.isEmpty())
    }

    @Test fun randomGraysAcrossCirclesNeverFire() {
        val rnd = Random(3)
        val r = rig()
        repeat(500) {
            r.feed(r.screen { s ->
                for (i in 0 until 8) { val v = 40 + rnd.nextInt(190); s.circle(r.g.xs[i], r.g.ys[i], r.g.radius, v, v, v) }
            })
        }
        assertTrue(r.fired.isEmpty())
    }

    @Test fun circleAlreadyBlueAtStartNeverFires() {
        val r = rig()
        repeat(50) { r.feed(r.blue(2)) }
        assertTrue(r.fired.isEmpty())
        // pulsar INICIAR con el círculo ya azul: solo un cambio nuevo cuenta
        val r2 = rig()
        r2.gray()
        r2.feed(r2.blue(4), fire = false)      // detenido: el azul se consume sin pulsar
        r2.feed(r2.blue(4))                    // se pulsa INICIAR: sigue azul
        assertTrue(r2.fired.isEmpty())
        r2.gray(); r2.feed(r2.blue(4))
        assertEquals(listOf(4), r2.fired)
    }

    @Test fun simultaneousCandidatesAreRejected() {
        val r = rig()
        r.gray()
        r.feed(r.blue(0, 3))
        assertTrue(r.fired.isEmpty())
        assertEquals(Reject.MULTI, r.p.debug.reject[0])
        assertEquals(Reject.MULTI, r.p.debug.reject[3])
        // y siguen bloqueados mientras sigan azules
        repeat(10) { r.feed(r.blue(0, 3)) }
        assertTrue(r.fired.isEmpty())
        // un estímulo posterior y único en otro punto sí se pulsa
        r.feed(r.blue(0, 3, 6))
        assertTrue("el bloqueo de 0 y 3 no impide 6 solo si es candidato único", r.fired == listOf(6))
    }

    @Test fun globalScreenChangesDoNotFire() {
        val r = rig()
        r.gray()
        r.feed(r.screen { it.fill(40, 120, 255) })                           // pantalla azul completa
        r.feed(r.blue(0, 1, 2, 3, 4, 5, 6, 7))                               // todos azules
        r.gray(times = 3)
        r.feed(r.screen { it.fill(250, 250, 250) })                          // destello blanco
        r.gray(times = 3)
        r.feed(r.screen { s -> for (i in 0 until 8) s.circle(r.g.xs[i], r.g.ys[i], r.g.radius, 200, 40, 40) }) // transición roja
        assertTrue("global: ${r.fired}", r.fired.isEmpty())
    }

    @Test fun partialOrBorderBlueDoesNotFire() {
        val r = rig()
        r.gray()
        val cx = r.g.xs[1]; val cy = r.g.ys[1]; val rad = r.g.radius
        // un solo píxel / una mancha pequeña azul en el área
        r.feed(r.screen { s -> r.g.draw(s); s.rect(cx, cy, cx + 3, cy + 3, 40, 120, 255) })
        // franja azul que cubre la mitad superior del círculo (elemento de interfaz)
        r.feed(r.screen { s -> r.g.draw(s); s.rect(cx - rad, cy - rad, cx + rad, cy, 40, 120, 255) })
        // solo el borde: anillo azul entre el 75 % y el 100 % del radio
        r.feed(r.screen { s ->
            r.g.draw(s); s.circle(cx, cy, rad, 40, 120, 255); s.circle(cx, cy, rad * 70 / 100, 128, 128, 128)
        })
        // salpicaduras de ruido azul en el 10 % de la zona
        val rnd = Random(9)
        r.feed(r.screen { s -> r.g.draw(s); repeat(900) { s.put(cx - rad + rnd.nextInt(2 * rad), cy - rad + rnd.nextInt(2 * rad), 40, 120, 255) } })
        assertTrue("parcial: ${r.fired}", r.fired.isEmpty())
    }

    @Test fun otherColorsAreNotBlue() {
        val colors = listOf(Triple(220, 40, 40), Triple(40, 200, 80), Triple(240, 220, 40), Triple(150, 60, 220), Triple(230, 130, 30), Triple(0, 220, 200))
        for (c in colors) {
            val r = rig()
            r.gray()
            r.feed(r.screen { s -> r.g.draw(s); s.circle(r.g.xs[4], r.g.ys[4], r.g.radius, c.first, c.second, c.third) })
            assertTrue("color $c", r.fired.isEmpty())
        }
    }

    @Test fun gradualAnimationToBlueFiresOnceAtTheBlueFrame() {
        val r = rig()
        r.gray()
        val steps = listOf(Triple(110, 120, 140), Triple(100, 118, 150), Triple(40, 120, 255))
        for ((i, c) in steps.withIndex()) {
            r.feed(r.screen { s -> r.g.draw(s); s.circle(r.g.xs[2], r.g.ys[2], r.g.radius, c.first, c.second, c.third) })
            if (i < steps.lastIndex) assertTrue("fotograma intermedio $i no dispara", r.fired.isEmpty())
        }
        repeat(20) { r.feed(r.blue(2)) }
        assertEquals(listOf(2), r.fired)
    }

    @Test fun longAnimationLosesBaseline() {
        val r = rig()
        r.gray()
        repeat(10) { r.feed(r.screen { s -> r.g.draw(s); s.circle(r.g.xs[0], r.g.ys[0], r.g.radius, 230, 150, 30) }) }
        r.feed(r.blue(0))
        assertTrue(r.fired.isEmpty())
        assertEquals(Reject.NOT_ARMED, r.p.debug.reject[0])
    }

    @Test fun debugStatesFollowTheLifecycle() {
        val r = rig()
        val d = r.p.debug
        assertEquals(PointStatus.WAIT, d.status[3])
        r.gray()
        assertEquals(PointStatus.GRAY, d.status[3])
        r.feed(r.blue(3))
        assertEquals(PointStatus.DETECTED, d.status[3])
        assertTrue(d.realChange[3])
        r.p.markPressed(3, 12.5)
        assertEquals(PointStatus.PRESSED, d.status[3])
        assertEquals(12.5, d.detectToTapMs[3], 0.0)
        r.feed(r.blue(3))
        assertEquals(PointStatus.PRESSED, d.status[3])
        r.gray(times = 1)
        assertEquals("un fotograma gris no basta", PointStatus.PRESSED, d.status[3])
        r.gray(times = 1)
        assertEquals(PointStatus.GRAY, d.status[3])
        assertTrue(d.bluePct[3] == 0 && d.grayPct[3] == 100)
        r.feed(r.blue(3))
        assertTrue(d.bluePct[3] >= 85)
        assertTrue("HSV de azul ${d.hue[3]}", d.hue[3] in 190..255 && d.sat[3] > 60)
    }

    @Test fun anyCountAndLayoutPointsAreIndependent() {
        for (n in listOf(2, 3, 4, 5, 6, 8, 10, 12, 16, 24)) {
            val g = GameLayout(W, H, n)
            for (k in 0 until n) {
                val r = Rig(g, g.config(), W, H)
                r.gray()
                r.feed(r.blue(k))
                repeat(5) { r.feed(r.blue(k)) }
                assertEquals("n=$n punto $k", listOf(k), r.fired)
            }
        }
    }

    @Test fun rescalesAfterResolutionChange() {
        val calibrated = GameLayout(1080, 2400).config()
        val now = GameLayout(720, 1600)
        val p = FrameProcessor(calibrated)
        val fired = ArrayList<Int>()
        val l = TriggerListener { fired.add(it) }
        fun feed(s: TestScreen) = p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, true, l)
        feed(TestScreen(720, 1600).also { now.draw(it) })
        feed(TestScreen(720, 1600).also { now.draw(it, blue = setOf(2)) })
        assertEquals(listOf(2), fired)
        // orientación distinta: no se detecta ni se pulsa nada
        feed(TestScreen(1600, 720))
        assertTrue(p.orientationMismatch)
    }

    @Test fun perPointRadiusUsesInnerZone() {
        val g = GameLayout(W, H, 3)
        val cfg = DetectorConfig(g.xs, g.ys, intArrayOf(10, 25, 40), 60, calibrated = true, calibW = W, calibH = H)
        val p = FrameProcessor(cfg)
        val s = TestScreen(W, H); g.draw(s)
        s.circle(g.xs[0], g.ys[0], 8, 40, 120, 255)
        s.circle(g.xs[2], g.ys[2], 8, 40, 120, 255)
        p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, false, null)
        assertTrue("radio 10 ve el azul", p.lastBlue[0])
        assertTrue("radio 40 no lo ve (solo 8 px azules)", !p.lastBlue[2])
    }

    @Test fun twoHundredPointsStayFast() {
        val g = GameLayout(W, H, 200, IntArray(200) { 60 + (it % 10) * 100 }, IntArray(200) { 100 + (it / 10) * 100 })
        val p = FrameProcessor(g.config(detectRadius = 20))
        val s = TestScreen(W, H); g.draw(s, blue = setOf(5))
        repeat(500) { p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, true, null) }
        val t0 = System.nanoTime()
        repeat(1000) { p.process(s.buf, s.rowStride, s.pixelStride, s.w, s.h, true, null) }
        val us = (System.nanoTime() - t0) / 1000.0 / 1000
        println("IR_METRIC 200 puntos: %.1f us por fotograma".format(us))
        assertTrue("demasiado lento: $us us", us < 5000)
    }
}
