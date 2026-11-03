package com.instantreact.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/** Ejecuta toques reales mediante el servicio de accesibilidad. Las gestures se cachean (6 posiciones). */
object TapExecutor {
    private val cache = HashMap<Long, GestureDescription>()

    interface Result {
        fun onCompleted()
        fun onFailed()
    }

    private fun gesture(x: Int, y: Int, durationMs: Long): GestureDescription = synchronized(cache) {
        val key = (x.toLong() shl 40) or (y.toLong() shl 16) or durationMs
        cache.getOrPut(key) {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            path.lineTo(x.toFloat(), y.toFloat())
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L)))
                .build()
        }
    }

    /** Precalienta las gestures de las 6 posiciones para no construirlas durante la reacción. */
    fun warmUp(xs: IntArray, ys: IntArray, durationMs: Long) {
        synchronized(cache) { cache.clear() }
        for (i in xs.indices) gesture(xs[i], ys[i], durationMs)
    }

    /** @return false si el servicio no está activo o el sistema rechaza el gesto. */
    fun tap(x: Int, y: Int, durationMs: Long, result: Result?): Boolean {
        val svc = ReactAccessibilityService.instance ?: return false
        val g = gesture(x, y, durationMs)
        return svc.dispatchGesture(
            g,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result?.onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result?.onFailed()
                }
            },
            null,
        )
    }
}
