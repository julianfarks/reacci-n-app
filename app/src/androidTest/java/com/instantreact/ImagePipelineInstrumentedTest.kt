package com.instantreact

import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.ImageWriter
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.instantreact.core.FrameProcessor
import com.instantreact.core.TriggerListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pasa fotogramas reales RGBA_8888 por un ImageReader (el mismo tipo de buffer que entrega MediaProjection)
 * y comprueba detección, ausencia de duplicados y latencia de procesado en el dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class ImagePipelineInstrumentedTest {

    @Test
    fun detectsGrayToBlueWithoutDuplicatesThroughRealImageReader() {
        val w = 720
        val h = 1280
        val layout = LayoutN(w, h)
        val processor = FrameProcessor(layout.config())
        val thread = HandlerThread("pipeline-test").also { it.start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        val writer = ImageWriter.newInstance(reader.surface, 3)

        val frames = AtomicInteger()
        val fired = CopyOnWriteArrayList<Int>()
        val firedAt = CopyOnWriteArrayList<Long>()
        val procMicros = CopyOnWriteArrayList<Long>()
        val listener = TriggerListener { i ->
            fired.add(i)
            firedAt.add(SystemClock.elapsedRealtimeNanos())
        }
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage()
            if (img != null) {
                val t0 = System.nanoTime()
                val p = img.planes[0]
                processor.process(p.buffer, p.rowStride, p.pixelStride, img.width, img.height, true, listener)
                procMicros.add((System.nanoTime() - t0) / 1000)
                img.close()
                frames.incrementAndGet()
            }
        }, handler)

        fun push(blue: Set<Int>) {
            val before = frames.get()
            val img = writer.dequeueInputImage()
            layout.fill(img, blue)
            writer.queueInputImage(img)
            assertTrue("fotograma no recibido", waitFor(3000) { frames.get() > before })
        }

        try {
            push(emptySet())
            push(emptySet())
            assertTrue("sin disparos en gris", fired.isEmpty())

            val tPush = SystemClock.elapsedRealtimeNanos()
            push(setOf(3))
            assertEquals(listOf(3), fired.toList())
            val endToEndMs = (firedAt[0] - tPush) / 1_000_000.0
            println("IR_METRIC pipeline push->trigger = %.1f ms".format(endToEndMs))

            repeat(10) { push(setOf(3)) } // sigue azul: no hay duplicados
            assertEquals(listOf(3), fired.toList())

            push(emptySet()); push(emptySet())   // vuelve a gris
            push(setOf(0))                       // nuevo estímulo
            push(emptySet()); push(emptySet())
            push(setOf(5))
            assertEquals(listOf(3, 0, 5), fired.toList())
            push(emptySet()); push(emptySet())
            push(setOf(1, 2))                    // dos "estímulos" a la vez: ambiguo, no se pulsa nada
            assertEquals(listOf(3, 0, 5), fired.toList())

            val avg = procMicros.average()
            println("IR_METRIC proceso medio por fotograma = %.0f us (%d fotogramas)".format(avg, procMicros.size))
            assertTrue("procesado lento: $avg us", avg < 20_000)
        } finally {
            reader.setOnImageAvailableListener(null, null)
            writer.close()
            reader.close()
            thread.quitSafely()
        }
    }
}
