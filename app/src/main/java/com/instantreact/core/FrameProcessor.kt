package com.instantreact.core

import java.nio.ByteBuffer

fun interface TriggerListener {
    /** Se invoca en el hilo de captura inmediatamente al detectar el flanco gris -> azul del punto [index]. */
    fun onTrigger(index: Int)
}

/**
 * Procesa un fotograma: muestrea cada punto configurado de forma INDEPENDIENTE (sin suponer ninguna
 * disposición), actualiza su máquina de estados y avisa de los puntos que acaban de ponerse azules.
 * Sin dependencias de Android. Debe usarse siempre desde el mismo hilo (el de captura).
 */
class FrameProcessor(config: DetectorConfig) {
    @Volatile
    private var config: DetectorConfig = config

    private var machine = TriggerStateMachine(config.count)
    private var samples = Array(config.count) { SampleResult() }

    /** Último color medio (ARGB) de cada punto. */
    var lastColors = IntArray(config.count)
        private set
    var lastBlue = BooleanArray(config.count)
        private set

    /** Coordenadas de pantalla (ya reescaladas a la resolución actual) para pulsar cada punto. */
    var tapX = IntArray(config.count)
        private set
    var tapY = IntArray(config.count)
        private set

    /** true si la orientación actual no coincide con la de la calibración (no se detecta). */
    @Volatile
    var orientationMismatch = false
        private set

    val count: Int get() = config.count

    private fun realloc(n: Int) {
        machine = TriggerStateMachine(n)
        samples = Array(n) { SampleResult() }
        lastColors = IntArray(n)
        lastBlue = BooleanArray(n)
        tapX = IntArray(n)
        tapY = IntArray(n)
    }

    fun updateConfig(c: DetectorConfig) {
        config = c
        if (c.count != machine.size) realloc(c.count)
    }

    fun reset() = machine.reset()

    /**
     * @param fire si es false solo se actualizan colores (modo parado / calibración).
     * @return número de disparos producidos.
     */
    fun process(
        buf: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        fire: Boolean,
        listener: TriggerListener?,
    ): Int {
        val cfg = config
        if (!cfg.calibrated || cfg.count == 0) return 0
        if (cfg.count != machine.size) realloc(cfg.count)

        var sx = 1f
        var sy = 1f
        if (cfg.calibW > 0 && cfg.calibH > 0 && (cfg.calibW != width || cfg.calibH != height)) {
            val sameOrientation = (cfg.calibW >= cfg.calibH) == (width >= height)
            if (!sameOrientation) {
                orientationMismatch = true
                return 0
            }
            sx = width.toFloat() / cfg.calibW
            sy = height.toFloat() / cfg.calibH
        }
        orientationMismatch = false
        val scale = (sx + sy) / 2f

        var fired = 0
        for (i in 0 until cfg.count) {
            val x = Math.round(cfg.xs[i] * sx)
            val y = Math.round(cfg.ys[i] * sy)
            tapX[i] = x
            tapY[i] = y
            val radius = maxOf(2, Math.round(cfg.radii[i] * scale))
            val s = samples[i]
            RegionSampler.sample(buf, rowStride, pixelStride, width, height, x, y, radius, cfg.blueThreshold, s)
            val blue = s.isBlue
            lastColors[i] = s.color
            lastBlue[i] = blue
            if (fire) {
                if (machine.update(i, blue)) {
                    fired++
                    listener?.onTrigger(i)
                }
            } else {
                // Parado: mantenemos el estado para no disparar al pulsar INICIAR con un punto ya azul.
                machine.update(i, blue)
            }
        }
        return fired
    }
}
