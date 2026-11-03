package com.instantreact.core

/** Estado compartido (mismo proceso) entre la interfaz, el servicio y el hilo de captura. */
object Engine {
    @Volatile
    var config: DetectorConfig = DetectorConfig()

    /** true mientras el usuario ha pulsado INICIAR. */
    @Volatile
    var running: Boolean = false

    @Volatile
    var captureActive: Boolean = false

    /** true si la orientación actual no coincide con la de la calibración. */
    @Volatile
    var orientationMismatch: Boolean = false

    val stats = StatsTracker()
}
