package com.instantreact.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorClassifierTest {
    private val t = 60

    @Test fun grayIsNotBlue() {
        for (v in listOf(60, 90, 128, 170, 220)) assertFalse("gris $v", ColorClassifier.isBlue(v, v, v, t))
    }

    @Test fun blueIsBlue() {
        assertTrue(ColorClassifier.isBlue(40, 120, 255, t))
        assertTrue(ColorClassifier.isBlue(30, 90, 220, t))
        assertTrue(ColorClassifier.isBlue(0, 0, 255, t))
    }

    @Test fun blueSurvivesBrightnessChanges() {
        for (scale in listOf(1.0, 0.85, 0.7, 0.55)) {
            val r = (40 * scale).toInt(); val g = (120 * scale).toInt(); val b = (255 * scale).toInt()
            assertTrue("azul x$scale", ColorClassifier.isBlue(r, g, b, t))
        }
    }

    @Test fun grayStaysGrayWhenBrightnessChanges() {
        for (v in 60..255 step 15) assertFalse(ColorClassifier.isBlue(v, v, v, t))
        // gris con leve tinte frío (típico de compresión / filtros)
        assertFalse(ColorClassifier.isBlue(120, 124, 132, t))
    }

    @Test fun otherColorsAreNotBlue() {
        assertFalse("rojo", ColorClassifier.isBlue(230, 40, 40, t))
        assertFalse("verde", ColorClassifier.isBlue(40, 210, 80, t))
        assertFalse("amarillo", ColorClassifier.isBlue(240, 220, 40, t))
        assertFalse("morado", ColorClassifier.isBlue(150, 60, 220, t))
        assertFalse("magenta", ColorClassifier.isBlue(230, 40, 230, t))
        assertFalse("negro", ColorClassifier.isBlue(0, 0, 10, t))
    }

    @Test fun thresholdControlsSensitivity() {
        // azul pálido
        assertFalse(ColorClassifier.isBlue(150, 170, 230, 60))
        assertFalse(ColorClassifier.isBlue(150, 170, 230, 150))
        assertTrue(ColorClassifier.isBlue(150, 170, 230, 20))
    }

    @Test fun grayDetection() {
        assertTrue(ColorClassifier.isGray(128, 128, 128))
        assertTrue(ColorClassifier.isGray(120, 130, 135))
        assertFalse(ColorClassifier.isGray(40, 120, 255))
    }
}
