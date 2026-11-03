package com.instantreact

import android.app.Application
import com.instantreact.service.Diagnostics

/** Instala el registro de excepciones no capturadas para poder leer la causa exacta de un cierre en el propio móvil. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }
}
