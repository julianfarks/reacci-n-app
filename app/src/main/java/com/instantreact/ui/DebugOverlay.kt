package com.instantreact.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.instantreact.core.PointDebug
import com.instantreact.core.PointStatus
import com.instantreact.core.Reject

/**
 * Modo de diagnóstico: ventana transparente que NO recibe toques y muestra, para cada punto, su estado,
 * el color RGB/HSV medido de la zona interior, si se reconoció un cambio real, a qué punto se va a pulsar,
 * el tiempo detección -> pulsación y el motivo del último rechazo.
 *
 * Indicadores: anillo VERDE = detección confirmada; anillo AZUL = pulsación ejecutada; gris = armado;
 * naranja = en espera de volver a gris. Los anillos se dibujan por FUERA del área analizada (el análisis
 * usa solo el 60 % interior), así que el propio diagnóstico no puede provocar detecciones.
 */
class DebugOverlay(private val ctx: Context, private val source: () -> PointDebug) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val bg = Paint().apply { color = Color.argb(200, 8, 12, 22) }

    private val view = object : View(ctx) {
        override fun onDraw(c: Canvas) {
            val d = source()
            val dp = ctx.resources.displayMetrics.density
            ring.strokeWidth = 3f * dp
            num.textSize = 13f * dp
            for (i in 0 until d.n) {
                if (d.radius[i] <= 0) continue
                val color = when (d.status[i]) {
                    PointStatus.DETECTED -> Color.parseColor("#22C55E")
                    PointStatus.PRESSED -> Color.parseColor("#3B82F6")
                    PointStatus.GRAY -> Color.argb(150, 200, 205, 215)
                    else -> Color.parseColor("#F59E0B")
                }
                ring.color = color
                val rr = d.radius[i] * 1.25f + 4f * dp
                c.drawCircle(d.cx[i].toFloat(), d.cy[i].toFloat(), rr, ring)
                c.drawText("${i + 1}", d.cx[i].toFloat(), d.cy[i] - rr - 6f * dp, num)
            }
            // Tarjeta de texto con el detalle de cada punto (esquina superior izquierda).
            txt.textSize = 9.5f * dp
            val lines = ArrayList<String>()
            val last = d.lastChosen
            lines.add("DIAGNÓSTICO · se pulsará: ${if (last >= 0) "punto ${last + 1}" else "—"}")
            for (i in 0 until minOf(d.n, 16)) {
                val rgb = d.rgb[i]
                val t = if (d.detectToTapMs[i] >= 0) " ${d.detectToTapMs[i].toInt()}ms" else ""
                val rej = if (d.reject[i] != Reject.NONE) " · ${Reject.text(d.reject[i])}" else ""
                lines.add(
                    "${i + 1} ${PointStatus.name(d.status[i])}${if (d.realChange[i]) " ✔cambio" else ""} " +
                        "rgb ${rgb shr 16 and 255},${rgb shr 8 and 255},${rgb and 255} " +
                        "hsv ${d.hue[i]}°/${d.sat[i]}/${d.value[i]} az${d.bluePct[i]}% gr${d.grayPct[i]}%$t$rej",
                )
            }
            val lh = txt.textSize * 1.3f
            var w = 0f
            for (l in lines) w = maxOf(w, txt.measureText(l))
            val x0 = 6f * dp
            val y0 = 40f * dp
            c.drawRect(x0, y0, x0 + w + 12f * dp, y0 + lh * lines.size + 8f * dp, bg)
            txt.color = Color.WHITE
            for ((k, l) in lines.withIndex()) c.drawText(l, x0 + 6f * dp, y0 + lh * (k + 1), txt)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            view.invalidate()
            handler.postDelayed(this, 80L)
        }
    }

    fun show() {
        if (attached) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(view, lp)
        attached = true
        handler.post(ticker)
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        if (attached) {
            try { wm.removeView(view) } catch (_: Exception) {}
            attached = false
        }
    }
}
