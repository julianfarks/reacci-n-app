package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTrackerTest {
    @Test fun emptySnapshot() {
        val s = StatsTracker().snapshot()
        assertEquals(0, s.detected); assertEquals(0.0, s.meanReactionMs, 0.0)
        assertEquals(0.0, s.bestReactionMs, 0.0); assertEquals(0.0, s.successPercent, 0.0)
    }

    @Test fun meanBestAndPercentages() {
        val t = StatsTracker()
        repeat(4) { t.onDetected() }
        t.onDispatched(100.0, 70.0, 30)
        t.onDispatched(60.0, 30.0, 30)
        t.onDispatched(80.0, 50.0, 30)
        t.onFailed()
        val s = t.snapshot()
        assertEquals(4, s.detected); assertEquals(3, s.taps); assertEquals(1, s.failed)
        assertEquals(80.0, s.meanReactionMs, 1e-9); assertEquals(60.0, s.bestReactionMs, 1e-9)
        assertEquals(50.0, s.meanSystemLatencyMs, 1e-9)
        assertEquals(75.0, s.successPercent, 1e-9)
        assertEquals(80.0, s.lastReactionMs, 1e-9); assertEquals(30L, s.lastConfiguredDelayMs)
    }

    @Test fun cancelledGestureIsNotCountedAsTap() {
        val t = StatsTracker()
        t.onDetected(); t.onDispatched(50.0, 50.0, 0); t.onGestureCancelled()
        val s = t.snapshot()
        assertEquals(0, s.taps); assertEquals(1, s.failed); assertEquals(0.0, s.successPercent, 0.0)
    }

    @Test fun resetClears() {
        val t = StatsTracker()
        t.onDetected(); t.onDispatched(10.0, 10.0, 0); t.reset()
        assertEquals(0, t.snapshot().detected); assertEquals(0, t.snapshot().taps)
    }
}
