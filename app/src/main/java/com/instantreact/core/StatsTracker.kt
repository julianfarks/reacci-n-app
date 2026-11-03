package com.instantreact.core

data class StatsSnapshot(
    val detected: Int,
    val taps: Int,
    val failed: Int,
    val meanReactionMs: Double,
    val bestReactionMs: Double,
    val lastReactionMs: Double,
    val lastSystemLatencyMs: Double,
    val lastConfiguredDelayMs: Long,
    val meanSystemLatencyMs: Double,
    val lastTapCompletedMs: Double,
    /** Índice (0-based) del último punto que se iluminó y se pulsó, o -1. */
    val lastPoint: Int = -1,
) {
    /** Porcentaje de estímulos detectados que acabaron en pulsación correcta. */
    val successPercent: Double
        get() = if (detected == 0) 0.0 else (detected - failed).coerceAtLeast(0) * 100.0 / detected
}

/**
 * Estadísticas. Definiciones:
 *  - Reacción = tiempo desde que se recibe el fotograma con el círculo azul hasta que se envía el toque
 *    (incluye el retraso configurado).
 *  - Latencia del sistema = reacción menos el retraso configurado (lo que realmente cuesta detectar y despachar).
 */
class StatsTracker {
    private val lock = Any()
    private var detected = 0
    private var taps = 0
    private var failed = 0
    private var sumReaction = 0.0
    private var sumSystem = 0.0
    private var best = Double.MAX_VALUE
    private var lastReaction = 0.0
    private var lastSystem = 0.0
    private var lastDelay = 0L
    private var lastCompleted = 0.0
    private var lastPoint = -1

    fun onDetected() = synchronized(lock) { detected++ }

    fun onDispatched(reactionMs: Double, systemMs: Double, configuredDelayMs: Long, point: Int = -1) = synchronized(lock) {
        lastPoint = point
        taps++
        sumReaction += reactionMs
        sumSystem += systemMs
        if (reactionMs < best) best = reactionMs
        lastReaction = reactionMs
        lastSystem = systemMs
        lastDelay = configuredDelayMs
    }

    fun onTapCompleted(totalMs: Double) = synchronized(lock) { lastCompleted = totalMs }

    /** Detección que no llegó a pulsar (servicio de accesibilidad ausente, gesto rechazado o cancelado). */
    fun onFailed() = synchronized(lock) { failed++ }

    /** Un gesto ya contado como pulsación fue cancelado por el sistema. */
    fun onGestureCancelled() = synchronized(lock) {
        if (taps > 0) taps--
        failed++
    }

    fun reset() = synchronized(lock) {
        detected = 0; taps = 0; failed = 0
        sumReaction = 0.0; sumSystem = 0.0; best = Double.MAX_VALUE
        lastReaction = 0.0; lastSystem = 0.0; lastDelay = 0L; lastCompleted = 0.0; lastPoint = -1
    }

    fun snapshot(): StatsSnapshot = synchronized(lock) {
        StatsSnapshot(
            detected = detected,
            taps = taps,
            failed = failed,
            meanReactionMs = if (taps == 0) 0.0 else sumReaction / taps,
            bestReactionMs = if (taps == 0) 0.0 else best,
            lastReactionMs = lastReaction,
            lastSystemLatencyMs = lastSystem,
            lastConfiguredDelayMs = lastDelay,
            meanSystemLatencyMs = if (taps == 0) 0.0 else sumSystem / taps,
            lastTapCompletedMs = lastCompleted,
            lastPoint = lastPoint,
        )
    }
}
