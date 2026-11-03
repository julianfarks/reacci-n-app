package com.instantreact

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.instantreact.service.Diagnostics
import com.instantreact.service.ReactAccessibilityService
import com.instantreact.service.TapExecutor

/**
 * Prueba de pulsación: muestra un objetivo, ordena a ReactAccessibilityService que lo toque con dispatchGesture()
 * y comprueba que el toque llega. Resultado en pantalla y en Logcat (SELFTEST OK / SELFTEST FAIL).
 * Se puede lanzar desde la app («Probar pulsación») o con el extra booleano «autorun».
 */
class TapTestActivity : Activity() {
    private lateinit var target: TextView
    private lateinit var result: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var attempt = 1
    @Volatile private var touched = false
    private var touchAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B1220")) }
        target = TextView(this).apply {
            text = "OBJETIVO"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E3A8A"))
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    touched = true
                    touchAt = SystemClock.uptimeMillis()
                    v.setBackgroundColor(Color.parseColor("#16A34A"))
                    (v as TextView).text = "¡TOQUE RECIBIDO!"
                }
                true
            }
        }
        root.addView(target, FrameLayout.LayoutParams(600, 600, Gravity.CENTER))
        result = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(32, 96, 32, 32)
            text = "Prueba de pulsación…"
        }
        root.addView(result, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        touched = false
        attempt = 1
        ui.postDelayed({ runTest() }, 1000)
    }

    override fun onPause() {
        ui.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun fail(msg: String) {
        result.text = "✗ $msg"
        result.setTextColor(Color.parseColor("#F87171"))
        Diagnostics.event("SELFTEST FAIL: $msg")
        Log.e(Diagnostics.TAG, "SELFTEST FAIL: $msg")
    }

    private fun runTest() {
        if (ReactAccessibilityService.instance == null) {
            fail("El servicio de accesibilidad no está conectado (onServiceConnected no se ha ejecutado). Actívalo en Ajustes > Accesibilidad.")
            return
        }
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val x = loc[0] + target.width / 2
        val y = loc[1] + target.height / 2
        val t0 = SystemClock.uptimeMillis()
        val accepted = TapExecutor.tap(x, y, 20, object : TapExecutor.Result {
            override fun onCompleted() {
                ui.postDelayed({ verdict(t0) }, 400)
            }

            override fun onFailed() {
                ui.post { fail("El sistema canceló el gesto (dispatchGesture → onCancelled).") }
            }
        })
        if (!accepted) fail("dispatchGesture() rechazó el gesto.")
        
    }

    private fun verdict(t0: Long) {
        if (result.text.startsWith("✓") || result.text.startsWith("✗")) return
        if (touched) {
            val ms = touchAt - t0
            result.text = "✓ Pulsación recibida en el objetivo ($ms ms desde dispatchGesture)."
            result.setTextColor(Color.parseColor("#4ADE80"))
            Diagnostics.event("SELFTEST OK ($ms ms)")
            Log.i(Diagnostics.TAG, "SELFTEST OK")
        } else if (attempt < 3) {
            // La primera pulsación tras abrir la pantalla puede perderse mientras Android termina de mostrarla.
            attempt++
            Diagnostics.event("Prueba de pulsación: reintento $attempt")
            ui.postDelayed({ runTest() }, 800)
        } else {
            fail("El gesto se ejecutó pero el toque no llegó al objetivo.")
        }
    }
}
