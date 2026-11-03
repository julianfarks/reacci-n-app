package com.instantreact

import android.content.Intent
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.ImageWriter
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.instantreact.core.FrameProcessor
import com.instantreact.core.TriggerListener
import com.instantreact.service.ReactAccessibilityService
import com.instantreact.service.TapExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Requiere el servicio de accesibilidad activado (el flujo de CI lo activa con `adb settings`).
 * Verifica que una pulsación enviada con TapExecutor llega de verdad a la pantalla, y la cadena
 * completa fotograma -> detección -> pulsación (sin el diálogo de MediaProjection, que exige un humano).
 */
@RunWith(AndroidJUnit4::class)
class TapInstrumentedTest {

    private fun launchTarget(): TapTargetActivity {
        val inst = InstrumentationRegistry.getInstrumentation()
        val i = Intent().setClassName(inst.targetContext.packageName, TapTargetActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val act = inst.startActivitySync(i) as TapTargetActivity
        inst.waitForIdleSync()
        Thread.sleep(800)
        return act
    }

    @Test
    fun accessibilityTapReachesTheScreenAtExactCoordinates() {
        assertTrue(
            "El servicio de accesibilidad no está activo",
            waitFor(40_000) { ReactAccessibilityService.instance != null },
        )
        val act = launchTarget()
        val x = 321
        val y = 654
        val t0 = SystemClock.elapsedRealtime()
        assertTrue("dispatchGesture rechazado", TapExecutor.tap(x, y, 10, null))
        assertTrue("el toque no llegó", waitFor(4000) { act.touches.isNotEmpty() })
        val t = act.touches[0]
        println("IR_METRIC tap dispatch->touch = ${t.timeMs - t0} ms")
        assertEquals(x.toFloat(), t.x, 3f)
        assertEquals(y.toFloat(), t.y, 3f)
        assertEquals("un solo toque (sin duplicados)", 1, act.touches.size)
        act.finish()
    }

    @Test
    fun fullChainFrameToDetectionToTap() {
        assertTrue(
            "El servicio de accesibilidad no está activo",
            waitFor(40_000) { ReactAccessibilityService.instance != null },
        )
        val act = launchTarget()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val m = realScreen(ctx)
        val layout = Layout6(m.widthPixels, m.heightPixels)
        val processor = FrameProcessor(layout.config())

        val thread = HandlerThread("chain-test").also { it.start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(layout.w, layout.h, PixelFormat.RGBA_8888, 3)
        val writer = ImageWriter.newInstance(reader.surface, 3)
        val frames = AtomicInteger()
        val triggerNs = AtomicLong()
        val taps = AtomicInteger()
        val target = 4

        val listener = TriggerListener { i ->
            triggerNs.set(SystemClock.elapsedRealtimeNanos())
            if (TapExecutor.tap(processor.tapX[i], processor.tapY[i], 10, null)) taps.incrementAndGet()
        }
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage()
            if (img != null) {
                val p = img.planes[0]
                processor.process(p.buffer, p.rowStride, p.pixelStride, img.width, img.height, true, listener)
                img.close()
                frames.incrementAndGet()
            }
        }, handler)

        fun push(blue: Set<Int>) {
            val before = frames.get()
            val img = writer.dequeueInputImage()
            layout.fill(img, blue)
            writer.queueInputImage(img)
            assertTrue(waitFor(3000) { frames.get() > before })
        }

        try {
            push(emptySet()); push(emptySet())
            assertEquals(0, taps.get())
            val tPush = SystemClock.elapsedRealtime()
            push(setOf(target))
            repeat(5) { push(setOf(target)) } // sigue azul varios fotogramas
            assertTrue("el toque no llegó", waitFor(4000) { act.touches.isNotEmpty() })
            val t = act.touches[0]
            println("IR_METRIC cadena completa fotograma->toque en pantalla = ${t.timeMs - tPush} ms")
            assertEquals(layout.xs[target].toFloat(), t.x, 3f)
            assertEquals(layout.ys[target].toFloat(), t.y, 3f)
            Thread.sleep(300)
            assertEquals("sin pulsaciones duplicadas", 1, act.touches.size)
            assertEquals(1, taps.get())
            assertNotNull(act)
        } finally {
            reader.setOnImageAvailableListener(null, null)
            writer.close(); reader.close(); thread.quitSafely()
            act.finish()
        }
    }
}
