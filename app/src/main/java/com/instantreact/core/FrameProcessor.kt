package com.instantreact.core

import java.nio.ByteBuffer

fun interface TriggerListener {
    /** Se invoca en el hilo de captura inmediatamente al detectar el flanco gris -> azul. */
    fun onTrigger(index: Int)
}

/**
 * Procesa un fotograma: muestrea las 6 regiones configuradas, actualiza la máquina de estados y
 * avisa de los círculos que acaban de ponerse azules. No tiene dependencias de Android.
 */
class FrameProcessor(config: DetectorConfig) {
    @Volatile
    private var config: DetectorConfig = config

    private val machine = TriggerStateMachine(DetectorConfig.CIRCLE_COUNT)
    private val samples = Array(DetectorConfig.CIRCLE_COUNT) { SampleResult() }

    /** Último color medio (ARGB) de cada círculo, para vista previa en calibración. */
    val lastColors = IntArray(DetectorConfig.CIRCLE_COUNT)
    val lastBlue = BooleanArray(DetectorConfig.CIRCLE_COUNT)

    /** Coordenadas de pantalla (ya reescaladas a la resolución actual) para pulsar. */
    val tapX = IntArray(DetectorConfig.CIRCLE_COUNT)
    val tapY = IntArray(DetectorConfig.CIRCLE_COUNT)

    /** true si la orientación actual no coincide con la de la calibración (no se detecta). */
    @Volatile
    var orientationMismatch = false
        private set

    fun updateConfig(c: DetectorConfig) {
        config = c
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
        if (!cfg.calibrated) return 0

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
        val radius = maxOf(2, Math.round(cfg.radius * (sx + sy) / 2f))

        var fired = 0
        for (i in 0 until DetectorConfig.CIRCLE_COUNT) {
            val x = Math.round(cfg.xs[i] * sx)
            val y = Math.round(cfg.ys[i] * sy)
            tapX[i] = x
            tapY[i] = y
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
                // Parado: mantenemos el estado para no disparar al pulsar INICIAR con un círculo ya azul.
                machine.update(i, blue)
            }
        }
        return fired
    }
}
