package com.instantreact.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerStateMachineTest {
    @Test fun firesOnceOnRisingEdge() {
        val m = TriggerStateMachine(6)
        assertFalse(m.update(0, false))
        assertTrue(m.update(0, true))
        for (i in 1..50) assertFalse("fotograma $i", m.update(0, true)) // sin pulsaciones duplicadas
    }

    @Test fun rearmsWhenCircleReturnsToGray() {
        val m = TriggerStateMachine(6)
        assertTrue(m.update(2, true))
        assertFalse(m.isArmed(2))
        assertFalse(m.update(2, false))
        assertTrue(m.isArmed(2))
        assertTrue(m.update(2, true))
    }

    @Test fun rapidBlueGrayBlueFiresTwice() {
        val m = TriggerStateMachine(6)
        var fires = 0
        for (b in listOf(true, false, true, false, true)) if (m.update(1, b)) fires++
        assertEquals(3, fires)
    }

    @Test fun circlesAreIndependent() {
        val m = TriggerStateMachine(6)
        assertTrue(m.update(0, true))
        assertTrue(m.update(5, true))
        assertFalse(m.update(0, true))
        assertFalse(m.update(5, true))
    }

    @Test fun rearmFramesDebounce() {
        val m = TriggerStateMachine(1, rearmFrames = 3)
        assertTrue(m.update(0, true))
        assertFalse(m.update(0, false))
        assertFalse(m.update(0, true)) // parpadeo: aún bloqueado
        assertFalse(m.update(0, false)); assertFalse(m.update(0, false)); assertFalse(m.update(0, false))
        assertTrue(m.update(0, true))
    }

    @Test fun resetRearmsEverything() {
        val m = TriggerStateMachine(3)
        m.update(0, true); m.update(1, true)
        m.reset()
        assertTrue(m.update(0, true))
        assertTrue(m.update(1, true))
    }
}
