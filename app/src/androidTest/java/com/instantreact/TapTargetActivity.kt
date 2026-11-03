package com.instantreact

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CopyOnWriteArrayList

/** Actividad de pruebas: registra cada toque recibido (coordenadas de pantalla y hora). */
class TapTargetActivity : Activity() {
    class Touch(val x: Float, val y: Float, val timeMs: Long)

    val touches = CopyOnWriteArrayList<Touch>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val host = this
        val v = object : View(host) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touches.add(Touch(event.rawX, event.rawY, android.os.SystemClock.elapsedRealtime()))
                }
                return true
            }
        }
        v.setBackgroundColor(Color.DKGRAY)
        setContentView(v)
    }
}
