package com.instantreact.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTextTest {
    @Test fun delayShowsConfiguredMs() = assertEquals("Retraso configurado: 30 ms", PanelText.delay(30))

    @Test fun lastShowsBothValues() {
        assertEquals("Último: —", PanelText.last(0.0, 0.0, 0))
        assertEquals("Último: 85 ms · sistema 55 ms", PanelText.last(85.4, 55.2, 3))
    }

    @Test fun tapsCounter() = assertEquals("Pulsaciones: 12", PanelText.taps(12))

    @Test fun stateText() {
        assertEquals("Sin calibrar", PanelText.state(true, false, false))
        assertEquals("Detección activa", PanelText.state(true, true, false))
        assertEquals("Detenido", PanelText.state(false, true, false))
        assertEquals("Gira la pantalla (orientación distinta)", PanelText.state(true, true, true))
    }
}
