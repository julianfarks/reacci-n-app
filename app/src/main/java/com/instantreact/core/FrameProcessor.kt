package com.instantreact.core

import java.nio.ByteBuffer

fun interface TriggerListener {
    /** Se invoca en el hilo de captura al CONFIRMAR el cambio gris -> azul del punto [index]. */
    fun onTrigger(index: Int)
}

/**
 * Procesa un fotograma. Cada punto tiene su PROPIO estado y se compara con su estado anterior:
 *
 *   ESPERA --(gris estable)--> GRIS(armado) --(azul confirmado)--> AZUL --(pulsación enviada)--> PULSADO
 *      ^                                                                                        |
 *      +------------------------ (vuelve a gris ≥ 2 fotogramas seguidos) ----------------------+
 *
 * Reglas que evitan las pulsaciones falsas:
 *  1. Solo se mira la zona interior del círculo y se exige que ≥ 85 % de ella sea azul HSV.
 *  2. Un punto solo puede disparar si antes estuvo en gris (armado). Al arrancar, o tras pulsar, queda
 *     en ESPERA hasta ver gris: un círculo que ya está azul, o que sigue azul, nunca dispara.
 *  3. Si en el mismo fotograma hay más de un candidato, se rechazan todos (no se pulsa a ciegas).
 *  4. Si cambia de color una gran parte de los puntos a la vez, se interpreta como cambio global de
 *     pantalla (transición, brillo, overlay) y no se pulsa.
 *  5. Un punto cuya zona pasa mucho tiempo en "otro color" (animación) pierde su base gris.
 * Sin dependencias de Android. Debe usarse siempre desde el mismo hilo (el de captura).
 */
class FrameProcessor(config: DetectorConfig) {
    @Volatile
    private var config: DetectorConfig = config

    private var samples = Array(config.count) { SampleResult() }
    private var armed = BooleanArray(config.count)
    private var grayStreak = IntArray(config.count)
    private var otherStreak = IntArray(config.count)
    private var needGray = IntArray(config.count) { 1 }
    private val candidates = IntArray(MAX_CAND)

    @Volatile
    var debug = PointDebug(config.count)
        private set

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
        samples = Array(n) { SampleResult() }
        armed = BooleanArray(n)
        grayStreak = IntArray(n)
        otherStreak = IntArray(n)
        needGray = IntArray(n) { 1 }
        lastColors = IntArray(n)
        lastBlue = BooleanArray(n)
        tapX = IntArray(n)
        tapY = IntArray(n)
        debug = PointDebug(n)
    }

    fun updateConfig(c: DetectorConfig) {
        val old = config
        config = c
        if (c.count != armed.size) realloc(c.count)
        else if (!old.xs.contentEquals(c.xs) || !old.ys.contentEquals(c.ys) || !old.radii.contentEquals(c.radii) ||
            old.calibW != c.calibW || old.calibH != c.calibH
        ) reset() // otras posiciones: toca volver a ver gris antes de armar
    }

    /** Todos los puntos vuelven a ESPERA: hasta ver gris no se dispara nada (p. ej. al pulsar INICIAR). */
    fun reset() {
        for (i in armed.indices) {
            armed[i] = false
            grayStreak[i] = 0
            otherStreak[i] = 0
            needGray[i] = 1
            debug.status[i] = PointStatus.WAIT
            debug.reject[i] = Reject.NONE
        }
    }

    /** La pulsación del punto [index] ya se envió: AZUL pasa a PULSADO. */
    fun markPressed(index: Int, detectToTapMs: Double) {
        val d = debug
        if (index < 0 || index >= d.n) return
        if (d.status[index] == PointStatus.DETECTED) d.status[index] = PointStatus.PRESSED
        d.detectToTapMs[index] = detectToTapMs
    }

    /**
     * @param fire si es false solo se actualiza el estado (modo parado / calibración): nunca se dispara.
     * @return número de disparos producidos (0 o 1 por fotograma).
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
        if (cfg.count != armed.size) realloc(cfg.count)

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
        val minSat = ColorClassifier.minSaturation(cfg.blueThreshold)
        val d = debug
        d.lastFrameW = width
        d.lastFrameH = height

        // 1) Muestreo independiente de cada punto.
        var nonGray = 0
        for (i in 0 until cfg.count) {
            val x = Math.round(cfg.xs[i] * sx)
            val y = Math.round(cfg.ys[i] * sy)
            tapX[i] = x
            tapY[i] = y
            val radius = maxOf(2, Math.round(cfg.radii[i] * scale))
            val s = samples[i]
            RegionSampler.sample(buf, rowStride, pixelStride, width, height, x, y, radius, minSat, s)
            val c = s.classify()
            if (c != ColorClassifier.GRAY) nonGray++
            lastColors[i] = s.color
            lastBlue[i] = c == ColorClassifier.BLUE
            d.cls[i] = c
            d.rgb[i] = s.color
            d.hue[i] = ColorClassifier.hue(s.r, s.g, s.b)
            d.sat[i] = ColorClassifier.saturation(s.r, s.g, s.b)
            d.value[i] = ColorClassifier.value(s.r, s.g, s.b)
            d.bluePct[i] = s.bluePct
            d.grayPct[i] = s.grayPct
            d.cx[i] = x
            d.cy[i] = y
            d.radius[i] = radius
            d.realChange[i] = false
        }

        // 2) Actualización del estado de cada punto frente a su estado anterior.
        var nCand = 0
        for (i in 0 until cfg.count) {
            when (d.cls[i]) {
                ColorClassifier.GRAY -> {
                    otherStreak[i] = 0
                    grayStreak[i]++
                    if (!armed[i] && grayStreak[i] >= needGray[i]) {
                        armed[i] = true
                        d.status[i] = PointStatus.GRAY
                    }
                    if (armed[i]) d.reject[i] = Reject.NONE
                }
                ColorClassifier.BLUE -> {
                    grayStreak[i] = 0
                    otherStreak[i] = 0
                    if (armed[i]) {
                        if (nCand < MAX_CAND) candidates[nCand++] = i
                    } else {
                        d.reject[i] = if (d.status[i] == PointStatus.WAIT && needGray[i] == 1) Reject.NOT_GRAY_BEFORE else Reject.NOT_ARMED
                    }
                }
                else -> {
                    grayStreak[i] = 0
                    otherStreak[i]++
                    if (armed[i] && otherStreak[i] >= OTHER_LIMIT) {
                        armed[i] = false
                        needGray[i] = 2
                        d.status[i] = PointStatus.WAIT
                        d.reject[i] = Reject.LOST_BASELINE
                    }
                }
            }
        }

        // 3) Decisión: exactamente un candidato y sin cambio global.
        if (nCand == 0) return 0
        val global = cfg.count >= GLOBAL_MIN_POINTS && nonGray * 2 > cfg.count
        val ok = nCand == 1 && !global && fire
        for (k in 0 until nCand) {
            val i = candidates[k]
            // Haya o no pulsación, este azul ya está "consumido": hasta volver a gris no se reevalúa.
            armed[i] = false
            needGray[i] = 2
            d.status[i] = PointStatus.WAIT
            d.reject[i] = when {
                global -> Reject.GLOBAL
                nCand > 1 -> Reject.MULTI
                else -> Reject.NONE
            }
        }
        if (!ok) return 0
        val chosen = candidates[0]
        d.status[chosen] = PointStatus.DETECTED
        d.realChange[chosen] = true
        d.lastChosen = chosen
        d.detectToTapMs[chosen] = -1.0
        listener?.onTrigger(chosen)
        return 1
    }

    companion object {
        private const val MAX_CAND = 64
        /** Fotogramas seguidos en "otro color" tras los que el punto pierde su base gris. */
        const val OTHER_LIMIT = 4
        /** Con al menos este número de puntos se activa la guarda de cambio global. */
        const val GLOBAL_MIN_POINTS = 4
    }
}
