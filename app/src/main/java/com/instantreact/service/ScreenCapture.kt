package com.instantreact.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.DisplayMetrics
import android.view.Display

/**
 * Captura de pantalla autorizada (MediaProjection) hacia un ImageReader RGBA_8888.
 * Se crea UN solo VirtualDisplay (Android 14 no permite reutilizar el permiso); al rotar se redimensiona.
 */
class ScreenCapture(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val handler: Handler,
    private val onFrame: (Image) -> Unit,
    private val onStopped: () -> Unit,
) {
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    var width = 0
        private set
    var height = 0
        private set
    private var dpi = 0

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            onStopped()
        }
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val metrics = DisplayMetrics()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics
    }

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ ir ->
            val image = try {
                ir.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) {
                try {
                    onFrame(image)
                } finally {
                    image.close()
                }
            }
        }, handler)
        return r
    }

    fun start() {
        val m = realMetrics()
        width = m.widthPixels
        height = m.heightPixels
        dpi = m.densityDpi
        projection.registerCallback(callback, handler)
        val r = newReader(width, height)
        reader = r
        display = projection.createVirtualDisplay(
            "InstantReact",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface,
            null,
            handler,
        )
    }

    /** Llamar tras un cambio de orientación/resolución. */
    fun resizeToDisplay() {
        val vd = display ?: return
        val m = realMetrics()
        if (m.widthPixels == width && m.heightPixels == height) return
        width = m.widthPixels
        height = m.heightPixels
        dpi = m.densityDpi
        val old = reader
        val r = newReader(width, height)
        reader = r
        vd.resize(width, height, dpi)
        vd.surface = r.surface
        old?.setOnImageAvailableListener(null, null)
        old?.close()
    }

    fun stop() {
        try {
            projection.unregisterCallback(callback)
        } catch (_: Exception) {
        }
        display?.release()
        display = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        try {
            projection.stop()
        } catch (_: Exception) {
        }
    }
}
