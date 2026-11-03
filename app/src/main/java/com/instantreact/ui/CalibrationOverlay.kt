package com.instantreact.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.instantreact.core.CircleFinder
import com.instantreact.core.DefaultLayout
import com.instantreact.core.DetectorConfig
import com.instantreact.core.PointCheck
import com.instantreact.core.PointState
import com.instantreact.service.Snapshot
import kotlin.math.hypot

/**
 * Calibración visual con N marcadores numerados arrastrables. Cada marcador tiene su propia área de
 * detección y un indicador: azul = sin comprobar, verde = sobre un círculo, rojo = revisar.
 * Se pueden añadir y quitar puntos, autodetectar los círculos y verificar la colocación.
 */
class CalibrationOverlay(
    private val ctx: Context,
    private val initial: DetectorConfig,
    private val profileName: String,
    private val screenW: Int,
    private val screenH: Int,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSave(cfg: DetectorConfig)
        fun onCancel()

        /** Última captura reducida de la pantalla (sin nuestra superposición), o null. */
        fun grabSnapshot(): Snapshot?
    }

    private class Marker(var x: Float, var y: Float, var r: Float, var state: Int = UNKNOWN)

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val markers = ArrayList<Marker>()
    private var selected = -1

    private val root = FrameLayout(ctx)
    private lateinit var canvasView: MarkerView
    private lateinit var bar: LinearLayout
    private lateinit var countText: TextView
    private lateinit var sizeText: TextView
    private var barOnTop = true
    private var attached = false

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private inner class MarkerView(c: Context) : View(c) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(2).toFloat()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(1).toFloat()
            color = Color.WHITE
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ctx.dp(15).toFloat()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(ctx.dp(3).toFloat(), 0f, 0f, Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0x44000000)
            for ((i, m) in markers.withIndex()) {
                val col = when (m.state) {
                    OK -> Palette.GREEN
                    BAD -> Palette.RED
                    else -> Palette.BLUE
                }
                fill.color = (col and 0x00FFFFFF) or 0x33000000
                ring.color = col
                if (i == selected) {
                    ring.strokeWidth = ctx.dp(4).toFloat()
                    canvas.drawCircle(m.x, m.y, m.r + ctx.dp(5), cross)
                } else {
                    ring.strokeWidth = ctx.dp(2).toFloat()
                }
                canvas.drawCircle(m.x, m.y, m.r, fill)
                canvas.drawCircle(m.x, m.y, m.r, ring)
                val arm = ctx.dp(8).toFloat()
                canvas.drawLine(m.x - arm, m.y, m.x + arm, m.y, cross)
                canvas.drawLine(m.x, m.y - arm, m.x, m.y + arm, cross)
                val mark = when (m.state) {
                    OK -> " ✓"
                    BAD -> " ✗"
                    else -> ""
                }
                canvas.drawText("${i + 1}$mark", m.x, m.y - m.r - ctx.dp(8), label)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for ((i, m) in markers.withIndex()) {
                        val d = hypot(e.x - m.x, e.y - m.y)
                        if (d < bestD) {
                            bestD = d
                            best = i
                        }
                    }
                    selected = if (best >= 0 && bestD <= maxOf(markers[best].r * 1.6f, ctx.dp(48).toFloat())) best else -1
                    updateTexts()
                    invalidate()
                    return selected >= 0
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selected >= 0) {
                        val m = markers[selected]
                        m.x = e.x.coerceIn(0f, width.toFloat())
                        m.y = e.y.coerceIn(0f, height.toFloat())
                        m.state = UNKNOWN
                        invalidate()
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun build() {
        canvasView = MarkerView(ctx)
        root.addView(canvasView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Palette.PANEL, ctx.dp(16).toFloat(), Palette.BLUE_DIM, ctx.dp(1))
            setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(12))
        }
        bar.addView(ctx.text("Calibración · $profileName", 13f, Palette.BLUE, true))
        bar.addView(ctx.text("Arrastra cada marcador al centro de su círculo. Toca uno para seleccionarlo.", 11f, Palette.MUTED))

        val countRow = ctx.row()
        countRow.margins(0, ctx.dp(8), 0, 0)
        countText = ctx.text("", 12f, Palette.TEXT, true)
        countText.weight(1f)
        countRow.addView(countText)
        countRow.addView(ctx.pill("Quitar", false, 12f) { removePoint() })
        countRow.addView(ctx.pill("Añadir", false, 12f) { addPoint() }.margins(ctx.dp(6), 0, 0, 0))
        bar.addView(countRow)

        val sizeRow = ctx.row()
        sizeRow.margins(0, ctx.dp(6), 0, 0)
        sizeText = ctx.text("", 12f, Palette.TEXT, true)
        sizeText.weight(1f)
        sizeRow.addView(sizeText)
        sizeRow.addView(ctx.pill("−", false, 14f) { changeRadius(-2f) })
        sizeRow.addView(ctx.pill("+", false, 14f) { changeRadius(2f) }.margins(ctx.dp(6), 0, 0, 0))
        bar.addView(sizeRow)

        val actions = ctx.row()
        actions.margins(0, ctx.dp(8), 0, 0)
        val auto = ctx.pill("Auto", false, 12f) { autoDetect() }
        val verify = ctx.pill("Verificar", false, 12f) { verify() }
        val move = ctx.pill("⇅", false, 12f) { toggleBar() }
        auto.weight(1f)
        verify.weight(1f)
        (auto.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        (verify.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        actions.addView(auto)
        actions.addView(verify)
        actions.addView(move)
        bar.addView(actions)

        val end = ctx.row()
        end.margins(0, ctx.dp(6), 0, 0)
        val save = ctx.pill("Guardar", true, 12f) { save() }
        val cancel = ctx.pill("Cancelar", false, 12f) { callbacks.onCancel() }
        save.weight(1f)
        cancel.weight(1f)
        (save.layoutParams as LinearLayout.LayoutParams).rightMargin = ctx.dp(4)
        end.addView(save)
        end.addView(cancel)
        bar.addView(end)
        bar.addView(ctx.text("Verde ✓ = sobre un círculo · Rojo ✗ = revisar · Azul = sin comprobar", 10f, Palette.MUTED).margins(0, ctx.dp(6), 0, 0))

        placeBar()
        updateTexts()
    }

    private fun placeBar() {
        root.removeView(bar)
        val barLp = FrameLayout.LayoutParams(ctx.dp(320), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = (if (barOnTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            topMargin = ctx.dp(36)
            bottomMargin = ctx.dp(36)
        }
        root.addView(bar, barLp)
    }

    private fun toggleBar() {
        barOnTop = !barOnTop
        placeBar()
    }

    private fun updateTexts() {
        countText.text = "Puntos: ${markers.size}"
        sizeText.text = if (selected in markers.indices) {
            "Área (punto ${selected + 1}): ${markers[selected].r.toInt()} px"
        } else {
            "Área (todos): ${if (markers.isEmpty()) 0 else markers.map { it.r }.average().toInt()} px"
        }
    }

    private fun changeRadius(delta: Float) {
        if (selected in markers.indices) {
            val m = markers[selected]
            m.r = (m.r + delta).coerceIn(4f, 300f)
            m.state = UNKNOWN
        } else {
            for (m in markers) {
                m.r = (m.r + delta).coerceIn(4f, 300f)
                m.state = UNKNOWN
            }
        }
        updateTexts()
        canvasView.invalidate()
    }

    private fun addPoint() {
        if (markers.size >= DetectorConfig.MAX_POINTS) {
            Toast.makeText(ctx, "Máximo ${DetectorConfig.MAX_POINTS} puntos", Toast.LENGTH_SHORT).show()
            return
        }
        val r = if (markers.isEmpty()) DefaultLayout.defaultRadius(screenW).toFloat() else markers.map { it.r }.average().toFloat()
        val k = markers.size
        val m = Marker(
            screenW / 2f + ((k % 5) - 2) * ctx.dp(28),
            screenH * 0.5f + ((k / 5) % 4) * ctx.dp(28),
            r,
        )
        markers.add(m)
        selected = markers.size - 1
        updateTexts()
        canvasView.invalidate()
    }

    private fun removePoint() {
        if (markers.size <= 1) {
            Toast.makeText(ctx, "Debe quedar al menos un punto", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = if (selected in markers.indices) selected else markers.size - 1
        markers.removeAt(idx)
        selected = -1
        updateTexts()
        canvasView.invalidate()
    }

    private fun defaultPositions(n: Int) {
        val (xs, ys) = DefaultLayout.positions(n, screenW, screenH)
        val r = DefaultLayout.defaultRadius(screenW).toFloat()
        markers.clear()
        for (i in 0 until n) markers.add(Marker(xs[i].toFloat(), ys[i].toFloat(), r))
    }

    private fun loadInitial(offsetX: Int, offsetY: Int) {
        markers.clear()
        val n = initial.count
        if (n == 0) {
            defaultPositions(DetectorConfig.DEFAULT_COUNT)
            return
        }
        var sx = 1f
        var sy = 1f
        if (initial.calibW > 0 && initial.calibH > 0 && (initial.calibW != screenW || initial.calibH != screenH)) {
            val same = (initial.calibW >= initial.calibH) == (screenW >= screenH)
            if (!same) {
                defaultPositions(n)
                return
            }
            sx = screenW.toFloat() / initial.calibW
            sy = screenH.toFloat() / initial.calibH
        }
        for (i in 0 until n) {
            markers.add(
                Marker(
                    initial.xs[i] * sx - offsetX,
                    initial.ys[i] * sy - offsetY,
                    initial.radii[i] * (sx + sy) / 2f,
                ),
            )
        }
    }

    /** Oculta la superposición un instante para ver la pantalla limpia y entrega la captura. */
    private fun withCleanSnapshot(action: (Snapshot) -> Unit) {
        root.visibility = View.INVISIBLE
        handler.postDelayed({
            val snap = callbacks.grabSnapshot()
            root.visibility = View.VISIBLE
            if (snap == null) {
                Toast.makeText(ctx, "No hay captura disponible todavía", Toast.LENGTH_SHORT).show()
            } else {
                action(snap)
            }
        }, 250L)
    }

    private fun checkAll(snap: Snapshot): Int {
        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        var ok = 0
        for (m in markers) {
            val st = PointCheck.evaluate(
                snap.pixels, snap.w, snap.h, snap.step,
                Math.round(m.x + loc[0]), Math.round(m.y + loc[1]), Math.round(m.r),
                initial.blueThreshold,
            )
            m.state = if (st == PointState.BAD) BAD else OK
            if (st != PointState.BAD) ok++
        }
        canvasView.invalidate()
        return ok
    }

    private fun verify(silent: Boolean = false) {
        withCleanSnapshot { snap ->
            val ok = checkAll(snap)
            if (!silent) {
                val msg = if (ok == markers.size) "Los ${markers.size} puntos están sobre un círculo ✓"
                else "$ok de ${markers.size} puntos correctos. Revisa los marcados con ✗."
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun autoDetect() {
        withCleanSnapshot { snap ->
            val found = CircleFinder.find(snap.pixels, snap.w, snap.h, markers.size)
            if (found.isEmpty()) {
                Toast.makeText(ctx, "No encontré círculos grises. Colócalos a mano.", Toast.LENGTH_LONG).show()
                return@withCleanSnapshot
            }
            val loc = IntArray(2)
            canvasView.getLocationOnScreen(loc)
            for ((i, c) in found.withIndex()) {
                if (i >= markers.size) break
                markers[i].x = (c.x * snap.step + snap.step / 2f) - loc[0]
                markers[i].y = (c.y * snap.step + snap.step / 2f) - loc[1]
                markers[i].r = (c.radius * snap.step * 0.55f).coerceIn(4f, 300f)
            }
            val ok = checkAll(snap)
            updateTexts()
            val msg = if (found.size < markers.size) {
                "Encontré ${found.size} de ${markers.size} círculos. Coloca el resto a mano."
            } else {
                "Encontré los ${markers.size} círculos ($ok verificados). Revisa y pulsa Guardar."
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        if (markers.isEmpty()) return
        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val n = markers.size
        callbacks.onSave(
            initial.copy(
                xs = IntArray(n) { Math.round(markers[it].x + loc[0]) },
                ys = IntArray(n) { Math.round(markers[it].y + loc[1]) },
                radii = IntArray(n) { Math.round(markers[it].r) },
                calibrated = true,
                calibW = screenW,
                calibH = screenH,
            ),
        )
    }

    fun show() {
        if (attached) return
        build()
        wm.addView(root, lp)
        attached = true
        canvasView.post {
            val loc = IntArray(2)
            canvasView.getLocationOnScreen(loc)
            loadInitial(loc[0], loc[1])
            updateTexts()
            canvasView.invalidate()
            // Al abrir, comprueba automáticamente si los puntos están sobre círculos.
            handler.postDelayed({ if (attached && selected < 0) verify(silent = true) }, 700L)
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        if (attached) {
            try {
                wm.removeView(root)
            } catch (_: Exception) {
            }
            attached = false
        }
    }

    companion object {
        private const val UNKNOWN = 0
        private const val OK = 1
        private const val BAD = 2
    }
}
